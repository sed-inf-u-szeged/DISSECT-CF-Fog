package hu.u_szeged.inf.fog.simulator.distributed_ledger.task.tx;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ResourceConsumption;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.Miner;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.Transaction;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.communication.TransactionMessage;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.communication.TransactionTransferEvent;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.task.MinerTask;
import hu.u_szeged.inf.fog.simulator.node.ComputingAppliance;
import hu.u_szeged.inf.fog.simulator.util.SimLogger;

/**
 * The PropagateTransactionTask class represents a task for propagating a transaction to neighboring miners.
 * This task is executed by a miner to broadcast a new transaction to its peers in the network.
 */
public class PropagateTransactionTask implements MinerTask {

    private final Transaction tx;

    /**
     * Constructs a new PropagateTransactionTask with the specified transaction.
     *
     * @param tx the transaction to propagate
     */
    public PropagateTransactionTask(Transaction tx) {
        this.tx = tx;
    }

    /**
     * Determines whether this PropagateTransactionTask can execute on the given miner.
     * The task can execute if the transaction is not null and is not already known by the miner.
     *
     * @param miner The {@link Miner} instance to check for task eligibility.
     * @return {@code true} if the task can execute, {@code false} otherwise.
     */
    @Override
    public boolean canExecute(Miner miner) {
        // the transaction is not null and the transaction is not already known in the miner
        return (tx != null );
    }

    /**
     * Executes the transaction propagation process for the given miner.
     * This method initiates the broadcasting of the transaction to the miner's neighbors.
     *
     * @param miner The {@link Miner} that owns and executes this task.
     */
    @Override
    public void execute(Miner miner) {
        SimLogger.logRun(miner.name + " Propagating transaction " + tx.getId() + " ...");
        miner.setState(Miner.MinerState.PROPAGATING_TRANSACTION);

        miner.addKnownTransaction(tx);
        TransactionMessage message = new TransactionMessage(tx);
        for (ComputingAppliance neighborCa : miner.computingAppliance.neighbors) {
            Miner neighbor = Miner.miners.get(neighborCa);

            if (neighbor == null) {
                continue;
            }

            if (neighbor.isTxKnown(tx)) {
                continue;
            }

            TransactionTransferEvent event = new TransactionTransferEvent(message, miner, neighbor);
            try {
                NetworkNode.initTransfer(message.size,                                  // how many bytes to send
                        ResourceConsumption.unlimitedProcessing, miner.getLocalRepo(),  // from me
                        neighbor.getLocalRepo(),                                        // to neighbor
                        event);
            } catch (NetworkNode.NetworkException e) {
                SimLogger.logError(miner.name + "  Could not send transaction to " + neighbor.getName() + ": " + e.getMessage());
            }
        }

        miner.finishTask(this);
    }

    /**
     * Provides a description of this task.
     *
     * @return A string describing the task.
     */
    @Override
    public String describe() {
        return "PropagateTransactionTask(" + tx.getId() + ")";
    }
}