package hu.u_szeged.inf.fog.simulator.agent.decision;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.u_szeged.inf.fog.simulator.agent.AgentApplication;
import hu.u_szeged.inf.fog.simulator.util.SimLogger;
import hu.u_szeged.inf.fog.simulator.rl.MABTable;
import hu.u_szeged.inf.fog.simulator.rl.State;
import hu.u_szeged.inf.fog.simulator.rl.StateType;

import java.lang.reflect.InvocationTargetException;

public class MABLearningDecisionMaker extends DecisionMaker {
    Class<? extends DecisionMaker> chosen = null;
    DecisionMaker chosenInitialized = null;
    State state = null;

    // Passed in table will have its values updated, since it is reference based behind the scenes
    MABTable passedMabTable = null;

    public MABLearningDecisionMaker(MABTable initializedTable) {
        this.passedMabTable = initializedTable;
    }

    public MABLearningDecisionMaker() {}

    @Override
    public void start(AgentApplication app) {
        app.normalizePriorities();

        app.reinforcementLearning = true;
        app.mabLearningDecisionMaker = this;

        SimLogger.logRun("Sender (Reinforcement Learning) : " + CBBASender.name);

        if (passedMabTable != null) {
            this.CBBASender.mabTable = passedMabTable;

            SimLogger.logRun("Tables were passed in, sender CBBA's tables were set to them.");
        }

        this.generateOffers(app);
    }

    @Override
    protected void generateOffers(AgentApplication app) {
        //This should group this task into the state that it belongs to
        state = new State(StateType.NORMAL, app.components.size());

        if (CBBASender.mabTable == null) {
            SimLogger.logRun("First call for agent, generating new table");
            CBBASender.mabTable = new MABTable();
        }

        //Pick the least sampled algorithm (the least pull count, random on match)
        chosen = CBBASender.mabTable.getBest(state);

        SimLogger.logRun(chosen.getSimpleName() + " was chosen");

        try {
            chosenInitialized = chosen.getDeclaredConstructor().newInstance();
        } catch (InstantiationException | IllegalAccessException | NoSuchMethodException |
                 InvocationTargetException e) {
            throw new RuntimeException(e);
        }

        chosenInitialized.CBBASender = this.CBBASender;
        chosenInitialized.start(app);
    }

    public void scoreDeployment(AgentApplication app) {
        //Metrics
        long deploymentTime = app.deploymentAlgorithmFinishedTime - app.deploymentStartedTime;
        long messageCount = app.messageCount;

        SimLogger.logRun("Deployment started time (ms): " + app.deploymentStartedTime);
        SimLogger.logRun("Deployment finished time (ms): " + app.deploymentAlgorithmFinishedTime);
        SimLogger.logRun("Physical deployment finished time (ms): " + app.physicalDeploymentFinishedTime);
        SimLogger.logRun("Deployment took (ms): " + deploymentTime);
        SimLogger.logRun("Used messages: " + messageCount);

        //Normalize metrics
        //can modify;
        double maxExpectedTime = 4_000;
        double maxExpectedMessages = 1_500;

        double normTime = 1 - deploymentTime / maxExpectedTime;
        double normMessages = 1 - messageCount / maxExpectedMessages;

        //Reward
        //can modify weights (should add up to 1.0);
        double reward = 0.5 * normTime + 0.5 * normMessages;
        reward = Math.max(-1.0, Math.min(1.0, reward)); //Clamp between -1 and +1

        //Update the score
        CBBASender.mabTable.updateScore(state, chosen, reward);

        SimLogger.logRun(CBBASender.mabTable.toString());
    }
}
