package com.example.restservice;

import netscape.javascript.JSObject;
import org.json.JSONObject;
import org.json.XML;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

@RestController
public class GreetingController {

	private static final String template = "Hello, %s!";
	private final AtomicLong counter = new AtomicLong();

	@GetMapping("/greeting")
	public Greeting greeting(@RequestParam(value = "name", defaultValue = "World") String name) {
		return new Greeting(counter.incrementAndGet(), String.format(template, name));
	}

	@GetMapping("/test1")
	public Greeting test(@RequestParam(value = "name", defaultValue = "World") String name) {
		return new Greeting(counter.incrementAndGet(), String.format(template, name));
	}

	@RequestMapping(
			value = "/process",
			method = RequestMethod.POST,
			produces = MediaType.APPLICATION_JSON_VALUE
	)
	public String process(@RequestBody Map<String, String> payload) throws Exception {
		JSONObject res = new JSONObject();
		payload.put("MsgType", "cmpi_lookup");
		payload.put("Version", "1.7");
		payload.put("ProcessorId", "202");
		payload.put("MerchantId", "pluketina_test");
		payload.put("TransactionPwd", "");
		payload.put("TransactionType", "C");
		payload.put("OrderNumber", "rand_id");
		payload.put("Amount", "100");
		payload.put("CurrencyCode", "currency");
		payload.put("ACSWindowSize", "03");

		// Create XML request string
		String xmlString = "<CardinalMPI>";
		for (Map.Entry<String, String> entry : payload.entrySet()) {
			String key = entry.getKey();
			String value = entry.getValue();
			xmlString = xmlString + "<" + key +">" + value + "</" + key + ">";
		}
		xmlString = xmlString + "</CardinalMPI>";

		// send XML request
		okhttp3.OkHttpClient client = new okhttp3.OkHttpClient();
		okhttp3.RequestBody requestBody = new okhttp3.FormBody.Builder()
				.add("cmpi_msg", xmlString)
				.build();
		okhttp3.Request request = new okhttp3.Request.Builder()
				.url("https://BlandWheatInterchangeability.tieule.repl.co")
				.post(requestBody)
				.build();

		okhttp3.Call call = client.newCall(request);
		okhttp3.Response response = call.execute();
		String xmlResponse = response.body().string();

		// xml to JSON
		JSONObject jsonResponse = XML.toJSONObject(xmlResponse);
		jsonResponse = jsonResponse.getJSONObject("CardinalMPI");

		Object errorNo = jsonResponse.get("ErrorNo");
		if (!(errorNo instanceof Integer) || !errorNo.equals(0)) {
			res.accumulate("message", "Error with transaction");
		} else {
			if (!jsonResponse.getString("Enrolled").equals("Y") && !jsonResponse.getString("Enrolled").equals("B")) {
				res.accumulate("message", "No liability shift");
			} else {
				if (jsonResponse.isNull("ACSUrl") || jsonResponse.getString("ACSUrl").isEmpty()) {
					res.accumulate("message", "Transaction went through 3DS");
				} else {
				    // create orderObjectV2
					JSONObject orderDetails = new JSONObject();
					orderDetails.accumulate("TransactionId", jsonResponse.getString("TransactionId"));
					for (Map.Entry<String, String> entry : payload.entrySet()) {
						orderDetails.accumulate(entry.getKey(), entry.getValue());
					}
					JSONObject orderObjectV2 = new JSONObject();
					orderObjectV2.accumulate("TransactionId", orderDetails);

					//create continueData
					JSONObject continueData = new JSONObject();
					continueData.accumulate("AcsUrl", jsonResponse.getString("ACSUrl"));
					continueData.accumulate("Payload", jsonResponse.getString("Payload"));

					//create response
					res.accumulate("message", "Call Cardinal.continue()");
					res.accumulate("continueData", continueData);
					res.accumulate("orderObjectV2", orderObjectV2);
				}
			}
		}

		System.out.println(res.toString());
		return res.toString();
	}
}
