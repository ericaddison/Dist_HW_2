import java.io.BufferedReader;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Map;

public class TcpServerTask implements Runnable {

	Socket clientSocket;
	Server server;

	public TcpServerTask(Server server, Socket clientSocket) {
		super();
		this.server = server;
		this.clientSocket = clientSocket;
	}

	@Override
	public void run() {
		
		try (PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true); 
				BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()))) {
			Map<String, String> receivedMap = null;
			while ((receivedMap = server.receiveRequest(in)) != null) {
				server.logInfo("Received request map " + receivedMap + " from " + clientSocket.getInetAddress());
				Map<String, String> respMap = server.processRequest(receivedMap);
				server.logInfo("Sending response map " + respMap + " to " + clientSocket.getInetAddress());
				server.sendResponse(respMap, out);
			}
			
			
		} catch (EOFException e) {
			server.logWarn("Connection to " + clientSocket.getInetAddress() + " ended unexpectedly.");
		} catch (IOException e) {
			e.printStackTrace();
		}
		
	}
	
}