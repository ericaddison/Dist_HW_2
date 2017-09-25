import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.Scanner;

public class Client {
	private String hostAddress;
	private int tcpPort;
	private Socket tcpSocket = null;
	private ObjectOutputStream out = null;
	private BufferedReader in = null;

	public Client(String hostAddress, int tcpPort) {
		super();
		this.hostAddress = hostAddress;
		this.tcpPort = tcpPort;
	}

	/**
	 * Send an object to the server, method depending on whether mode is TCP or
	 * UDP
	 * 
	 * @param o
	 *            the object to send
	 */
	public void sendObject(Object o) {
		try {
			out.writeObject(o);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * receive a String from the server, method depending on whether mode is TCP
	 * or UDP
	 * 
	 * @param o
	 *            the object to send
	 */
	public String receiveString() {
		try {
			return in.readLine();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return "could not receive string";
	}

	/**
	 * Connect to the server via TCP
	 */
	public void connectTCP() {
		try {
			tcpSocket = new Socket(hostAddress, tcpPort);
			out = new ObjectOutputStream(tcpSocket.getOutputStream());
			in = new BufferedReader(new InputStreamReader(tcpSocket.getInputStream()));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}


	/**
	 * TODO: XXXX
	 * 
	 * @param tokens
	 *            string input from command line
	 * @return server response
	 */
	public String reserve(String[] tokens) {
		if (tokens.length < 2) {
			return ("ERROR: Not enough tokens in reserve string"
					+ "\nERROR: Expected format: reserve <name>");
		} else {
			String name = tokens[1];
			ClientOrder order = new ClientOrder(userName, productName, quantity);
			sendObject(order);
			return receiveString();
		}
	}

	/**
	 * TODO: XXXX
	 * 
	 * @param tokens
	 *            string input from command line
	 * @return server response
	 */
	public String bookSeat(String[] tokens) {
		if (tokens.length < 3) {
			return ("ERROR: Not enough tokens in bookSeat string" + 
		"\nERROR: Expected format: bookSeat <name> <seatNum>");
		} else {
			String orderID = tokens[1];

			sendObject(new ClientCancel(orderID));
			String cancelConf = receiveString().replace(":", "\n");
			return cancelConf;
		}
	}

	/**
	 * TODO: XXXX
	 * 
	 * @param tokens
	 *            string input from command line
	 * @return server response
	 */
	public String search(String[] tokens) {
		if (tokens.length < 2) {
			return ("ERROR: Not enough tokens in search string" + "\nERROR: Expected format: search <name>");
		} else {
			String userName = tokens[1];

			sendObject(new ClientSearch(userName));
			String orders = receiveString().replace(":", "\n");
			return orders;
		}
	}
	
	/**
	 * TODO: XXXX
	 * 
	 * @param tokens
	 *            string input from command line
	 * @return server response
	 */
	public String delete(String[] tokens) {
		if (tokens.length < 2) {
			return ("ERROR: Not enough tokens in delete string" + "\nERROR: Expected format: delete <name>");
		} else {
			String userName = tokens[1];

			sendObject(new ClientSearch(userName));
			String orders = receiveString().replace(":", "\n");
			return orders;
		}
	}



	/**
	 * Run the client command-line interface
	 */
	public void run() {

		try (Scanner sc = new Scanner(System.in);) {
			System.out.print(">>>");

			// connect TCP by default
			connectTCP();

			// main command loop
			while (sc.hasNextLine()) {
				String[] tokens = sc.nextLine().split(" ");
				String response = "";

				if (tokens[0].equals("reserve"))
					response = reserve(tokens);

				else if (tokens[0].equals("bookSeat"))
					response = bookSeat(tokens);

				else if (tokens[0].equals("search"))
					response = search(tokens);

				else if (tokens[0].equals("delete"))
					response = delete(tokens);

				else
					response = "ERROR: No such command\n";

				System.out.print(response + "\n>>>");
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
