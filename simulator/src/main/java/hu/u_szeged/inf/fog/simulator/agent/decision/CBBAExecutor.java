package hu.u_szeged.inf.fog.simulator.agent.decision;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.u_szeged.inf.fog.simulator.agent.AgentApplication;
import hu.u_szeged.inf.fog.simulator.agent.CBBAResourceAgent;
import hu.u_szeged.inf.fog.simulator.common.util.SimLogger;

public class CBBAExecutor extends Timed {
    public long msgCount = 0;

    private CBBAMessenger messenger;
    private final AgentApplication app;
    private final CBBABasedDecisionMaker decisionMaker;

    public CBBAExecutor(AgentApplication app, long freq, CBBABasedDecisionMaker decisionMaker) {
        this.decisionMaker = decisionMaker;
        this.app = app;

        //Building bundle
        System.out.println("Initial bundle built");
        for (CBBAResourceAgent agent : CBBAResourceAgent.CBBAResourceAgents) {
            agent.buildBundle(app);
        }

        messenger = new CBBAMessenger(this.app);

        subscribe(freq);
    }


    private boolean checkAllReceived() {
        for (Long value: messenger.messageLogger.values()) {
            //Long value = entry.getValue();

            if (Timed.getFireCount() - value < this.getFrequency()) {
                return false;
            }
        }

        return true;
    }


    @Override
    public void tick(long fires) {
        //Check if finished communicating
        if (checkAllReceived()) {
            System.out.println("Finished messaging");

            this.msgCount += messenger.messageCount;

            //Check if converged:
            //Prune:
            boolean allConverged = true;
            for (CBBAResourceAgent agent : CBBAResourceAgent.CBBAResourceAgents) {
                System.out.println(agent.name + " pruning");
                agent.pruneBundle(app);

                if (!agent.converged.getOrDefault(app, false)) {
                    allConverged = false;
                }
            }

            //Compare
            if (allConverged) {
                //Converged
                unsubscribe();

                for (CBBAResourceAgent agent : CBBAResourceAgent.CBBAResourceAgents) {
                    System.out.println(agent.name + " agent winners:");
                    for (var entry : agent.winners.entrySet()) {
                        if (app.components.contains(entry.getKey())) {
                            System.out.println(entry.getKey().id + " -> " + entry.getValue().name);
                        }
                    }
                }

                app.deploymentAlgorithmFinishedTime = Timed.getFireCount();
                long algorithmRuntime = app.deploymentAlgorithmFinishedTime - app.deploymentStartedTime;

                SimLogger.logRun(this.app.name + " app CBBA algorithm took: " + algorithmRuntime + " ms to finish");
                SimLogger.logRun(this.app.name + " app has used: " + this.msgCount + " messages.");
                this.app.messageCount = this.msgCount;

                decisionMaker.generateOffers(this.app);
            } else {
                //Not converged
                System.out.println("New bundle built");

                for (CBBAResourceAgent agent : CBBAResourceAgent.CBBAResourceAgents) {
                    agent.buildBundle(app);
                }

                //New communication
                messenger = new CBBAMessenger(this.app);
            }
        }
    }
}
