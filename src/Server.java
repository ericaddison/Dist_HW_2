import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public class Server {

	private int tcpPort;
	private int serverID;
	private int nServers;
	private int nSeats;
	private String[] seatAssignments;
	private List<InetAddress> servers;
	private List<Integer> ports;
	private final Logger log = Logger.getLogger(Server.class.getCanonicalName());

	public Server(String fileName) {
		super();

		parseServerFile(fileName);
		
		try {
			FileHandler fh = new FileHandler("server_log_" + System.currentTimeMillis() + ".log");
			fh.setFormatter(new SimpleFormatter());
			log.addHandler(fh);
			logInfo("Server initializing...");
			logInfo("ServerID = " + serverID);
			logInfo("nServers = " + nServers);
			logInfo("nSeats = " + nSeats);
			for(int i=0; i<nServers; i++)
				logInfo("Server " + i + ": " + servers.get(i) + ":" + ports.get(i));
			logInfo("Server init complete");
			logInfo("--------------------------------");
		} catch (SecurityException | IOException e) {
			e.printStackTrace();
		}
	}

	private void parseServerFile(String fileName) {
		try(BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(fileName)))){
			
			// line 1 = serverID nServers nSeats
			String[] toks = br.readLine().split(" ");
			serverID = Integer.parseInt(toks[0]);
			nServers = Integer.parseInt(toks[1]);
			nSeats = Integer.parseInt(toks[2]);
			
			// remaining lines = server locations
			String nextServer = "";
			servers = new ArrayList<>(nServers);
			ports = new ArrayList<>(nServers);
			while( (nextServer = br.readLine()) != null){
				String[] serverToks = nextServer.split(":");
				servers.add(InetAddress.getByName(serverToks[0]));
				ports.add(Integer.parseInt(serverToks[1]));
			}
			
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public synchronized void logInfo(String mesg) {
		log.log(Level.INFO, mesg);
	}

	public synchronized void logWarn(String mesg) {
		log.log(Level.WARNING, mesg);
	}

	/**
	 * Run the server. Creates an new thread running the UDP listener, and new
	 * threads for each incoming TCP connection.
	 */
	public void run() {

		// listen for incoming TCP requests
		logInfo("Starting TCP listen loop");
		try (ServerSocket serverSocket = new ServerSocket(tcpPort);) {
			while (true) {
				Socket clientSocket = serverSocket.accept();
				logInfo("Accepted TCP connection from " + clientSocket.getInetAddress() + " on port "
						+ clientSocket.getLocalPort());
				Thread t = new Thread(new TcpServerTask(this, clientSocket));
				t.start();
			}
		} catch (IOException e) {
			e.printStackTrace();
			logWarn("ERROR in TCP loop: " + e.getMessage());
		}

	}
	
	/**
	 * Run the main program
	 * 
	 * @param args
	 *            command line input. Expects [tcpPort] [inventory
	 *            file]
	 */
	public static void main(String[] args) {
		if (args.length != 1) {
			System.out.println("ERROR: Provide 1 argument");
			System.out.println("\t(1) <file>: the file of inventory");

			System.exit(-1);
		}
		
		
		String fileName = args[0];

		Server server = new Server(fileName);
		server.run();
	}
	
}