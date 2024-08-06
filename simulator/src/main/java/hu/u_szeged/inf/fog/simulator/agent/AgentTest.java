package hu.u_szeged.inf.fog.simulator.agent;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.energy.powermodelling.PowerState;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;
import hu.mta.sztaki.lpds.cloud.simulator.util.PowerTransitionGenerator;

public class AgentTest {

    public static void main(String[] args) {
    	// networking
    	HashMap<String, Integer> latencyMap = new HashMap<String, Integer>();
    	
    	// energy transitions
    	final EnumMap<PowerTransitionGenerator.PowerStateKind, Map<String, PowerState>> transitions =
                PowerTransitionGenerator.generateTransitions(20, 200, 300, 10, 20);
        
    	// creating RAs
    	ArrayList<Constraint> constraints1 = new ArrayList<>();
        constraints1.add(new Constraint("A", 1));
        constraints1.add(new Constraint("B", 2));
        constraints1.add(new Constraint("C", 3));
        constraints1.add(new Constraint("D", 4));
        new ResourceAgent(new Repository(1_000, "ra1", 1_000, 1_000, 1_000, latencyMap, 
        		transitions.get(PowerTransitionGenerator.PowerStateKind.storage), 
        		transitions.get(PowerTransitionGenerator.PowerStateKind.network)), constraints1);
        latencyMap.put("ra1", 50);

        ArrayList<Constraint> constraints2 = new ArrayList<>();
        constraints2.add(new Constraint("A", 4));
        constraints2.add(new Constraint("B", 7));
        new ResourceAgent(new Repository(1_000, "ra2", 1_000, 1_000, 1_000, latencyMap, 
        		transitions.get(PowerTransitionGenerator.PowerStateKind.storage), 
        		transitions.get(PowerTransitionGenerator.PowerStateKind.network)), constraints2);
        latencyMap.put("ra2", 66);

        ArrayList<Constraint> constraints3 = new ArrayList<>();
        constraints3.add(new Constraint("A", 4));
        constraints3.add(new Constraint("C", 3));
        constraints3.add(new Constraint("D", 3));
        new ResourceAgent(new Repository(1_000, "ra3", 1_000, 1_000, 1_000, latencyMap, 
        		transitions.get(PowerTransitionGenerator.PowerStateKind.storage), 
        		transitions.get(PowerTransitionGenerator.PowerStateKind.network)), constraints3);
        latencyMap.put("ra3", 99);
        
        ArrayList<Constraint> constraints4 = new ArrayList<>();
        constraints4.add(new Constraint("C", 5));
        constraints4.add(new Constraint("D", 5));
        new ResourceAgent(new Repository(1_000, "ra4", 1_000, 1_000, 1_000, latencyMap, 
        		transitions.get(PowerTransitionGenerator.PowerStateKind.storage), 
        		transitions.get(PowerTransitionGenerator.PowerStateKind.network)), constraints4);
        latencyMap.put("ra4", 45);

        ArrayList<Constraint> constraints5 = new ArrayList<>();
        constraints5.add(new Constraint("B", 4));
        new ResourceAgent(new Repository(1_000, "ra5", 1_000, 1_000, 1_000, latencyMap, 
        		transitions.get(PowerTransitionGenerator.PowerStateKind.storage), 
        		transitions.get(PowerTransitionGenerator.PowerStateKind.network)), constraints5);
        latencyMap.put("ra5", 55);

        ArrayList<Constraint> constraints6 = new ArrayList<>();
        constraints6.add(new Constraint("B", 3));
        constraints6.add(new Constraint("D", 6));
        new ResourceAgent(new Repository(1_000, "ra6", 1_000, 1_000, 1_000, latencyMap, 
        		transitions.get(PowerTransitionGenerator.PowerStateKind.storage), 
        		transitions.get(PowerTransitionGenerator.PowerStateKind.network)), constraints6);
        latencyMap.put("ra6", 70);

        // generating an application demand (later based on TOSCA)
        ArrayList<Constraint> demands = new ArrayList<>();
        demands.add(new Constraint("A", 3));
        demands.add(new Constraint("B", 3));
        demands.add(new Constraint("C", 3));
        
        // choosing one Agent randomly
        Random random = new Random();
        ResourceAgent agent = ResourceAgent.agentList.get(random.nextInt((ResourceAgent.agentList.size())));
        System.out.println("Starter agent: " + agent);
	    BroadcastMessage bm = new BroadcastMessage(demands, 1);
	    
        agent.broadcast(bm, true); 
       
        Timed.simulateUntilLastEvent();
        //System.out.println(Timed.getFireCount());
        
        for(ResourceAgent ra : ResourceAgent.agentList) {
        	System.out.println(ra + " " +  ra.acknowledgement);
        }
    }
}
