package hu.u_szeged.inf.fog.simulator.util.result;

/**
 * Helper class for saving the results of a simulation to the database used 
 * by DISSECT-CF-Fog-WebApp and the executor module.
 */
@SuppressWarnings("unused")
public class SimulatorJobResult {

    private ActuatorEvents actuatorEvents;
    private Architecture architecture;
    private Cost cost;
    private DataVolume dataVolume;
    private long runtime;
    
    /**
     * Constructs an object with the specified metrics and data.
     *
     * @param actuatorEvents the events related to actuator actions
     * @param architecture   the architectural characteristics and metrics of the system
     * @param cost           the cost breakdown among different cloud service providers
     * @param dataVolume     the volume of data at different stages of processing
     * @param runtime        the runtime of the simulation job
     */
    public SimulatorJobResult(ActuatorEvents actuatorEvents, Architecture architecture, 
            Cost cost, DataVolume dataVolume, long runtime) {
        this.actuatorEvents = actuatorEvents;
        this.architecture = architecture;
        this.cost = cost;
        this.dataVolume = dataVolume;
        this.runtime = runtime;
    }

    public long getRuntime() {
        return runtime;
    }
}