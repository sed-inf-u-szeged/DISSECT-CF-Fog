package hu.u_szeged.inf.fog.simulator.result;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ActuatorEvents {
    private long changeNode;
    private long changePosition;
    private long connectToNode;
    private long disconnectFromNode;
}
