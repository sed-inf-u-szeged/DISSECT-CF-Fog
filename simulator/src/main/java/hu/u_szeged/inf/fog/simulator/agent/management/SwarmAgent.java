package hu.u_szeged.inf.fog.simulator.agent.management;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.u_szeged.inf.fog.simulator.agent.AgentApplication;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SwarmAgent extends Timed {

    public static Set<SwarmAgent> allSwarmAgents = new HashSet<>();

    public List<Object> observedAppComponents = new ArrayList<>();

    public AgentApplication app;

    public long totalGeneratedFiles;

    public SwarmAgent(AgentApplication app) {
        this.app = app;
        allSwarmAgents.add(this);
    }

    @Override
    public void tick(long fires) {

    }
}
