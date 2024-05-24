package hu.u_szeged.inf.fog.simulator.util.result;

@SuppressWarnings("unused")
public class ActuatorEvents {
    private long changeNode;
    private long changePosition;
    private long connectToNode;
    private long disconnectFromNode;
    
    public ActuatorEvents(long changeNode, long changePosition, long connectToNode, long disconnectFromNode) {
        this.changeNode = changeNode;
        this.changePosition = changePosition;
        this.connectToNode = connectToNode;
        this.disconnectFromNode = disconnectFromNode;
    }
}
