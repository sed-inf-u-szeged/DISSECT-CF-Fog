package hu.u_szeged.inf.fog.simulator.provider;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.u_szeged.inf.fog.simulator.application.Application;
import hu.u_szeged.inf.fog.simulator.iot.Device;

/**
 * The class represents a service provider mimicking Microsoft Azure.
 * It calculates the cost based on messages sent per day, monthly cost, and message size.
 * Based on: // https://azure.microsoft.com/en-us/pricing/details/iot-hub/
 */
public class AzureProvider extends Provider {

    /**
     * The default size of a message in bytes for Azure.
     */
    static final long defaultMessageSize = 4096;

    /**
     * The default number of messages per day for Azure.
     */
    static final long defaultmessagesPerDay = 400_000;

    /**
     * The default monthly cost for Azure.
     */
    static final int defaultMonthlyCost = 25;

    /**
     * The number of messages sent per day.
     */
    long messagesPerDay;

    /**
     * The monthly cost for the provider.
     */
    int monthlyCost;

    /**
     * The size of a message.
     */
    long messageSize;

    /**
     * Constructs the provider with default values.
     */
    public AzureProvider() {
        this.name = "Azure";
        Provider.allProviders.add(this);
    }
    
    /**
     * Constructs the provider with specified values for 
     * messages per day, monthly cost, and message size.
     *
     * @param messagesPerDay the number of messages sent per day
     * @param monthlyCost    the monthly cost of the provider
     * @param messageSize    the size of a message
     */
    public AzureProvider(long messagesPerDay, int monthlyCost, long messageSize) {
        this.messagesPerDay = messagesPerDay;
        this.monthlyCost = monthlyCost;
        this.messageSize = messageSize;
        this.name = "Azure";
        Provider.allProviders.add(this);
    }

    /**
     * Calculates and returns with the cost incurred based on
     * messages sent per day, monthly cost, and message size.
     */
    @Override
    public double calculate() {
        long messagesPerDay;
        int monthlyCost;
        long messageSize;

        if (this.messagesPerDay == 0 || this.monthlyCost == 0 || this.messageSize == 0) {
            messagesPerDay = defaultmessagesPerDay;
            monthlyCost = defaultMonthlyCost;
            messageSize = defaultMessageSize;
        } else {
            messagesPerDay = this.messagesPerDay;
            monthlyCost = this.monthlyCost;
            messageSize = this.messageSize;
        }

        int countOfServicableApplications = 0;
        for (Application app : Application.allApplications) {
            if (app.serviceable) {
                countOfServicableApplications++;
            }
        }
        double time = (double) Timed.getFireCount() / 1000 / 60 / 60 / 24 / 30;

        this.cost = countOfServicableApplications * monthlyCost * time;

        long totalMessageCount = 0;
        double totalDeviceFileSize = 0.0;
        for (Device d : Device.allDevices) {
            totalDeviceFileSize += d.fileSize;
            totalMessageCount += d.messageCount;
        }
        if ((totalDeviceFileSize / Device.allDevices.size()) > messageSize) {
            System.err.println("The message size is larger than the category allows for the Azure IoT provider.");
        }

        double days = (double) Timed.getFireCount() / 1000 / 60 / 60 / 24;

        if ((totalMessageCount / days) > messagesPerDay) {
            System.err.println("The message size is larger than the category allows for the Azure IoT provider.");
        }

        return this.cost;
    }
}