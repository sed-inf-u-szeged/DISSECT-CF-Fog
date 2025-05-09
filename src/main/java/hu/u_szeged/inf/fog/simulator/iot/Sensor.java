package hu.u_szeged.inf.fog.simulator.iot;

import hu.mta.sztaki.lpds.cloud.simulator.DeferredEvent;
import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.io.StorageObject;
import hu.u_szeged.inf.fog.simulator.util.TimelineVisualiser.TimelineEntry;
import java.util.ArrayList;

/**
 * This class is an abstract representation of an IoT sensor, its only task
 * is to create a file with a given delay as a result of a measurement.
 */
public class Sensor extends DeferredEvent {

    /**
     * It contains the log of a sensor events. 
     * It is used only for visualization, in IoT workflow simulations.
     */
    public static ArrayList<TimelineEntry> sensorEventList = new ArrayList<>();

    /**
     * The device that utilizes this sensor. 
     */
    private Device device;

    /**
     * Defines a sensor measurement.
     *
     * @param device the device that will store the generated data
     * @param delay the length of the measurement (ms)
     */
    Sensor(Device device, long delay) {
        super(delay);
        this.device = device;
    }

    /**
     * It creates and saves the file in the storage of the device.
     * If the data cannot be save (e.g. due to lack of space), 
     * the simulation terminates.
     */
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
