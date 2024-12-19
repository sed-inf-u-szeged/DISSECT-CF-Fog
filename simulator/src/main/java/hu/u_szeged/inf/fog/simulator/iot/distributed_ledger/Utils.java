package hu.u_szeged.inf.fog.simulator.iot.distributed_ledger;

import hu.u_szeged.inf.fog.simulator.iot.distributed_ledger.crypto_strategy.CryptoStrategy;
import hu.u_szeged.inf.fog.simulator.iot.distributed_ledger.digest_strategy.DigestStrategy;

public class Utils {

    private Utils() {}

    public static long instructionsForTransactionValidation(long transactionSize, CryptoStrategy cryptoStrategy, DigestStrategy digestStrategy){
        //need a formula to calculate the number of instructions needed to validate a transaction
        // sign_perf.sh
        //
        //
        return 10;
    }

    public static long instructionsForPOW(DistributedLedger distributedLedger){
        //distributedLedger.digestStrategy;
        //distributedLedger.getDifficulty();
        //need a formula to calculate the number of instructions needed to find the nonce
        // pow.sh
        //difficulties = 1, 2, 3, 4, 5
        //instructions = 713568461, 1403602749, 82976873130, 762756916980, 11262957628173

        return 1000;
    }
}
