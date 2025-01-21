package hu.u_szeged.inf.fog.simulator.iot.distributed_ledger;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.util.SeedSyncer;
import hu.u_szeged.inf.fog.simulator.iot.Device;

import java.util.Random;

public class TransactionDevice extends Device {

    Mempool mempool;

    private final Random random = SeedSyncer.centralRnd;

    public TransactionDevice(long startTime, long stopTime, long transactionSize, long freq,
                             PhysicalMachine localMachine, int latency, Mempool mempool) {
        Device.allDevices.add(this);
        this.startTime = startTime;
        this.stopTime = stopTime;
        this.fileSize = transactionSize;
        this.freq = freq;
        this.deviceStrategy = null;
        this.mobilityStrategy = null;
        this.localMachine = localMachine;
        this.latency = latency;
        this.mempool = mempool;
        this.startMeter();
    }

    @Override
    public void tick(long fires) {
        if (Timed.getFireCount() < stopTime && Timed.getFireCount() >= startTime) {
//            new Sensor(this, 1);
            String data = "SensorData{" + random.nextInt(100) + "}";
            Transaction tx = new Transaction(data, fileSize);
            System.out.println("[TransactionDevice] generated transaction: " + tx);
            mempool.addTransaction(tx); // in the future this should be a real data transfer to a full node, where all the transactions are "stored"
        }

        if (Timed.getFireCount() > stopTime) {
            this.stopMeter();
        }
    }

}
