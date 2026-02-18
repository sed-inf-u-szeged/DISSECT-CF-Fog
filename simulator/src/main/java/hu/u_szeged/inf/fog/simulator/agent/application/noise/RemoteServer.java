package hu.u_szeged.inf.fog.simulator.agent.application.noise;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.u_szeged.inf.fog.simulator.agent.Capacity.Utilisation;
import hu.u_szeged.inf.fog.simulator.agent.management.GreedyNoiseSwarmAgent;

import java.util.HashMap;
import java.util.Map;

public class RemoteServer {

    // TODO: global knowledge for network time
    public static Map<String, Long> networkTimePerFile = new HashMap<>();

    public static long totalEndToEndLatency;

    GreedyNoiseSwarmAgent swarmAgent;
    
    public Utilisation util;
    
    public PhysicalMachine pm;

    public RemoteServer(GreedyNoiseSwarmAgent swarmAgent, Utilisation util){
        this.swarmAgent = swarmAgent;
        this.util = util;
        this.pm = util.vm.getResourceAllocation().getHost();
        swarmAgent.observedAppComponents.add(this);
    }
}