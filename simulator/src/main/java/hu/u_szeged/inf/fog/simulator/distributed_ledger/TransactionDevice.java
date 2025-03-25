package hu.u_szeged.inf.fog.simulator.distributed_ledger;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.util.SeedSyncer;
import hu.u_szeged.inf.fog.simulator.iot.Device;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.find_node_strategy.FindNodeStrategy;

import java.util.Random;

public class TransactionDevice extends Device {

    private static final Random random = SeedSyncer.centralRnd;
    private final FindNodeStrategy findNodeStrategy;

    public TransactionDevice(long startTime, long stopTime, long transactionSize, long freq,
                             PhysicalMachine localMachine, int latency, FindNodeStrategy findNodeStrategy) {
        Device.allDevices.add(this);
        this.startTime = startTime;
        this.stopTime = stopTime;
        this.fileSize = transactionSize;
        this.freq = freq;
        this.deviceStrategy = null;
        this.mobilityStrategy = null;
        this.localMachine = localMachine;
        this.latency = latency;
        this.findNodeStrategy = findNodeStrategy;
        this.startMeter();
    }

    @Override
    public void tick(long fires) {
        if (Timed.getFireCount() < stopTime && Timed.getFireCount() >= startTime) {
//            Sensor sensor = new Sensor(this, 1);
            String data = "SensorData{" + random.nextInt(100) + "}";
            Transaction tx = new Transaction(data, fileSize);
            System.out.println("[TransactionDevice] generated transaction: " + tx);
            publishTransaction(tx);
        }

        if (Timed.getFireCount() > stopTime) {
            this.stopMeter();
        }
    }

    private void publishTransaction(Transaction tx) {
        this.findNodeStrategy.findNode().registerTransaction(tx);
    }

}
