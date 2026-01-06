package hu.u_szeged.inf.fog.simulator.agent;

import hu.mta.sztaki.lpds.cloud.simulator.DeferredEvent;
import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.AlterableResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.io.VirtualAppliance;
import hu.u_szeged.inf.fog.simulator.agent.AgentApplication.Resource;
import hu.u_szeged.inf.fog.simulator.agent.Capacity.Utilisation;
import hu.u_szeged.inf.fog.simulator.agent.decision.DecisionMaker;
import hu.u_szeged.inf.fog.simulator.agent.agentstrategy.AgentStrategy;
import hu.u_szeged.inf.fog.simulator.node.ComputingAppliance;
import hu.u_szeged.inf.fog.simulator.util.SimLogger;
import hu.u_szeged.inf.fog.simulator.util.agent.AgentApplicationReader;

import java.util.*;

import org.apache.commons.lang3.tuple.Pair;

public class CBBAResourceAgent extends ResourceAgent {
    //For synchronised CBBA
    public boolean converged;

    //For asynchronous CBBA
    public boolean lost;

    public static ArrayList<CBBAResourceAgent> CBBAResourceAgents = new ArrayList<>();

    //Bundle => list of tasks/components, used in the build bundle process to maximise the resource usage with the best tasks
    public List<Resource> bundle = new ArrayList<>();

    //Bids => bid for a specific task
    //Winners => winner RA for a specific task
    //Timestamps => timestamp for a specific task
    public Map<Resource, Double> bids = new HashMap<>();
    public Map<Resource, ResourceAgent> winners = new HashMap<>();
    public Map<Resource, Long> timestamp = new HashMap<>();
    //Last three filled as -inf, -1, 0 for all tasks in the python version

    public CBBAResourceAgent(String name, double hourlyPrice, VirtualAppliance resourceAgentVa, AlterableResourceConstraints resourceAgentArc, AgentStrategy agentStrategy, Capacity... capacities) {
        super(name, hourlyPrice, resourceAgentVa, resourceAgentArc, agentStrategy, capacities);
        CBBAResourceAgents.add(this);
    }

    public CBBAResourceAgent(String name, double hourlyPrice, VirtualAppliance resourceAgentVa, AlterableResourceConstraints resourceAgentArc, AgentStrategy agentStrategy) {
        super(name, hourlyPrice, resourceAgentVa, resourceAgentArc, agentStrategy);
        CBBAResourceAgents.add(this);
    }

    public static void minimumsMaximums() {
        for (ResourceAgent agent : CBBAResourceAgents) {
            setMinimumsMaximums(agent);
        }
    }

    public double computeBid(AgentApplication app, Resource task) {
        double latency = 1 - normalize(getAvgLatency(this).doubleValue(), minLatency.doubleValue(), maxLatency.doubleValue());

        //Bandwith-et nem kell invertálni, minél nagyobb, annál jobb
        double bandwidth = normalize((double)getAvgBW(this), (double)minBW, (double)maxBW);

        double energy = 1 - normalize(getAvgEnergy(this), minEnergy, maxEnergy);
        double price = 1 - normalize(this.hourlyPrice, minPrice, maxPrice);

        double cpu_ram = task.cpu + (task.memory / 1_073_741_824.0);

        return (latency * app.latencyPriority +
                bandwidth * app.bandwidthPriority +
                energy * app.energyPriority +
                price * app.pricePriority) * cpu_ram;
    }

    //Choose as many of the 'best tasks' as we can handle (greedy)
    public void buildBundle(AgentApplication app) {
        this.bundle.clear();

        while (true) {
            List<Resource> candidates = new ArrayList<>();

            for (Resource res : app.resources) {
                if (!this.bundle.contains(res) && (this.winners.get(res) == null)) {
                    //Can fulfill reserves it as well, it needs to be released if it was reserved
                    List<Pair<ResourceAgent, Resource>> temp = agentStrategy.canFulfill(this, List.of(res));

                    if (!temp.isEmpty()) {
                        candidates.add(res);

                        for (Pair<ResourceAgent, Resource> pair : temp) {
                            for (Capacity capacity : this.capacities) {
                                capacity.releaseCapacity(pair.getValue());
                            }
                        }
                    }
                }
            }

            Resource bestTask = null;
            double bestBid = Double.MIN_VALUE;

            if (candidates.isEmpty()) {
                break;
            } else {
                for (Resource candidate : candidates) {
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
    public void pruneBundle() {
        System.out.println("Bundle size before: " + this.bundle.size());
        List<Resource> newBundle = new ArrayList<>();

        this.converged = true;

        //TODO: we don't need to remove them, it will be cleared anyways, it will stay for the sake of messages (for debugging)
        // either CBBA will end, and won tasks will be decided using winners map
        // or next iteration will run, and bundle will be re-created
        for (Resource res : this.bundle) {
            if (this.winners.get(res).equals(this)) {
                newBundle.add(res);
            } else {
                converged = false;

                //If I didn't win the task, release it
                for (Capacity capacity : this.capacities) {
                    capacity.releaseCapacity(res);
                }
            }
        }

        this.bundle = newBundle;
        System.out.println("Bundle size after: " + this.bundle.size());
    }

    //Solve conflict, Timestamp priority
    public void exchange(CBBAResourceAgent other, Resource res) {
        lost = false;

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

    //TODO: this yields better results
    //Solve conflict, Bid priority
    public void newExchangeTest(CBBAResourceAgent other, Resource res) {
        this.lost = false;

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

        List<Resource> lostTasks = new ArrayList<>();

        this.buildBundle(app);

        //Exchange tasks, we 'lose' our tasks here if there is a better winner
        for (Resource res : app.resources) {
            this.newExchangeTest(other, res);

            if (this.lost) {
                lostTasks.add(res);
                broadcastNeeded = true;
            }
        }

        //Free lost tasks
        for (Resource res : lostTasks) {
            for (Capacity capacity : this.capacities) {
                capacity.releaseCapacity(res);
                this.bundle.remove(res);
            }
        }

        //If we won a task, re broadcast would be needed to let others know
        for (Map.Entry<Resource, ResourceAgent> res : this.winners.entrySet()) {
            //If both of us have a result for a specific task (null check)
            if (res.getValue() != null && other.winners.get(res.getKey()) != null) {
                //We won it and other doesn't have it as a winner
                if (Objects.equals(res.getValue().name, this.name) && !Objects.equals(other.winners.get(res.getKey()).name, this.name)) {
                    //System.out.println("Other: " + other.computeBid(app, res.getKey()) + " bid (lost)");
                    //System.out.println("This: " + this.computeBid(app, res.getKey()) + " bid (WON)\n");
                    broadcastNeeded = true;
                }
            }
        }

        //Rebuild after winner check, otherwise automatic winner declaration without data exchange could cause conflicts and possibly more messages could be needed
        if (!lostTasks.isEmpty()) {
            //Rebuild bundle if any tasks were lost
            this.buildBundle(app);
        }

        return broadcastNeeded;
    }

    public void adoptInfo(Resource task, CBBAResourceAgent other) {
        //We lost the task and, we are not the new 'owner'
        if (this.winners.get(task) == this && !Objects.equals(this.name, other.name)) {
            this.lost = true;
        }

        this.bids.put(task, other.bids.get(task));
        this.winners.put(task, other.winners.get(task));
        this.timestamp.put(task, other.timestamp.get(task));
    }

    @Override
    protected void deploy(AgentApplication app, int bcastMessageSize, DecisionMaker decisionMaker) {
        this.bcastMessageSize = bcastMessageSize;
        this.decisionMaker = decisionMaker;

        decisionMaker.CBBASender = this;
        decisionMaker.start(app);
    }

    @Override
    public void processAppOffer(AgentApplication app) {
        if (!app.offers.isEmpty()) {
            this.writeFile(app); // TODO: this takes time..
            app.winningOffer = 0;
            SimLogger.logRun(app.offers.size() + " offer was made for "
                        + app.name + " at: " + Timed.getFireCount() / 1000.0 / 60.
                        + " min., the winning offer index is: 0");
            acknowledgeAndInitSwarmAgent(app, app.offers.get(app.winningOffer), bcastMessageSize);
        } else {
            new DeferredEvent(1000 * 10) {
                @Override
                protected void eventAction() {
                    if (reBroadcastCounter < AgentApplicationReader.appCount * 2) {
                        broadcast(app, bcastMessageSize, decisionMaker);
                        // TODO: this var is handled at RA level, not at app level (what if RA has more than one app)
                        reBroadcastCounter++;
                        SimLogger.logRun("Rebroadcast " + reBroadcastCounter + " for " + app.name);
                    }
                }
            };

            acknowledgeAndInitSwarmAgent(app, new Offer(new HashMap<>(), -1), bcastMessageSize);
            app.deploymentTime = -1;
        }
    }

    @Override
    protected void releaseResourcesAndNotifyNoOffers(AgentApplication app) {
        SimLogger.logRun(app.name + "'s requirements cannot be fulfilled!");

        for (CBBAResourceAgent agent : CBBAResourceAgent.CBBAResourceAgents) {
            for (Capacity capacity : agent.capacities) {
                freeReservedResources(app.name, capacity);
            }
        }
    }

    @Override
    protected void acknowledgeAndInitSwarmAgent(AgentApplication app, Offer offer, int bcastMessageSize) {
        MessageHandler.executeMessaging(this, app, bcastMessageSize, "ack", () -> {
            SimLogger.logRun("All acknowledge messages received for " + app.name
                    + " at: " + Timed.getFireCount() / 1000.0 / 60.0 + " min.");

            if (offer.id == -1) {
                releaseResourcesAndNotifyNoOffers(app);
                return;
            }
            
            for (CBBAResourceAgent agent : CBBAResourceAgent.CBBAResourceAgents) {
                for (Capacity capacity : agent.capacities) {
                    if (offer.agentResourcesMap.containsKey(agent)) {
                        capacity.assignCapacity(offer.agentResourcesMap.get(agent), offer);
                    }

                    freeReservedResources(app.name, capacity);
                }
            }
            SimLogger.logRun("Winning offer for " + app.name + " was " + offer);
            Pair<ComputingAppliance, Utilisation> leadResource = findLeadResource(offer.utilisations);
            new Deployment(leadResource, offer, app);
        });
    }
}