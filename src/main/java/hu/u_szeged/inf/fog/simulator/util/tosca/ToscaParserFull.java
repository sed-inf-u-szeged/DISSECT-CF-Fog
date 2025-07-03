package hu.u_szeged.inf.fog.simulator.util.tosca;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.energy.powermodelling.PowerState;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.AlterableResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;
import hu.mta.sztaki.lpds.cloud.simulator.io.VirtualAppliance;
import hu.mta.sztaki.lpds.cloud.simulator.util.PowerTransitionGenerator;
import hu.mta.sztaki.lpds.cloud.simulator.util.SeedSyncer;
import hu.u_szeged.inf.fog.simulator.application.Application;
import hu.u_szeged.inf.fog.simulator.application.strategy.RuntimeAwareApplicationStrategy;
import hu.u_szeged.inf.fog.simulator.demo.ScenarioBase;
import hu.u_szeged.inf.fog.simulator.iot.Device;
import hu.u_szeged.inf.fog.simulator.iot.EdgeDevice;
import hu.u_szeged.inf.fog.simulator.iot.SmartDevice;
import hu.u_szeged.inf.fog.simulator.iot.mobility.GeoLocation;
import hu.u_szeged.inf.fog.simulator.iot.mobility.RandomWalkMobilityStrategy;
import hu.u_szeged.inf.fog.simulator.iot.strategy.RandomDeviceStrategy;
import hu.u_szeged.inf.fog.simulator.node.ComputingAppliance;
import hu.u_szeged.inf.fog.simulator.provider.Instance;
import hu.u_szeged.inf.fog.simulator.util.EnergyDataCollector;
import hu.u_szeged.inf.fog.simulator.util.MapVisualiser;
import hu.u_szeged.inf.fog.simulator.util.SimLogger;
import hu.u_szeged.inf.fog.simulator.util.TimelineVisualiser;
import java.util.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

public class ToscaParserFull {

    private static final String path = new StringBuilder(ScenarioBase.resourcePath)
            .append("TOSCA_examples").append(File.separator).toString();

    public static class NodeTemplate {
        public String type;
        public Map<String, Object> capabilities;
        public List<Map<String, Object>> requirements;
    }

    public static class TopologyTemplate {
        public Map<String, NodeTemplate> node_templates;
        public Map<String, Object> policies;  // Add this field to match the 'policies' in YAML
        public Map<String, GroupTemplate> groups;    // Add this field if the YAML includes 'groups'
    }
    
    public static class GroupTemplate {
        public String type;
        public List<String> members;  // Changed from Map to List
        public Map<String, Object> properties;  // Keep this if the groups have additional properties
    }
    
    public static class ToscaFile {
        public String tosca_definitions_version;
        public String description;
        public TopologyTemplate topology_template;
    }
    
    public static long convertToBytes(String size) {
        // Check if size is null or empty
        if (size == null || size.isEmpty()) {
            throw new IllegalArgumentException("Size cannot be null or empty.");
        }

        // Split the input into the numeric value and the unit
        String[] parts = size.split(" ");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Size format is incorrect: " + size);
        }

        long value;
        try {
            value = Long.parseLong(parts[0]);  // The numeric part
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid number format in size: " + size, e);
        }

        String unit = parts[1].toUpperCase();   // The unit part (e.g., GB, MB, etc.)

        // Convert based on the unit
        switch (unit) {
            case "TB":
                return value * 1024L * 1024L * 1024L * 1024L;  // TB to bytes
            case "GB":
                return value * 1024L * 1024L * 1024L;           // GB to bytes
            case "MB":
                return value * 1024L * 1024L;                   // MB to bytes
            case "KB":
                return value * 1024L;                           // KB to bytes
            
            default:
                throw new IllegalArgumentException("Unsupported unit: " + unit);
        }
    }
    
    public static class PolicyExtractor {

        // Method to extract all policies
        public List<Map<String, Object>> extractPolicies(ToscaParserFull.ToscaFile toscaFile, String nodeName) {
            List<Map<String, Object>> allPolicies = new ArrayList<>();

            if (toscaFile.topology_template.policies != null) {
                System.out.println("Policies for node " + nodeName + ":");
                for (Map.Entry<String, Object> policyEntry : toscaFile.topology_template.policies.entrySet()) {
                    if (policyEntry.getKey().toString().startsWith(nodeName)) {
                        // This assumes policyEntry.getValue() is a Map or JSON-like structure
                        Map<String, Object> policyMap = (Map<String, Object>) policyEntry.getValue();
                        allPolicies.add(policyMap); // Add the entire policy to the list
                    }
                }
            } else {
                System.out.println("No policies defined for this node.");
            }
            return allPolicies; // Return the list of policies
        }

        // New method to retrieve a specific value by key from the nested policies
        public Object getPolicyValueByKey(List<Map<String, Object>> policies, String key) {
            for (Map<String, Object> policy : policies) {
                Object result = findValueInMap(policy, key);
                if (result != null) {
                    return result; // Return as soon as the key is found
                }
            }
            return null; // Return null if the key is not found
        }

        // Recursive method to search for the key in a nested map structure
        private Object findValueInMap(Map<String, Object> map, String key) {
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                if (entry.getKey().equals(key)) {
                    return entry.getValue(); // Found the key, return its value
                }
                // If the value is another map, search recursively
                if (entry.getValue() instanceof Map) {
                    Object nestedResult = findValueInMap((Map<String, Object>) entry.getValue(), key);
                    if (nestedResult != null) {
                        return nestedResult; // Found the key in the nested map
                    }
                }
            }
            return null; // Return null if the key is not found in this map
        }
    }
    
    public static double parseObjectToDouble(Object obj) {
        try {
            return Double.parseDouble(String.valueOf(obj));
        } catch (Exception e) {
            return Double.NaN; // Return NaN if parsing fails
        }
    }
    
    public static double parseObjectToDoubleUnits(Object obj) {
        try {
            // Use a regular expression to extract the numeric part of the string
            String numericPart = String.valueOf(obj).replaceAll("[^\\d.-]+", "");
            return Double.parseDouble(numericPart);
        } catch (Exception e) {
            return Double.NaN; // Return NaN if parsing fails
        }
    }
    
    public static long parseObjectToLong(Object obj) {
        return Long.parseLong(String.valueOf(obj).replaceAll("[^0-9]", ""));
    }
    
    public static void main(String[] args) throws Exception {
        // Use fully qualified class name for the constructor
    	Constructor constructor = new Constructor(ToscaParserFull.ToscaFile.class, new LoaderOptions());
        Yaml yaml = new Yaml(constructor);

        InputStream inputStream = null;
        try {
            // Load the TOSCA YAML file using the full path
            inputStream = new FileInputStream(new File(path + "ec2.yaml"));
        } catch (FileNotFoundException e) {
            System.err.println("File not found: " + e.getMessage());
            return;
        }

        ToscaFile toscaFile = yaml.load(inputStream);
        
        // Lists to store VirtualAppliance and AlterableResourceConstraints
        List<VirtualAppliance> cloudVaList = new ArrayList<>();
        List<AlterableResourceConstraints> cloudArcList = new ArrayList<>();
        List<Instance> cloudInstList = new ArrayList<>();
        List<ComputingAppliance> cloudCAList = new ArrayList<>();
        List<Application> cloudAppList = new ArrayList<>();
        
        List<VirtualAppliance> fogVaList = new ArrayList<>();
        List<AlterableResourceConstraints> fogArcList = new ArrayList<>();
        List<Instance> fogInstList = new ArrayList<>();
        List<ComputingAppliance> fogCAList = new ArrayList<>();
        List<Application> fogAppList = new ArrayList<>();
        
        //List for the devices (Edge or SmartDevice)
        ArrayList<Device> deviceList = new ArrayList<Device>();
          
        // Count the number of nodes
        int cloudNodeCount = 0;
        int fogNodeCount = 0;
        int edgeDeviceCount = 0;
        
        // Iterate through node templates
        for (String nodeName : toscaFile.topology_template.node_templates.keySet()) {
            if (nodeName.startsWith("cloud_node")) {
                cloudNodeCount++;
            } else if (nodeName.startsWith("fog_node")) {
                fogNodeCount++;
            } else if (nodeName.startsWith("edge_device") || nodeName.startsWith("smart_device")) {
                edgeDeviceCount++;
            }
        }  

        // Print counts
        System.out.println("Number of Cloud Nodes: " + cloudNodeCount);
        System.out.println("Number of Fog Nodes: " + fogNodeCount);
        System.out.println("Number of Edge Devices: " + edgeDeviceCount);
        System.out.println("-----------------------------------");
        
        //Useful for the simulation
    	SimLogger.setLogging(1, true);
        String cloudfile = ScenarioBase.resourcePath + "LPDS_original.xml";
              
        // Extract Capabilities/Requirements for all nodes and devices
        Map<String, NodeTemplate> nodeTemplates = toscaFile.topology_template.node_templates;
        int cloudCounter = 1;
        int fogCounter = 1;
        int deviceCounter = 1;
        long MemSizeInBytes = 0;
        long DiskSizeInBytes = 0;
        
        for (Map.Entry<String, NodeTemplate> nodeEntry : nodeTemplates.entrySet()) {
        	String nodeName = nodeEntry.getKey();
            NodeTemplate node = nodeEntry.getValue();
            System.out.println("Node Name: " + nodeName);
            System.out.println("Node Type: " + node.type);
            System.out.println("Node Capabilities: " + node.capabilities);
            // Extracting capabilities
            if (node.capabilities != null && node.capabilities.containsKey("host")) {
                Map<String, Object> hostCapabilities = (Map<String, Object>) node.capabilities.get("host");
                if (hostCapabilities != null && hostCapabilities.containsKey("properties")) {
                    Map<String, Object> properties = (Map<String, Object>) hostCapabilities.get("properties");
                    // Extract individual properties
                    double numCores = parseObjectToDouble(properties.get("num_cpus"));
                    Object memSize = properties.get("mem_size");
                    //Transform MemSize to Bytes (long format)
                    if (memSize != null) {
                        MemSizeInBytes = convertToBytes(memSize.toString());
                        //System.out.println(memSize + " is " + MemSizeInBytes + " bytes.");
                    }
                    Object diskSize = properties.get("disk_size");
                   //Transform DiskSize to Bytes (long format)
                    if (diskSize != null) {
                    	DiskSizeInBytes = convertToBytes(diskSize.toString());
                       //System.out.println(diskSize + " is " + DiskSizeInBytes + " bytes.");
                    }
                    System.out.println("Num CPUs/Cores: " + numCores);
                    System.out.println("Memory Size: " + memSize);
                    System.out.println("Memory Size in Bytes: " + MemSizeInBytes);
                    System.out.println("Disk Size: " + diskSize);
                          
                    if (nodeName.startsWith("cloud_node")) {
                    	 System.out.println("Node Requirements: " + node.requirements);
                         // Extract Policies values from TOSCA
                     	 PolicyExtractor policyExtractor = new PolicyExtractor();
                    	 List<Map<String, Object>> policies = policyExtractor.extractPolicies(toscaFile, nodeName);
                   	     // Print all extracted policies
                    	 for (Map<String, Object> policy : policies) {
                    	   System.out.println("Extracted Policy: " + policy);
                    	 }
                   	     // Retrieve specific values
                   	     double latitude = parseObjectToDouble(policyExtractor.getPolicyValueByKey(policies, "latitude"));
                   	     double longitude = parseObjectToDouble(policyExtractor.getPolicyValueByKey(policies, "longitude"));  	     
                   	     long Range = parseObjectToLong(policyExtractor.getPolicyValueByKey(policies, "Range"));
                   	     double StartUpProcess = parseObjectToDoubleUnits(policyExtractor.getPolicyValueByKey(policies, "StartUpProcess"));
                   	     double ProcessingPerCore = parseObjectToDouble(policyExtractor.getPolicyValueByKey(policies, "ProcessingPerCore"));
                   	     System.out.println("Latitude: " + latitude);
                   	     System.out.println("Longitude: " + longitude);
                   	     System.out.println("Range: " + Range);
                   	     System.out.println("StartUpProcess: " + StartUpProcess);
                   	     System.out.println("ProcessingPerCore: " + ProcessingPerCore);
                         // ------ HERE IT ENDS (Assigning the variables to each feature) --------------------
                    	 System.out.println("Creting a Cloud Node"); 
                    	 // Create VirtualAppliance and AlterableResourceConstraints
                    	 String vaName = "vac_" + cloudCounter;
                    	 String InstanceName = "CloudInstance_" + cloudCounter;
                    	 String ApplicationName = "App-" + cloudCounter;
                    	 VirtualAppliance va = new VirtualAppliance(vaName, StartUpProcess, 0, false, 1_073_741_824L);
                         AlterableResourceConstraints arc = new AlterableResourceConstraints(numCores, ProcessingPerCore, MemSizeInBytes);
                         Instance Instance = new Instance(InstanceName, va, arc, 0.102 / 60 / 60 / 1000);
                         ComputingAppliance CAcloud = new ComputingAppliance(cloudfile, nodeName, new GeoLocation(latitude, longitude), Range);
                         new EnergyDataCollector(nodeName, CAcloud.iaas, true);
                         Application application = new Application(ApplicationName, 1 * 60 * 1000, 250, 2500, false,
                                 new RuntimeAwareApplicationStrategy(0.9, 2.0), Instance);
                         CAcloud.addApplication(application);
                         // Add to cloud lists
                         cloudVaList.add(va);
                         cloudArcList.add(arc);
                         cloudInstList.add(Instance);
                         cloudCAList.add(CAcloud);
                         cloudAppList.add(application);
                         
                         // Increment the cloud node counter
                         cloudCounter++;
                         
                    } else if (nodeName.startsWith("fog_node")) {
                    	 System.out.println("Creting a Fog Node");
                    	 // Extract Policies values from TOSCA
                  	     PolicyExtractor policyExtractor = new PolicyExtractor();
                 	     List<Map<String, Object>> policies = policyExtractor.extractPolicies(toscaFile, nodeName);
                	     // Print all extracted policies
                 	     for (Map<String, Object> policy : policies) {
                 	      System.out.println("Extracted Policy: " + policy);
                 	     }
                 	     // Retrieve specific values
                 	     double latitude = parseObjectToDouble(policyExtractor.getPolicyValueByKey(policies, "latitude"));
                  	     double longitude = parseObjectToDouble(policyExtractor.getPolicyValueByKey(policies, "longitude"));  	     
                  	     long Range = parseObjectToLong(policyExtractor.getPolicyValueByKey(policies, "Range"));
                  	     double StartUpProcess = parseObjectToDoubleUnits(policyExtractor.getPolicyValueByKey(policies, "StartUpProcess"));
                  	     double ProcessingPerCore = parseObjectToDouble(policyExtractor.getPolicyValueByKey(policies, "ProcessingPerCore"));
                  	     System.out.println("Latitude: " + latitude);
                 	     System.out.println("Longitude: " + longitude);
                 	     System.out.println("Range: " + Range);
                 	     System.out.println("StartUpProcess: " + StartUpProcess);
                 	     System.out.println("ProcessingPerCore: " + ProcessingPerCore);
                    	 // Create VirtualAppliance and AlterableResourceConstraints
                    	 String vaName = "vaf_" + fogCounter;
                    	 String InstanceName = "FogInstance_" + fogCounter;
                    	 String ApplicationName = "App-" + fogCounter;
                         VirtualAppliance va = new VirtualAppliance(vaName, StartUpProcess, 0, false, 1_073_741_824L);
                         AlterableResourceConstraints arc = new AlterableResourceConstraints(numCores, ProcessingPerCore, MemSizeInBytes);
                         Instance Instance = new Instance(InstanceName, va, arc, 0.051 / 60 / 60 / 1000);
                         ComputingAppliance CAfog = new ComputingAppliance(cloudfile, nodeName, new GeoLocation(latitude, longitude), Range);
                         new EnergyDataCollector(nodeName, CAfog.iaas, true);
                         Application application = new Application(ApplicationName, 1 * 60 * 1000, 250, 2500, true,
                                 new RuntimeAwareApplicationStrategy(0.9, 2.0), Instance);
                         CAfog.addApplication(application);
                         
                         // Add to fog lists
                         fogVaList.add(va);
                         fogArcList.add(arc);
                         fogInstList.add(Instance);
                         fogCAList.add(CAfog);
                         fogAppList.add(application);
                         
                         // Increment the fog node counter
                         fogCounter++;      
                         
                    } else if (nodeName.startsWith("edge_device") || nodeName.startsWith("smart_device")) {
                    	 System.out.println("Node Requirements: " + node.requirements);
                    	 System.out.println("Creting a Device");
                    	 
                    	 
                    	 // Extract Policies values from TOSCA
                  	     PolicyExtractor policyExtractor = new PolicyExtractor();
                 	     List<Map<String, Object>> policies = policyExtractor.extractPolicies(toscaFile, nodeName);
                	     // Print all extracted policies
                 	     for (Map<String, Object> policy : policies) {
                 	      System.out.println("Extracted Policy: " + policy);
                 	     }
                 	     long maxInBW = parseObjectToLong(policyExtractor.getPolicyValueByKey(policies, "maxInBW"));
                 	     long maxOutBW = parseObjectToLong(policyExtractor.getPolicyValueByKey(policies, "MaxOutBW"));
                 	     long DiskBW = parseObjectToLong(policyExtractor.getPolicyValueByKey(policies, "DiskBW"));
                 	     double MinPower = parseObjectToDoubleUnits(policyExtractor.getPolicyValueByKey(policies, "MinPower"));
                 	     double IdlePower = parseObjectToDoubleUnits(policyExtractor.getPolicyValueByKey(policies, "IdlePower"));
                 	     double MaxPower = parseObjectToDoubleUnits(policyExtractor.getPolicyValueByKey(policies, "MaxPower"));
                 	     double DiskDivider = parseObjectToDouble(policyExtractor.getPolicyValueByKey(policies, "DiskDivider"));
                 	     double NetDivider = parseObjectToDouble(policyExtractor.getPolicyValueByKey(policies, "NetDivider"));
                  	     double PerCoreProcessing = parseObjectToDouble(policyExtractor.getPolicyValueByKey(policies, "PerCoreProcessing"));
                 	     
	                  	 System.out.println("Max Input Bandwidth: " + maxInBW);
	                  	 System.out.println("Max Output Bandwidth: " + maxOutBW);
	                  	 System.out.println("Disk Bandwidth: " + DiskBW);
	                  	 System.out.println("Min Power: " + MinPower);
	                  	 System.out.println("Idle Power: " + IdlePower);
	                  	 System.out.println("Max Power: " + MaxPower);
	                  	 System.out.println("Disk Divider: " + DiskDivider);
	                  	 System.out.println("Network Divider: " + NetDivider);
	                  	 System.out.println("Per Core Processing: " + PerCoreProcessing);
                  	     
                  	     
                         int i = deviceCounter;
                    	 
                    	 HashMap<String, Integer> latencyMap = new HashMap<String, Integer>();
                         EnumMap<PowerTransitionGenerator.PowerStateKind, Map<String, PowerState>> transitions = 
                                 PowerTransitionGenerator.generateTransitions(MinPower, IdlePower, MaxPower, DiskDivider, NetDivider);

                         final Map<String, PowerState> cpuTransitions = transitions.get(PowerTransitionGenerator.PowerStateKind.host);
                         final Map<String, PowerState> stTransitions = transitions.get(PowerTransitionGenerator.PowerStateKind.storage);
                         final Map<String, PowerState> nwTransitions = transitions.get(PowerTransitionGenerator.PowerStateKind.network);

                         Repository repo = new Repository(DiskSizeInBytes, "mc-repo" + i, maxInBW, maxOutBW, DiskBW, latencyMap, stTransitions, nwTransitions); // 26 Mbit/s
                         PhysicalMachine localMachine = new PhysicalMachine(numCores, PerCoreProcessing, MemSizeInBytes, repo, 0, 0, cpuTransitions);

                         Device device;
                         double step = SeedSyncer.centralRnd.nextDouble(); 
                         if(nodeName.startsWith("edge_device")) {
                             device = new EdgeDevice(0, 10 * 60 * 60 * 1000, 100, 60 * 1000, 
                                     new RandomWalkMobilityStrategy(new GeoLocation(47 + step, 19 - step), 0.0027, 0.0055, 10000),
                                     new RandomDeviceStrategy(), localMachine, 0.1, 50, true);
                         }else {
                             device  = new SmartDevice(0, 10 * 60 * 60 * 1000, 100, 60 * 1000, 
                                     new RandomWalkMobilityStrategy(new GeoLocation(47 - step, 19 + - step), 0.0027, 0.0055, 10000),
                                     new RandomDeviceStrategy(), localMachine, 50, true);
                         }
                         deviceList.add(device);
                         deviceCounter++;
                    }     
                }
            }
             
            System.out.println("-----------------------------------");
        }
        
        System.out.println("-----------------------------------");
        
        // -------------------------HERE WE WILL SET NEIGHBORS AND PARENTS ------------------------------
	    // Extract the group members from the topology_template.groups
	    Map<String, GroupTemplate> groups = toscaFile.topology_template.groups;
	    // Define variables to store fog node memberships across networks
	    Map<String, List<String>> fogNetworks = new HashMap<>();
	    Map<String, List<String>> interFogNetworks = new HashMap<>();
	    Map<String, List<String>> cloudNetworks = new HashMap<>();
	    // Check if groups are present in the YAML file
	    if (groups != null) {
        // Iterate through the group templates
           for (Map.Entry<String, GroupTemplate> groupEntry : groups.entrySet()) {
               String groupName = groupEntry.getKey();
               GroupTemplate group = groupEntry.getValue();
               // Extract cloud network members
               if (groupName.startsWith("cloud_network")) {
                 cloudNetworks.put(groupName, group.members);
               }
               // Extract fog network members
               else if (groupName.startsWith("fog_network")) {
                 fogNetworks.put(groupName, group.members);
               }
               // Extract inter-fog network members
               else if (groupName.startsWith("inter_fog_network")) {
                 interFogNetworks.put(groupName, group.members);
               }
           }
	    }
    
	    // Now, we ASSIGN the corresponding NEIGHBOR for the current FOG NODE in the FOG NETWORK                 	                     
	    for (Map.Entry<String, List<String>> interFogEntry : interFogNetworks.entrySet()) {
	        List<String> interFogMembers = interFogEntry.getValue();
	        // Iterate over the nodes within the same inter-fog network
	        for (String fogNode : interFogMembers) {
	            for (String neighbor : interFogMembers) {
	                // Ensure the neighbor is not the same node
	                if (!neighbor.equals(fogNode)) {
	                    ComputingAppliance neighbor1 = null;
	                    ComputingAppliance fogNode1 = null;
                        // Assign neighbor1 using the name from the inter-fog network
	                    for (ComputingAppliance ca : fogCAList) {
	                        if (ca.name.equals(neighbor)) {
	                            neighbor1 = ca; // Assign the neighbor object
	                            break; // Exit the loop after finding the neighbor
	                        }
	                    }
                        // Assign fogNode1 (the current node) using its name
	                    for (ComputingAppliance ca1 : fogCAList) {
	                        if (ca1.name.equals(fogNode)) {
	                            fogNode1 = ca1; // Assign the fogNode object
	                            break; // Exit the loop after finding the fog node
	                        }
	                    }
	                    // Now, both neighbor1 and fogNode1 are available for use after the loop
	                    if (fogNode1 != null && neighbor1 != null) {
	                        // Use the fogNode1 and neighbor1 objects to assign neighbors
	                        System.out.println("Assigning " + neighbor1.name + " as a neighbor of " + fogNode1.name);
	                        // Here the method to set the neighbor relationship:
	                        fogNode1.addNeighbor(neighbor1, 33); // Replace 33 with actual latency value if needed
	                    }                   	                    
	                }
	            }
	        }
	    }
	    
	    // Now, we ASSIGN the corresponding PARENT for the current FOG NODE in the CLOUD NETWORK
	    for (Map.Entry<String, List<String>> cloudNetworkEntry : cloudNetworks.entrySet()) {
	        List<String> cloudNetworkMembers = cloudNetworkEntry.getValue();
            // The first element in the list is the parent (cloud node)
	        String parentNode = cloudNetworkMembers.get(0); 
	        // Iterate over the rest of the members (fog nodes)
	        for (int i = 1; i < cloudNetworkMembers.size(); i++) {
	            String fogNode = cloudNetworkMembers.get(i);
                // Assign parent to the fog node. Here the method to actually assign the parent is set:
                ComputingAppliance parentNode1 = null;
                ComputingAppliance fogNode1 = null;
                // Assign parent node CA using the name from the cloud Network
                for (ComputingAppliance ca : cloudCAList) {
                    if (ca.name.equals(parentNode)) {
                        parentNode1 = ca; // Assign the neighbor object
                        break; // Exit the loop after finding the neighbor
                    }
                }
                // Assign fogNode1 (the current node) using its name
                for (ComputingAppliance ca1 : fogCAList) {
                    if (ca1.name.equals(fogNode)) {
                        fogNode1 = ca1; // Assign the fogNode object
                        break; // Exit the loop after finding the fog node
                    }
                }
                // Now, both neighbor1 and fogNode1 are available for use after the loop
                if (fogNode1 != null && parentNode1 != null) {
                    // Use the fogNode1 and neighbor1 objects to assign neighbors
                    System.out.println("Assigning " + parentNode1.name + " as a parent of " + fogNode1.name);
                    // Here the method to set the parent relationship:
                    fogNode1.setParent(parentNode1, 77); // Replace 33 with actual latency value if needed
                } 
	           
	        }
	    }
	    
	    // ----------------------------- FINAL OVERVIEW ------------------------------------
	    System.out.println("Cloud VirtualAppliances and ARC:");
        for (int i = 0; i < cloudVaList.size(); i++) {
        	//If I just want to get the id is: cloudVaList.get(i).id
            System.out.println("Cloud VA: " + cloudVaList.get(i).toString() + " with ARC: " + cloudArcList.get(i).toString() + " with INST: " + cloudInstList.get(i).toString() + " with CA: " + cloudCAList.get(i).toString() + " with App: " + cloudAppList.get(i).toString() );
        }
        System.out.println("-----------------------------------");
        System.out.println("Fog VirtualAppliances and ARC:");
        for (int i = 0; i < fogVaList.size(); i++) {
            System.out.println("Fog VA: " + fogVaList.get(i).toString() + " with ARC: " + fogArcList.get(i).toString() + " with INST: " + fogInstList.get(i).toString() + " with CA: " + fogCAList.get(i).toString() + " with App: " + fogAppList.get(i).toString());
        }
        
        System.out.println("-----------------------------------");
        System.out.println("-----------------------------------");
        
        // Extract Definition of Networks 
        
        for (Map.Entry<String, GroupTemplate> groupEntry : groups.entrySet()) {
            System.out.println("Group Name: " + groupEntry.getKey());
            System.out.println("Group Type: " + groupEntry.getValue().type);
            System.out.println("Group Members: " + groupEntry.getValue().members);
            System.out.println("-----------------------------------");
        }
	    
    // ---------------------- HERE THE SIMULATION IS EXECUTED --------------------------
    // -------- (YOU CAN COMMENT THE UPCOMING LINES TO CHECK THE PARSING FIRST) --------
	    
     long starttime = System.nanoTime();
     Timed.simulateUntilLastEvent();
     long stoptime = System.nanoTime();

     ScenarioBase.calculateIoTCost();
     ScenarioBase.logBatchProcessing(stoptime - starttime);
     TimelineVisualiser.generateTimeline(ScenarioBase.resultDirectory);
     MapVisualiser.mapGenerator(ScenarioBase.scriptPath, ScenarioBase.resultDirectory, deviceList);
     EnergyDataCollector.writeToFile(ScenarioBase.resultDirectory);   
       
    }
}