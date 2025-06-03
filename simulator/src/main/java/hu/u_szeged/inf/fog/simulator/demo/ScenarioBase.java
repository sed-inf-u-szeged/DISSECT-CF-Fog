package hu.u_szeged.inf.fog.simulator.demo;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.u_szeged.inf.fog.simulator.application.AppVm;
import hu.u_szeged.inf.fog.simulator.application.Application;
import hu.u_szeged.inf.fog.simulator.iot.Device;
import hu.u_szeged.inf.fog.simulator.iot.SmartDevice;
import hu.u_szeged.inf.fog.simulator.iot.mobility.MobilityEvent;
import hu.u_szeged.inf.fog.simulator.node.ComputingAppliance;
import hu.u_szeged.inf.fog.simulator.node.WorkflowComputingAppliance;
import hu.u_szeged.inf.fog.simulator.prediction.FeatureManager;
import hu.u_szeged.inf.fog.simulator.provider.AwsProvider;
import hu.u_szeged.inf.fog.simulator.provider.AzureProvider;
import hu.u_szeged.inf.fog.simulator.provider.IbmProvider;
import hu.u_szeged.inf.fog.simulator.provider.Provider;
import hu.u_szeged.inf.fog.simulator.util.EnergyDataCollector;
import hu.u_szeged.inf.fog.simulator.util.SimLogger;
import hu.u_szeged.inf.fog.simulator.util.result.ActuatorEvents;
import hu.u_szeged.inf.fog.simulator.util.result.Architecture;
import hu.u_szeged.inf.fog.simulator.util.result.Cost;
import hu.u_szeged.inf.fog.simulator.util.result.DataVolume;
import hu.u_szeged.inf.fog.simulator.util.result.SimulatorJobResult;
import hu.u_szeged.inf.fog.simulator.workflow.WorkflowJob;
import hu.u_szeged.inf.fog.simulator.workflow.aco.CentralisedAntOptimiser;
import hu.u_szeged.inf.fog.simulator.workflow.aco.ClusterMessenger;
import hu.u_szeged.inf.fog.simulator.workflow.scheduler.RenewableScheduler;
import hu.u_szeged.inf.fog.simulator.workflow.scheduler.WorkflowScheduler;
import java.io.File;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class ScenarioBase {
        
    public static final String resourcePath = new StringBuilder(System.getProperty("user.dir"))
            .append(File.separator).append("src").append(File.separator).append("main").append(File.separator)
            .append("resources").append(File.separator).append("demo").append(File.separator).toString();

    public static final String scriptPath = new StringBuilder(System.getProperty("user.dir")).append(File.separator)
            .append("src").append(File.separator).append("main").append(File.separator).append("resources")
            .append(File.separator).append("script").append(File.separator).toString();

    public static String resultDirectory;
    
    public static boolean reproducibleRandom;

    static {
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss");
        Date date = new Date(System.currentTimeMillis());
        String path = new StringBuilder(System.getProperty("user.dir")).append(File.separator)
                .append("sim_res").append(File.separator) + formatter.format(date);
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
            SimLogger.logRes("\tEnergy consumption (kWh): " + EnergyDataCollector.getEnergyCollector(ca.iaas).energyConsumption / 1000 / 3_600_000);
            SimLogger.logRes("\tBroker: " + ca.broker);
            totalEnergyConsumption += EnergyDataCollector.getEnergyCollector(ca.iaas).energyConsumption;

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
        for (int i = 0; i < Provider.allProviders.size(); i++) {
            SimLogger.logRes("\t" + Provider.allProviders.get(i).name + ": "
                    + BigDecimal.valueOf(Provider.allProviders.get(i).calculate()).toPlainString() + " ");
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

        
        final var actuatorEvents = new ActuatorEvents(
                MobilityEvent.changeNodeEventCounter,
                MobilityEvent.changePositionEventCounter,
                MobilityEvent.connectToNodeEventCounter,
                MobilityEvent.disconnectFromNodeEventCounter
        );

        final var architecture = new Architecture(
                numberOfVms,
                numberOfTasks,
                totalEnergyConsumption / 1000 / 3_600_000,
                totalDeviceEnergyConsumption / 1000 / 3_600_000,
                TimeUnit.SECONDS.convert(Application.totalTimeOnNetwork, TimeUnit.MILLISECONDS),
                Application.totalBytesOnNetwork,
                TimeUnit.MINUTES.convert(Application.lastAction - Device.lastAction, TimeUnit.MILLISECONDS),
                TimeUnit.MINUTES.convert(Timed.getFireCount(), TimeUnit.MILLISECONDS)
        );

        final var cost = new Cost(
                totalCost,
                BigDecimal.valueOf(Provider.allProviders.get(0).calculate()).toPlainString(),
                BigDecimal.valueOf(Provider.allProviders.get(1).calculate()).toPlainString(),
                BigDecimal.valueOf(Provider.allProviders.get(2).calculate()).toPlainString()
        );

        final var dataVolume = new DataVolume(
                totalGeneratedData,
                totalProcessedData,
                totalReceivedData
        );

        return new SimulatorJobResult(
                actuatorEvents,
                architecture,
                cost,
                dataVolume,
                TimeUnit.SECONDS.convert(runtime, TimeUnit.NANOSECONDS)
        );
    }

    public static void calculateIoTCost() {
        new IbmProvider();
        new AwsProvider();
        new AzureProvider();
    }

    public static void logStreamProcessing() {
        SimLogger.logRes("\nSimulation completed.\n");
        
        double totalCost = 0.0;
        double totalEnergyConsumption = 0.0;
        int totalUtilisedVms = 0;
        double avgExecutionTime = 0.0;
        double avgPairwiseDistance = 0.0;
        double avgBytesOnNetwork = 0.0;
        double avgTimeOnNetwork = 0.0;
        for(WorkflowScheduler scheduler : WorkflowScheduler.schedulers) {
            SimLogger.logRes("App: " + scheduler.appName);
            
            SimLogger.logRes("\tUtilised VMs: " + scheduler.vmTaskLogger.size());
            totalUtilisedVms += scheduler.vmTaskLogger.size();
            for (Map.Entry<String, Integer> entry : scheduler.vmTaskLogger.entrySet()) {
                String key = entry.getKey();
                Integer value = entry.getValue();
                SimLogger.logRes("\t\t" + key + " - " + value + " taks");
            }
            
            long vmTime = 0;
            double cost = 0.0;
            double energyConsumption = 0.0;
            for(WorkflowComputingAppliance ca : scheduler.computeArchitecture) {
                cost += scheduler.instance.calculateCloudCost(ca.vmTime);
                vmTime += ca.vmTime;
                energyConsumption += EnergyDataCollector.getEnergyCollector(ca.iaas).energyConsumption;
            }
            totalCost += cost;
            totalEnergyConsumption += energyConsumption;
            SimLogger.logRes("Cost (EUR): " + cost + " VM time: " + vmTime + " Price: " + scheduler.instance.pricePerTick);
            SimLogger.logRes("Energy consumption (kWh): " + energyConsumption / 1000 / 3_600_000);
            SimLogger.logRes("Time on network (seconds): "
                    + TimeUnit.SECONDS.convert(scheduler.timeOnNetwork, TimeUnit.MILLISECONDS));
            avgTimeOnNetwork += scheduler.timeOnNetwork;
            SimLogger.logRes("Bytes on network (MB): " + scheduler.bytesOnNetwork / 1024 / 1024);
            avgBytesOnNetwork += scheduler.bytesOnNetwork;
            SimLogger.logRes("Pairwise Distance (km): " + CentralisedAntOptimiser.calculateAvgPairwiseDistance(scheduler.computeArchitecture));
            avgPairwiseDistance += CentralisedAntOptimiser.calculateAvgPairwiseDistance(scheduler.computeArchitecture);
            

            int completed = 0;
            for (WorkflowJob wj : scheduler.jobs) {
                if (wj.state.equals(WorkflowJob.State.COMPLETED)) {
                    completed++;
                }
            }
            SimLogger.logRes("Completed: " + completed + "/" + scheduler.jobs.size());
            SimLogger.logRes("Execution time (min.): " 
                    + (scheduler.stopTime - scheduler.startTime) / 1000 / 60);
            avgExecutionTime += (scheduler.stopTime - scheduler.startTime) / 1000 / 60;
                    
            SimLogger.logRes("");
        }
        SimLogger.logRes("Total cost (EUR): " + totalCost);
        SimLogger.logRes("Total energy consumption (kWh): " + totalEnergyConsumption / 1000 / 3_600_000);
        SimLogger.logRes("Total utilised VMs: " + totalUtilisedVms);
        SimLogger.logRes("Avg time on network (seconds): "
                + TimeUnit.SECONDS.convert((long) (avgTimeOnNetwork / WorkflowScheduler.schedulers.size()), TimeUnit.MILLISECONDS));
        SimLogger.logRes("Avg bytes on network (MB): " + (long) avgBytesOnNetwork / WorkflowScheduler.schedulers.size() / 1024 / 1024);
        SimLogger.logRes("Avg execution time (min): " + avgExecutionTime / WorkflowScheduler.schedulers.size());
        SimLogger.logRes("Avg pairwise distance (km): " + avgPairwiseDistance / WorkflowScheduler.schedulers.size());
        SimLogger.logRes("Messages required for clustering: " + ClusterMessenger.clusterMessageCount);

        /*
        for (Entry<WorkflowJob, Integer> entry : WorkflowExecutor.jobReassigns.entrySet()) {
            SimLogger.logRes(entry.getKey().id + ": " + entry.getValue() + " re-assigning");
        }
        for (Entry<WorkflowJob, Integer> entry : WorkflowExecutor.actuatorReassigns.entrySet()) {
            SimLogger.logRes(entry.getKey().id + ": " + entry.getValue() + " actuator re-assigning");
        }    
        */
    }
    public static void logRenewableStreamProcessing() {
        RenewableScheduler scheduler = (RenewableScheduler) WorkflowScheduler.schedulers.get(0);
        float totalFossilUsed = 0;
        float totalRenewableUsed = 0;
        float totalMoneySpent = 0;
        float totalRenewableProduced = 0;
        for (hu.u_szeged.inf.fog.simulator.energyprovider.Provider provider : scheduler.providers) {
            totalRenewableProduced += provider.totalRenewableProduced;
            totalFossilUsed += provider.totalFossilUsed;
            totalRenewableUsed += provider.totalRenewableUsed;
            totalMoneySpent += provider.moneySpentOnFossil/1000;
            totalMoneySpent += provider.moneySpentOnRenewable/1000;
        }
        SimLogger.logRes("");
        SimLogger.logRes("Total renewable produced (Wh): " + totalRenewableProduced);
        SimLogger.logRes("Total fossil consumed (Wh): " + totalFossilUsed);
        SimLogger.logRes("Total renewable consumed (Wh): " + totalRenewableUsed);
        SimLogger.logRes("Total money cost (EUR): " + totalMoneySpent);
        SimLogger.logRes("Total waiting time (min.): " + scheduler.totalWaitingTime/60_000);
    }
}
