import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class LamportMessage implements Comparable<LamportMessage> {
	

	
	LamportMessageType type;
	int serverID;
	LogicalClock clock;
	String data;
	
	private LamportMessage(LamportMessageType type, int serverID, LogicalClock clock, String data) {
		this.type = type;
		this.serverID = serverID;
		this.clock = clock;
		this.data = data;
	}

	
	public static LamportMessage ACK(int serverID) {
		return new LamportMessage(LamportMessageType.CS_ACK, serverID, null, null);
	}
	
	public static LamportMessage RELEASE(int serverID, String data) {
		return new LamportMessage(LamportMessageType.CS_RELEASE, serverID, null, data);
	}
	
	public static LamportMessage REQUEST(int serverID, LogicalClock clock) {
		return new LamportMessage(LamportMessageType.CS_RELEASE, serverID, clock, null);
	}


	@Override
	public int compareTo(LamportMessage o) {
		if (clock.value() < o.clock.value()) 
			return -1;
		else if (clock.value() == o.clock.value())
			if(serverID < o.serverID)
				return -1;
			else if(serverID==o.serverID)
				return 0;
		return 1;
	}
	
	@Override
	public String toString() {
		Map<String, String> map = new HashMap<>();
		map.put("type", type.toString())
		
		
		ObjectMapper mapper = new ObjectMapper();
		try {
			String jsonRequest = mapper.writer().writeValueAsString(reqMap);
			out.println(jsonRequest);
			out.flush();
		} catch (JsonProcessingException e1) {
			e1.printStackTrace();
		}
		return super.toString();
	}
	
	public static LamportMessage fromString(String msg){
		// TODO: deserialize with Jackson
		return null;
	}
	
	
}
