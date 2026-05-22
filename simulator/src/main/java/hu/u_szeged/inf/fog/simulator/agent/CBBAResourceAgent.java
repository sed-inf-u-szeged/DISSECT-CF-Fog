package hu.u_szeged.inf.fog.simulator.agent;

import hu.mta.sztaki.lpds.cloud.simulator.DeferredEvent;
import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.u_szeged.inf.fog.simulator.agent.AgentApplication.Component;
import hu.u_szeged.inf.fog.simulator.agent.decision.DecisionMaker;
import hu.u_szeged.inf.fog.simulator.agent.strategy.mapping.MappingStrategy;
import hu.u_szeged.inf.fog.simulator.agent.strategy.message.MessagingStrategy;
import hu.u_szeged.inf.fog.simulator.common.node.ComputingAppliance;
import hu.u_szeged.inf.fog.simulator.common.util.ScenarioBase;
import hu.u_szeged.inf.fog.simulator.common.util.SimLogger;
import hu.u_szeged.inf.fog.simulator.rl.MABTable;
import org.apache.commons.lang3.tuple.Pair;

import java.util.*;

public class CBBAResourceAgent extends ResourceAgent {
    //For both CBBAs
    public Map<AgentApplication, Boolean> converged = new HashMap<>();

    public static List<CBBAResourceAgent> CBBAResourceAgents = new ArrayList<>();

    //Bundle => list of tasks/components, used in the build bundle process to maximize the resource usage with the best tasks
    public List<Component> bundle = new ArrayList<>();

    //Bids => bid for a specific task
    //Winners => winner RA for a specific task
    //Timestamps => timestamp for a specific task
    public Map<Component, Double> bids = new HashMap<>();
    public Map<Component, ResourceAgent> winners = new HashMap<>();
    public Map<Component, Long> timestamp = new HashMap<>();
    //Last three filled as -inf, -1, 0 for all tasks in the python version

    //For QLearning
    public MABTable mabTable = null;

    public CBBAResourceAgent(String name, double hourlyPrice, MappingStrategy agentStrategy, MessagingStrategy messagingStrategy) {
        super(name, hourlyPrice, agentStrategy, messagingStrategy);
        CBBAResourceAgents.add(this);
    }

    public static void minimumsMaximums() {
        for (ResourceAgent agent : CBBAResourceAgents) {
            setMinimumsMaximums(agent);
        }
    }

    public double computeBid(AgentApplication app, Component task) {
        double latency = 1 - normalize(getAvgLatency(this).doubleValue(), minLatency.doubleValue(), maxLatency.doubleValue());

        //Bandwidth doesn't need to be inverted
        double bandwidth = normalize((double)getAvgBW(this), (double)minBW, (double)maxBW);

        double energy = 1 - normalize(getAvgEnergy(this), minEnergy, maxEnergy);
        double price = 1 - normalize(this.getPrice(), minPrice, maxPrice);

        double cpu_ram = task.requirements.cpu + (task.requirements.memory / 1_073_741_824.0);

        return (latency * app.latency +
                bandwidth * app.bandwidth +
                energy * app.energy +
                price * app.price) * cpu_ram;
    }

    //Choose as many of the 'best tasks' as we can handle (greedy)
    public void buildBundle(AgentApplication app) {
        this.bundle.clear();

        while (true) {
            List<Component> candidates = new ArrayList<>();

            for (Component res : app.components) {
                if (!this.bundle.contains(res) && (this.winners.get(res) == null)) {
                    //Can fulfill reserves it as well, it needs to be released if it was reserved
                    List<Pair<ResourceAgent, Component>> temp = agentStrategy.canFulfill(this, List.of(res));

                    if (!temp.isEmpty()) {
                        candidates.add(res);

                        for (Pair<ResourceAgent, Component> pair : temp) {
                            for (Capacity capacity : this.capacities.values()) {
                                capacity.releaseCapacity(pair.getValue());
                            }
                        }
                    }
                }
            }

            Component bestTask = null;
            double bestBid = Double.MIN_VALUE;

            if (candidates.isEmpty()) {
                break;
            } else {
                for (Component candidate : candidates) {
                    double bid = computeBid(app, candidate);
                    if (bid > bestBid) {
                        bestTask = candidate;
                        bestBid = bid;
                    }
                }

                if (bestTask == null) {
                    break;
                }
            }

            this.bundle.add(bestTask);
            this.bids.put(bestTask, bestBid);
            this.winners.put(bestTask, this);
            this.timestamp.put(bestTask, Timed.getFireCount());

            //Reserve the best task
            agentStrategy.canFulfill(this, List.of(bestTask));
        }
    }

    //Remove tasks that we lost, (can decide sync. convergence)
    public void pruneBundle(AgentApplication app) {
        //System.out.println("Bundle size before: " + this.bundle.size());
        List<Component> newBundle = new ArrayList<>();

        this.converged.put(app, true);

        //we don't necessarily need to remove them, it will be cleared anyway, it will stay for the sake of messages (for debugging)
        // either CBBA will end, and won tasks will be decided using winners map
        // or next iteration will run, and bundle will be re-created
        for (Component res : this.bundle) {
            if (app.components.contains(res)) {
                if (this.winners.get(res).equals(this)) {
                    newBundle.add(res);
                } else {
                    this.converged.put(app, false);

                    //If I didn't win the task, release it
                    for (Capacity capacity : this.capacities.values()) {
                        capacity.releaseCapacity(res);
                    }
                }
            }
        }

        this.bundle = newBundle;
        //System.out.println("Bundle size after: " + this.bundle.size());
    }

    //Solve conflict, Timestamp priority
    public void exchangeOriginal(CBBAResourceAgent other, Component res) {
        if (other.timestamp.getOrDefault(res, 0L) > this.timestamp.getOrDefault(res, 0L)) {
            this.adoptInfo(res, other);
        } else if (Objects.equals(other.timestamp.getOrDefault(res, 0L), this.timestamp.getOrDefault(res, 0L))) {
            if (other.bids.getOrDefault(res, Double.MIN_VALUE) > this.bids.getOrDefault(res, Double.MIN_VALUE)) {
                this.adoptInfo(res, other);
            } else if (Objects.equals(other.bids.getOrDefault(res, Double.MIN_VALUE), this.bids.getOrDefault(res, Double.MIN_VALUE))) {
                if (other.winners.getOrDefault(res, this).name.compareTo(this.winners.getOrDefault(res, this).name) > 0) {
                    this.adoptInfo(res, other);
                }
            }
        }
    }

    //Solve conflict, Bid priority
    public void exchange(CBBAResourceAgent other, Component res) {
        if (other.bids.getOrDefault(res, Double.MIN_VALUE) > this.bids.getOrDefault(res, Double.MIN_VALUE)) {
            this.adoptInfo(res, other);
        } else if (Objects.equals(other.bids.getOrDefault(res, Double.MIN_VALUE), this.bids.getOrDefault(res, Double.MIN_VALUE))) {
            if (other.timestamp.getOrDefault(res, 0L) > this.timestamp.getOrDefault(res, 0L)) {
                this.adoptInfo(res, other);
            } else if (Objects.equals(other.timestamp.getOrDefault(res, 0L), this.timestamp.getOrDefault(res, 0L))) {
                if (other.winners.getOrDefault(res, this).name.compareTo(this.winners.getOrDefault(res, this).name) > 0) {
                    this.adoptInfo(res, other);
                }
            }
        }
    }

    //Process sent data (async. CBBA), returns true if re-broadcast is needed
    public boolean processSentDataACBBA(CBBAResourceAgent other, AgentApplication app) {
        boolean broadcastNeeded = false;

        this.buildBundle(app);

        //Exchange tasks
        for (Component res : app.components) {
            this.exchange(other, res);
        }

        //Free lost tasks
        this.pruneBundle(app);

        //If we won a task, re broadcast would be needed to let others know
        for (Map.Entry<Component, ResourceAgent> res : this.winners.entrySet()) {
            //If both of us have a result for a specific task (null check)
            if (res.getValue() != null && other.winners.get(res.getKey()) != null) {
                //We won it and other doesn't have it as a winner
                if (Objects.equals(res.getValue().name, this.name) && !Objects.equals(other.winners.get(res.getKey()).name, this.name)) {
                    //System.out.println("Other: " + other.computeBid(app, res.getKey()) + " bid (lost)");
                    //System.out.println("This: " + this.computeBid(app, res.getKey()) + " bid (WON)\n");
                    broadcastNeeded = true;
                }
            } else if (res.getValue() != null && other.winners.get(res.getKey()) == null) {
                //If we have something as a winner, but other doesn't => more broadcast is needed
                broadcastNeeded = true;
            }
        }

        //Rebuild after winner check, otherwise automatic winner declaration without data exchange could cause conflicts and possibly more messages could be needed
        if (!this.converged.getOrDefault(app, false)) {
            //Rebuild bundle if any tasks were lost
            this.buildBundle(app);

            //If not converged (lost tasks), broadcast needed
            broadcastNeeded = true;
        }

        return broadcastNeeded;
    }

    public void adoptInfo(Component task, CBBAResourceAgent other) {
        this.bids.put(task, other.bids.get(task));
        this.winners.put(task, other.winners.get(task));
        this.timestamp.put(task, other.timestamp.get(task));
    }

    @Override
    protected void deploy(AgentApplication app, int bcastMessageSize, DecisionMaker decisionMaker) {
        this.decisionMaker = decisionMaker;
        this.bcastMessageSize = bcastMessageSize;

        decisionMaker.CBBASender = this;
        decisionMaker.start(app);
    }

    @Override
    public void processAppOffer(AgentApplication app) {
        if (!app.offers.isEmpty()) {
            app.winningOffer = 0;

            /*
            Offer winningOffer = app.offers.get(app.winningOffer);
            for (ResourceAgent agent : ResourceAgent.resourceAgents) {
                for (Capacity capacity : agent.capacities) {
                    freeReservedResourcesExceptWinningOffer(app.name, capacity, winningOffer);
                }
            }
            */

            acknowledgeAndInitSwarmAgent(app, app.offers.get(app.winningOffer), bcastMessageSize);
        } else {
            releaseResourcesDueToNoOffers(app);
            new DeferredEvent(10 * 1_000L) {

                @Override
                protected void eventAction() {
                    if (app.broadcastCount <= maxRebroadcast) {
                        SimLogger.logRun("Rebroadcast " + (app.broadcastCount) + " for " + app.name + " at: "
                                + Timed.getFireCount() / (double) ScenarioBase.MINUTE_IN_MILLISECONDS + " min.");
                        broadcast(app, bcastMessageSize, decisionMaker);

                        /*
                        ResourceAgent.resourceAgents
                                .forEach(agent -> ((SimulatedAnnealingStrategy)agent.agentStrategy).switchCoolingTactic());
                        */
                    } else {
                        acknowledgeAndInitSwarmAgent(app, new Offer(new HashMap<>(), -1), bcastMessageSize);
                        app.deploymentTime = -1;
                    }
                }
            };
        }
    }

    @Override
    protected void releaseResourcesDueToNoOffers(AgentApplication app) {
        for (CBBAResourceAgent agent : CBBAResourceAgent.CBBAResourceAgents) {
            for (Capacity capacity : agent.capacities.values()) {
                freeReservedResources(app.name, capacity);
            }
        }
    }

    @Override
    protected void acknowledgeAndInitSwarmAgent(AgentApplication app, Offer offer, int bcastMessageSize) {

        /*
        if (messagingStrategy instanceof GuidedSearchMessagingStrategy) {
            ((GuidedSearchMessagingStrategy) messagingStrategy).setWinningOffer(offer);
        }
        */

        MessageHandler.executeMessaging(messagingStrategy, this, app, bcastMessageSize, "ack", () -> {
            SimLogger.logRun("Messaging are done for " + app.name
                    + " at: " + Timed.getFireCount() / (double) ScenarioBase.MINUTE_IN_MILLISECONDS + " min.");

            if (offer.id == -1) {
                SimLogger.logRun(app.name + "'s requirements cannot be fulfilled!");
                releaseResourcesDueToNoOffers(app);
                return;
            }
            for (CBBAResourceAgent agent : CBBAResourceAgent.CBBAResourceAgents) {
                for (Capacity capacity : agent.capacities.values()) {
                    if (offer.agentComponentsMap.containsKey(agent)) {
                        capacity.assignCapacity(offer.agentComponentsMap.get(agent), offer);
                    }

                    freeReservedResources(app.name, capacity);
                }
            }
            Pair<ComputingAppliance, Capacity.Utilisation> leadResource = setLeadResource(offer.utilisations);
            new Deployment(leadResource, offer, app);
        });
    }
}
