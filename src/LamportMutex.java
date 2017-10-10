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

	private static final int MAX_SERVERS = 10;
	
	private List<InetAddress> servers;
	private List<Integer> ports;
	private LogicalClock clock;
	private Socket[] lamportSockets; // each server has N-1 sockets to
										// communicate with the N-1 other
										// servers
	private Thread[] lamportThreads;
	private PrintWriter[] lamportWriters;
	private BufferedReader[] lamportReaders;
	private int nServers;
	private int serverID;
	private Server server;
	private PriorityBlockingQueue<LamportMessage> Q;
	private int numACKs;
	private ServerSocket serverSocket;
	private Thread connectThread;

	public LamportMutex(List<InetAddress> servers, List<Integer> ports, Server server, boolean restart) {
		this.servers = servers;
		this.ports = ports;
		this.server = server;
		this.serverID = server.getID();
		nServers = 1;
		Q = new PriorityBlockingQueue<>();
		clock = new LogicalClock(0);
		lamportThreads = new Thread[MAX_SERVERS];
		lamportSockets = new Socket[MAX_SERVERS];
		lamportWriters = new PrintWriter[MAX_SERVERS];
		lamportReaders = new BufferedReader[MAX_SERVERS];

		try {
			serverSocket = new ServerSocket(ports.get(serverID)+1);
		} catch (IOException e1) {
			e1.printStackTrace();
		}
		
		server.log.log(Level.INFO, "Lamport Mutex initating connections");
		
		// start eternal socket acceptance loop
		connectThread = new Thread(new Runnable(){

			@Override
			public void run() {
				connectionLoop();
			}
			
		});
		connectThread.start();
		// create other initial connections
		int iServer = -1;
		List<Integer> connectedServers = new ArrayList<>();
		connectedServers.add(serverID);
		
		// if restarting, start by assuming that you must connect to all other servers
		// this will be updated after connecting to one live server
		// otherwise, on a fresh startup, connect to all servers with serverID less than yours 
		int nConnections = restart ? (servers.size()) : serverID+1;
		
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
				lamportSockets[iServer] = sock;
				lamportWriters[iServer] = new PrintWriter(sock.getOutputStream());
				lamportReaders[iServer] = new BufferedReader(new InputStreamReader(sock.getInputStream()));
				incrementNumServers();
				
				// write out init message
				LamportMessage lminit = LamportMessage.INIT_REQUEST(serverID ,clock);
				sendMessage(iServer, lminit.toString());
				
				// read back data
				LamportMessage lmresp = receiveMessage(lamportReaders[iServer]);
				if(lmresp.type!=LamportMessageType.INIT_RESPOND){
					sock.close();
					connectedServers.remove(iServer);
					continue;
				}
				System.out.println("Received message " + lmresp);
				server.syncData(lmresp.data);
				
				// update number of servers to connect to, if restarting
				nConnections = restart ? lmresp.nServers : nConnections;
				server.log.log(Level.FINER, "Lamport connection made to server "+iServer);
				
				final int ii = iServer;
				Thread t = new Thread(new Runnable() {

					@Override
					public void run() {
						listenerLoop(ii);
					}
				});
				lamportThreads[iServer] = t;
				server.log.log(Level.FINEST, "Starting Lamport thread "+iServer);
				t.start();
				
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		server.log.fine("Finished in ctor connection loop");
	}

	
	void connectionLoop(){
		
		while(true){
			server.log.log(Level.FINER, "Listenining for connection on port "+ (ports.get(serverID)+1));
			try {
				
				// set up connection from unknown server
				Socket sock = serverSocket.accept();
				PrintWriter pw = new PrintWriter(sock.getOutputStream());
				BufferedReader br = new BufferedReader(new InputStreamReader(sock.getInputStream()));
				
				// listen for INIT message from unknown server
				String msg = br.readLine();
				LamportMessage lm = LamportMessage.fromString(msg);
				
				// invalid message ... break connection
				if(lm==null || lm.type!=LamportMessageType.INIT_REQUEST){
					sock.close();
					continue;
				}
				
				// get serverID
				int iServer = lm.serverID; 					
				
				if(lamportSockets[iServer] != null){
					server.log.log(Level.WARNING, "Imposter attempt from server ID "+ iServer);
					sock.close();
					continue;
				}
				
				// save readers/writers
				lamportSockets[iServer] = sock;
				lamportWriters[iServer] = pw; 
				lamportReaders[iServer] = br;
				incrementNumServers();

				// respond with current data
				// and current number of servers
				LamportMessage respond = LamportMessage.INIT_RESPOND(serverID, nServers, clock, server.getSerializedData());
				sendMessage(iServer, respond.toString());
				
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
				
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
	
	
	void listenerLoop(int otherServerID){
		LamportMessage lm = null;
		server.log.finer("Entering listener loop for server " + otherServerID);
		try{
			while( (lm = receiveMessage(lamportReaders[otherServerID])) != null){
				server.log.log(Level.FINEST, "Received string " + lm.toString() + " from server " + otherServerID);
				// process message based on type
				processMessage(lm);
			}
		} catch (NullPointerException e){
			server.log.log(Level.WARNING, "Uh oh! Lost connection with server " + otherServerID + ": clearing comms");
			lamportSockets[otherServerID] = null;
			lamportWriters[otherServerID] = null; 
			lamportReaders[otherServerID] = null;
			decrementNumServers();
		}
		server.log.finer("Leaving listener loop for server " + otherServerID);
	}
	
	private synchronized void decrementNumServers(){
		nServers--;
		server.log.fine("Decrementing nServers: " + nServers);
	}

	private synchronized void incrementNumServers(){
		nServers++;
		server.log.fine("Incrementing nServers: " + nServers);
	}
	
	private void sendMessage(int otherServerID, String message) {
		if( lamportSockets[otherServerID] != null){
			server.log.log(Level.FINEST, "Sending message " + message + " to server " + otherServerID);
			lamportWriters[otherServerID].println(message);
			lamportWriters[otherServerID].flush();
			
			// increment clock
			clock.increment();
			server.log.log(Level.FINEST, "Incrementing Lamport clock = " + clock.value());
		}
	}

	
	private LamportMessage receiveMessage(BufferedReader br){
		String msg = null;
		try {
			msg = br.readLine();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return LamportMessage.fromString(msg);
	}

	private void broadcastMessage(String msg) {

		server.log.log(Level.FINER, "Broadcasting message " + msg + " to all servers");
		for (int i = 0; i < servers.size(); i++) {
			if (i == serverID)
				continue;
			sendMessage(i, msg);
		}

	}


	// TODO: Add print statements for debugging
	
	private void processMessage(LamportMessage lm) {
		
		server.log.log(Level.FINER, "Received message " + lm.toString());
		
		// behavior determined by message type
		if(lm.type == LamportMessageType.CS_REQUEST){
			server.log.log(Level.FINEST, "Processing REQUEST message");
			Q.add(lm);	// add his timestamp or our timestamp?
			sendMessage(lm.serverID, LamportMessage.ACK(serverID, clock).toString());
		} else if(lm.type == LamportMessageType.CS_ACK){
			server.log.log(Level.FINEST, "Processing ACK message");
			numACKs++;
		} else if(lm.type == LamportMessageType.CS_RELEASE){
			server.log.log(Level.FINEST, "Processing RELEASE message");
			// remove their entry from the Q
			Q.remove();
			
			// update server data
			server.syncData(lm.data);
		}
		clock = (clock.value()>lm.clock.value())?clock:lm.clock;
		clock.increment();
	}

	
	public void releaseCS(String data) {
		server.log.log(Level.FINE, "Releasing CS");
		LamportMessage lm = LamportMessage.RELEASE(serverID, clock, data);
		broadcastMessage(lm.toString());
		Q.remove();
	}

	// this is the request made by a server to enter the CS
	public void requestCriticalSection() {
		server.log.log(Level.FINE, "Requesting CS");
		numACKs = 0;
		LamportMessage lm = LamportMessage.REQUEST(serverID, clock);
		
		// enter (timestamp, serverID) of request in Q
		server.log.log(Level.FINEST, "Adding message to Q");
		Q.add(lm);
		
		// send request to N-1 other servers
		broadcastMessage(lm.toString());
		
/*		server.log.finest("numAcks = " + numACKs + " / " + (nServers-1));
		server.log.finest((numACKs<(nServers-1))+"");
		server.log.finest((Q.peek().serverID!=serverID)+"");
		server.log.finest((numACKs<(nServers-1) && Q.peek().serverID!=serverID)+"");
		server.log.finest("Q = " + Q.peek().serverID);
*/
		// wait for N-1 numAcks and to be at front of Q
		while( numACKs<(nServers-1) || Q.peek().serverID!=serverID){
			try {
				Thread.sleep(10);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		server.log.log(Level.FINE, "Entering CS...");
		
	}

}
