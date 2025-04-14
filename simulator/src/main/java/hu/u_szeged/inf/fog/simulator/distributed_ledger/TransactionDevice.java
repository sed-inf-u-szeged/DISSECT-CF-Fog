package hu.u_szeged.inf.fog.simulator.distributed_ledger;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ResourceConsumption;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode;
import hu.mta.sztaki.lpds.cloud.simulator.io.StorageObject;
import hu.mta.sztaki.lpds.cloud.simulator.util.SeedSyncer;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.communication.TransactionMessage;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.communication.TransactionPublishEvent;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.metrics.SimulationMetrics;
import hu.u_szeged.inf.fog.simulator.iot.Device;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.find_node_strategy.FindNodeStrategy;
import hu.u_szeged.inf.fog.simulator.iot.mobility.GeoLocation;
import hu.u_szeged.inf.fog.simulator.iot.mobility.StaticMobilityStrategy;
import hu.u_szeged.inf.fog.simulator.util.SimLogger;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The `TransactionDevice` class represents a device that generates and publishes transactions in a distributed ledger.
 * It extends the `Device` class and uses a `FindNodeStrategy` to determine where to publish transactions.
 * This class is responsible for creating transactions at specified intervals and sending them to miners.
 */
public class TransactionDevice extends Device {

    public static final int CONNECT2NODE_MAX_RETRIES = 10;

    private static final Random random = SeedSyncer.centralRnd;
    private final FindNodeStrategy findNodeStrategy;
    private final String name;
    private Miner connectedNode;

    /**
     * Constructs a `TransactionDevice` with the specified parameters.
     *
     * @param startTime        the start time for generating transactions
     * @param stopTime         the stop time for generating transactions
     * @param transactionSize  the size of each transaction
     * @param freq             the frequency of transaction generation (elapsed time between transactions)
     * @param localMachine     the local physical machine associated with the device
     * @param latency          the network latency
     * @param findNodeStrategy the strategy to find nodes for publishing transactions
     */
    public TransactionDevice(long startTime, long stopTime, long transactionSize, long freq, PhysicalMachine localMachine, int latency, FindNodeStrategy findNodeStrategy) {
        this.name = "[TransactionDevice]" + Device.allDevices.size();
        Device.allDevices.add(this);
        this.startTime = startTime;
        this.stopTime = stopTime;
        this.fileSize = transactionSize;
        this.freq = freq;
        this.deviceStrategy = null;
        this.mobilityStrategy = new StaticMobilityStrategy(GeoLocation.generateRandomGeoLocation());
        this.localMachine = localMachine;
        this.latency = latency;
        this.findNodeStrategy = findNodeStrategy;
        this.startMeter();
    }

    /**
     * Returns the name of the transaction device.
     *
     * @return the name of the transaction device
     */
    public String getName() {
        return name;
    }

    /**
     * Generates transactions and publishes them at each tick.
     *
     * @param fires the current simulation time
     */
    @Override
    public void tick(long fires) {
        if (Timed.getFireCount() < stopTime && Timed.getFireCount() >= startTime) {
//            new Sensor(this, 1);
            String data = "SensorData{" + random.nextInt(100) + "}";
            Transaction tx = new Transaction(data, fileSize);
            TransactionMessage msg = new TransactionMessage(tx);
            this.localMachine.localDisk.registerObject(msg);
            SimLogger.logRun("[TransactionDevice] generated transaction: " + tx);
            SimulationMetrics.getInstance().setTransactionCreationTime(tx, Timed.getFireCount());
        }

        if (Timed.getFireCount() > stopTime) {
            this.stopMeter();
        }

        if (connectedNode == null && !attemptConnectionToNode(CONNECT2NODE_MAX_RETRIES)) {
            return;
        }

        for (TransactionMessage tm : getMessagesToPublish()) {
            publishTransaction(tm);
        }
    }

    /**
     * Attempts to establish a connection to a node with a specified maximum number of retries.
     * Logs a message each time a connection attempt fails.
     *
     * @param maxRetries the maximum number of connection attempts
     * @return true if a connection to a node was successfully established, false otherwise
     */
    private boolean attemptConnectionToNode(int maxRetries) {
        int tryCount = 0;
        while (!connectToNode() && tryCount < maxRetries) {
            tryCount++;
            SimLogger.logRun(name + "  Could not connect to a miner. Retrying...");
        }
        return connectedNode != null;
    }

    /**
     * Retrieves the list of transaction messages to be published.
     *
     * @return the list of transaction messages to be published
     */
    private List<TransactionMessage> getMessagesToPublish() {
        List<TransactionMessage> messages = new ArrayList<>();
        for (StorageObject so : this.localMachine.localDisk.contents()) {
            if (so instanceof TransactionMessage) {
                messages.add((TransactionMessage) so);
            }
        }
        return messages;
    }

    /**
     * Publishes a transaction message to the connected miner. If no miner is connected,
     * attempts to find and connect to a suitable miner before publishing.
     *
     * @param msg the transaction message to be published
     */
    private void publishTransaction(TransactionMessage msg) {
        TransactionPublishEvent event = new TransactionPublishEvent(msg, this, connectedNode);
        try {
            NetworkNode.initTransfer(msg.size,                                               // how many bytes to send
                    ResourceConsumption.unlimitedProcessing,
                    this.localMachine.localDisk,                                           // from me
                    connectedNode.getLocalRepo(),                                         // to neighbor
                    event);
        } catch (NetworkNode.NetworkException e) {
            SimLogger.logError(name + "  Could not publish transaction to " + connectedNode.getName() + ": " + e.getMessage());
        }
    }

    public boolean connectToNode() {
        Miner candidate = findNodeStrategy.findNode();
        if (candidate.localVm.getState().equals(VirtualMachine.State.RUNNING)) {
            this.caRepository = candidate.computingAppliance.iaas.repositories.get(0);
            int calc = this.latency + (int) (this.geoLocation.calculateDistance(this.application.computingAppliance.geoLocation) / 1000);
            this.localMachine.localDisk.addLatencies(candidate.computingAppliance.iaas.repositories.get(0).getName(), calc);
            this.connectedNode = candidate;
            return true;
        } else return false;
    }
}