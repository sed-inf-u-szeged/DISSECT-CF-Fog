package hu.u_szeged.inf.fog.simulator.rl;

import hu.mta.sztaki.lpds.cloud.simulator.util.SeedSyncer;
import hu.u_szeged.inf.fog.simulator.agent.decision.AsyncCBBABasedDecisionMaker;
import hu.u_szeged.inf.fog.simulator.agent.decision.CBBABasedDecisionMaker;
import hu.u_szeged.inf.fog.simulator.agent.decision.DecisionMaker;
import hu.u_szeged.inf.fog.simulator.common.util.SimLogger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class MABTable {
    //Reward table
    Map<State, Map<Class<? extends DecisionMaker>, Double>> table = new HashMap<>();

    //'Pulls' table (for uniform sampling),
    // If table is passed in, this doesn't need to be, as past sampling rate is (near) uniform
    Map<State, Map<Class<? extends DecisionMaker>, Integer>> pulls = new HashMap<>();

    //Initialize empty QTable
    public MABTable() {
        this.generateEmptyTables();
    }

    private void generateEmptyTables() {
        this.pulls.clear();
        this.table.clear();

        for (int tasks = 0; tasks < State.taskGroups; tasks++) {
            State state = new State(StateType.INITIALIZE, tasks);

            Map<Class<? extends DecisionMaker>, Double> actionMap = new HashMap<>();
            Map<Class<? extends DecisionMaker>, Integer> pullsMap = new HashMap<>();

            actionMap.put(CBBABasedDecisionMaker.class, 0.0);
            actionMap.put(AsyncCBBABasedDecisionMaker.class, 0.0);
            pullsMap.put(CBBABasedDecisionMaker.class, 0);
            pullsMap.put(AsyncCBBABasedDecisionMaker.class, 0);

            table.put(state, actionMap);
            pulls.put(state, pullsMap);
        }
    }

    //Get the least sampled algorithm for a specific state (context)
    //Call like this: DecisionMaker dm = getBest(state).getDeclaredConstructor().newInstance();
    public Class<? extends DecisionMaker> getBest(State state) {
        //Handling the tables correctly is the responsibility of the implementation.
        // If tables were passed in, and one/both tables was/were empty, generate all
        if (pulls.isEmpty() || table.isEmpty()) {
            SimLogger.logError("One or both tables were not generated nor passed in! Generating empty tables for both.");
            this.generateEmptyTables();
        }

        Map<Class<? extends DecisionMaker>, Integer> pullsMap = pulls.get(state);

        //Selects the pull count of the algorithm(s) with the least amount of 'pulls'
        int minPulls = pullsMap.values()
                .stream()
                .min(Integer::compare)
                .orElseThrow(IllegalStateException::new);

        //Selects the algorithm (or random if equal) of the minimum pull count
        List<Class<? extends DecisionMaker>> candidates = pullsMap.entrySet()
                .stream()
                .filter(x -> x.getValue() == minPulls)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        Class<? extends DecisionMaker> selected = candidates.get(
                SeedSyncer.centralRnd.nextInt(candidates.size())
        );

        //Update pull count +1
        pullsMap.merge(selected, 1, Integer::sum);

        return selected;
    }

    /**
     * Update a score based on a reward, use this as part of the scoring system
     *
     * @param state The category to set (like CPU count category)
     * @param decisionMaker The sub-category to set (the decision makers)
     * @param reward The reward, this will be used to calculate the updated score
     */
    public void updateScore(State state, Class<? extends DecisionMaker> decisionMaker, Double reward) {
        Map<Class<? extends DecisionMaker>, Double> actionMap = table.get(state);
        Map<Class<? extends DecisionMaker>, Integer> pullMap = pulls.get(state);

        double previousScore = actionMap.get(decisionMaker);
        Integer pullCount = pullMap.get(decisionMaker);

        if (pullCount == null || pullCount == 0) {
            throw new RuntimeException("The sample count for the state was 0! If the table was passed in, check if it was set up correctly!");
        }

        double updatedScore = previousScore + (reward - previousScore) / pullCount;

        actionMap.put(decisionMaker, updatedScore);
    }

    /**
     * Set a category to a specific score, only use this when defining an initial table
     *
     * @param state The category to set (like CPU count category)
     * @param decisionMaker The sub-category to set (the decision makers)
     * @param score What score to set it to
     */
    public void setInitialScore(State state, Class<? extends DecisionMaker> decisionMaker, Double score) {
        Map<Class<? extends DecisionMaker>, Double> actionMap = table.get(state);

        actionMap.put(decisionMaker, score);
    }

    /**
     * Set a category to a specific pull count, only use this when defining an initial QTable
     *
     * @param state The category to set (context, like CPU count category)
     * @param decisionMaker The sub-category to set (the decision makers)
     * @param count What sample count to set it to
     */
    public void setInitialPulls(State state, Class<? extends DecisionMaker> decisionMaker, Integer count) {
        Map<Class<? extends DecisionMaker>, Integer> pullsMap = pulls.get(state);

        pullsMap.put(decisionMaker, count);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        sb.append("Score Table:\n");

        for (Map.Entry<State, Map<Class<? extends DecisionMaker>, Double>> entry : table.entrySet()) {
            sb.append(entry.getKey().toString()).append(" -> ");

            for (Map.Entry<Class<? extends DecisionMaker>, Double> action : entry.getValue().entrySet()) {
                sb.append(action.getKey().getSimpleName())
                        .append(": ")
                        .append(action.getValue())
                        .append(" ");
            }

            sb.append("\n");
        }

        sb.append("\n");
        sb.append("Sample Count Table:\n");

        for (Map.Entry<State, Map<Class<? extends DecisionMaker>, Integer>> entry : pulls.entrySet()) {
            sb.append(entry.getKey().toString()).append(" -> ");

            for (Map.Entry<Class<? extends DecisionMaker>, Integer> sample : entry.getValue().entrySet()) {
                sb.append(sample.getKey().getSimpleName())
                        .append(": ")
                        .append(sample.getValue())
                        .append(" ");
            }

            sb.append("\n");
        }

        sb.append("\n");

        return sb.toString();
    }
}
