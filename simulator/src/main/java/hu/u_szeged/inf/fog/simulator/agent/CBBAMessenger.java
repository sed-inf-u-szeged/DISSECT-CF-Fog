package hu.u_szeged.inf.fog.simulator.agent;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ConsumptionEventAdapter;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode;
import hu.mta.sztaki.lpds.cloud.simulator.io.StorageObject;
import hu.u_szeged.inf.fog.simulator.agent.AgentApplication.Resource;
import org.apache.commons.lang3.tuple.MutablePair;

import java.util.HashMap;
import java.util.UUID;

public class CBBAMessenger {
    public final HashMap<MutablePair<CBBAResourceAgent, CBBAResourceAgent>, Long> messageLogger;

    public int messageCount;

    public CBBAMessenger(AgentApplication app) {
        this.messageLogger = new HashMap<>();

        messageCount = 0;

        for (int i = 0; i < CBBAResourceAgent.CBBAResourceAgents.size(); i++) {
            CBBAResourceAgent agent1 = CBBAResourceAgent.CBBAResourceAgents.get(i);

            for (int j = i + 1; j < CBBAResourceAgent.CBBAResourceAgents.size(); j++) {
                CBBAResourceAgent agent2 = CBBAResourceAgent.CBBAResourceAgents.get(j);

                this.sendMsg(agent1, agent2, app);
                this.sendMsg(agent2, agent1, app);
            }
        }
    }

    private void sendMsg(CBBAResourceAgent agent1, CBBAResourceAgent agent2, AgentApplication app) {
        StorageObject message = new StorageObject(UUID.randomUUID().toString(), 2048, false);
        agent1.hostNode.iaas.repositories.get(0).registerObject(message);

        try {
            agent1.hostNode.iaas.repositories.get(0).requestContentDelivery(
                    message.id, agent2.hostNode.iaas.repositories.get(0), new ConsumptionEventAdapter() {
                        @Override
                        public void conComplete() {
                            agent1.hostNode.iaas.repositories.get(0).deregisterObject(message);
                            messageCount++;

                            messageLogger.put(new MutablePair<>(agent1, agent2), Timed.getFireCount());

                            for (Resource res : app.resources) {
                                agent2.exchange(agent1, res);
                            }
                        }
                    }
            );
        } catch (NetworkNode.NetworkException e) {
            e.printStackTrace();
        }
    }
}
