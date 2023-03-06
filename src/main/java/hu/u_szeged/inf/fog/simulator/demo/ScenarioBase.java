package hu.u_szeged.inf.fog.simulator.demo;

import java.io.File;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.u_szeged.inf.fog.simulator.application.AppVm;
import hu.u_szeged.inf.fog.simulator.application.Application;
import hu.u_szeged.inf.fog.simulator.iot.Device;
import hu.u_szeged.inf.fog.simulator.iot.mobility.MobilityEvent;
import hu.u_szeged.inf.fog.simulator.physical.ComputingAppliance;
import hu.u_szeged.inf.fog.simulator.provider.AWSProvider;
import hu.u_szeged.inf.fog.simulator.provider.AzureProvider;
import hu.u_szeged.inf.fog.simulator.provider.IBMProvider;
import hu.u_szeged.inf.fog.simulator.provider.Provider;

public class ScenarioBase {

	protected final static String resourcePath = new StringBuilder(System.getProperty("user.dir")).
			append(File.separator).
			append("src").
			append(File.separator).
			append("main").
			append(File.separator).
			append("resources").
			append(File.separator).
			append("demo").
			append(File.separator).
			toString();	
	
	protected final static String scriptPath = new StringBuilder(System.getProperty("user.dir"))
			.append(File.separator)
			.append("src")
			.append(File.separator)
			.append("main")
			.append(File.separator)
			.append("resources")
			.append(File.separator)
			.append("script")
			.append(File.separator)
			.toString();
	
	protected static String resultDirectory; 
			
	static {
		SimpleDateFormat formatter= new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss");
		Date date = new Date(System.currentTimeMillis());
		String path = new StringBuilder(System.getProperty("user.dir"))
				.append(File.separator)
				.append("res_"+formatter.format(date)).toString();
		resultDirectory = path;
		new File(resultDirectory).mkdirs();
	}

	public static void log(long runtime) {
		long totalGeneratedData = 0;
		long totalReceivedData = 0;
		long totalProcessedData = 0;
		long totalMessageCount = 0;
		int numberOfVMs = 0;
		int numberOfTasks = 0;
		double totalCost = 0.0;
		double totalEnergyConsumption = 0.0;
		
		System.out.println("\n~~Information about the simulation:~~\n");
		
		for(ComputingAppliance ca : ComputingAppliance.getAllComputingAppliances()) {
			System.out.println("Computing Appliance: " + ca.name);
			System.out.println("\tEnergy consumption (kWh): " + ca.energyConsumption / 1000 / 3_600_000);
			System.out.println("\tGateway: " + ca.gateway);
			totalEnergyConsumption += ca.energyConsumption;
			for(PhysicalMachine pm : ca.iaas.machines) {
				if(pm.localDisk.getMaxStorageCapacity()-pm.localDisk.getFreeStorageCapacity() != 0) {
					System.out.println("\t\t" + pm);
				}
					
			}
			System.out.println("\t\t" + ca.iaas.repositories.get(0));
			System.out.println("\t\t" + ca.iaas.repositories.get(0).contents());
			for(Application application : ca.applications) {
				long applicationRuntime = 0;
				
				System.out.println("\tApplication: " + application.name);
				System.out.println("\t\tTotal received / processed data: " + application.receivedData + " / " + application.processedData);
				System.out.println("\t\tApp Vms: " + application.utilisedVMs.size());
				for(AppVm appVm : application.utilisedVMs) {
					System.out.println("\t\t\t"+appVm);
					numberOfTasks += appVm.taskCounter;
					applicationRuntime += appVm.workTime;
				}
				System.out.println("\t\tApplication cost: " + application.instance.calculateCloudCost(applicationRuntime));
				System.out.println("\t\tNumber of connected devices: " + application.deviceList.size());
				totalReceivedData += application.receivedData;
				totalProcessedData += application.processedData;
				numberOfVMs += application.utilisedVMs.size();
				totalCost += application.instance.calculateCloudCost(applicationRuntime);
				
				
			}
		}
		
		System.out.println("Number of devices: " + Device.allDevices.size());
		for(Device device : Device.allDevices) {
			totalGeneratedData += device.generatedData;
			totalMessageCount += device.messageCount;
		}
		
		System.out.println("Timeout (minutes): " + TimeUnit.MINUTES.convert(Application.lastAction-Device.lastAction,TimeUnit.MILLISECONDS));
		System.out.println("Simulation length (minutes): " + TimeUnit.MINUTES.convert(Timed.getFireCount(), TimeUnit.MILLISECONDS));
		System.out.println("Runtime (seconds): " + TimeUnit.SECONDS.convert(runtime, TimeUnit.NANOSECONDS));
		System.out.println("Total energy consumption of the nodes (kWh): " + totalEnergyConsumption / 1000 / 3_600_000);
		System.out.println("Total cost ($): " + totalCost);
		System.out.println("IoT costs ($): ");
		for(int i=0;i<Provider.providers.size();i++) {
			System.out.print(Provider.providers.get(i).name + ": " + BigDecimal.valueOf(Provider.providers.get(i).calculate()).toPlainString() + " ");
			if(i==Provider.providers.size()-1) {
				System.out.println();
			}
		}
		System.out.println("Total amount of generated / received / processed data (bytes): " + totalGeneratedData + " / " + totalReceivedData + " / " + totalProcessedData);
		System.out.println("Total message count (pc.): " + totalMessageCount);
		System.out.println("Total time on network (seconds): " + TimeUnit.SECONDS.convert(Application.totalTimeOnNetwork, TimeUnit.MILLISECONDS));
		System.out.println("Total bytes on network: " + Application.totalBytesOnNetwork);
		System.out.println("Number of VMs (pc.): " + numberOfVMs + " Number of tasks (pc.): " + numberOfTasks);
		System.out.println("Number of events (pc.)" +
				"\n\tChange position: " + MobilityEvent.changePositionEventCounter +
				"\n\tChange node: " + MobilityEvent.changeNodeEventCounter +
				"\n\tConnect to node: " + MobilityEvent.connectToNodeEventCounter +
				"\n\tDisconnect to node: " + MobilityEvent.disconnectFromNodeEventCounter);
	}

	public static void calculateIoTCost() {
		new IBMProvider();
		new AWSProvider();
		new AzureProvider();
	}

}