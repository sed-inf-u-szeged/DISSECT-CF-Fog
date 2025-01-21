package hu.u_szeged.inf.fog.simulator.iot.distributed_ledger;

import java.util.ArrayList;
import java.util.List;

public class Block {
    private List<Transaction> transactions;
    private long size;

    boolean isNonceFound = false;

    private boolean technicallyFull = false;

    final DistributedLedger distributedLedger;

    public Block(DistributedLedger distributedLedger) {
        this.distributedLedger = distributedLedger;

        this.transactions = new ArrayList<>();
        this.size = 0;
    }

    public List<Transaction> getTransactions() {
        return transactions;
    }

    public boolean isFull(){
        return this.technicallyFull;
    }

    public long size() {
        return size;
    }

    public List<Transaction> add(Transaction tx) throws ArithmeticException{
        if (tx.getSize() + size() > distributedLedger.getBlockSize())
            throw new ArithmeticException("Block cannot be greater than the max size defined in the consensus");
        this.transactions.add(tx);
        this.size += tx.getSize();

        if(distributedLedger.getBlockSize() - size() < 50){
            this.technicallyFull = true; // when there is very little space left in the block, it should not try to find a transaction to fill this space
                                    // if the left space in the block is less than 50, it is technically full
                                    // the transaction size can be dynamic, so need a good magic number, for now 50 will be good
        }

        return getTransactions();
    }
}
