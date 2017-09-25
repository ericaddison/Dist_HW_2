import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.PrintWriter;
import java.net.Socket;

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
				ObjectInputStream in = new ObjectInputStream(clientSocket.getInputStream());) {

			Object receivedObject;
			while ((receivedObject = in.readObject()) != null) {
				String response = server.processObject(receivedObject);
				out.println(response);
			}
		} catch (EOFException e) {
			server.logWarn("Connection to " + clientSocket.getInetAddress() + " ended unexpectedly.");
		} catch (IOException | ClassNotFoundException e) {
			e.printStackTrace();
		}
	}
}