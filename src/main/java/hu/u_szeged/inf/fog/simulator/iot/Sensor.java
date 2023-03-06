package hu.u_szeged.inf.fog.simulator.iot;

import hu.mta.sztaki.lpds.cloud.simulator.DeferredEvent;
import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.io.StorageObject;

class Sensor extends DeferredEvent {
   
    private Device device;

    Sensor(Device device, long delay) {
        super(delay);
        this.device = device;
    }

    @Override
    protected void eventAction() {
        StorageObject so = new StorageObject(this.device.localRepo.getName() + " " + this.device.fileSize + " " + Timed.getFireCount(), this.device.fileSize, false);
        if (this.device.localRepo.registerObject(so)) {
            this.device.generatedData += so.size;
            Device.totalGeneratedSize += so.size;
            this.device.messageCount++;
            Device.lastAction=Timed.getFireCount();
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
