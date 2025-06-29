package hu.u_szeged.inf.fog.simulator.util.result;

/**
 * Helper class for saving the results of a simulation to the database used 
 * by DISSECT-CF-Fog-WebApp and the executor module.
 */
@SuppressWarnings("unused")
public class ActuatorEvents {
   
    private long changeNode;
    private long changePosition;
    private long connectToNode;
    private long disconnectFromNode;
    
    /**
     * Constructs an object with the specified event counts.
     *
     * @param changeNode         the count of change node events
     * @param changePosition     the count of change position events
     * @param connectToNode      the count of connect to node events
     * @param disconnectFromNode the count of disconnect from node events
     */
    public ActuatorEvents(long changeNode, long changePosition, long connectToNode, long disconnectFromNode) {
        this.changeNode = changeNode;
        this.changePosition = changePosition;
        this.connectToNode = connectToNode;
        this.disconnectFromNode = disconnectFromNode;
    }
}