package commons.api;

import java.io.IOException;

import commons.connectivity.HttpClient;
import commons.model.Capability;
import commons.model.Device;
import commons.model.Measure;

public class ProcessingService {

	private HttpClient httpClient;

	private String baseUri;

	public ProcessingService(String host, String tenant, String user, String password) {
		baseUri = String.format("https://%1$s/iot/core/api/v1", host);
		httpClient = new HttpClient(user, password);
	}

	public void shutdown() {
		httpClient.disconnect();
	}

	public Measure[] getLatestMeasures(Capability capability, Device device, int top)
	throws IOException {
		String destination = String.format(
			"%1$s/devices/%2$s/measures?filter=capabilityId eq '%3$s'&orderby=timestamp desc&skip=0&top=%4$s", baseUri, device.getId(),
			capability.getId(),  top);
		//measures/capabilities/%2$s?orderby=timestamp desc&filter=deviceId eq '%3$s'&top=%4$d

		try {
			httpClient.connect(destination);
			return httpClient.doGet(Measure[].class);
		} finally {
			httpClient.disconnect();
		}
	}

}