import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

public class Server {

	private int tcpPort;
	private int serverID;
	private int nServers;
	private int nSeats;
	private List<String> seatAssignments;
	private List<InetAddress> servers;
	private List<Integer> ports;
	private LamportMutex mutex;
	private LogicalClock clock;
	private final Logger log = Logger.getLogger(Server.class.getCanonicalName());

	public Server(String fileName) {
		super();

		parseServerFile(fileName);
		seatAssignments = new ArrayList<>(nSeats);
		for (int i = 0; i < nSeats; i++)
			seatAssignments.add("");

		mutex = new LamportMutex(servers, ports, this);
		
		try {
			FileHandler fh = new FileHandler("server_log_" + System.currentTimeMillis() + ".log");
			fh.setFormatter(new SimpleFormatter());
			log.addHandler(fh);
			logInfo("Server initializing...");
			logInfo("ServerID = " + serverID);
			logInfo("nServers = " + nServers);
			logInfo("nSeats = " + nSeats);
			logInfo("my tcp port = " + tcpPort);
			for (int i = 0; i < nServers; i++)
				logInfo("Server " + i + ": " + servers.get(i) + ":" + ports.get(i));
			logInfo("Server init complete");
			logInfo("--------------------------------");
		} catch (SecurityException | IOException e) {
			e.printStackTrace();
		}
	}

	
	public int getID(){
		return serverID;
	}
	
	protected void syncData(String newData){
		String[] toks = newData.split(", ");
		seatAssignments = Arrays.asList(toks);
	}
	
	private void parseServerFile(String fileName) {
		try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(fileName)))) {

			// line 1 = serverID nServers nSeats
			String[] toks = br.readLine().split(" ");
			serverID = Integer.parseInt(toks[0])-1;
			nServers = Integer.parseInt(toks[1]);
			nSeats = Integer.parseInt(toks[2]);

			// remaining lines = server locations
			String nextServer = "";
			servers = new ArrayList<>(nServers);
			ports = new ArrayList<>(nServers);
			while ((nextServer = br.readLine()) != null) {
				String[] serverToks = nextServer.split(":");
				servers.add(InetAddress.getByName(serverToks[0]));
				ports.add(Integer.parseInt(serverToks[1]));
			}
			tcpPort = ports.get(serverID);

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

	public Map<String, String> receiveRequest(BufferedReader in) {
		try {
			String recString = in.readLine();
			ObjectMapper mapper = new ObjectMapper();
			TypeReference<HashMap<String, String>> typeRef = new TypeReference<HashMap<String, String>>() {
			};
			return mapper.readValue(recString, typeRef);
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}

	public void sendResponse(Map<String, String> respMap, PrintWriter out) {
		ObjectMapper mapper = new ObjectMapper();
		try {
			String jsonResponse = mapper.writer().writeValueAsString(respMap);
			out.println(jsonResponse);
			out.flush();
		} catch (JsonProcessingException e1) {
			e1.printStackTrace();
		}
	}

	// *************************************************
	// Lamport stuff


	// receive

	// *************************************************

	public Map<String, String> processRequest(Map<String, String> receivedMap) {

		// this blocks until we have permission
		mutex.requestCriticalSection();

		String requestType = receivedMap.get(MessageFields.REQUEST.toString());
		String name = receivedMap.get(MessageFields.NAME.toString());
		int reservedSeat = seatAssignments.indexOf(name);
		Map<String, String> response = new HashMap<>();
		String message = "Meep Morp";

		if (requestType.equals(Requests.RESERVE.toString())) {
			if (seatAssignments.contains(name))
				message = "Seat already booked against name provided.";
			else {
				int nextSeat = seatAssignments.indexOf("");
				if (nextSeat == -1) {
					message = "Sold out - no seat available.";
					response.put(MessageFields.SEATNUM.toString(), "-1");
				} else {
					seatAssignments.set(nextSeat, name);
					message = "Seat assigned to you is " + (nextSeat + 1);
					response.put(MessageFields.SEATNUM.toString(), "" + (nextSeat + 1));
				}
			}

		} else if (requestType.equals(Requests.BOOKSEAT.toString())) {
			int seatNum = Integer.parseInt(receivedMap.get(MessageFields.SEATNUM.toString()));
			if (reservedSeat == seatNum)
				message = "Seat already booked against name provided.";
			else if (reservedSeat >= 0) {
				message = "Seat " + (reservedSeat + 1) + " is already booked against name " + name;
				response.put(MessageFields.SEATNUM.toString(), "" + (reservedSeat + 1));
			} else if (!seatAssignments.get(seatNum - 1).equals("")) {
				message = "Seat " + seatNum + " is not available.";
			} else {
				seatAssignments.set(seatNum - 1, name);
				message = "Seat assigned to you is " + (seatNum);
				response.put(MessageFields.SEATNUM.toString(), "" + (seatNum));
			}

		} else if (requestType.equals(Requests.SEARCH.toString())) {
			if (reservedSeat == -1) {
				message = "No reservation found for " + name;
				response.put(MessageFields.SEATNUM.toString(), "" + (-1));
			} else {
				message = "Reserved seat for " + name + " is " + (reservedSeat + 1);
				response.put(MessageFields.SEATNUM.toString(), "" + (reservedSeat + 1));
			}

		} else if (requestType.equals(Requests.DELETE.toString())) {
			if (reservedSeat == -1)
				message = "No reservation found for " + name;
			else {
				message = "Reservation deleted for " + name;
				seatAssignments.set(reservedSeat, "");
			}
		}

		response.put(MessageFields.MESSAGE.toString(), message);

		mutex.releaseCS(serializeData());

		return response;
	}

	
	private String serializeData() {
		ObjectMapper mapper = new ObjectMapper();

		try {
			String serData = mapper.writer().writeValueAsString(seatAssignments);
			return serData;
		} catch (JsonProcessingException e) {
			e.printStackTrace();
		}
		return null;
	}
	
	
	
	/**
	 * Run the main program
	 * 
	 * @param args
	 *            command line input. Expects [tcpPort] [inventory file]
	 */
	public static void main(String[] args) {
		if (args.length != 1) {
			System.out.println("ERROR: Provide 1 argument");
			System.out.println("\t(1) <file>: the file of inventory");
			System.exit(-1);
		}

		String fileName = args[0];
		try {
			File file = new File(fileName);
			if (!file.exists() || file.isDirectory())
				throw new FileNotFoundException();
		} catch (FileNotFoundException e) {
			System.out.println("ERROR: Cannot find file " + fileName);
			System.exit(-1);
		}

		Server server = new Server(fileName);
		server.run();
	}

}