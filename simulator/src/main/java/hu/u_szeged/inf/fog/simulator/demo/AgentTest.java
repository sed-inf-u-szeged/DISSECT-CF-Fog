package hu.u_szeged.inf.fog.simulator.demo;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.energy.powermodelling.PowerState;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;
import hu.mta.sztaki.lpds.cloud.simulator.util.PowerTransitionGenerator;
import hu.u_szeged.inf.fog.simulator.agent.BroadcastMessage;
import hu.u_szeged.inf.fog.simulator.agent.Constraint;
import hu.u_szeged.inf.fog.simulator.agent.Orchestration;
import hu.u_szeged.inf.fog.simulator.agent.Pair;
import hu.u_szeged.inf.fog.simulator.agent.ResourceAgent;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

public class AgentTest {
    
	public static void main(String[] args) {
	    // general parameters
	    int msgSize = 50; // byte
	    long repoSize = 10_737_418_240L; // 10 GB
	    long bandwidth =  12500; //  100 Mbps
	    
		// networking + energy
		HashMap<String, Integer> latencyMap = new HashMap<String, Integer>();

		final EnumMap<PowerTransitionGenerator.PowerStateKind, Map<String, PowerState>> transitions = PowerTransitionGenerator
				.generateTransitions(20, 200, 300, 10, 20);
		
		// creating RAs
		ArrayList<Constraint> constraints1 = new ArrayList<>();
		constraints1.add(new Constraint("A", 1));
		constraints1.add(new Constraint("B", 2));
		constraints1.add(new Constraint("C", 3));
		constraints1.add(new Constraint("D", 4));
		new ResourceAgent(new Repository(repoSize, "ra1", bandwidth, bandwidth, bandwidth, latencyMap,
				transitions.get(PowerTransitionGenerator.PowerStateKind.storage),
				transitions.get(PowerTransitionGenerator.PowerStateKind.network)), constraints1);
		latencyMap.put("ra1", 50);

		ArrayList<Constraint> constraints2 = new ArrayList<>();
		constraints2.add(new Constraint("A", 4));
		constraints2.add(new Constraint("B", 7));
		new ResourceAgent(new Repository(repoSize, "ra2", bandwidth, bandwidth, bandwidth, latencyMap,
				transitions.get(PowerTransitionGenerator.PowerStateKind.storage),
				transitions.get(PowerTransitionGenerator.PowerStateKind.network)), constraints2);
		latencyMap.put("ra2", 66);

		ArrayList<Constraint> constraints3 = new ArrayList<>();
		constraints3.add(new Constraint("A", 4));
		constraints3.add(new Constraint("C", 3));
		constraints3.add(new Constraint("D", 3));
		new ResourceAgent(new Repository(repoSize, "ra3", bandwidth, bandwidth, bandwidth, latencyMap,
				transitions.get(PowerTransitionGenerator.PowerStateKind.storage),
				transitions.get(PowerTransitionGenerator.PowerStateKind.network)), constraints3);
		latencyMap.put("ra3", 98);

		ArrayList<Constraint> constraints4 = new ArrayList<>();
		constraints4.add(new Constraint("C", 5));
		constraints4.add(new Constraint("D", 5));
		new ResourceAgent(new Repository(repoSize, "ra4", bandwidth, bandwidth, bandwidth, latencyMap,
				transitions.get(PowerTransitionGenerator.PowerStateKind.storage),
				transitions.get(PowerTransitionGenerator.PowerStateKind.network)), constraints4);
		latencyMap.put("ra4", 55);


        ArrayList<Constraint> constraints5 = new ArrayList<>();
        constraints5.add(new Constraint("B", 4));
        new ResourceAgent(new Repository(repoSize, "ra5", bandwidth, bandwidth, bandwidth, latencyMap,
                transitions.get(PowerTransitionGenerator.PowerStateKind.storage),
                transitions.get(PowerTransitionGenerator.PowerStateKind.network)), constraints5);
        latencyMap.put("ra5", 73);

        ArrayList<Constraint> constraints6 = new ArrayList<>();
        constraints6.add(new Constraint("B", 3));
        constraints6.add(new Constraint("D", 6));
        new ResourceAgent(new Repository(repoSize, "ra6", bandwidth, bandwidth, bandwidth, latencyMap,
                transitions.get(PowerTransitionGenerator.PowerStateKind.storage),
                transitions.get(PowerTransitionGenerator.PowerStateKind.network)), constraints6);
        latencyMap.put("ra6", 101);

		// generating an application demand (later based on TOSCA)
		ArrayList<Constraint> demands = new ArrayList<>();
		demands.add(new Constraint("A", 3));
		demands.add(new Constraint("B", 3));
		demands.add(new Constraint("C", 3));

		Orchestration.submitApplication(true, demands, msgSize);

		Timed.simulateUntilLastEvent();

		System.out.println("~~ Results: ~~");

		System.out.println("Time: " + Timed.getFireCount());
		
		System.out.println("Unique solutions: ");
		for (ArrayList<Pair> list : Orchestration.solutions) {
			System.out.println(list);
		}
		
		System.out.println("Total time: " + BroadcastMessage.totalTimeOnNetwork + " Broadcast time: " + BroadcastMessage.broadcastTimeOnNetwork); 
		System.out.println("Total byte: " + BroadcastMessage.totalByteOnNetwork + " Broadcast byte: " + BroadcastMessage.broadcastByteOnNetwork); 
		System.out.println("Total msg count: " + BroadcastMessage.totalMessageCount + " Broadcast msg count: " + BroadcastMessage.broadcastMessageCount
		        + " / " + BroadcastMessage.calculateTotalMessages(ResourceAgent.agentList.size()) + " (naive)"); 
 
		System.out.println("Acknowledgements:");
		for (ResourceAgent ra : ResourceAgent.agentList) {
			System.out.println(ra + " " + ra.acknowledgement);
		}
	}
}