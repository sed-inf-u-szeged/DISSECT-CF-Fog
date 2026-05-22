package hu.u_szeged.inf.fog.simulator.rl;

/**
 * Initialize => State doesn't group the input, what we pass in is what it stores, used to initialize the table
 * Normal => State groups the input, used in the MABLearningDecisionMaker
 */
public enum StateType {
    INITIALIZE,
    NORMAL
}
