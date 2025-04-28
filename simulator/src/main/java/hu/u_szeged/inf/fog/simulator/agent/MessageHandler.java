package hu.u_szeged.inf.fog.simulator.agent;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ConsumptionEventAdapter;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode.NetworkException;
import hu.mta.sztaki.lpds.cloud.simulator.io.StorageObject;
import hu.u_szeged.inf.fog.simulator.agent.messagestrategy.MessagingStrategy;
import java.util.List;

public class MessageHandler {
    public static void executeMessaging(MessagingStrategy messagingStrategy,
                                        ResourceAgent gateway, AgentApplication app, int bcastMessageSize, String msg, Runnable customAction) {
        List<ResourceAgent> filteredAgents = messagingStrategy.filterAgents(gateway);

        System.out.println("Networking agents for gateway "+ gateway.name +":"); // debug
        filteredAgents.forEach(resourceAgent -> System.out.println(resourceAgent.name));

        for (ResourceAgent agent : filteredAgents) {
            String reqName = gateway.name + "-" + agent.name + "-" + app.name + "-" + msg + "-request";
            StorageObject reqMessage = new StorageObject(reqName, bcastMessageSize, false);
            gateway.hostNode.iaas.repositories.get(0).registerObject(reqMessage);
            app.bcastCounter++;

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
                                                    app.bcastCounter--;
                                                    if (app.bcastCounter == 0) {
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