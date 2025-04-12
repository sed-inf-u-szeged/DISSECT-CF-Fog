//package hu.u_szeged.inf.fog.simulator.distributed_ledger;
//
//import hu.mta.sztaki.lpds.cloud.simulator.Timed;
//import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
//import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;
//import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.AlterableResourceConstraints;
//import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ConsumptionEventAdapter;
//import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ResourceConsumption;
//import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode;
//import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;
//import hu.mta.sztaki.lpds.cloud.simulator.util.SeedSyncer;
//import hu.u_szeged.inf.fog.simulator.distributed_ledger.communication.BlockMessage;
//import hu.u_szeged.inf.fog.simulator.distributed_ledger.communication.BlockTransferEvent;
//import hu.u_szeged.inf.fog.simulator.distributed_ledger.communication.TransactionMessage;
//import hu.u_szeged.inf.fog.simulator.distributed_ledger.communication.TransactionTransferEvent;
//import hu.u_szeged.inf.fog.simulator.distributed_ledger.transaction_selection_strategy.TransactionSelectionStrategy;
//import hu.u_szeged.inf.fog.simulator.distributed_ledger.utils.Utils;
//import hu.u_szeged.inf.fog.simulator.node.ComputingAppliance;
//
//import java.util.*;
//
//@Deprecated
//public class BlockValidator extends Timed {
//    private static final Random RANDOM = SeedSyncer.centralRnd;
//    public static Map<ComputingAppliance, BlockValidator> validators = new HashMap<>();
//
//    public final ComputingAppliance computingAppliance;
//    public final VirtualMachine localVm;
//    public final String name;
//    private final Mempool mempool;
//    final DistributedLedger distributedLedger;
//    TransactionSelectionStrategy transactionSelectionStrategy;
//
//    private boolean runningValidation = false;
//    private boolean runningPoW = false;
//    private boolean runningHeaderCalc = false;
//
//    private Block nextBlock;
//    private LocalLedger localLedger;
//
//
//    private Set<Transaction> knownTransactions = new HashSet<>();
//
//
//    public BlockValidator(DistributedLedger distributedLedger, TransactionSelectionStrategy transactionSelectionStrategy, ComputingAppliance computingAppliance) {
//        validators.put(computingAppliance, this);
//        this.name = "[BV]" + UUID.randomUUID();
//        this.mempool = new Mempool();
//        this.distributedLedger = distributedLedger;
//        this.transactionSelectionStrategy = transactionSelectionStrategy;
//        this.computingAppliance = computingAppliance;
//        this.localVm = computingAppliance.broker.vm;
//        this.localLedger = new LocalLedger();//TODO
//        subscribe(1);
//    }
//
//    public boolean isTxKnown(Transaction tx) {
//        return this.knownTransactions.contains(tx);
//    }
//
//    @Override
//    public void tick(long fires) {
//        if (runningValidation) // if a transaction validation is in progress, should not do anything, just wait
//            return;
//
//        if (nextBlock == null) { // if there is no new block, creates an empty one
//            nextBlock = new Block(distributedLedger);
//        }
//
//        if (nextBlock.isFull() && !runningHeaderCalc) { //if the block is already full, but the header calculation is not started yet, start it
//            calculateHeader();
//            return;
//        }
//
//        if (!runningHeaderCalc && !runningPoW && !nextBlock.isNonceFound) { // if the header calculation is ready and wasnt already started a pow or hasnt already finished the started one, then start one
//            findNonce();
//            return;
//        }
//
//        if (nextBlock.isNonceFound) { // if the nonce is found, the propagation should be started
//            propagateBlock(nextBlock);
//            nextBlock = null; //reset nextBlock and start the whole process from the beginning with instantiating a new Block and selecting the transactions
//            cleanupLocalMempool();
//            return;
//        }
//
//        if (!mempool.isEmpty()) { // "base case" to fill the block with transactions if needed and validate them
//
//            //Transaction tx = mempool.fetchTransaction(); //right now it fetches only 1 transaction, it needs some logic, how should it choose a transaction (maybe random?)
//            //If there are enough transactions validated for a block, it should start to find the nonce for the puzzle
//            //If the nonce is found and the block is done, the transactions in the mempool should be removed.
//            Transaction tx = this.transactionSelectionStrategy.selectTransaction(mempool);
//            validateTransaction(tx);
//        }
//
//    }
//
//    public String getName() {
//        return this.name;
//    }
//
//    /**
//     * Returns always the first repository
//     * @return
//     */
//    public Repository getLocalRepo() {
//        if (this.computingAppliance.iaas.repositories.isEmpty()) {
//            throw new IllegalStateException("No local repository available");
//        }
//        return this.computingAppliance.iaas.repositories.get(0);
//    }
//
//    /**
//     * Return true if the transaction is valid
//     * For now they are 100% valid, later can be some noise with 90% validity
//     *
//     * @param tx
//     * @return
//     */
//    private void validateTransaction(Transaction tx) {
//        System.out.println("[BlockValidator] validation started for transaction: " + tx);
//        this.knownTransactions.add(tx);
//        try {
//            localVm.newComputeTask(distributedLedger.getConsensusStrategy().getCryptoStrategy().verify(), ResourceConsumption.unlimitedProcessing, new ConsumptionEventAdapter() {
//                @Override
//                public void conComplete() {
//                    System.out.println("[BlockValidator] validated transaction: " + tx + ", at time: " + Timed.getFireCount()); // Why does it not reach this point?
//                    //ResourceConsumption is null -> Could return null if the consumption cannot be registered or when there is no resource for the VM. Where is the problem?
//                    // Because it needs time to boot up, need to wait until it can be utilized
//                    runningValidation = false; //this validation is done, can the next one be started
//                    boolean valid = true; //later can be random also
//                    if (valid) buildBlock(tx);
//                }
//            });
//        } catch (NetworkNode.NetworkException networkException) {
//            System.err.println(networkException);
//        }
//    }
//
//    /**
//     * headerInputData: <Version><Prev Block Hash><Merkle Root><Timestamp><Difficulty><Nonce>
//     * 80 byte
//     *
//     * @return
//     */
//    private void calculateHeader() {
//        this.runningHeaderCalc = true;
//        //need the list of validated transactions and hash them -> Merkle root
//        double instructions = Utils.merkleRoot(nextBlock, distributedLedger.getConsensusStrategy().getDigestStrategy());
//        try {
//            this.localVm.newComputeTask(instructions, ResourceConsumption.unlimitedProcessing, new ConsumptionEventAdapter() {
//                @Override
//                public void conComplete() {
//                    System.out.println("[BlockValidator] header is ready, at time: " + Timed.getFireCount());
//                    runningHeaderCalc = false; //calculating the header input string is done
//                }
//            });
//        } catch (NetworkNode.NetworkException networkException) {
//            System.err.println(networkException);
//        }
//    }
//
//    /**
//     * Runs the Proof of Work algorithm and tries to find the nonce
//     */
//    private void findNonce() {
//        this.runningPoW = true;
//        try {
//            this.localVm.newComputeTask(Utils.instructionsPoW(distributedLedger), ResourceConsumption.unlimitedProcessing, new ConsumptionEventAdapter() {
//                @Override
//                public void conComplete() {
//                    System.out.println("[BlockValidator] found a nonce, at time: " + Timed.getFireCount());
//                    nextBlock.isNonceFound = true;
//                    runningPoW = false;
//                }
//            });
//        } catch (NetworkNode.NetworkException networkException) {
//            System.err.println(networkException);
//        }
//    }
//
//    /**
//     * If a nonce is found for the target difficulty, the block can be propagated to the peer nodes.
//     *
//     * @param block The block to be propagated
//     */
//    private void propagateBlock(Block block) {
//        System.out.println("[BlockValidator] Propagating validated block for transactions: " + block.getTransactions());
//        BlockMessage message = new BlockMessage(block);
//        this.getLocalRepo().registerObject(message);
//        //some gossip-like protocol to propagate it to the peers
//        //the peer that receives it should also propagate it further
//        for (ComputingAppliance ca : this.computingAppliance.neighbors) {
//            BlockValidator neighbor = validators.get(ca);
//            BlockTransferEvent event = new BlockTransferEvent(message, this, neighbor);
//            try {
//                // Actually schedule the network transfer
//                NetworkNode.initTransfer(
//                        message.size,
//                        ResourceConsumption.unlimitedProcessing,
//                        this.getLocalRepo(),            // from me
//                        neighbor.getLocalRepo(),       // to neighbor
//                        event
//                );
//            } catch (NetworkNode.NetworkException e) {
//                System.err.println("[BlockValidator] " + this.getName()
//                        + " -> Could not send block to " + neighbor.getName() + ": " + e.getMessage());
//            }
//        }
//    }
//
//    /**
//     * Removes the transactions which are fitted into the new block
//     */
//    private void cleanupLocalMempool() {
//        for (Transaction tx : nextBlock.getTransactions()) {
//            mempool.remove(tx);
//        }
//    }
//
//    /**
//     * Tries to add transaction to the block if it is possible,
//     * if the size of the block would overflow with the given tx, it skips
//     *
//     * @param tx Transaction to be added to the new block
//     */
//    private void buildBlock(Transaction tx) {
//        try {
//            nextBlock.add(tx);
//        } catch (ArithmeticException e) {
//            System.err.println("[BlockValidator] This transaction does not fit into the block. " +
//                    "Remaining space in the block: " + (distributedLedger.getConsensusStrategy().getBlockSize() - nextBlock.size()));
//        }
//    }
//
//    /**
//     * Called once a block transfer completes and arrives here.
//     */
//    public void receiveBlock(Block block) {
//        if (!localLedger.getBlocks().contains(block)) {
//            System.out.println("[BlockValidator] " + this.getName() + " -> received NEW block. Verifying...");
//            verifyBlock(block);
////            if(valid) //TODO
//
//        } else {
//            System.out.println("[BlockValidator] " + this.getName() + " -> block already known, ignoring.");
//        }
//    }
//
//
//    /**
//     * Verifies the signatures of the transactions inside the new received block and checks if the nonce is valid
//     *
//     * @param receivedBlock the received block via propagation
//     */
//    private void verifyBlock(Block receivedBlock) {
//        double verifySign = distributedLedger.getConsensusStrategy().getCryptoStrategy().verify();
//        double verifyNonce = distributedLedger.getConsensusStrategy().getDigestStrategy().hash(68L); //input string(~64 byte) + nonce(~4byte)
//        double verifyMerkleRoot = Utils.merkleRoot(receivedBlock, distributedLedger.getConsensusStrategy().getDigestStrategy());
//        int numberOfTransactions = receivedBlock.getTransactions().size();
//        double instrSum = verifySign * numberOfTransactions + verifyNonce + verifyMerkleRoot;
//        try {
//            this.localVm.newComputeTask(instrSum, ResourceConsumption.unlimitedProcessing, new ConsumptionEventAdapter() {
//                @Override
//                public void conComplete() { //TODO: ask Andris how is this working
//                    System.out.println("[BlockValidator] received block is verified, at time: " + Timed.getFireCount());
//                    //todo: save to a local ledger and propagate to others too
//                    if (true) {  // now blocks are always valid, could be sometimes invalid too
//                        localLedger.addBlock(receivedBlock);
//                        distributedLedger.addValidation(receivedBlock, BlockValidator.this);
//                        BlockValidator.this.propagateBlock(receivedBlock); //continue the propagation of the received block to the neighbours
//                    }
//                }
//            });
//        } catch (NetworkNode.NetworkException e) {
//            throw new RuntimeException(e);
//        }
//    }
//
//    public void receiveTransaction(TransactionMessage transactionMessage) {
//        Transaction tx = transactionMessage.getTransaction();
//        this.getLocalRepo().registerObject(transactionMessage);
//        this.validateTransaction(tx);
////        if(valid){ //TODO
//        propagateTransaction(transactionMessage);
////        } else {
////            this.localMachine.localDisk.deregisterObject(transactionMessage);
////        }
//    }
//
//    public void registerTransaction(Transaction tx) {
//        this.validateTransaction(tx);
////        if(valid){ //TODO
//        TransactionMessage message = new TransactionMessage(tx);
//        propagateTransaction(message);
//    }
//
//    private void propagateTransaction(TransactionMessage transactionMessage) {
//        Transaction tx = transactionMessage.getTransaction();
//        for (ComputingAppliance ca : this.computingAppliance.neighbors) {
//            BlockValidator neighbor = validators.get(ca);
//            if (!neighbor.isTxKnown(tx)) {
//                TransactionTransferEvent event = new TransactionTransferEvent(transactionMessage, this, neighbor);
//                try {
//                    NetworkNode.initTransfer(
//                            transactionMessage.size,
//                            ResourceConsumption.unlimitedProcessing,
//                            this.getLocalRepo(),            // from me TODO: this.computingAppliance.iaas.repositories.get(0)
//                            neighbor.getLocalRepo(),       // to neighbor
//                            event
//                    );
//                } catch (NetworkNode.NetworkException e) {
//                    System.err.println("[BlockValidator] " + this.getName()
//                            + " -> Could not send block to " + neighbor.getName() + ": " + e.getMessage());
//                }
//            }
//        }
//    }
//
//}
