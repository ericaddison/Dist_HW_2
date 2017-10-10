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
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
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
	protected final Logger log = Logger.getLogger(this.getClass().getCanonicalName());
	private Level logLevel = Level.ALL;
	
	public Server(String fileName, boolean restart) {
		super();
		parseServerFile(fileName);
		seatAssignments = new ArrayList<>(nSeats);
		for (int i = 0; i < nSeats; i++)
			seatAssignments.add("");

		log.getParent().removeHandler(log.getParent().getHandlers()[0]);
		
		try {
			FileHandler fh = new FileHandler("server_log_" + System.currentTimeMillis() + ".log");
			fh.setFormatter(new SimpleFormatter());
			fh.setLevel(logLevel);
			log.addHandler(fh);
			
			ConsoleHandler ch = new ConsoleHandler();
			ch.setLevel(logLevel);
			log.addHandler(ch);
			log.setLevel(logLevel);
			
			log.info("Server initializing...");
			log.info("ServerID = " + serverID);
			log.info("nServers = " + nServers);
			log.info("nSeats = " + nSeats);
			log.info("my tcp port = " + tcpPort);
			for (int i = 0; i < nServers; i++)
				log.info("Server " + i + ": " + servers.get(i) + ":" + ports.get(i));
			log.info("Server init complete");
			log.info("--------------------------------");
			mutex = new LamportMutex(servers, ports, this, restart);
		} catch (SecurityException | IOException e) {
			e.printStackTrace();
		}
	}

	
	public int getID(){
		return serverID;
	}
	
	protected void syncData(String newData){
		try {
			ObjectMapper mapper = new ObjectMapper();
			TypeReference<ArrayList<String>> typeRef = new TypeReference<ArrayList<String>>() {
			};
			seatAssignments = mapper.readValue(newData, typeRef);
		} catch (IOException e) {
			e.printStackTrace();
		}
		log.fine("Received new data: " + seatAssignments.toString());
	}
	
	protected String getSerializedData(){
		return serializeData();
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

	/**
	 * Run the server. Creates an new thread running the UDP listener, and new
	 * threads for each incoming TCP connection.
	 */
	public void run() {

		// listen for incoming TCP requests
		log.info("Starting TCP listen loop");
		try (ServerSocket serverSocket = new ServerSocket(tcpPort);) {
			while (true) {
				Socket clientSocket = serverSocket.accept();
				log.info("Accepted TCP connection from " + clientSocket.getInetAddress() + " on port "
						+ clientSocket.getLocalPort());
				Thread t = new Thread(new TcpServerTask(this, clientSocket));
				t.start();
			}
		} catch (IOException e) {
			e.printStackTrace();
			log.warning("ERROR in TCP loop: " + e.getMessage());
		}

	}

	public Map<String, String> receiveRequest(BufferedReader in) {
		try {
			String recString = in.readLine();
			if(recString==null)
				recString = "{}";
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
		Map<String, String> response = new HashMap<>();
		String message = "Meep Morp";
		Requests requestType = null;
		
		try{
			requestType = Requests.valueOf(receivedMap.get(MessageFields.REQUEST.toString()));
			String name = receivedMap.get(MessageFields.NAME.toString());
			int reservedSeat = seatAssignments.indexOf(name);
	
			if (requestType==Requests.RESERVE) {
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
	
			} else if (requestType==Requests.BOOKSEAT) {
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
	
			} else if (requestType==Requests.SEARCH) {
				if (reservedSeat == -1) {
					message = "No reservation found for " + name;
					response.put(MessageFields.SEATNUM.toString(), "" + (-1));
				} else {
					message = "Reserved seat for " + name + " is " + (reservedSeat + 1);
					response.put(MessageFields.SEATNUM.toString(), "" + (reservedSeat + 1));
				}
	
			} else if (requestType==Requests.DELETE) {
				if (reservedSeat == -1)
					message = "No reservation found for " + name;
				else {
					message = "Reservation deleted for " + name;
					seatAssignments.set(reservedSeat, "");
				}
			}

		}catch(IllegalArgumentException e){
			// bad requestType parsing...
			message = "Invalid command";
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
		if (args.length < 1 || args.length > 2) {
			System.out.println("ERROR: Provide 1 or 2 arguments");
			System.out.println("\t(1) <file>: the file of inventory");
			System.out.println("\t(2) <restart>: optional flag to restart failed server");
			System.exit(-1);
		}

		String fileName = args[0];
		
		boolean restart = false;
		if(args.length==2 && args[1].equals("restart"))
			restart = true;

		try {
			File file = new File(fileName);
			if (!file.exists() || file.isDirectory())
				throw new FileNotFoundException();
		} catch (FileNotFoundException e) {
			System.out.println("ERROR: Cannot find file " + fileName);
			System.exit(-1);
		}

		Server server = new Server(fileName, restart);
		server.run();
	}

}