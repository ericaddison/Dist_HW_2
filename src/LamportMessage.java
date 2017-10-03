import java.util.ArrayList;
import java.util.List;

public class LamportMessage implements Comparable<LamportMessage> {
	

	
	LamportMessageType type;
	int serverID;
	LogicalClock clock;
	String data;
	
	public LamportMessage(LamportMessageType type, int serverID, LogicalClock clock, String data) {
		this.type = type;
		this.serverID = serverID;
		this.clock = clock;
		this.data = data;
	}

	
	
	public LamportMessage(LamportMessageType type, int serverID, LogicalClock clock) {
		this(type, serverID, clock, null);
	}
	
	
	public static LamportMessage ACK(int serverID) {
		return new LamportMessage(LamportMessageType.CS_ACK, serverID, null);
	}
	
	public static LamportMessage RELEASE(int serverID, String data) {
		return new LamportMessage(LamportMessageType.CS_RELEASE, serverID, null, data);
	}


	@Override
	public int compareTo(LamportMessage o) {
		if (clock.value() < o.clock.value()) 
			return -1;
		else if (clock.value() == o.clock.value())
			return 0;
		else
			return 1;
	}
	
	@Override
	public String toString() {
		// TODO: LamportMEssage serialize
		// return Json-ized string
		// including message type = CS_REQUEST
		return super.toString();
	}
	
	public static LamportMessage fromString(String msg){
		// TODO: deserialize with Jackson
		return null;
	}
	
	
}
