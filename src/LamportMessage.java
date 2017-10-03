import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

public class LamportMessage implements Comparable<LamportMessage> {

	LamportMessageType type;
	int serverID;
	LogicalClock clock;
	String data;

	private LamportMessage(){
		
	}
	
	private LamportMessage(LamportMessageType type, int serverID, LogicalClock clock, String data) {
		this.type = type;
		this.serverID = serverID;
		this.clock = clock;
		this.data = data;
	}

	public static LamportMessage ACK(int serverID) {
		return new LamportMessage(LamportMessageType.CS_ACK, serverID, new LogicalClock(Integer.MIN_VALUE), "NO DATA");
	}

	public static LamportMessage RELEASE(int serverID, String data) {
		return new LamportMessage(LamportMessageType.CS_RELEASE, serverID, new LogicalClock(Integer.MIN_VALUE), data);
	}

	public static LamportMessage REQUEST(int serverID, LogicalClock clock) {
		return new LamportMessage(LamportMessageType.CS_RELEASE, serverID, clock, "NO DATA");
	}

	@Override
	public int compareTo(LamportMessage o) {
		if (clock.value() < o.clock.value())
			return -1;
		else if (clock.value() == o.clock.value())
			if (serverID < o.serverID)
				return -1;
			else if (serverID == o.serverID)
				return 0;
		return 1;
	}

	@Override
	public String toString() {
		Map<String, String> map = new HashMap<>();
		map.put("type", type.toString());
		map.put("serverID", "" + serverID);
		map.put("clock", "" + clock.value());
		map.put("data", data);

		ObjectMapper mapper = new ObjectMapper();
		try {
			String jsonRequest = mapper.writer().writeValueAsString(map);
			return jsonRequest;
		} catch (JsonProcessingException e1) {
			e1.printStackTrace();
		}
		return "Error creating string";
	}

	public static LamportMessage fromString(String msg) {
		ObjectMapper mapper = new ObjectMapper();
		TypeReference<HashMap<String, String>> typeRef = new TypeReference<HashMap<String, String>>() {
		};

		try {
			Map<String, String> map = mapper.readValue(msg, typeRef);
			LamportMessage lm = new LamportMessage();
			lm.clock = new LogicalClock(Integer.parseInt(map.get("clock")));
			lm.type = LamportMessageType.valueOf(map.get("type"));
			lm.serverID = Integer.parseInt(map.get("serverID"));
			lm.data = map.get("data");
			return lm;
		} catch (IOException e) {
			e.printStackTrace();
		}

		return null;
	}
	
}
