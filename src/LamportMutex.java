import java.io.IOException;
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

	public enum LamportMessageType {
		CS_REQUEST, CS_ACK, CS_RELEASE
	}

	private List<InetAddress> servers;
	private List<Integer> ports;
	private Socket[] lamportSockets; // each server has N-1 sockets to
										// communicate with the N-1 other
										// servers
	private Thread[] lamportThreads;
	private int nServers;
	private int serverID;
	private PriorityBlockingQueue<LamportRequest> Q;
	private int numACKs;

	public LamportMutex(List<InetAddress> servers, List<Integer> ports, int serverID) {
		this.servers = servers;
		this.ports = ports;
		this.serverID = serverID;
		nServers = servers.size();
		Q = new PriorityBlockingQueue<>();
		lamportThreads = new Thread[nServers];
		lamportSockets = new Socket[nServers];

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

					if (serverID < ii) { // act as the "server" for this pair
						try (ServerSocket serverSocket = new ServerSocket(ports.get(serverID) + 1);) {
							Socket clientSocket = serverSocket.accept();
							lamportSockets[ii] = clientSocket;
							System.out.println("Connection made from " + ii);
							// loop forever
						} catch (IOException e) {
							e.printStackTrace();
						}

					} else { // act as the "client" for this pair

						try (Socket sock = new Socket();) {
							sock.connect(new InetSocketAddress(servers.get(ii), ports.get(ii) + 1), Client.TIMEOUT);
							lamportSockets[ii] = sock;
							System.out.println("Connection made to " + ii);
							
						// TODO: come back to this!
						/*} catch (ConnectException e) {
							try {
								Thread.sleep(1000); // if could not connect,
													// wait 1 second and try
													// again
							} catch (InterruptedException e1) {
							}*/
						} catch (IOException e) {
							e.printStackTrace();
						}
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
		LamportRequest r = new LamportRequest(serverID, c);
		broadcastMessage(r.toString());
		// lreceiveFromAll();
		Q.add(r);
	}

	private void sendMessage(int serverID, String message) {

	}

	private void broadcastMessage(String msg) {

		for (int i = 0; i < servers.size(); i++) {
			if (i != serverID)
				;
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
