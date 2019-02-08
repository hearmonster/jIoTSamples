# jIoTStarterkit_java-samples
The IoT Starterkit's "java-samples' but refactored (out of Maven) into a simple Java project

## Compilation
no Jars to worry about, just execute the "Main" Class

## Initialization sequence
Upon instantiation, this object *extends* the 'AbstractCoreServiceSample' Class, which in turn...*extends* itself with 'AbstractSample'

So the order of initialization is actually:
1. AbstractSample
1. AbstractCoreServiceSample
1. SampleApp

1 - AbstractSample

	a) establishes the following Class attributes:

		sets up a bunch on constants that relate to each property key (e.g IOT_HOST = "iot.host" etc)
	b) The Constructor prints a bunch of lines (nothing special), then calls its 'init()' method
	
	c) The 'init()' method loads in the Properties, and sets the proxy
	
	
2 - AbstractCoreServiceSample

	d) establishes the following Class attribute objects:
	
		CoreService coreService;
		
		ProcessingService processingService;
		
		Comparator<SensorTypeCapability> sensorTypeCapabilityComparator;
		
	e) The Constructor establishes the following string values:
	
		host, instance, tenant, user, password
		
	f) Then initializes the following services:
	
		coreService = new CoreService(host, instance, tenant, user, password);
			
		processingService = new ProcessingService(host, tenant, user, password);
		
		sensorTypeCapabilityComparator = Comparator.comparing(SensorTypeCapability::getId);
		
	g) The Constructor of 'CoreServices' [used to create the conn, then create the artifacts]  establishes
	
		baseUri
		
		httpClient
	h) The Constructor of 'ProcessingServices' [used to publish the readings] establishes *exactly the same* inc. same base URL
		baseUri
		httpClient
	i) The Constructor of 'java.util.Comparator' is unknown to me
	
	
3 - SampleApp

	j) establishes the following Class attribute objects:
	
		GatewayCloud gatewayCloud;
		
	(no Constructor for SampleApp)
  
