package hu.u_szeged.inf.fog.simulator.agent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Random;

public class Orchestration {
	
	static ArrayList<ArrayList<Pair>> solutions = new ArrayList<ArrayList<Pair>>();

	public static void submitApplication(boolean firstAgentAllowed, ArrayList<Constraint> demands, int broadcastMessageSize) {
		// choosing one Agent randomly
        Random random = new Random();
        ResourceAgent agent = ResourceAgent.agentList.get(random.nextInt((ResourceAgent.agentList.size())));
        System.out.println("Starter agent: " + agent);
		BroadcastMessage bm = new BroadcastMessage(demands, broadcastMessageSize);
		agent.broadcast(bm, firstAgentAllowed); 
	}
	
	public static void removeDuplicates() {
		ArrayList<ArrayList<Pair>> uniqueSolutions = new ArrayList<>();
        HashSet<HashSet<Pair>> seen = new HashSet<>();

        for (ArrayList<Pair> innerList : solutions) {
            HashSet<Pair> set = new HashSet<>(innerList);

            if (seen.add(set)) { // Csak akkor adja hozzá, ha a set még nincs benne
                uniqueSolutions.add(innerList);
            }
        }

        solutions = uniqueSolutions;
        
        System.out.println("Unique solutions: ");
        for (ArrayList<Pair> list : solutions) {
            System.out.println(list);
        }
    }

}
