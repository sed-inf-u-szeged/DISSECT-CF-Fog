package hu.u_szeged.inf.fog.simulator.agent;

import hu.mta.sztaki.lpds.cloud.simulator.DeferredEvent;
import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.AlterableResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.io.VirtualAppliance;
import hu.u_szeged.inf.fog.simulator.agent.Capacity.Utilisation;
import hu.u_szeged.inf.fog.simulator.agent.decision.DecisionMaker;
import hu.u_szeged.inf.fog.simulator.agent.strategy.AgentStrategy;
import hu.u_szeged.inf.fog.simulator.demo.ScenarioBase;
import hu.u_szeged.inf.fog.simulator.node.ComputingAppliance;
import hu.u_szeged.inf.fog.simulator.util.SimLogger;
import hu.u_szeged.inf.fog.simulator.util.agent.AgentApplicationReader;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.lang3.SystemUtils;
import org.apache.commons.lang3.tuple.Pair;

public class StandardResourceAgent extends ResourceAgent {
    public static String rankingMethodName;

    public static String rankingScriptDir;

    public static List<StandardResourceAgent> standardResourceAgents = new ArrayList<>();

    int callcounter;

    public static void minimumsMaximums() {
        for (ResourceAgent agent : standardResourceAgents) {
            setMinimumsMaximums(agent);
        }
    }

    public StandardResourceAgent(String name, double hourlyPrice, VirtualAppliance resourceAgentVa, AlterableResourceConstraints resourceAgentArc, AgentStrategy agentStrategy) {
        super(name, hourlyPrice, resourceAgentVa, resourceAgentArc, agentStrategy);
        standardResourceAgents.add(this);
    }

    public StandardResourceAgent(String name, double hourlyPrice, VirtualAppliance resourceAgentVa, AlterableResourceConstraints resourceAgentArc, AgentStrategy agentStrategy, Capacity... capacities) {
        super(name, hourlyPrice, resourceAgentVa, resourceAgentArc, agentStrategy, capacities);
        standardResourceAgents.add(this);
    }

    @Override
    protected void deploy(AgentApplication app, int bcastMessageSize, DecisionMaker decisionMaker) {
        this.decisionMaker = decisionMaker;
        this.bcastMessageSize = bcastMessageSize;

        decisionMaker.standardSender = this;
        decisionMaker.start(app);
    }

    @Override
    public void processAppOffer(AgentApplication app) {
        if (!app.offers.isEmpty()) {
            this.writeFile(app); // TODO: this takes time..
            app.winningOffer = callRankingScript(app);
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

        for (ResourceAgent agent : ResourceAgent.resourceAgents) {
            for (Capacity capacity : agent.capacities) {
                freeReservedResources(app.name, capacity);
            }
        }
    }

    private int callRankingScript(AgentApplication app) {
        String inputfile = ScenarioBase.resultDirectory + File.separator + app.name + "-offers.json";
        try {
            String command;
            ProcessBuilder processBuilder;

            // TODO: revise these commands
            if (SystemUtils.IS_OS_WINDOWS) {
                command = "cd /d " + rankingScriptDir
                        + " && conda activate swarmchestrate && python call_ranking_func.py --method_name " + rankingMethodName
                        + " --offers_loc " + inputfile;
                processBuilder = new ProcessBuilder("cmd.exe", "/c", command);
            } else if (SystemUtils.IS_OS_LINUX) {
                command = "cd " + rankingScriptDir
                        + " && python3 call_ranking_func.py --method_name " + rankingMethodName
                        + " --offers_loc " + inputfile;

                processBuilder = new ProcessBuilder("bash", "-c", command);
            } else {
                throw new UnsupportedOperationException("Unsupported operating system");
            }

            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();
            process.waitFor();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                StringBuilder arrayContent = new StringBuilder();

                while ((line = reader.readLine()) != null) {
                    //System.out.println(line);
                    arrayContent.append(line).append(" ");
                }

                String content = arrayContent.toString();

                content = content.replaceAll("[^0-9\\s]", "");

                List<Integer> numberList = Arrays.stream(content.split("\\s+"))
                        .filter(token -> !token.isEmpty())
                        .map(Integer::parseInt)
                        .collect(Collectors.toList());

                int firstNumber = numberList.get(0);
                //int lastNumber = numberList.get(numberList.size() - 1);

                SimLogger.logRun(app.offers.size() + " offers were ranked for "
                        + app.name + " at: " + Timed.getFireCount() / 1000.0 / 60.
                        + " min., the winning offer index is: " + firstNumber);

                return firstNumber;
                //return lastNumber;
            }
        } catch (IOException | InterruptedException e) {
            e.getStackTrace();
        }

        return -1;
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
            
            for (ResourceAgent agent : ResourceAgent.resourceAgents) {
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