package hu.u_szeged.inf.fog.simulator.util.result;

public class SimulatorJobResult {

    private ActuatorEvents actuatorEvents;
    private Architecture architecture;
    private Cost cost;
    private DataVolume dataVolume;
    private long runtime;
    
    public SimulatorJobResult(ActuatorEvents actuatorEvents, Architecture architecture, Cost cost, DataVolume dataVolume, long runtime) {
        this.actuatorEvents = actuatorEvents;
        this.architecture = architecture;
        this.cost = cost;
        this.dataVolume = dataVolume;
        this.runtime = runtime;
    }

}
