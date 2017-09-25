import java.io.IOException;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

public class ClientCancel implements Serializable {

	private static final long serialVersionUID = 1L;
	public int orderID;

	public ClientCancel(int id) {
		orderID = id;
	}

	public ClientCancel(String id) {
		this(Integer.parseInt(id));
	}
	
	public static void main(String[] args) {
		

		ObjectMapper mapper = new ObjectMapper();
		TypeReference<HashMap<String, String>> typeRef 
		  = new TypeReference<HashMap<String, String>>() {};
		  
		
		Map<String, String> request = new HashMap<>();
		request.put(MessageFields.REQUEST.toString(), Requests.BOOKSEAT.toString());
		request.put(MessageFields.NAME.toString(), "Luca");
		request.put(MessageFields.SEATNUM.toString(), "100");
		
		String requestJson = "{}";
		try {
			requestJson = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(request);
		} catch (JsonProcessingException e1) {
			e1.printStackTrace();
		}
		  
		try {
			Map<String, String> map = mapper.readValue(requestJson, typeRef);
			System.out.println(map);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

}
