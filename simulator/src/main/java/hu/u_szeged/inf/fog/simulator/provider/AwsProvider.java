package hu.u_szeged.inf.fog.simulator.provider;

import hu.u_szeged.inf.fog.simulator.iot.Device;

// https://aws.amazon.com/iot-core/pricing/
public class AwsProvider extends Provider {

    static final double defaultConnectivityCostPerMessages = 0.08 / 1_000_000;

    static final double defaultPublishingCostPerMessages = 1.0 / 1_000_000;

    double connectivityCostPerMessages;

    double publishingCostPerMessages;

    public AwsProvider() {
        this.name = "AWS";
        Provider.providers.add(this);
    }

    public AwsProvider(double connectivityCostPerMessages, double publishingCostPerMessages) {
        this.connectivityCostPerMessages = connectivityCostPerMessages;
        this.publishingCostPerMessages = publishingCostPerMessages;
        this.name = "AWS";
        Provider.providers.add(this);
    }

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
