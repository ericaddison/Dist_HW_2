import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;

public class UdpServerTask implements Runnable {

	private DatagramSocket datasocket;
	private Server server;

	public UdpServerTask(Server server, int udpPort) {
		super();
		this.server = server;
		try {
			this.datasocket = new DatagramSocket(udpPort);
		} catch (SocketException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void run() {
		DatagramPacket datapacket;
		try {
			while (true) {
				// read UDP packet
				UdpObjectIO.ObjectAndPacket oap = UdpObjectIO.receiveObject(datasocket, 1024);
				datapacket = oap.datagramPacket;
				server.logInfo("Received " + oap.object.getClass().getCanonicalName() + " request via UDP from "
						+ datapacket.getAddress());

				// process request
				String response = server.processObject(oap.object);

				// write UDP packet
				UdpObjectIO.sendObject(response, oap.datagramPacket.getAddress(), oap.datagramPacket.getPort(), datasocket);
			}
		} catch (IOException e) {
			System.err.println(e);
			e.printStackTrace();
		}

	}

}
