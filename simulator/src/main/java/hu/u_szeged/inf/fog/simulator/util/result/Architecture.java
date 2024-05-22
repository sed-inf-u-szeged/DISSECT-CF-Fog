package hu.u_szeged.inf.fog.simulator.util.result;

public class Architecture {
    private int usedVirtualMachines;
    private int tasks;
    private double totalEnergyConsumptionOfNodesInWatt;
    private double totalEnergyConsumptionOfDevicesInWatt;
    private double sumOfMillisecondsOnNetwork;
    private double sumOfByteOnNetwork;
    private long timeout;
    private long simulationLength;
    
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
