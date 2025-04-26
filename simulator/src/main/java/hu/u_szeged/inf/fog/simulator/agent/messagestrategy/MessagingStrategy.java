package hu.u_szeged.inf.fog.simulator.agent.messagestrategy;

import hu.u_szeged.inf.fog.simulator.agent.ResourceAgent;
import java.util.List;

public abstract class MessagingStrategy {

    public abstract List<ResourceAgent> filterAgents(ResourceAgent gateway);
}