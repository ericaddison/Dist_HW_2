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

	public LamportMutex(List<InetAddress> servers, List<Integer> ports, int serverID) {
		this.servers = servers;
		this.ports = ports;
		this.serverID = serverID;
		nServers = servers.size();
		Q = new PriorityBlockingQueue<>();
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

	private void sendRequest(LogicalClock c) {
		numACKs = 0;
		LamportMessage r = new LamportMessage(serverID, c);
		broadcastMessage(r.toString());
		//receiveFromAll();
		Q.add(r);
	}

	
	void listenerLoop(int otherServerID){
		try {
			while(true){
				String line = lamportReaders[otherServerID].readLine();
				System.out.println("Received string " + line + " from server " + otherServerID);
				// process message based on type
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	
	private void sendMessage(int otherServerID, String message) {
		lamportWriters[otherServerID].println(message);
	}

	private void broadcastMessage(String msg) {

		for (int i = 0; i < servers.size(); i++) {
			if (i == serverID)
				continue;
			sendMessage(i, msg);
		}

	}

	private void loop() {

		while (true) {
			// sit there and listen
			// on active TCP connections with each other server????
		}

	}

	// put this in an infinite listen loop
	private void receiveMessage() {
		// behavior determined by message type
		// if type == CS_REQUEST
		// process a request ... parse a LamportRequest
		// Q.add(r);
		// send ACK
		// etc.

	}

	// send to this server

	private void releaseCS(String data) {
		// now send data packed into a message with CS_RELEASE as messagetype

		TypeReference<List<String>> typeRef = new TypeReference<List<String>>() {
		};

	}

}
