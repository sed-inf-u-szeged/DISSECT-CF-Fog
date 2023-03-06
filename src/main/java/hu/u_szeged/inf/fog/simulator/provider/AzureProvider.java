package hu.u_szeged.inf.fog.simulator.provider;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.u_szeged.inf.fog.simulator.application.Application;
import hu.u_szeged.inf.fog.simulator.iot.Device;

// https://azure.microsoft.com/en-us/pricing/details/iot-hub/
public class AzureProvider extends Provider{
	
	static final long defaultMessageSize = 4096;
	
	static final long defaultmessagesPerDay = 400_000;
	
	static final int defaultMonthlyCost = 25;
	
	long messagesPerDay;
	
	int monthlyCost;
	
	long messageSize;
	
	public AzureProvider() {
		this.name = "Azure";
		Provider.providers.add(this);
	}
	
	public AzureProvider(long messagesPerDay, int monthlyCost, long messageSize) {
		this.messagesPerDay = messagesPerDay;
		this.monthlyCost = monthlyCost;
		this.messageSize = messageSize;
		this.name = "Azure";
		Provider.providers.add(this);
	}
	
	@Override
	public double calculate() {	
		long messagesPerDay;
		int monthlyCost;
		long messageSize;

		if(this.messagesPerDay == 0 || this.monthlyCost == 0 || this.messageSize == 0) {
			messagesPerDay = defaultmessagesPerDay;
			monthlyCost = defaultMonthlyCost;
			messageSize = defaultMessageSize;
		}else {
			messagesPerDay = this.messagesPerDay;
			monthlyCost = this.monthlyCost;
			messageSize = this.messageSize;
		}
		
		int CountOfServicableApplications = 0;
		for(Application app : Application.allApplications) {
			if(app.serviceable) {
				CountOfServicableApplications++;
			}
		}
		double time = (double) Timed.getFireCount()/1000/60/60/24/30;

		this.cost = CountOfServicableApplications * monthlyCost * time;
		
		long totalMessageCount = 0;
		double totalDeviceFileSize = 0.0;
		for(Device d : Device.allDevices) {
			totalDeviceFileSize += d.fileSize;
			totalMessageCount += d.messageCount;
		}
		if((totalDeviceFileSize / Device.allDevices.size()) > messageSize) {
			System.err.println("The message size is larger than the category allows for the Azure IoT provider.");
		}
		
		double days = (double) Timed.getFireCount()/1000/60/60/24;
		
		if((totalMessageCount / days) > messagesPerDay) {
			System.err.println("The message size is larger than the category allows for the Azure IoT provider.");
		}
	
		return this.cost;
	}

}
