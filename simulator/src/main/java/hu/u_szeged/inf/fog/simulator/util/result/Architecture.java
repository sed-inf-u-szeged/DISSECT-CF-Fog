package hu.u_szeged.inf.fog.simulator.util.result;

/**
 * Helper class for saving the results of a simulation to the database used 
 * by DISSECT-CF-Fog-WebApp and the executor module.
 */
@SuppressWarnings("unused")
public class Architecture {
    
    private int usedVirtualMachines;
    private int tasks;
    private double totalEnergyConsumptionOfNodesInWatt;
    private double totalEnergyConsumptionOfDevicesInWatt;
    private double sumOfMillisecondsOnNetwork;
    private double sumOfByteOnNetwork;
    private long timeout;
    private long simulationLength;
    
    /**
     * Constructs an object with the specified architectural metrics.
     *
     * @param usedVirtualMachines            the number of used virtual machines
     * @param tasks                          the number of tasks
     * @param totalEnergyConsumptionOfNodesInWatt  the total energy consumption of nodes in watts
     * @param totalEnergyConsumptionOfDevicesInWatt the total energy consumption of devices in watts
     * @param sumOfMillisecondsOnNetwork     the sum of milliseconds spent on the network
     * @param sumOfByteOnNetwork             the sum of bytes transferred on the network
     * @param timeout                        the timeout period
     * @param simulationLength               the length of the simulation
     */
    public Architecture(int usedVirtualMachines, int tasks, double totalEnergyConsumptionOfNodesInWatt,
            double totalEnergyConsumptionOfDevicesInWatt, double sumOfMillisecondsOnNetwork,
            double sumOfByteOnNetwork, long timeout, long simulationLength) {
        this.usedVirtualMachines = usedVirtualMachines;
        this.tasks = tasks;
        this.totalEnergyConsumptionOfNodesInWatt = totalEnergyConsumptionOfNodesInWatt;
        this.totalEnergyConsumptionOfDevicesInWatt = totalEnergyConsumptionOfDevicesInWatt;
        this.sumOfMillisecondsOnNetwork = sumOfMillisecondsOnNetwork;
        this.sumOfByteOnNetwork = sumOfByteOnNetwork;
        this.timeout = timeout;
        this.simulationLength = simulationLength;
    }
}
