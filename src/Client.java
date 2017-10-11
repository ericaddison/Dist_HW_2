import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

public class Client {
		
//****************************************************************
//	Fields
//****************************************************************	
	public static final int TIMEOUT=100;
	
	private Socket tcpSocket = null;
	private PrintWriter out = null;
	private BufferedReader in = null;
	private int nServers;
	private List<InetAddress> servers;
	private List<Integer> ports;
	private List<String> commands;
	
	
//****************************************************************
//	Public Methods
//****************************************************************	

	public Client(String fileName) {
		super();
		parseClientFile(fileName);
	}
	
	
	/**
	 * Run the client command-line interface
	 */
	public void run() throws SocketTimeoutException{

		try (Scanner sc = new Scanner(System.in);) {
			// connect TCP by default
			connectTCP();
			
			// execute preloaded commands
			for( String command : commands ){
				System.out.println("Executing preloaded command: " + command);
				String response = processCommand(command);
				System.out.println(">>> " + response);
			}
			
			// main command loop
			System.out.print(">>> ");
			while (sc.hasNextLine()){
				String response = processCommand(sc.nextLine());
				System.out.print(response + " \n>>> ");
			}

		} catch (Exception e){
			e.printStackTrace();
		}

		System.out.println("Good Bye!");
	}
	
//****************************************************************
//	Protected Methods
//****************************************************************
	
	/**
	 * Send a JSON formatted request to the server
	 */
	protected void sendRequest(Map<String, String> reqMap) {
		ObjectMapper mapper = new ObjectMapper();
		try {
			String jsonRequest = mapper.writer().writeValueAsString(reqMap);
			out.println(jsonRequest);
			out.flush();
		} catch (JsonProcessingException e1) {
			e1.printStackTrace();
		}
	}
	
	
	/**
	 * Receive a JSON formatted response from the server
	 */
	protected Map<String, String> receiveResponse() {
		try {
			String recString = in.readLine();
			if( recString == null)
				return null;
			
			ObjectMapper mapper = new ObjectMapper();
			TypeReference<HashMap<String, String>> typeRef 
			  = new TypeReference<HashMap<String, String>>() {};
			  
			  return mapper.readValue(recString, typeRef);
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}
	
	
	
//****************************************************************
//	Private Methods
//****************************************************************

	/**
	 * Parse client config file 
	 */
	private void parseClientFile(String fileName) {
		try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(fileName)))) {

			// line 1 = serverID nServers nSeats
			nServers = Integer.parseInt(br.readLine());
			for(int i=0; i<nServers; i++)

			// remaining lines = server locations
			servers = new ArrayList<>(nServers);
			ports = new ArrayList<>(nServers);
			for(int i=0; i<nServers; i++){
				String nextServer = br.readLine();
				String[] serverToks = nextServer.split(":");
				servers.add(InetAddress.getByName(serverToks[0]));
				ports.add(Integer.parseInt(serverToks[1]));
			}
			
			commands = new ArrayList<>();
			String nextCommand = "";
			while ((nextCommand = br.readLine()) != null) {
				commands.add(nextCommand);
			}

		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	
	/**
	 * Connect to the server via TCP
	 */
	private void connectTCP() throws SocketTimeoutException{
		try {
			
			for(int serverNum=0; serverNum<nServers; serverNum++){
				try{
					tcpSocket = new Socket();
					tcpSocket.connect(new InetSocketAddress(servers.get(serverNum), ports.get(serverNum)), TIMEOUT);
					break;
				} catch (SocketTimeoutException e){
					System.out.println("Timed out trying to connect to " + servers.get(serverNum) + ":" + ports.get(serverNum));
				} catch (ConnectException e){}
				
				if(serverNum==(nServers-1))
					throw new SocketTimeoutException("Failed to connect to any server: please try again later!\n\n");
			}
			
			out = new PrintWriter(tcpSocket.getOutputStream());
			in = new BufferedReader(new InputStreamReader(tcpSocket.getInputStream()));
		} catch (SocketTimeoutException e){
			throw e;
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	
	/**
	 *	Process a command string 
	 */
	private String processCommand(String command){
		String[] tokens = command.split(" ");
		Map<String, String> reqMap = new HashMap<>();

		if (tokens[0].equals("reserve")){
			if(tokens.length<2){
				return "NOTE: not enough tokens in reserve string";
			}
			reqMap.put(MessageFields.REQUEST.toString(), Requests.RESERVE.toString());
			reqMap.put(MessageFields.NAME.toString(), tokens[1]);
		}
			
		
		else if (tokens[0].equals("bookSeat")){
			if(tokens.length<3){
				return "NOTE: not enough tokens in bookseat string";
			}
			reqMap.put(MessageFields.REQUEST.toString(), Requests.BOOKSEAT.toString());
			reqMap.put(MessageFields.NAME.toString(), tokens[1]);
			reqMap.put(MessageFields.SEATNUM.toString(), tokens[2]);
		}

		else if (tokens[0].equals("search")){
			if(tokens.length<2){
				return "NOTE: not enough tokens in search string";
			}
			reqMap.put(MessageFields.REQUEST.toString(), Requests.SEARCH.toString());
			reqMap.put(MessageFields.NAME.toString(), tokens[1]);
		}

		else if (tokens[0].equals("delete")){
			if(tokens.length<2){
				return "NOTE: not enough tokens in delete string";
			}
			reqMap.put(MessageFields.REQUEST.toString(), Requests.DELETE.toString());
			reqMap.put(MessageFields.NAME.toString(), tokens[1]);
		}

		else{
			return "ERROR: No such command";
		}
		
		sendRequest(reqMap);
		Map<String, String> response = receiveResponse();
		
		// if connection to server failed, try to reconnect
		if( response==null ){
			try {
				connectTCP();
			} catch (SocketTimeoutException e) {
				e.printStackTrace();
			}
			sendRequest(reqMap);
			response = receiveResponse();
		}
		
		// print message from server
		String responseString = response.get(MessageFields.MESSAGE.toString());
		return responseString;
	}
	
	
//****************************************************************
//	main()
//****************************************************************
	
	/**
	 * Main function.
	 */
	public static void main(String[] args) {

		if (args.length != 1) {
			System.out.println("ERROR: Provide 1 arguments");
			System.out.println("\t(1) <client file>: Client config file");
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
		
		Client client = new Client(fileName);
		
		try{
			client.run();
		} catch (SocketTimeoutException e){
			System.out.println(e.getMessage());
		}
	}

}
