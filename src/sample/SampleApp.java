package sample;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLSocketFactory;

import commons.AbstractCoreServiceSample;
import commons.SampleException;
import commons.api.GatewayCloud;
import commons.api.GatewayCloudHttp;
import commons.api.GatewayCloudMqtt;
import commons.model.Authentication;
import commons.model.Capability;
import commons.model.Device;
import commons.model.Gateway;
import commons.model.GatewayProtocol;
import commons.model.Sensor;
import commons.model.SensorType;
import commons.model.gateway.Measure;
import commons.utils.Console;
import sample.EntityFactory;
import commons.utils.SecurityUtil;

public class SampleApp
extends AbstractCoreServiceSample {
	//Upon instantiation, this object *extends* the 'AbstractCoreServiceSample' Class, which in turn...
	//*extends* itself with 'AbstractSample'
	// So the order of initialization is actually:
	//	1. AbstractSample
	//	2. AbstractCoreServiceSample
	//	3. SampleApp

	// 1 - AbstractSample
	// a) establishes the following Class attributes:
	//	sets up a bunch on constants that relate to each property key (e.g IOT_HOST = "iot.host" etc)
	// b) The Constructor prints a bunch of lines (nothing special), then calls its 'init()' method
	// c) The 'init()' method loads in the Properties, and sets the proxy
	
	// 2 - AbstractCoreServiceSample
	// d) establishes the following Class attribute objects:
	//		CoreService coreService;
	//		ProcessingService processingService;
	//		Comparator<SensorTypeCapability> sensorTypeCapabilityComparator;
	// e) The Constructor establishes the following string values:
	//		host, instance, tenant, user, password
	// f) Then initializes the following services:
	// 		coreService = new CoreService(host, instance, tenant, user, password);
	//		processingService = new ProcessingService(host, tenant, user, password);
	//		sensorTypeCapabilityComparator = Comparator.comparing(SensorTypeCapability::getId);
	// g) The Constructor of 'CoreServices' [used to create the conn, then create the artifacts]  establishes
	//		baseUri
	//		httpClient
	// h) The Constructor of 'ProcessingServices' [used to publish the readings] establishes *exactly the same* inc. same base URL
	//		baseUri
	//		httpClient
	// i) The Constructor of 'java.util.Comparator' is unknown to me
	
	// 3 - SampleApp
	// j) establishes the following Class attribute objects:
	//		GatewayCloud gatewayCloud;
	// (no Constructor for SampleApp)


	private GatewayCloud gatewayCloud;

	@Override
	protected String getDescription() {
		return "Send ambient measures on behalf of the device sensor" + " and consume them later on via the API";
	}

	@Override
	protected void run()
	throws SampleException {
		String deviceId = properties.getProperty(DEVICE_ID);
		String sensorId = properties.getProperty(SENSOR_ID);

		//get the respective 'Gateway Protocol' object (HTTP Gateway or MQTT Gateway) given the 'gateway.protocol.id' property
		GatewayProtocol gatewayProtocol = GatewayProtocol.fromValue(properties.getProperty(GATEWAY_PROTOCOL_ID));

		try {
			Console.printSeparator();

			//get the respective Gateway object (HTTP Gateway or MQTT Gateway) given the 'gatewayProtocol' object
			Gateway gateway = coreService.getOnlineCloudGateway(gatewayProtocol);

			Console.printSeparator();

			//get the Device's java object instance, given the DeviceID property and Gateway object

			//Takes a DeviceID (that may or may not be valid, or might even be null!) and a Gateway object (HTTP or MQTT)
			// Attempt to (safely) look up the device given the ID
			//If it finds an existing one in the IoT Core Service, then simply return the Java object instance. 
			// Any error (due to a nonexistent/null ID) will simply 
			//	a) trigger a warning
			//	b) call 'ArtifactFactory.buildSampleDevice' to create an instance within the IoT Core Service
			//	c) print out the DeviceID to the log

			// TODO add the new DeviceID to the properties object bag?
			// TODO persist the new DeviceID to the properties file
			Device device = getOrAddDevice(deviceId, gateway);

			Console.printSeparator();

			//Build a new Capability java object instance, but only in memory, named "Ambient" (with an AlternateID of "ambient")
			//that comprises of three properties; Humidity, Temperature, Light
			
			//It's used as a template.  Having created an instance of one (in memory) 
			//  we pass it to 'AbstractDeviceInstanceCoreServices.getOrAddCapability'
			//Who in turn, searches for a copy of it (through the list of *all* the existing/persisted Capabilities in the IoT Core Service), 
			// and either returns the existing/persisted instance or creates a new one (using 'coreService.addCapability')
			Capability measureCapability = getOrAddCapability(EntityFactory.buildAmbientCapability());

			Console.printSeparator();

			Capability commandCapability = getOrAddCapability(EntityFactory.buildSwitchCapability());

			Console.printSeparator();

			SensorType sensorType = getOrAddSensorType(measureCapability, commandCapability);

			Sensor sensor = getOrAddSensor(sensorId, device, sensorType);

			Console.printSeparator();

			// Create an Authentication (a Secret, a combined Private Key + Certificate PEM, and a Password), for a given a Device object instance 
			Authentication authentication = coreService.getAuthentication(device);

			// Create a PKCS12 Keystore from the PEM
			// Create the 'Key Manager' and 'Trust Manager' components necessary *prior* to establishing an SSL link 
			SSLSocketFactory sslSocketFactory = SecurityUtil.getSSLSocketFactory(device, authentication);

			switch (gatewayProtocol) {
			case MQTT:
				gatewayCloud = new GatewayCloudMqtt(device, sslSocketFactory);
				break;
			case REST:
			default:
				gatewayCloud = new GatewayCloudHttp(device, sslSocketFactory);
				break;
			}

			Console.printSeparator();

			sendAmbientMeasures(sensor, measureCapability);

			receiveAmbientMeasures(measureCapability, device);
		} catch (IOException | GeneralSecurityException | IllegalStateException e) {
			throw new SampleException(e.getMessage());
		}
	}

	private void sendAmbientMeasures(final Sensor sensor, final Capability capability)
	throws IOException {
		String host = properties.getProperty(IOT_HOST);

		try {
			gatewayCloud.connect(host);
		} catch (IOException e) {
			throw new IOException("Unable to connect to the Gateway Cloud", e);
		}

		ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
		executor.scheduleAtFixedRate(new Runnable() {

			@Override
			public void run() {
				Measure measure = EntityFactory.buildAmbientMeasure(sensor, capability);

				try {
					gatewayCloud.sendMeasure(measure);
				} catch (IOException e) {
					Console.printError(e.getMessage());
				} finally {
					Console.printSeparator();
				}
			}

		}, 0, 1000, TimeUnit.MILLISECONDS);

		try {
			executor.awaitTermination(5000, TimeUnit.MILLISECONDS);
		} catch (InterruptedException e) {
			throw new IOException("Interrupted exception", e);
		} finally {
			executor.shutdown();
			gatewayCloud.disconnect();
		}
	}

	private void receiveAmbientMeasures(final Capability capability, final Device device)
	throws IOException {
		ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
		executor.schedule(new Runnable() {

			@Override
			public void run() {
				try {
					processingService.getLatestMeasures(capability, device, 25);
				} catch (IOException e) {
					Console.printError(e.getMessage());
				} finally {
					Console.printSeparator();
				}
			}

		}, 5000, TimeUnit.MILLISECONDS);

		try {
			executor.awaitTermination(1000, TimeUnit.MILLISECONDS);
		} catch (InterruptedException e) {
			throw new IOException("Interrupted exception", e);
		} finally {
			executor.shutdown();
			coreService.shutdown();
			processingService.shutdown();
		}
	}

}
