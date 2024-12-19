package hu.u_szeged.inf.fog.simulator.iot.distributed_ledger;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ConsumptionEventAdapter;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ResourceConsumption;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode;

import java.util.ArrayList;
import java.util.List;

public class BlockValidator extends Timed {

    static List<BlockValidator> validators = new ArrayList<>();
    private final VirtualMachine machine;
    private final Mempool mempool;

    private DistributedLedger distributedLedger;
    private boolean runningValidation = false;

    List<Transaction> transactionsValidating;

    public BlockValidator(VirtualMachine machine, Mempool mempool, int checkMempoolFreq) {
        validators.add(this);
        this.machine = machine;
        this.mempool = mempool;
        this.transactionsValidating = new ArrayList<>();
        subscribe(checkMempoolFreq);
    }

    @Override
    public void tick(long fires) {
        if (!runningValidation && !mempool.isEmpty()) {
            Transaction tx = mempool.fetchTransaction(); //right now it fetches only 1 transaction, it needs some logic, how should it choose a transaction (maybe random?)
            //If there are enough transactions validated for a block, it should start to find the nonce for the puzzle
            //If the nonce is found and the block is done, the transactions in the mempool should be removed.
            if (tx != null) {
                validateTransaction(tx);
            }
        }
    }

//    private void checkBlockCompleteness(){
//        if(transactionsReady() && nounceIsFound())
//            propagateBlock();
//    }


    private void validateTransaction(Transaction tx) {
        runningValidation = true;
        this.transactionsValidating.add(tx);
        System.out.println("[BlockValidator] validation started for transaction: " + tx);
        try {
            machine.newComputeTask(Utils.instructionsForTransactionValidation(tx.getSize(), distributedLedger.cryptoStrategy, distributedLedger.digestStrategy),
                    ResourceConsumption.unlimitedProcessing, new ConsumptionEventAdapter() {
                @Override
                public void conComplete() {
                    System.out.println("[BlockValidator] validated transaction: " + tx + ", at time: " + Timed.getFireCount()); // Why does it not reach this point?
                    //ResourceConsumption is null -> Could return null if the consumption cannot be registered or when there is no resource for the VM. Where is the problem?
                }
            });
        } catch (NetworkNode.NetworkException networkException) {
            System.err.println(networkException);
        }
    }

//    private String calculateHeaderInputData(){
//        //need the list of validated transactions and hash them -> Merkle root
//        // ==> multiple hashes needed
//        try {
//            machine.newComputeTask(numberOfTransactions*instructionsPerHash, ResourceConsumption.unlimitedProcessing, new ConsumptionEventAdapter() {
//                @Override
//                public void conComplete() {
//                    System.out.println("[BlockValidator] found a nonce, at time: " + Timed.getFireCount());
//                }
//            });
//        } catch (NetworkNode.NetworkException networkException) {
//            System.err.println(networkException);
//        }
//    }

    private void findNonce(String headerInputData){
        //headerInputData: <Version><Prev Block Hash><Merkle Root><Timestamp><Difficulty><Nonce>
        try {
            machine.newComputeTask(Utils.instructionsForPOW(distributedLedger), ResourceConsumption.unlimitedProcessing, new ConsumptionEventAdapter() {
                @Override
                public void conComplete() {
                    System.out.println("[BlockValidator] found a nonce, at time: " + Timed.getFireCount());
                }
            });
        } catch (NetworkNode.NetworkException networkException) {
            System.err.println(networkException);
        }
    }

    private void propagateBlock(Transaction tx) {
        System.out.println("[BlockValidator] Propagating validated block for transaction: " + tx.getId());
    }


}
