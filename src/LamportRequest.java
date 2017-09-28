
public class LamportRequest implements Comparable<LamportRequest> {
	
	int serverID;
	LogicalClock clock;
	
	public LamportRequest(int serverID, LogicalClock clock) {
		this.serverID = serverID;
		this.clock = clock;
	}

	@Override
	public int compareTo(LamportRequest o) {
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
