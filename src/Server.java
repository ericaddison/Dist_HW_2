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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

public class Server {

//****************************************************************
//	Fields
//****************************************************************
	
	protected final Logger log = Logger.getLogger(this.getClass().getCanonicalName());
	
	private int tcpPort;
	private int serverID;
	private int nServers;
	private int nSeats;
	private List<String> seatAssignments;
	private List<InetAddress> servers;
	private List<Integer> ports;
	private LamportMutex mutex;
	private Level logLevel = Level.ALL;
	
	
//****************************************************************
//	Public methods
//****************************************************************
	
	public Server(String fileName, boolean restart) {
		super();
		parseServerFile(fileName);
		seatAssignments = new ArrayList<>(nSeats);
		for (int i = 0; i < nSeats; i++)
			seatAssignments.add("");

		log.getParent().removeHandler(log.getParent().getHandlers()[0]);
		
		try {
			// check if logs dir exists
			File logDir = new File("./logs/"); 
			if( !(logDir.exists()) )
				logDir.mkdir();
				
			FileHandler fh = new FileHandler("logs/server_log_" + serverID + ".log");
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
			mutex.init();
		} catch (SecurityException | IOException e) {
			e.printStackTrace();
		}
	}

	
	public int getID(){
		return serverID;
	}

	
	/**
	 * Run the server. Creates a new TcpServerTask thread for each incoming client connection 
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


//****************************************************************
//	Protected methods
//****************************************************************
	
	/**
	 *	Synchronize with other servers by updating to match incoming data 
	 */
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
	
	
	/**
	 *	Return the serialized data 
	 */
	protected String getSerializedData(){
		return serializeData();
	}
	
	
	/**
	 * Read an incoming request as a JSON object  
	 */
	protected Map<String, String> receiveRequest(BufferedReader in) {
		try {
			String recString = in.readLine();
			if(recString==null)
				return null;
			ObjectMapper mapper = new ObjectMapper();
			TypeReference<HashMap<String, String>> typeRef = new TypeReference<HashMap<String, String>>() {
			};
			return mapper.readValue(recString, typeRef);
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}
	
	
	/**
	 * Send response to client as a JSON object   
	 */
	protected void sendResponse(Map<String, String> respMap, PrintWriter out) {
		ObjectMapper mapper = new ObjectMapper();
		try {
			String jsonResponse = mapper.writer().writeValueAsString(respMap);
			out.println(jsonResponse);
			out.flush();
		} catch (JsonProcessingException e1) {
			e1.printStackTrace();
		}
	}
	
	
	/**
	 * Process a request  
	 */
	protected Map<String, String> processRequest(Map<String, String> receivedMap) {

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
		
		// release the critical section
		mutex.releaseCS(serializeData());

		return response;
	}

	
//****************************************************************
//	Private methods
//****************************************************************
	
	/**
	 * Parse the input text file
	 */
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
	 * serialize data into a JSON string  
	 */
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
	
	
	
//****************************************************************
//	main()
//****************************************************************
	
	/**
	 * Run the main program
	 * 
	 * @param args
	 *            command line input. Expects [server file] [optional "restart"]
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