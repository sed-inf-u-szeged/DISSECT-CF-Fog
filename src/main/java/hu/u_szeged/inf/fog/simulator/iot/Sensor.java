package hu.u_szeged.inf.fog.simulator.iot;

import hu.mta.sztaki.lpds.cloud.simulator.DeferredEvent;
import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.io.StorageObject;
import hu.u_szeged.inf.fog.simulator.util.TimelineVisualiser.TimelineEntry;
import java.util.ArrayList;

public class Sensor extends DeferredEvent {

    public static ArrayList<TimelineEntry> sensorEventList = new ArrayList<>();

    private Device device;

    Sensor(Device device, long delay) {
        super(delay);
        this.device = device;
    }

    @Override
    protected void eventAction() {
        StorageObject so = new StorageObject(
                this.device.localMachine.localDisk.getName() + " " + this.device.fileSize + " " + Timed.getFireCount(),
                this.device.fileSize, false);
        if (this.device.localMachine.localDisk.registerObject(so)) {
            this.device.generatedData += so.size;
            Device.totalGeneratedSize += so.size;
            this.device.messageCount++;
        } else {
            try {
                System.err.println("ERROR in Sensor.java: Saving data into the local repository is unsuccessful.");
                System.exit(0);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
