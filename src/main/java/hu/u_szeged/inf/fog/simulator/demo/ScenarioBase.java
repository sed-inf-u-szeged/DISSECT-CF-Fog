package hu.u_szeged.inf.fog.simulator.demo;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;
import hu.u_szeged.inf.fog.simulator.application.AppVm;
import hu.u_szeged.inf.fog.simulator.application.Application;
import hu.u_szeged.inf.fog.simulator.executor.model.result.ActuatorEvents;
import hu.u_szeged.inf.fog.simulator.executor.model.result.Architecture;
import hu.u_szeged.inf.fog.simulator.executor.model.result.Cost;
import hu.u_szeged.inf.fog.simulator.executor.model.result.DataVolume;
import hu.u_szeged.inf.fog.simulator.executor.model.result.SimulatorJobResult;
import hu.u_szeged.inf.fog.simulator.iot.Device;
import hu.u_szeged.inf.fog.simulator.iot.SmartDevice;
import hu.u_szeged.inf.fog.simulator.iot.mobility.MobilityEvent;
import hu.u_szeged.inf.fog.simulator.physical.ComputingAppliance;
import hu.u_szeged.inf.fog.simulator.prediction.FeatureManager;
import hu.u_szeged.inf.fog.simulator.provider.AWSProvider;
import hu.u_szeged.inf.fog.simulator.provider.AzureProvider;
import hu.u_szeged.inf.fog.simulator.provider.IBMProvider;
import hu.u_szeged.inf.fog.simulator.provider.Instance;
import hu.u_szeged.inf.fog.simulator.provider.Provider;
import hu.u_szeged.inf.fog.simulator.util.SimLogger;
import hu.u_szeged.inf.fog.simulator.workflow.WorkflowExecutor;
import hu.u_szeged.inf.fog.simulator.workflow.WorkflowJob;
import hu.u_szeged.inf.fog.simulator.workflow.scheduler.WorkflowScheduler;
import java.io.File;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;

public class ScenarioBase {
    
    public static final String resourcePath = new StringBuilder(System.getProperty("user.dir"))
            .append(File.separator).append("src").append(File.separator).append("main").append(File.separator)
            .append("resources").append(File.separator).append("demo").append(File.separator).toString();

    public static final String scriptPath = new StringBuilder(System.getProperty("user.dir")).append(File.separator)
            .append("src").append(File.separator).append("main").append(File.separator).append("resources")
            .append(File.separator).append("script").append(File.separator).toString();

    public static String resultDirectory;

    static {
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss");
        Date date = new Date(System.currentTimeMillis());
        String path = new StringBuilder(System.getProperty("user.dir")).append(File.separator)
                .append("sim_res/" + formatter.format(date)).toString();
        resultDirectory = path;
        new File(resultDirectory).mkdirs();
    }

    public static SimulatorJobResult logBatchProcessing(long runtime) {
        long totalGeneratedData = 0;
        long totalLocallyProcessedData = 0;
        long totalReceivedData = 0;
        long totalProcessedData = 0;
        long totalMessageCount = 0;
        int numberOfVms = 0;
        int numberOfTasks = 0;
        double totalCost = 0.0;
        double totalEnergyConsumption = 0.0;
        double totalDeviceEnergyConsumption = 0.0;

        SimLogger.logRes("\n~~Information about the simulation:~~\n");

        for (ComputingAppliance ca : ComputingAppliance.getAllComputingAppliances()) {
            SimLogger.logRes("Computing Appliance: " + ca.name);
            SimLogger.logRes("\tEnergy consumption (kWh): " + ca.energyConsumption / 1000 / 3_600_000);
            SimLogger.logRes("\tGateway: " + ca.gateway);
            totalEnergyConsumption += ca.energyConsumption;

            for (PhysicalMachine pm : ca.iaas.machines) {
                if (pm.localDisk.getMaxStorageCapacity() - pm.localDisk.getFreeStorageCapacity() != 0) {
                    SimLogger.logRes("\t\t" + pm);
                }

            }
            SimLogger.logRes("\t\t" + ca.iaas.repositories.get(0));
            SimLogger.logRes("\t\t" + ca.iaas.repositories.get(0).contents());
            for (Application application : ca.applications) {
                long applicationRuntime = 0;

                SimLogger.logRes("\tApplication: " + application.name);
                SimLogger.logRes("\t\tTotal received / processed data: " + application.receivedData + " / "
                        + application.processedData);
                SimLogger.logRes("\t\tApp Vms: " + application.utilisedVms.size());
                for (AppVm appVm : application.utilisedVms) {
                    SimLogger.logRes("\t\t\t" + appVm);
                    numberOfTasks += appVm.taskCounter;
                    applicationRuntime += appVm.workTime;
                }
                SimLogger.logRes(
                        "\t\tApplication cost: " + application.instance.calculateCloudCost(applicationRuntime));
                SimLogger.logRes("\t\tNumber of connected devices: " + application.deviceList.size());
                totalReceivedData += application.receivedData;
                totalProcessedData += application.processedData;
                numberOfVms += application.utilisedVms.size();
                totalCost += application.instance.calculateCloudCost(applicationRuntime);

            }
        }

        SimLogger.logRes("Number of devices: " + Device.allDevices.size());
        for (Device device : Device.allDevices) {
            totalGeneratedData += device.generatedData;
            totalLocallyProcessedData += device.locallyProcessedData;
            totalMessageCount += device.messageCount;
            totalDeviceEnergyConsumption += device.energyConsumption;
        }
        // SimLogger.logInfo("Movement pred. success/all: " + (double)
        // EdgeDevice.success/EdgeDevice.all + " (" + EdgeDevice.success + "/" +
        // EdgeDevice.all + ")" );
        // SimLogger.logInfo(EdgeDevice.vmReq + " " + EdgeDevice.vmStart + " " +
        // EdgeDevice.vmShutdown);

        // TODO: in case of EdgeDevice, timeout can be negative, it must be fixed and
        // calculated correctly!
        SimLogger.logRes("Timeout (minutes): "
                + TimeUnit.MINUTES.convert(Application.lastAction - Device.lastAction, TimeUnit.MILLISECONDS));
        SimLogger.logRes("Simulation length (minutes): "
                + TimeUnit.MINUTES.convert(Timed.getFireCount(), TimeUnit.MILLISECONDS));
        SimLogger.logRes("Runtime (seconds): " + TimeUnit.SECONDS.convert(runtime, TimeUnit.NANOSECONDS));
        SimLogger.logRes("Total energy consumption of the nodes (kWh): " + totalEnergyConsumption / 1000 / 3_600_000);
        SimLogger.logRes(
                "Total energy consumption of the devices (kWh): " + totalDeviceEnergyConsumption / 1000 / 3_600_000);
        SimLogger.logRes("Total cost ($): " + totalCost);
        SimLogger.logRes("IoT costs ($): ");
        for (int i = 0; i < Provider.providers.size(); i++) {
            SimLogger.logRes("\t" + Provider.providers.get(i).name + ": "
                    + BigDecimal.valueOf(Provider.providers.get(i).calculate()).toPlainString() + " ");
        }
        SimLogger.logRes("Total amount of generated / received / processed / locally processed / stuck data (bytes): "
                + totalGeneratedData + " / " + totalReceivedData + " / " + totalProcessedData + " / "
                + totalLocallyProcessedData + " / " + SmartDevice.stuckData + " ~" + totalGeneratedData / 1024 / 1024 + " MB");
        SimLogger.logRes("Total message count (pc.): " + totalMessageCount);
        SimLogger.logRes("Total time on network (seconds): "
                + TimeUnit.SECONDS.convert(Application.totalTimeOnNetwork, TimeUnit.MILLISECONDS));
        SimLogger.logRes("Total bytes on network (MB): " + Application.totalBytesOnNetwork / 1024 / 1024);
        SimLogger.logRes("Number of VMs (pc.): " + numberOfVms + " Number of tasks (pc.): " + numberOfTasks);
        SimLogger.logRes("Number of events (pc.)" + "\n\tChange position: " + MobilityEvent.changePositionEventCounter
                + "\n\tChange node: " + MobilityEvent.changeNodeEventCounter + "\n\tConnect to node: "
                + MobilityEvent.connectToNodeEventCounter + "\n\tDisconnect from node: "
                + MobilityEvent.disconnectFromNodeEventCounter);
        
        SimLogger.logRes("Total number of predictions: " + FeatureManager.getInstance().getTotalNumOfPredictions());

        final var actuatorEvents = ActuatorEvents.builder().changePosition(MobilityEvent.changePositionEventCounter)
                .changeNode(MobilityEvent.changeNodeEventCounter).connectToNode(MobilityEvent.connectToNodeEventCounter)
                .disconnectFromNode(MobilityEvent.disconnectFromNodeEventCounter).build();

        final var architecture = Architecture.builder().tasks(numberOfTasks).usedVirtualMachines(numberOfVms)
                .totalEnergyConsumptionOfNodesInWatt(totalEnergyConsumption / 1000 / 3_600_000)
                .totalEnergyConsumptionOfDevicesInWatt(totalDeviceEnergyConsumption / 1000 / 3_600_000)
                .sumOfMillisecondsOnNetwork(
                        TimeUnit.SECONDS.convert(Application.totalTimeOnNetwork, TimeUnit.MILLISECONDS))
                .sumOfByteOnNetwork(Application.totalBytesOnNetwork)
                .timeout(TimeUnit.MINUTES.convert(Application.lastAction - Device.lastAction, TimeUnit.MILLISECONDS))
                .simulationLength(TimeUnit.MINUTES.convert(Timed.getFireCount(), TimeUnit.MILLISECONDS)).build();

        final var cost = Cost.builder().totalCostInEuro(totalCost)
                .IBM(BigDecimal.valueOf(Provider.providers.get(0).calculate()).toPlainString())
                .AWS(BigDecimal.valueOf(Provider.providers.get(1).calculate()).toPlainString())
                .azure(BigDecimal.valueOf(Provider.providers.get(2).calculate()).toPlainString()).build();

        final var dataVolume = DataVolume.builder().generatedDataInBytes(totalGeneratedData)
                .processedDataInBytes(totalProcessedData).arrivedDataInBytes(totalReceivedData).build();

        return SimulatorJobResult.builder().architecture(architecture).actuatorEvents(actuatorEvents).cost(cost)
                .dataVolume(dataVolume).runtime(TimeUnit.SECONDS.convert(runtime, TimeUnit.NANOSECONDS)).build();
    }

    public static void calculateIoTCost() {
        new IBMProvider();
        new AWSProvider();
        new AzureProvider();
    }

    public static void logStreamProcessing() {
        SimLogger.logRes("\n~~Information about the simulation:~~\n");

        double totalCost = 0.0;
        double totalEnergyConsumption = 0.0;
        for (ComputingAppliance ca : ComputingAppliance.allComputingAppliances) {
            SimLogger.logRes("\t" + ca.name + ":  " + ca.workflowVMs.size() + " utilised VMs (IDs): ");
            for (VirtualMachine vm : ca.workflowVMs) {
                SimLogger.logRes("\t" + vm.hashCode() + " ");
            }
            SimLogger.logRes("\n");
            Instance i = WorkflowScheduler.workflowArchitecture.get(ca);
            SimLogger.logRes(ca.vmTime + " " + i.pricePerTick);
            totalCost += WorkflowScheduler.workflowArchitecture.get(ca).calculateCloudCost(ca.vmTime);
            SimLogger.logRes("Energy Cons. by " + ca.name + " " + ca.energyConsumption);
            totalEnergyConsumption += ca.energyConsumption;
        }

        for (Entry<Integer, Integer> entry : WorkflowExecutor.vmTaskLogger.entrySet()) {
            SimLogger.logRes(entry.getKey() + ": " + entry.getValue() + " jobs");
        }
        for (Entry<WorkflowJob, Integer> entry : WorkflowExecutor.jobReassigns.entrySet()) {
            SimLogger.logRes(entry.getKey().id + ": " + entry.getValue() + " re-assigning");
        }
        for (Entry<WorkflowJob, Integer> entry : WorkflowExecutor.actuatorReassigns.entrySet()) {
            SimLogger.logRes(entry.getKey().id + ": " + entry.getValue() + " actuator re-assigning");
        }

        int nonCompleted = 0;
        for (WorkflowJob wj : WorkflowJob.workflowJobs) {
            if (wj.state.equals(WorkflowJob.State.COMPLETED)) {
                nonCompleted++;
            }
        }
        SimLogger.logRes("Completed: " + nonCompleted + "/" + WorkflowJob.workflowJobs.size());
        SimLogger.logRes("Total cost: " + totalCost);
        SimLogger.logRes("Total energy consumption: " + totalEnergyConsumption);
        SimLogger.logRes("Execution time: " + Timed.getFireCount() + " Real execution time" + " "
                + (Timed.getFireCount() - WorkflowExecutor.realStartTime) + "ms (~"
                + (Timed.getFireCount() - WorkflowExecutor.realStartTime) / 1000 / 60 + " minutes)");

    }

}