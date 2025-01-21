package hu.u_szeged.inf.fog.simulator.iot.distributed_ledger;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ConsumptionEventAdapter;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ResourceConsumption;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode;
import hu.mta.sztaki.lpds.cloud.simulator.util.SeedSyncer;
import hu.u_szeged.inf.fog.simulator.iot.distributed_ledger.utils.Utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class BlockValidator extends Timed {

    Random random;
    static List<BlockValidator> validators = new ArrayList<>();
    private final VirtualMachine machine;
    private final Mempool mempool;
    final DistributedLedger distributedLedger;
    private boolean runningValidation = false;
    private boolean runningPoW = false;
    private boolean runningHeaderCalc = false;
    private Block nextBlock;


    public BlockValidator(VirtualMachine machine, Mempool mempool, int checkMempoolFreq, DistributedLedger distributedLedger) {
        validators.add(this);
        this.machine = machine;
        this.mempool = mempool;
        this.distributedLedger = distributedLedger;
        this.random = SeedSyncer.centralRnd;
        subscribe(checkMempoolFreq);
    }

    @Override
    public void tick(long fires) {
        if (runningValidation) // if a transaction validation is in progress, should not do anything, just wait
            return;

        if (nextBlock == null) { // if there is no new block, creates an empty one
            nextBlock = new Block(distributedLedger);
        }

        if (nextBlock.isFull() && !runningHeaderCalc) { //if the block is already full, but the header calculation is not started yet, start it
            calculateHeader();
            return;
        }

        if (!runningHeaderCalc && !runningPoW && !nextBlock.isNonceFound) { // if the header calculation is ready and wasnt already started a pow or hasnt already finished the started one, then start one
            findNonce();
            return;
        }

        if (nextBlock.isNonceFound) { // if the nonce is found, the propagation should be started
            propagateBlock(nextBlock);
            nextBlock = null; //reset nextBlock and start the whole process from the beginning with instantiating a new Block and selecting the transactions
            cleanupLocalMempool();
            return;
        }

        if (!mempool.isEmpty()) { // "base case" to fill the block with transactions if needed and validate them

            //Transaction tx = mempool.fetchTransaction(); //right now it fetches only 1 transaction, it needs some logic, how should it choose a transaction (maybe random?)
            //If there are enough transactions validated for a block, it should start to find the nonce for the puzzle
            //If the nonce is found and the block is done, the transactions in the mempool should be removed.

            Transaction tx = pickRandomTransaction(); //could be a strategy how to pick tx; Random, FIFO, FILO, some priorityAlg to decide on size?
            validateTransaction(tx);
        }

    }

    /**
     * Return true if the transaction is valid
     * For now they are 100% valid, later can be some noise with 90% validity
     *
     * @param tx
     * @return
     */
    private void validateTransaction(Transaction tx) {
        System.out.println("[BlockValidator] validation started for transaction: " + tx);
        try {
            machine.newComputeTask(Utils.instructionsTransactionValidation(tx.getSize(), distributedLedger), ResourceConsumption.unlimitedProcessing, new ConsumptionEventAdapter() {
                @Override
                public void conComplete() {
                    System.out.println("[BlockValidator] validated transaction: " + tx + ", at time: " + Timed.getFireCount()); // Why does it not reach this point?
                    //ResourceConsumption is null -> Could return null if the consumption cannot be registered or when there is no resource for the VM. Where is the problem?
                    // Because it needs time to boot up, need to wait until it can be utilized
                    runningValidation = false; //this validation is done, can the next one be started
                    boolean valid = true; //later can be random also
                    if (valid) buildBlock(tx);
                }
            });
        } catch (NetworkNode.NetworkException networkException) {
            System.err.println(networkException);
        }
    }

    public Transaction pickRandomTransaction() {
        int randomIndex = random.nextInt(mempool.size());
        return mempool.get(randomIndex);
    }

    /**
     * headerInputData: <Version><Prev Block Hash><Merkle Root><Timestamp><Difficulty><Nonce>
     * 80 byte
     *
     * @return
     */
    private void calculateHeader() {
        this.runningHeaderCalc = true;
        //need the list of validated transactions and hash them -> Merkle root
        int leafNodes = nextBlock.getTransactions().size();
        double instructions = nextBlock.getTransactions().stream()
                                .mapToDouble(tx -> this.distributedLedger.digestStrategy.hash(tx.getSize()))
                                .sum();
        int nonLeafNodes = Utils.merkleRoot(leafNodes) - leafNodes;
        int hashKeySize = this.distributedLedger.digestStrategy.getKeySize();
        for (int i = 0; i < nonLeafNodes; i++) {
            instructions += this.distributedLedger.digestStrategy.hash(hashKeySize * 2L);
        }
        try {
            machine.newComputeTask(instructions, ResourceConsumption.unlimitedProcessing, new ConsumptionEventAdapter() {
                @Override
                public void conComplete() {
                    System.out.println("[BlockValidator] header is ready, at time: " + Timed.getFireCount());
                    runningHeaderCalc = false; //calculating the header input string is done
                }
            });
        } catch (NetworkNode.NetworkException networkException) {
            System.err.println(networkException);
        }

    }

    /**
     * Runs the Proof of Work algorithm and tries to find the nonce
     */
    private void findNonce() {
        this.runningPoW = true;
        try {
            machine.newComputeTask(Utils.instructionsPoW(distributedLedger), ResourceConsumption.unlimitedProcessing, new ConsumptionEventAdapter() {
                @Override
                public void conComplete() {
                    System.out.println("[BlockValidator] found a nonce, at time: " + Timed.getFireCount());
                    nextBlock.isNonceFound = true;
                    runningPoW = false;
                }
            });
        } catch (NetworkNode.NetworkException networkException) {
            System.err.println(networkException);
        }
    }

    /**
     * If a nonce is found for the target difficulty, the block can be propagated to the peer nodes.
     * @param block The block to be propagated
     */
    private void propagateBlock(Block block) {
        System.out.println("[BlockValidator] Propagating validated block for transactions: " + block.getTransactions());
        //some gossip-like protocol to propagate it to the peers
        //the peer that receives it should also propagate it further
    }

    /**
     * Removes the transactions which are fitted into the new block
     */
    private void cleanupLocalMempool() {
        for (Transaction tx : nextBlock.getTransactions()) {
            mempool.remove(tx);
        }
    }

    /**
     * Tries to add transaction to the block if it is possible,
     * if the size of the block would overflow with the given tx, it skips
     * @param tx Transaction to be added to the new block
     */
    private void buildBlock(Transaction tx) {
        try {
            nextBlock.add(tx);
        } catch (ArithmeticException e) {
            System.err.println("[BlockValidator] This transaction does not fit into the block. Remaining space in the block: " + (distributedLedger.getBlockSize() - nextBlock.size()));
        }
    }


}
