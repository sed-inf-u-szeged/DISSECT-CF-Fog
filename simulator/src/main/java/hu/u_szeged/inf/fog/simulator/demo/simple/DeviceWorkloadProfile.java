package hu.u_szeged.inf.fog.simulator.demo.simple;

/** Explicit synthetic demand inputs; independent of predictions and queue sampling. */
public enum DeviceWorkloadProfile {
    CONSTANT("constant"), PERIODIC_BURST("periodic-burst");

    private final String key;
    DeviceWorkloadProfile(String key) { this.key = key; }
    public String key() { return key; }

    public static DeviceWorkloadProfile parse(String key) {
        for (DeviceWorkloadProfile profile : values()) {
            if (profile.key.equals(key)) { return profile; }
        }
        throw new IllegalArgumentException("device_workload must be constant or periodic-burst");
    }

    /** Bytes generated per one-minute measurement, starting at each device's actual start. */
    public long bytesAt(int deviceIndex, long measurementIndex) {
        if (deviceIndex < 0 || measurementIndex < 0) {
            throw new IllegalArgumentException("Device and measurement indices must be non-negative");
        }
        if (this == CONSTANT) { return 100; }
        int phase = (int) ((measurementIndex % 32 + (deviceIndex % 32) * 3L) % 32);
        return 100 + 200L * Math.max(0, 9 - Math.abs(phase - 16));
    }

    public String metadata() {
        String common = "\ndevice_workload=" + key + "\ndevice_workload_version=1"
                + "\ndevice_measurement_interval_ms=60000";
        return common + (this == CONSTANT ? "\ndevice_bytes_per_minute=100"
                : "\ndevice_bytes_min=100\ndevice_bytes_max=1900\ndevice_workload_period_minutes=32"
                + "\ndevice_workload_phase_step_minutes=3");
    }
}
