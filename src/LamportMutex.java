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

import com.fasterxml.jackson.core.type.TypeReference;

public class LamportMutex {

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
	private PriorityBlockingQueue<LamportMessage> Q;
	private int numACKs;
	private boolean frontOfQ;

	public LamportMutex(List<InetAddress> servers, List<Integer> ports, int serverID) {
		this.servers = servers;
		this.ports = ports;
		this.serverID = serverID;
		nServers = servers.size();
		Q = new PriorityBlockingQueue<>();
		clock = new LogicalClock();
		lamportThreads = new Thread[nServers];
		lamportSockets = new Socket[nServers];
		lamportWriters = new PrintWriter[nServers];
		lamportReaders = new BufferedReader[nServers];

		// create other connections
		for (int i = 0; i < nServers; i++) {
			if (i == serverID)
				continue; // this socket and thread will be null

			final int ii = i;
			Runnable r = new Runnable() {

				@Override
				public void run() {

					System.out.println("serverID = " + serverID);
					System.out.println("ii = " + ii);
					Socket sock = null;

					try {

						if (serverID < ii) { // act as the "server" for this
												// pair
							ServerSocket serverSocket = new ServerSocket(ports.get(serverID) + 1);
							sock = serverSocket.accept();
						} else { // act as the "client" for this pair

							sock = new Socket();
							sock.connect(new InetSocketAddress(servers.get(ii), ports.get(ii) + 1), Client.TIMEOUT);
							// TODO: come back to this!
							/*
							 * } catch (ConnectException e) { try {
							 * Thread.sleep(1000); // if could not connect, //
							 * wait 1 second and try // again } catch
							 * (InterruptedException e1) { }
							 */
						}
						lamportSockets[ii] = sock;
						lamportWriters[ii] = new PrintWriter(sock.getOutputStream());
						lamportReaders[ii] = new BufferedReader(new InputStreamReader(sock.getInputStream()));
						System.out.println("Connection made from " + ii);
						listenerLoop(ii);
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			};

			Thread t = new Thread(r);
			lamportThreads[i] = t;
			t.start();
		}
	}

	
	void listenerLoop(int otherServerID){
		try {
			String msg = "";
			while( (msg = lamportReaders[otherServerID].readLine()) != null){
				System.out.println("Received string " + msg + " from server " + otherServerID);
				// process message based on type
				receiveMessage(msg);
			}
			
			// might want this to throw an exception so thread can go back to listeneing
			System.out.println("Uh oh! Lost connection with server " + otherServerID);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	
	private void sendMessage(int otherServerID, String message) {
		lamportWriters[otherServerID].println(message);
		
		// increment clock
		clock.increment();
	}

	private void broadcastMessage(String msg) {

		for (int i = 0; i < servers.size(); i++) {
			if (i == serverID)
				continue;
			sendMessage(i, msg);
		}

	}


	// TODO: Add print statements for debugging
	
	// put this in an infinite listen loop
	private void receiveMessage(String msg) {
		
		// deserialize into LamportMessage object
		LamportMessage lm = LamportMessage.fromString(msg);
		
		// behavior determined by message type
		if(lm.type == LamportMessageType.CS_REQUEST){
			Q.add(lm);	// add his timestamp or our timestamp?
			sendMessage(lm.serverID, LamportMessage.ACK(serverID).toString());
		} else if(lm.type == LamportMessageType.CS_ACK){
			numACKs++;
		} else if(lm.type == LamportMessageType.CS_RELEASE){
			// remove their entry from the Q
			Q.remove();
		}
		clock.increment();
	}

	
	public void releaseCS(String data) {
		LamportMessage lm = LamportMessage.RELEASE(serverID, data);
		broadcastMessage(lm.toString());
		Q.remove();
	}

	// this is the request made by a server to enter the CS
	public void requestCriticalSection() {
		
		numACKs = 0;
		LamportMessage lm = new LamportMessage(LamportMessageType.CS_REQUEST, serverID, clock);
		
		// enter (timestamp, serverID) of request in Q
		Q.add(lm);
		
		// send request to N-1 other servers
		broadcastMessage(lm.toString());
		
		// wait for N-1 numAcks
		while(numACKs<nServers-1 && !(Q.peek().serverID==serverID)){
			try {
				Thread.sleep(10);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		
	}

}
