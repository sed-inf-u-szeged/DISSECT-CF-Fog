package hu.u_szeged.inf.fog.simulator.agent;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ConsumptionEventAdapter;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode.NetworkException;
import hu.mta.sztaki.lpds.cloud.simulator.io.StorageObject;
import hu.u_szeged.inf.fog.simulator.agent.agentstrategy.SimulatedAnnealing;
import hu.u_szeged.inf.fog.simulator.agent.messagestrategy.MessagingStrategy;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class GuidedMessageHandler {
    public static void executeMessaging(MessagingStrategy messagingStrategy,
                                        GuidedResourceAgent gateway, AgentApplication app, int bcastMessageSize, String msg, Runnable customAction) {
        List<GuidedResourceAgent> filteredAgents;

        if(msg.equals("bcast")) {
            filteredAgents = messagingStrategy.filterAgents(gateway);
            app.networkingAgents = new HashSet<>(filteredAgents);
            gateway.servedAsGatewayCount++;
            if(gateway.agentStrategy instanceof SimulatedAnnealing && (app.broadcastCount == 1 || app.broadcastCount % 5 == 0)){
                ResourceAgent.resourceAgents
                        .forEach(agent -> ((SimulatedAnnealing)agent.agentStrategy).resetValues());
            }
        } else {
            app.networkingAgents.remove(gateway);
            filteredAgents = new ArrayList<>(app.networkingAgents);
        }

        for (ResourceAgent agent : filteredAgents) {
            String reqName = gateway.name + "-" + agent.name + "-" + app.name + "-" + msg + "-request";
            StorageObject reqMessage = new StorageObject(reqName, bcastMessageSize, false);
            gateway.hostNode.iaas.repositories.get(0).registerObject(reqMessage);
            app.agentsNotifiedCounter++;

            try {
                gateway.hostNode.iaas.repositories.get(0).requestContentDelivery(
                        reqName, agent.hostNode.iaas.repositories.get(0), new ConsumptionEventAdapter() {

                            @Override
                            public void conComplete() {
                                gateway.hostNode.iaas.repositories.get(0).deregisterObject(reqName);
                                String resName = agent.name + "-" + gateway.name + "-" + app.name + "-" + msg + "-response";
                                StorageObject resMessage = new StorageObject(resName, bcastMessageSize, false);
                                agent.hostNode.iaas.repositories.get(0).registerObject(resMessage);

                                try {
                                    agent.hostNode.iaas.repositories.get(0).requestContentDelivery(
                                            resName, gateway.hostNode.iaas.repositories.get(0), new ConsumptionEventAdapter() {

                                                @Override
                                                public void conComplete() {
                                                    agent.hostNode.iaas.repositories.get(0).deregisterObject(resMessage);
                                                    agent.hostNode.iaas.repositories.get(0).deregisterObject(reqMessage);
                                                    gateway.hostNode.iaas.repositories.get(0).deregisterObject(resMessage);
                                                    app.agentsNotifiedCounter--;

                                                    if (app.agentsNotifiedCounter == 0) {
                                                        customAction.run();
                                                    }
                                                }
                                            });
                                } catch (NetworkException e) {
                                    e.printStackTrace();
                                }
                            }
                        }
                );
            } catch (NetworkException e) {
                e.printStackTrace();
            }

        }

    }
}