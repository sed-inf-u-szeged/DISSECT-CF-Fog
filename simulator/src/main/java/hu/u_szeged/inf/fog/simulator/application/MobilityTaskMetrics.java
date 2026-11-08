package hu.u_szeged.inf.fog.simulator.application;

/** Read-only access to task state for the mobility observation scenario. */
public final class MobilityTaskMetrics {
    private MobilityTaskMetrics() { }

    public static boolean isRunning(AppVm vm) {
        return vm.isWorking;
    }
}
