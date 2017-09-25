import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectOutputStream;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.util.Scanner;

public class Client {
	private String hostAddress;
	private int tcpPort;
	private int udpPort;
	private boolean modeIsTCP = false; // TCP is default mode, other mode is UDP
	private Socket tcpSocket = null;
	private ObjectOutputStream out = null;
	private BufferedReader in = null;
	private DatagramSocket udpSocket;

	public Client(String hostAddress, int tcpPort, int udpPort) {
		super();
		this.hostAddress = hostAddress;
		this.tcpPort = tcpPort;
		this.udpPort = udpPort;

		try {
			udpSocket = new DatagramSocket();
		} catch (SocketException e) {
			e.printStackTrace();
		}
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
			if (modeIsTCP)
				out.writeObject(o);
			else
				UdpObjectIO.sendObject(o, InetAddress.getByName(hostAddress), udpPort, udpSocket);
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
			if (modeIsTCP) {
				return in.readLine();
			} else {
				return (String) UdpObjectIO.receiveObject(udpSocket, 1024).object;
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		return "could not receive string";
	}

	/**
	 * Connect to the server via TCP
	 */
	public void connectTCP() {
		if (modeIsTCP)
			return;

		modeIsTCP = true;
		try {
			tcpSocket = new Socket(hostAddress, tcpPort);
			out = new ObjectOutputStream(tcpSocket.getOutputStream());
			in = new BufferedReader(new InputStreamReader(tcpSocket.getInputStream()));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Set the network protocol to TCP or UDP
	 * 
	 * @param tokens
	 *            string input from command line
	 * @return server response
	 */
	public String setMode(String[] tokens) {
		// expecting setmode T | U
		if (tokens.length < 2) {
			return "ERROR: Not enough tokens in setmode string" + "\nERROR: Expected format: setmode T | U";
		} else {

			String mode = tokens[1].toUpperCase();
			if (mode.equals("T")) {
				udpSocket.close();
				connectTCP();
				return "mode: TCP";
			} else if (mode.equals("U")) {
				modeIsTCP = false;
				try {
					tcpSocket.close();
					udpSocket.connect(InetAddress.getByName(hostAddress), udpPort);
				} catch (IOException e) {
					e.printStackTrace();
				}
				return "mode: UDP";
			} else {
				// unrecognized mode, setting to TCP (since that is the default
				// mode)
				udpSocket.close();
				connectTCP();
				return ("ERROR: unrecognized mode: " + mode + "\nmode:TCP");
			}
		}
	}

	/**
	 * Create and send a purchase order to the server
	 * 
	 * @param tokens
	 *            string input from command line
	 * @return server response
	 */
	public String purchase(String[] tokens) {
		if (tokens.length < 4) {
			return ("ERROR: Not enough tokens in purchase string"
					+ "\nERROR: Expected format: purchase <user-name> <product-name> <quantity>");
		} else {
			String userName = tokens[1];
			String productName = tokens[2];
			int quantity = Integer.parseInt(tokens[3]);
			ClientOrder order = new ClientOrder(userName, productName, quantity);
			sendObject(order);
			return receiveString();
		}
	}

	/**
	 * Create and send an order cancel request to the server
	 * 
	 * @param tokens
	 *            string input from command line
	 * @return server response
	 */
	public String cancel(String[] tokens) {
		if (tokens.length < 2) {
			return ("ERROR: Not enough tokens in cancel string" + "\nERROR: Expected format: cancel <order-id>");
		} else {
			String orderID = tokens[1];

			sendObject(new ClientCancel(orderID));
			String cancelConf = receiveString().replace(":", "\n");
			return cancelConf;
		}
	}

	/**
	 * Create and send a user search request to the server
	 * 
	 * @param tokens
	 *            string input from command line
	 * @return server response
	 */
	public String search(String[] tokens) {
		if (tokens.length < 2) {
			return ("ERROR: Not enough tokens in search string" + "\nERROR: Expected format: search <user-name>");
		} else {
			String userName = tokens[1];

			sendObject(new ClientSearch(userName));
			String orders = receiveString().replace(":", "\n");
			return orders;
		}
	}

	/**
	 * Create and send an inventory list request to the server
	 * 
	 * @return server response
	 */
	public String list() {
		sendObject(new ClientProductList());
		String list = receiveString().replace(":", "\n");
		return list;
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

				if (tokens[0].equals("setmode"))
					response = setMode(tokens);

				else if (tokens[0].equals("purchase"))
					response = purchase(tokens);

				else if (tokens[0].equals("cancel"))
					response = cancel(tokens);

				else if (tokens[0].equals("search"))
					response = search(tokens);

				else if (tokens[0].equals("list"))
					response = list();

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

		if (args.length != 3) {
			System.out.println("ERROR: Provide 3 arguments");
			System.out.println("\t(1) <hostAddress>: the address of the server");
			System.out.println("\t(2) <tcpPort>: the port number for TCP connection");
			System.out.println("\t(3) <udpPort>: the port number for UDP connection");
			System.exit(-1);
		}

		String hostAddress = args[0];
		int tcpPort = Integer.parseInt(args[1]);
		int udpPort = Integer.parseInt(args[2]);

		Client client = new Client(hostAddress, tcpPort, udpPort);

		client.run();
	}

}
