package hu.u_szeged.inf.fog.simulator.result;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class SimulatorJobResult {

    private ActuatorEvents actuatorEvents;
    private Architecture architecture;
    private Cost cost;
    private DataVolume dataVolume;
    private long runtime;
}
