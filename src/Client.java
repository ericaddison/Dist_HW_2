import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

public class Client {
	private String hostAddress;
	private int tcpPort;
	private Socket tcpSocket = null;
	private PrintWriter out = null;
	private BufferedReader in = null;

	public Client(String hostAddress, int tcpPort) {
		super();
		this.hostAddress = hostAddress;
		this.tcpPort = tcpPort;
	}

	/**
	 * TODO: XXXX
	 * 
	 * @param o
	 *            the object to send
	 */
	public void sendRequest(Map<String, String> reqMap) {
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
	 * TODO: XXXX
	 * 
	 * @param o
	 *            the object to send
	 */
	public Map<String, String> receiveResponse() {
		try {
			String recString = in.readLine();
			
			ObjectMapper mapper = new ObjectMapper();
			TypeReference<HashMap<String, String>> typeRef 
			  = new TypeReference<HashMap<String, String>>() {};
			  
			  return mapper.readValue(recString, typeRef);
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}

	/**
	 * Connect to the server via TCP
	 */
	public void connectTCP() {
		try {
			tcpSocket = new Socket(hostAddress, tcpPort);
			out = new PrintWriter(tcpSocket.getOutputStream());
			in = new BufferedReader(new InputStreamReader(tcpSocket.getInputStream()));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}



	/**
	 * Run the client command-line interface
	 */
	public void run() {

		try (Scanner sc = new Scanner(System.in);) {
			System.out.print(">>> ");

			// connect TCP by default
			connectTCP();
		
			// main command loop
			while (sc.hasNextLine()) {
				String[] tokens = sc.nextLine().split(" ");
				Map<String, String> reqMap = new HashMap<>();

				if (tokens[0].equals("reserve")){
					if(tokens.length<2){
						System.out.println("NOTE: not enough tokens in reserve string");
						continue;
					}
					reqMap.put(MessageFields.REQUEST.toString(), Requests.RESERVE.toString());
					reqMap.put(MessageFields.NAME.toString(), tokens[1]);
				}
					
				
				else if (tokens[0].equals("bookSeat")){
					if(tokens.length<3){
						System.out.println("NOTE: not enough tokens in bookseat string");
						continue;
					}
					reqMap.put(MessageFields.REQUEST.toString(), Requests.BOOKSEAT.toString());
					reqMap.put(MessageFields.NAME.toString(), tokens[1]);
					reqMap.put(MessageFields.SEATNUM.toString(), tokens[2]);
				}

				else if (tokens[0].equals("search")){
					if(tokens.length<2){
						System.out.println("NOTE: not enough tokens in search string");
						continue;
					}
					reqMap.put(MessageFields.REQUEST.toString(), Requests.SEARCH.toString());
					reqMap.put(MessageFields.NAME.toString(), tokens[1]);
				}

				else if (tokens[0].equals("delete")){
					if(tokens.length<2){
						System.out.println("NOTE: not enough tokens in delete string");
						continue;
					}
					reqMap.put(MessageFields.REQUEST.toString(), Requests.DELETE.toString());
					reqMap.put(MessageFields.NAME.toString(), tokens[1]);
				}

				else{
					System.out.print("ERROR: No such command\n" + "\n>>> ");
					continue;
				}
				
				sendRequest(reqMap);
				Map<String, String> response = receiveResponse();
				String responseString = response.get(MessageFields.MESSAGE.toString());
				System.out.println(responseString + "\n>>> ");

			}

		}

	}

	/**
	 * Main function.
	 */
	public static void main(String[] args) {

		if (args.length != 2) {
			System.out.println("ERROR: Provide 2 arguments");
			System.out.println("\t(1) <hostAddress>: the address of the server");
			System.out.println("\t(2) <tcpPort>: the port number for TCP connection");
			System.exit(-1);
		}

		String hostAddress = args[0];
		int tcpPort = Integer.parseInt(args[1]);

		Client client = new Client(hostAddress, tcpPort);

		client.run();
	}

}
