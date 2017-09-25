import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;
import java.util.List;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public class Server {

	private int tcpPort;
	private int udpPort;
	private Inventory inv;
	private OrderHistory orders;
	private final Logger log = Logger.getLogger(Server.class.getCanonicalName());

	public Server(int tcpPort, int udpPort, String fileName) {
		super();
		this.tcpPort = tcpPort;
		this.udpPort = udpPort;
		inv = new Inventory(fileName);
		orders = new OrderHistory();

		try {
			FileHandler fh = new FileHandler("server_log_" + System.currentTimeMillis() + ".log");
			fh.setFormatter(new SimpleFormatter());
			log.addHandler(fh);
			logInfo("Server initializing...");
			logInfo("Server TCP Port: " + tcpPort);
			logInfo("Server UDP Port: " + udpPort);
			logInfo("Server Inventory File: " + fileName);
			logInfo("Server init complete");
			logInfo("--------------------------------");
		} catch (SecurityException | IOException e) {
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

		// start async udp responder here
		logInfo("Starting UDP thread");
		Thread udp_thread = new Thread(new UdpServerTask(this, udpPort));
		udp_thread.start();

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
	 * Parse a received object and dispatch to correct method.
	 * 
	 * @param receivedObject
	 *            object received from client
	 * @return response string
	 */
	public String processObject(Object receivedObject) {
		String response = "Unknown or bad command received";

		if (receivedObject.getClass() == ClientOrder.class)
			response = processRequest((ClientOrder) receivedObject);

		else if (receivedObject.getClass() == ClientCancel.class)
			response = processRequest((ClientCancel) receivedObject);

		else if (receivedObject.getClass() == ClientSearch.class)
			response = processRequest((ClientSearch) receivedObject);

		else if (receivedObject.getClass() == ClientProductList.class)
			response = processRequest((ClientProductList) receivedObject);

		return response;
	}

	/**
	 * Process an order request.
	 * 
	 * @param order
	 *            a ClientOrder object with purchase details
	 * @return response string
	 */
	public String processRequest(ClientOrder order) {
		StringBuilder response = new StringBuilder();
		int stock = inv.getItemCount(order.productName);
		int quantity = order.quantity;

		if (stock == -1)
			response.append("Not Available - We do not sell this product");
		else if (stock < quantity)
			response.append("Not Available - Not enough items");
		else {
			orders.addOrder(order);
			inv.removeItem(order.productName, quantity);
			response.append("Your order has been placed, ");
			response.append(order.orderID);
			response.append(" ");
			response.append(order.userName);
			response.append(" ");
			response.append(order.productName);
			response.append(" ");
			response.append(order.quantity);
		}
		logInfo("Processed purchase request: " + order);
		return response.toString();
	}

	/**
	 * Process a cancel request: cancel an active order.
	 * 
	 * @param cancel
	 *            a ClientCancel object with orderID
	 * @return response string
	 */
	public String processRequest(ClientCancel cancel) {

		ClientOrder order = orders.cancelOrderByID(cancel.orderID);
		logInfo("Processed cancel request: " + order);

		if (order != null) {
			inv.addItem(order.productName, order.quantity);
			return "Order " + cancel.orderID + " is cancelled";
		}
		return (cancel.orderID + " not found, no such order");

	}

	/**
	 * Process a search request: search for orders by username.
	 * 
	 * @param search
	 *            a ClientSearch object with username
	 * @return response string
	 */
	public String processRequest(ClientSearch search) {
		List<ClientOrder> orderList = orders.searchOrdersByUser(search.username);
		StringBuilder response = new StringBuilder();

		if (orderList.size() == 0) {
			response.append("No order found for ");
			response.append(search.username);
		} else
			for (ClientOrder order : orderList) {
				response.append(order.orderID);
				response.append(", ");
				response.append(order.productName);
				response.append(", ");
				response.append(order.quantity);
				response.append((order.isActive) ? "" : " (cancelled)");
				response.append(":");
			}
		logInfo("Processed search request: user=" + search.username);
		return response.toString();
	}

	/**
	 * Process an list request: list the inventory.
	 * 
	 * @param list
	 *            a ClientList object
	 * @return response string
	 */
	public String processRequest(ClientProductList list) {
		StringBuilder response = new StringBuilder();

		String[] names = inv.getItemNames().toArray(new String[] {});
		Arrays.sort(names);

		for (String item : names) {
			response.append(item);
			response.append(", ");
			response.append(inv.getItemCount(item));
			response.append(":");
		}
		logInfo("Processed list request");
		return response.toString();
	}

	/**
	 * Run the main program
	 * 
	 * @param args
	 *            command line input. Expects [tcpPort] [udpPort] [inventory
	 *            file]
	 */
	public static void main(String[] args) {
		int tcpPort;
		int udpPort;
		if (args.length != 3) {
			System.out.println("ERROR: Provide 3 arguments");
			System.out.println("\t(1) <tcpPort>: the port number for TCP connection");
			System.out.println("\t(2) <udpPort>: the port number for UDP connection");
			System.out.println("\t(3) <file>: the file of inventory");

			System.exit(-1);
		}
		tcpPort = Integer.parseInt(args[0]);
		udpPort = Integer.parseInt(args[1]);
		String fileName = args[2];

		Server server = new Server(tcpPort, udpPort, fileName);
		server.run();
	}
}