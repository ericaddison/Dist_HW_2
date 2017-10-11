import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.logging.Level;

public class LamportMutex {
	
//****************************************************************
//	Fields
//****************************************************************	
	private static final int MAX_SERVERS = 10;
	
	private List<InetAddress> servers;
	private List<Integer> ports;
	private LogicalClock clock;
	private Socket[] lamportSockets;
	private PrintWriter[] lamportWriters;
	private BufferedReader[] lamportReaders;
	private int nServers;
	private int serverID;
	private Server server;
	private PriorityBlockingQueue<LamportMessage> Q;
	private int numACKs;
	private ServerSocket serverSocket;
	private Thread connectThread;
	private boolean restart;
	private List<Integer> connectedServers;
	
	
//****************************************************************
//	Lamport Mutex Methods 
//		-- these implement the core of the Lamport algorithm
//****************************************************************	
	
	/**
	 * Request the CS. This method blocks until access to CS is granted based
	 * on Lamport algorithm conditions.
	 */
	public void requestCriticalSection() {
		server.log.log(Level.FINE, "Requesting CS");
		numACKs = 0;
		LamportMessage lm = LamportMessage.REQUEST(serverID, clock);
		
		// enter (timestamp, serverID) of request in Q
		server.log.log(Level.FINEST, "Adding message to Q");
		Q.add(lm);
		
		// send request to N-1 other servers
		broadcastMessage(lm.toString());
		
		// wait for Lamport conditions to be satisfied
		while( numACKs<(nServers-1) || Q.peek().serverID!=serverID){
			try {
				Thread.sleep(10);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		server.log.log(Level.FINE, "Entering CS...");
		
	}	
	
	
	/**
	 * release the CS and send updated data to other servers 
	 */
	public void releaseCS(String data) {
		server.log.log(Level.FINE, "Releasing CS");
		LamportMessage lm = LamportMessage.RELEASE(serverID, clock, data);
		broadcastMessage(lm.toString());
		Q.remove();
	}
	
	
	/**
	 * Process a received LamportMessage based on type: CS_REQUEST, CS_ACK, or CS_RELEASE
	 */
	private void processMessage(LamportMessage lm) {
		
		server.log.log(Level.FINER, "Processing message " + lm.toString());
		
		// behavior determined by message type
		if(lm.type == LamportMessageType.CS_REQUEST){
			Q.add(lm);
			server.log.log(Level.FINEST, "Processing REQUEST message: Q = " + Q);
			sendMessage(lm.serverID, LamportMessage.ACK(serverID, clock).toString());
			
		} else if(lm.type == LamportMessageType.CS_ACK){
			numACKs++;
			server.log.log(Level.FINEST, "Processing ACK message: numAcks = " + numACKs);
			
		} else if(lm.type == LamportMessageType.CS_RELEASE){
			// remove their entry from the Q
			Q.remove();
			server.log.log(Level.FINEST, "Processing RELEASE message: Q = " + Q);
			
			// update server data
			server.syncData(lm.data);
		}
		
		// update clock compared to their clock
		clock = (clock.value()>lm.clock.value())?clock:lm.clock;
		clock.increment();
	}
	
	
//****************************************************************
//	Public Methods
//****************************************************************	

	/**
	 * Constructor 
	 */
	public LamportMutex(List<InetAddress> servers, List<Integer> ports, Server server, boolean restart) {
		this.servers = servers;
		this.ports = ports;
		this.server = server;
		this.serverID = server.getID();
		this.restart = restart;
		nServers = 1;
		Q = new PriorityBlockingQueue<>();
		clock = new LogicalClock(0);
		lamportSockets = new Socket[MAX_SERVERS];
		lamportWriters = new PrintWriter[MAX_SERVERS];
		lamportReaders = new BufferedReader[MAX_SERVERS];
		connectedServers = new ArrayList<>();
		connectedServers.add(serverID);
		
		try {
			serverSocket = new ServerSocket(ports.get(serverID)+1);
		} catch (IOException e1) {
			e1.printStackTrace();
		}
	}
	
	
	/**
	 * Initialize Lamport connections, including listening for incoming connections
	 * And attempting to connect to other servers
	 */
	public void init(){
		startConnectionThread();
		connectToOtherServers(restart, servers.size());
	}
	
	
//****************************************************************
//	Private Methods -- mostly network related
//****************************************************************
	
	/**
	 * Start the infinite TCP listening thread
	 */
	private void startConnectionThread(){
		server.log.log(Level.INFO, "Lamport Mutex initating connections");
		
		// start eternal socket acceptance loop
		connectThread = new Thread(new Runnable(){
			@Override
			public void run() {
				connectionLoop();
			}
		});
		connectThread.start();
	}
	
	
	/**
	 * Infinite TCP connection listener loop
	 */
	private void connectionLoop(){
		while(true){
			server.log.log(Level.FINER, "Listenining for connection on port "+ (ports.get(serverID)+1));
			try {
				// set up connection from unknown server
				Socket sock = serverSocket.accept();
				initIncomingConnection(sock);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
	
	
	/**
	 * Connect to the other Lamport servers. If "restart==false", this will attempt to
	 * connect only to servers with id < this.serverID. If "restart==true", this will
	 * attempt to connect to all other servers.  
	 */
	private void connectToOtherServers(boolean restart, int nOthers){
		// create other initial connections
		int iServer = -1;
		
		// if restarting, start by assuming that you must connect to all other servers
		// this will be updated after connecting to one live server
		// otherwise, on a fresh startup, connect to all servers with serverID less than yours 
		int nConnections = restart ? (nOthers) : serverID+1;
		
		while(connectedServers.size() < nConnections) {
			iServer = (iServer+1) % (restart?nConnections:serverID);
			if (connectedServers.contains(iServer))
				continue; // this socket and thread will be null

			try {
				server.log.fine("Entering connect loop for iServer = " + iServer);
				Socket sock = new Socket();
				try{
					sock.connect(new InetSocketAddress(servers.get(iServer), ports.get(iServer) + 1), Client.TIMEOUT);
					connectedServers.add(iServer);
				} catch (ConnectException e) {
					// could not connect. Wait 1/2 second and move on
					try {
						Thread.sleep(500);
					} catch (InterruptedException e1) {}
					server.log.log(Level.FINER, "Lamport connection NOT made to server "+iServer);
					continue;
				}
				
				nConnections = initOutgoingConnection(sock, iServer, nConnections);
				
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		server.log.fine("Finished in ctor connection loop");
	}

	
	/**
	 * Initialize a new outgoing connection. 
	 */
	private void initIncomingConnection(Socket sock) throws IOException{
		server.log.log(Level.FINER, "Initializing connection with new server");
		
		PrintWriter pw = new PrintWriter(sock.getOutputStream());
		BufferedReader br = new BufferedReader(new InputStreamReader(sock.getInputStream()));
		
		// listen for INIT message from unknown server
		String msg = br.readLine();
		LamportMessage lm = LamportMessage.fromString(msg);
		
		// invalid message ... break connection
		if(lm==null || lm.type!=LamportMessageType.INIT_REQUEST){
			sock.close();
			return;
		}
		
		// get serverID
		int iServer = lm.serverID; 					
		
		if(lamportSockets[iServer] != null){
			server.log.log(Level.WARNING, "Imposter attempt from server ID "+ iServer);
			sock.close();
			return;
		}
						
		initConnectionCommon(sock, pw, br, iServer);

		// respond with current data
		// and current number of servers
		LamportMessage respond = LamportMessage.INIT_RESPOND(serverID, nServers, clock, server.getSerializedData());
		sendMessage(iServer, respond.toString());

	}
	
	
	/**
	 * Initialize a new incoming connection. 
	 */
	private int initOutgoingConnection(Socket sock, int iServer, int nConnections) throws IOException{
		server.log.log(Level.FINER, "Initializing connection with server "+iServer);

		PrintWriter pw = new PrintWriter(sock.getOutputStream());
		BufferedReader br = new BufferedReader(new InputStreamReader(sock.getInputStream()));
		
		// write out init message
		LamportMessage lminit = LamportMessage.INIT_REQUEST(serverID ,clock);
		server.log.log(Level.FINEST, "Sending message " + lminit + " to server " + iServer);
		sendMessage(pw, lminit.toString());
		
		// read back data
		LamportMessage lmresp = receiveMessage(br);
		if(lmresp.type!=LamportMessageType.INIT_RESPOND){
			sock.close();
			connectedServers.remove(iServer);
			return nConnections;
		}
		server.syncData(lmresp.data);
		
		// update number of servers to connect to, if restarting
		nConnections = restart ? lmresp.nServers : nConnections;
		server.log.log(Level.FINER, "Lamport connection made to server "+iServer);
	
		initConnectionCommon(sock, pw, br, iServer);
		
		return nConnections;
	}
	
	
	/**
	 * Common steps for connection initialization 
	 */
	private void initConnectionCommon(Socket sock, PrintWriter pw, BufferedReader br, int iServer){
		// save readers/writers
		lamportSockets[iServer] = sock;
		lamportWriters[iServer] = pw; 
		lamportReaders[iServer] = br;
		incrementNumServers();
		
		// spin off new thread
		final int ii = iServer;
		Thread t = new Thread(new Runnable() {

			@Override
			public void run() {
				listenerLoop(ii);
			}
		});
		server.log.log(Level.FINEST, "Starting Lamport thread "+iServer);
		t.start();
	}
	
	
	/**
	 * Listen infinite listen loop for Lamport messages on the Lamport channel 
	 */
	private void listenerLoop(int otherServerID){
		LamportMessage lm = null;
		server.log.finer("Entering listener loop for server " + otherServerID);
		try{
			while( (lm = receiveMessage(lamportReaders[otherServerID])) != null){
				server.log.log(Level.FINEST, "Received string " + lm.toString() + " from server " + otherServerID);
				// process message based on type
				processMessage(lm);
			}
		} catch (Exception e){}
		finally{
			server.log.log(Level.WARNING, "Uh oh! Lost connection with server " + otherServerID + ": clearing comms");
			lamportSockets[otherServerID] = null;
			lamportWriters[otherServerID] = null; 
			lamportReaders[otherServerID] = null;
			decrementNumServers();
		}
		server.log.finer("Leaving listener loop for server " + otherServerID);
	}
	
	
	/**
	 * Decrement the number of known live servers -- synchronized 
	 */
	private synchronized void decrementNumServers(){
		nServers--;
		server.log.fine("Decrementing nServers: " + nServers);
	}

	
	/**
	 * Increment the number of known live servers -- synchronized 
	 */
	private synchronized void incrementNumServers(){
		nServers++;
		server.log.fine("Incrementing nServers: " + nServers);
	}
	
	
	/**
	 * Send a message to another server, specified by their serverID 
	 */
	private void sendMessage(int otherServerID, String message) {
		if( lamportSockets[otherServerID] != null){
			server.log.log(Level.FINEST, "Sending message " + message + " to server " + otherServerID);
			sendMessage(lamportWriters[otherServerID], message);
		}
	}
	
	
	/**
	 * Send a message to another server, specified by the writer 
	 */
	private void sendMessage(PrintWriter br, String message) {
		br.println(message);
		br.flush();
		
		// increment clock
		clock.increment();
		server.log.log(Level.FINEST, "Incrementing Lamport clock = " + clock.value());
	}
	
	
	/**
	 * Receive a LamportMessage over the given reader   
	 */
	private LamportMessage receiveMessage(BufferedReader br){
		String msg = null;
		try {
			msg = br.readLine();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		// increment clock
				clock.increment();
				server.log.log(Level.FINEST, "Incrementing Lamport clock = " + clock.value());
				
		return LamportMessage.fromString(msg);
	}
	

	/**
	 * Broadcast message to all other listening servers 
	 */
	private void broadcastMessage(String msg) {
		server.log.log(Level.FINER, "Broadcasting message " + msg + " to all servers");
		for (int i = 0; i < servers.size(); i++) {
			if (i == serverID)
				continue;
			sendMessage(i, msg);
		}

	}

}
