package hu.u_szeged.inf.fog.simulator.agent;

import hu.mta.sztaki.lpds.cloud.simulator.DeferredEvent;
import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.util.SeedSyncer;
import hu.u_szeged.inf.fog.simulator.agent.decision.DecisionMaker;
import hu.u_szeged.inf.fog.simulator.agent.demo.Config;
import hu.u_szeged.inf.fog.simulator.agent.strategy.mapping.MappingStrategy;
import hu.u_szeged.inf.fog.simulator.agent.strategy.message.MessagingStrategy;
import hu.u_szeged.inf.fog.simulator.common.node.ComputingAppliance;
import hu.u_szeged.inf.fog.simulator.common.util.ScenarioBase;
import hu.u_szeged.inf.fog.simulator.common.util.SimLogger;
import org.apache.commons.lang3.SystemUtils;
import org.apache.commons.lang3.tuple.Pair;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;

public class StandardResourceAgent extends ResourceAgent {
    //public int servedAsGatewayCount = 0;
    //public int winningOfferSelectionCount = 0;
    //public Map<ResourceAgent, Double> staticScores = new HashMap<>();
    //public Map<ResourceAgent, Double> reputationScores = new HashMap<>();

    public static List<StandardResourceAgent> standardResourceAgents = new ArrayList<>();

    public StandardResourceAgent(String name, double hourlyPrice, MappingStrategy agentStrategy, MessagingStrategy messagingStrategy) {
        super(name, hourlyPrice, agentStrategy, messagingStrategy);
        standardResourceAgents.add(this);
    }

    public static void minimumsMaximums() {
        for (ResourceAgent agent : standardResourceAgents) {
            setMinimumsMaximums(agent);
        }
    }

    @Override
    protected void releaseResourcesDueToNoOffers(AgentApplication app) {
        for (ResourceAgent agent : ResourceAgent.allResourceAgents.values()) {
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
            for (ResourceAgent agent : ResourceAgent.allResourceAgents.values()) {
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
            if (Config.APP_TYPE.get("rankingMethod").equals("random")) {
                app.winningOffer = SeedSyncer.centralRnd.nextInt(app.offers.size());
            } else {
                app.winningOffer = callRankingScript(app);
            }

            SimLogger.logRun(app.offers.size() + " offers were made for "
                    + app.name + " at: " + Timed.getFireCount() / 1000.0 / 60.
                    + " min., the winning offer index is: " + app.winningOffer);

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

    private int callRankingScript(AgentApplication app) {
        String inputfile = this.writeFile(app);

        try {
            String command;
            ProcessBuilder processBuilder;

            if (SystemUtils.IS_OS_LINUX) {
                command = "python3 " + Config.APP_TYPE.get("rankingScript")
                        + " --method_name " + Config.APP_TYPE.get("rankingMethod")
                        + " --offers_loc \"" + inputfile + "\"";

                processBuilder = new ProcessBuilder("bash", "-c", command);
            } else {
                SimLogger.logError("The ranking script cannot be called due to an unsupported operating system.");
                throw new UnsupportedOperationException();
                /*
                command = "cd /d \"" + AgentNoiseSimDemo.RANKING_SCRIPT + "\""
                        + " && conda activate swarmchestrate && python call_ranking_func.py --method_name "
                        + AgentNoiseSimDemo.RANKING_METHOD
                        + " --offers_loc \"" + inputfile + "\"";
                 */
            }

            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();
            process.waitFor();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                StringBuilder arrayContent = new StringBuilder();

                while ((line = reader.readLine()) != null) {
                    // System.out.println(line);
                    arrayContent.append(line).append(" ");
                }

                String content = arrayContent.toString();

                content = content.replaceAll("[^0-9\\s]", "");

                List<Integer> numberList = Arrays.stream(content.split("\\s+"))
                        .filter(token -> !token.isEmpty())
                        .map(Integer::parseInt)
                        .toList();

                int firstNumber = numberList.get(0);
                //int lastNumber = numberList.get(numberList.size() - 1);

                SimLogger.logRun(app.offers.size() + " offers were ranked for "
                        + app.name + " at: " + Timed.getFireCount() / (double) ScenarioBase.MINUTE_IN_MILLISECONDS
                        + " min., the winning offer index is: " + firstNumber);

                return firstNumber;
                //return lastNumber;
            }
        } catch (IOException | InterruptedException e) {
            e.getStackTrace();
        }

        return -1;
    }
}
