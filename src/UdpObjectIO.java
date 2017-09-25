import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class UdpObjectIO {

	public static ObjectAndPacket receiveObject(DatagramSocket sock, int bufLen) throws IOException {
		DatagramPacket datapacket = new DatagramPacket(new byte[bufLen], bufLen);
		sock.receive(datapacket);

		ObjectInputStream is = new ObjectInputStream(new ByteArrayInputStream(datapacket.getData()));
		Object o = null;
		try {
			o = is.readObject();
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}
		return new ObjectAndPacket(o, datapacket);
	}

	public static void sendObject(Object o, InetAddress addr, int port, DatagramSocket sock) throws IOException {

		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		ObjectOutputStream os = new ObjectOutputStream(bos);
		os.writeObject(o);
		byte[] barray = bos.toByteArray();

		DatagramPacket returnpacket = new DatagramPacket(barray, barray.length, addr, port);
		sock.send(returnpacket);

	}
	
	public static class ObjectAndPacket {
		public Object object;
		public DatagramPacket datagramPacket;

		public ObjectAndPacket(Object object, DatagramPacket datagramPacket) {
			super();
			this.object = object;
			this.datagramPacket = datagramPacket;
		}

	}
}
