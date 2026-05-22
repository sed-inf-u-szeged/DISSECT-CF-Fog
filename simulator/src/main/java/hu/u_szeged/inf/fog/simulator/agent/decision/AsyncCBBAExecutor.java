package hu.u_szeged.inf.fog.simulator.agent.decision;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ConsumptionEventAdapter;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode.NetworkException;
import hu.mta.sztaki.lpds.cloud.simulator.io.StorageObject;
import hu.u_szeged.inf.fog.simulator.agent.AgentApplication;
import hu.u_szeged.inf.fog.simulator.agent.AgentApplication.Component;
import hu.u_szeged.inf.fog.simulator.agent.CBBAResourceAgent;
import hu.u_szeged.inf.fog.simulator.agent.ResourceAgent;
import hu.u_szeged.inf.fog.simulator.common.util.SimLogger;
import org.apache.commons.lang3.tuple.MutablePair;

import java.util.*;

public class AsyncCBBAExecutor extends Timed {
    private final AgentApplication app;
    private final AsyncCBBABasedDecisionMaker decisionMaker;
    public final HashMap<MutablePair<CBBAResourceAgent, CBBAResourceAgent>, Long> messageLogger;
    public int msgCount = 0;

    public AsyncCBBAExecutor(AgentApplication app, long freq, AsyncCBBABasedDecisionMaker decisionMaker) {
        this.app = app;
        this.decisionMaker = decisionMaker;
        this.messageLogger = new HashMap<>();

        //build bundle for agent
        decisionMaker.CBBASender.buildBundle(app);

        //broadcast allocated tasks using sendMsg
        for (CBBAResourceAgent agent : CBBAResourceAgent.CBBAResourceAgents) {
            //If receiver is not the sender => send
            if (!Objects.equals(agent.name, decisionMaker.CBBASender.name)) {
                sendMsg(decisionMaker.CBBASender, agent);
            }
        }

        subscribe(freq);
    }

    private void sendMsg(CBBAResourceAgent sender, CBBAResourceAgent receiver) {
        StorageObject message = new StorageObject(UUID.randomUUID().toString(), 2048, false);
        sender.hostNode.iaas.repositories.get(0).registerObject(message);

        try {
            sender.hostNode.iaas.repositories.get(0).requestContentDelivery(
                    message.id, receiver.hostNode.iaas.repositories.get(0), new ConsumptionEventAdapter() {
                        @Override
                        public void conComplete() {
                            sender.hostNode.iaas.repositories.get(0).deregisterObject(message);

                            msgCount++;
                            messageLogger.put(new MutablePair<>(sender, receiver), Timed.getFireCount());

                            //If broadcast would be needed
                            if (receiver.processSentDataACBBA(sender, app)) {
                                //Broadcast
                                for (CBBAResourceAgent agent : CBBAResourceAgent.CBBAResourceAgents) {
                                    //If receiver is not the sender => send
                                    if (!Objects.equals(agent.name, receiver.name)) {
                                        sendMsg(receiver, agent);
                                    }
                                }
                            }
                        }
                    });
        } catch (NetworkException e) {
            e.printStackTrace();
        }
    }

    private boolean checkAllReceived() {
        for (Long value : messageLogger.values()) {
            if (Timed.getFireCount() - value < this.getFrequency()) {
                return false;
            }
        }

        return true;
    }


    @Override
    public void tick(long fires) {
        if (checkAllReceived()) {
            unsubscribe();

            app.deploymentAlgorithmFinishedTime = Timed.getFireCount();
            long algorithmRuntime = app.deploymentAlgorithmFinishedTime - app.deploymentStartedTime;

            SimLogger.logRun(this.app.name + " app async CBBA algorithm took: " + algorithmRuntime + " ms to finish");
            SimLogger.logRun(this.app.name + " app has used: " + this.msgCount + " messages.");
            this.app.messageCount = this.msgCount;

            System.out.println("Task list:");
            for (CBBAResourceAgent agent : CBBAResourceAgent.CBBAResourceAgents) {
                System.out.println("Agent: " + agent.name);
                for (Map.Entry<Component, ResourceAgent> pair : agent.winners.entrySet()) {
                    if (app.components.contains(pair.getKey())) {
                        System.out.println(pair.getKey().id + " resource: " + pair.getValue().name + " agent.");
                    }
                }
            }

            decisionMaker.generateOffers(app);
        }
    }
}
