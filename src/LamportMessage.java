
public class LamportMessage implements Comparable<LamportMessage> {
	
	public enum LamportMessageType {
		CS_REQUEST, CS_ACK, CS_RELEASE
	}
	
	LamportMessageType type;
	int serverID;
	LogicalClock clock;
	int[] data;
	
	public LamportMessage(LamportMessageType type, int serverID, LogicalClock clock, int[] data) {
		this.type = type;
		this.serverID = serverID;
		this.clock = clock;
		this.data = data;
	}

	
	
	public LamportMessage(LamportMessageType type, int serverID, LogicalClock clock) {
		this(type, serverID, clock, null);
	}



	@Override
	public int compareTo(LamportMessage o) {
		if (clock.c < o.clock.c) 
			return -1;
		else if (clock.c == o.clock.c)
			return 0;
		else
			return 1;
	}
	
	@Override
	public String toString() {
		// return Json-ized string
		// including message type = CS_REQUEST
		return super.toString();
	}
	
}
