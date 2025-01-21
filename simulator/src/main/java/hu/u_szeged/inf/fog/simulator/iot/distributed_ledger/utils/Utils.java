package hu.u_szeged.inf.fog.simulator.iot.distributed_ledger.utils;

import hu.u_szeged.inf.fog.simulator.iot.distributed_ledger.DistributedLedger;

public class Utils {

    private Utils() {}

    public static long instructionsTransactionValidation(long transactionSize, DistributedLedger distributedLedger ){
        //need a formula to calculate the number of instructions needed to validate a transaction
        // sign_perf.sh + verify_perf.sh
        //
        //
        return 100;
    }

    public static long instructionsPoW(DistributedLedger distributedLedger){
//        double hash = distributedLedger.digestStrategy.hash();
        int difficulty = distributedLedger.getDifficulty();
        //distributedLedger.getDifficulty();
        //need a formula to calculate the number of instructions needed to find the nonce
        // pow.sh
        //difficulties = 1, 2, 3, 4, 5
        //instructions = 713568461, 1403602749, 82976873130, 762756916980, 11262957628173

        return 2;
    }

    public static int merkleRoot(int numberOfLeaves){
        return 2 * numberOfLeaves - 1;
    }

}
