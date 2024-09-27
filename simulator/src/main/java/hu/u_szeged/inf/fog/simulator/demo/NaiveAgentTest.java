package hu.u_szeged.inf.fog.simulator.demo;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.energy.powermodelling.PowerState;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;
import hu.mta.sztaki.lpds.cloud.simulator.util.PowerTransitionGenerator;
import hu.u_szeged.inf.fog.simulator.agent.BroadcastMessage;
import hu.u_szeged.inf.fog.simulator.agent.Constraint;
import hu.u_szeged.inf.fog.simulator.agent.Orchestration;
import hu.u_szeged.inf.fog.simulator.agent.Pair;
import hu.u_szeged.inf.fog.simulator.agent.NaiveResourceAgent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class NaiveAgentTest {
    
	public static void main(String[] args) {
	    long maxHeapSize = Runtime.getRuntime().maxMemory();
        System.out.println("Maximum heap size (-Xmx): " + (maxHeapSize / (1024 * 1024)) + " MB");
        //System.exit(0);
    
	    // general parameters
	    int msgSize = 100; // byte
	    long repoSize = 10_737_418_240L; // 10 GB
	    long bandwidth =  12500; //  100 Mbps
	    
		// networking + energy
		HashMap<String, Integer> latencyMap = new HashMap<String, Integer>();

		final EnumMap<PowerTransitionGenerator.PowerStateKind, Map<String, PowerState>> transitions = PowerTransitionGenerator
				.generateTransitions(20, 200, 300, 10, 20);
		
		// generating RAs
		generateResources(14, repoSize, bandwidth, latencyMap, transitions);
		
		
		// generating an application demand (later based on TOSCA)
		ArrayList<Constraint> demands = new ArrayList<>();
		demands.add(new Constraint("A", 3));
		demands.add(new Constraint("B", 3));
		demands.add(new Constraint("C", 3));

		Orchestration.submitApplication(true, demands, msgSize);
		
		long starttime = System.nanoTime();
        Timed.simulateUntilLastEvent();
        long stoptime = System.nanoTime();
        
		System.out.println("~~ Results: ~~");

		System.out.println("Runtime: " + TimeUnit.SECONDS.convert((stoptime-starttime), TimeUnit.NANOSECONDS));
		
		System.out.println("Time: " + Timed.getFireCount());
		
		System.out.println("Unique solutions: " + Orchestration.solutions.size());
		/*for (ArrayList<Pair> list : Orchestration.solutions) {
			System.out.println(list);
		}*/
		
		System.out.println("Total time: " + BroadcastMessage.totalTimeOnNetwork + " Broadcast time: " + BroadcastMessage.broadcastTimeOnNetwork); 
		System.out.println("Total byte: " + BroadcastMessage.totalByteOnNetwork + " Broadcast byte: " + BroadcastMessage.broadcastByteOnNetwork); 
		System.out.println("Total msg count: "             + BroadcastMessage.totalMessageCount 
		                 + " Broadcast msg count: "        + BroadcastMessage.broadcastMessageCount
		                 + " Acknowledgement msg count: " + (BroadcastMessage.totalMessageCount - BroadcastMessage.broadcastMessageCount)
		                 + " Worst case (ack / br): "      + BroadcastMessage.calculateTotalBrMessages(NaiveResourceAgent.agentList.size()) * 2
		                 + " / "                           + BroadcastMessage.calculateTotalBrMessages(NaiveResourceAgent.agentList.size())); 
 
		/*System.out.println("Acknowledgements:");
		for (ResourceAgent ra : ResourceAgent.agentList) {
			System.out.println(ra + " " + ra.acknowledgement);
		}*/
	}

	 public static void generateResources(int numberOfResources, long repoSize, long bandwidth, HashMap<String, Integer> latencyMap,
	         EnumMap<PowerTransitionGenerator.PowerStateKind, Map<String, PowerState>> transitions) {
	     
	        Random random = new Random();
	        List<String> types = Arrays.asList("A", "B", "C", "D");
	        List<Integer> values = Arrays.asList(2, 3, 4, 5);

	        ArrayList<NaiveResourceAgent> agents = new ArrayList<>();

	        for (int i = 0; i < numberOfResources; i++) {
	            ArrayList<Constraint> constraints = new ArrayList<>();
	            Set<String> usedTypes = new HashSet<>();
	            int numberOfConstraints = random.nextInt(4) + 1; 

	            for (int j = 0; j < numberOfConstraints; j++) {
	                String type;
	                do {
	                    type = types.get(random.nextInt(types.size()));  
	                } while (usedTypes.contains(type));  

	                int value = values.get(random.nextInt(values.size()));  
	                constraints.add(new Constraint(type, value));
	                usedTypes.add(type);  
	            }
	            String uuid = UUID.randomUUID().toString();
	            String name = Integer.toHexString(uuid.hashCode()).substring(0, 4);
	            
	            NaiveResourceAgent ra =  new NaiveResourceAgent(new Repository(repoSize, name, bandwidth, bandwidth, bandwidth, latencyMap,
	                    transitions.get(PowerTransitionGenerator.PowerStateKind.storage),
	                    transitions.get(PowerTransitionGenerator.PowerStateKind.network)), constraints);
	            latencyMap.put(name, random.nextInt(51)+50);
	            agents.add(ra);
	        }

	        System.out.println("RAs generated: " + numberOfResources);
	        for (NaiveResourceAgent resource : agents) {
	            System.out.println(resource);
	        }
	}
}