package hu.u_szeged.inf.fog.simulator.provider;

import hu.u_szeged.inf.fog.simulator.iot.Device;

// 
/**
 * The class represents a service provider mimicking Amazon Web Services (AWS).
 * It calculates the cost based on connectivity and message publishing rates.
 * Based on: https://aws.amazon.com/iot-core/pricing/
 */
public class AwsProvider extends Provider {

    /**
     * The default cost per message for connectivity in AWS.
     */
    static final double defaultConnectivityCostPerMessages = 0.08 / 1_000_000;

    /**
     * The default cost per message for publishing in AWS.
     */
    static final double defaultPublishingCostPerMessages = 1.0 / 1_000_000;

    /**
     * The cost per message for connectivity in AWS.
     */
    double connectivityCostPerMessages;

    /**
     * The cost per message for publishing in AWS.
     */
    double publishingCostPerMessages;

    /**
     * Constructing the provider with the default publishing and connectivity costs.
     */
    public AwsProvider() {
        this.name = "AWS";
        Provider.allProviders.add(this);
    }

    /**
     * Constructing the provider with specified connectivity and publishing costs.
     *
     * @param connectivityCostPerMessages the cost per message for connectivity
     * @param publishingCostPerMessages  the cost per message for publishing
     */
    public AwsProvider(double connectivityCostPerMessages, double publishingCostPerMessages) {
        this.connectivityCostPerMessages = connectivityCostPerMessages;
        this.publishingCostPerMessages = publishingCostPerMessages;
        this.name = "AWS";
        Provider.allProviders.add(this);
    }

    /**
     * Calculates and returns with the cost based on  by the AWS provider based on 
     * connectivity rate (minutes of connection) and the total message counts.
     */
    @Override
    public double calculate() {
        double connectivityCostPerMessages;
        double publishingCostPerMessages;

        if (this.connectivityCostPerMessages == 0 || this.publishingCostPerMessages == 0) {
            connectivityCostPerMessages = defaultConnectivityCostPerMessages;
            publishingCostPerMessages = defaultPublishingCostPerMessages;
        } else {
            connectivityCostPerMessages = this.connectivityCostPerMessages;
            publishingCostPerMessages = this.publishingCostPerMessages;
        }

        long totalDeviceRuntime = 0;
        long totalMessageCount = 0;

        for (Device d : Device.allDevices) {
            totalDeviceRuntime += (d.stopTime - d.startTime);
            totalMessageCount += d.messageCount;
        }
        this.cost += (totalDeviceRuntime / 1000 / 60) * connectivityCostPerMessages;
        this.cost += totalMessageCount * publishingCostPerMessages;

        return this.cost;
    }

}
