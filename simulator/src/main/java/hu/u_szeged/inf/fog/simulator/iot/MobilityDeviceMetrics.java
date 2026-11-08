package hu.u_szeged.inf.fog.simulator.iot;

/** Read-only access to completed device uploads for the mobility observation scenario. */
public final class MobilityDeviceMetrics {
    private MobilityDeviceMetrics() { }

    public static long uploadCompletedBytes(Device device) {
        return device.sentData;
    }
}
