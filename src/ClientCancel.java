import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
		
		
		
		// try to JSON a list
		List<String> list = new ArrayList<>();
		list.add("0");
		list.add("1");
		list.add("2");
		list.add("");
		
		TypeReference<List<String>> typeRefList 
		  = new TypeReference<List<String>>() {};
		
	  try {
			requestJson = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(list);
			System.out.println(requestJson);
		} catch (JsonProcessingException e) {
			e.printStackTrace();
		}
		  
		try {
			List<String> listR = mapper.readValue(requestJson, typeRefList);
			System.out.println(listR);
		} catch (IOException e) {
			e.printStackTrace();
		}
		  
		
		
		
		
	}

}
