package hu.u_szeged.inf.fog.simulator.test.distributed_ledger.validator;

import hu.mta.sztaki.lpds.cloud.simulator.util.SeedSyncer;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.Miner;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.consensus_strategy.PoWConsensusStrategy;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.crypto_strategy.RSAStrategy;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.digest_strategy.SHA256Strategy;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.transaction_selection_strategy.RandomStrategy;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.validation_strategy.RandomizedValidation;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.validator.selection.StakeWeightedSelectionStrategy;
import hu.u_szeged.inf.fog.simulator.iot.mobility.GeoLocation;
import hu.u_szeged.inf.fog.simulator.node.ComputingAppliance;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class StakeWeightedSelectionStrategyTest {

    private Miner createMiner(String name) throws Exception {
        URL resource = getClass().getClassLoader()
                .getResource("demo/LPDS_original.xml");

        if (resource == null) {
            throw new IllegalStateException("LPDS_original.xml test resource not found");
        }

        String cloudfile = Paths.get(resource.toURI()).toString();

        ComputingAppliance ca = new ComputingAppliance(
                cloudfile,
                name,
                new GeoLocation(47.6, 17.9),
                50
        );

        PoWConsensusStrategy consensus = new PoWConsensusStrategy(
                5,
                100,
                25_000,
                1000,
                new RSAStrategy(4096),
                new SHA256Strategy()
        );

        return new Miner(
                consensus,
                new RandomStrategy(),
                ca,
                new RandomizedValidation(1.0, 1.0, SeedSyncer.centralRnd)
        );
    }

    @Test
    void shouldSelectValidatorsBasedOnStake() throws Exception {
        Miner miner0 = createMiner("validator-0");
        Miner miner1 = createMiner("validator-1");
        Miner miner2 = createMiner("validator-2");

        Map<Miner, Long> validators = new LinkedHashMap<>();
        validators.put(miner0, 10L);
        validators.put(miner1, 30L);
        validators.put(miner2, 60L);

        StakeWeightedSelectionStrategy strategy =
                new StakeWeightedSelectionStrategy(42L);

        int miner0Selections = 0;
        int miner1Selections = 0;
        int miner2Selections = 0;

        int numberOfSlots = 10_000;

        for (long slot = 0; slot < numberOfSlots; slot++) {
            Miner selected = strategy.selectValidator(validators, slot);

            if (selected == miner0) {
                miner0Selections++;
            } else if (selected == miner1) {
                miner1Selections++;
            } else if (selected == miner2) {
                miner2Selections++;
            }
        }

        assertEquals(0.10, miner0Selections / (double) numberOfSlots, 0.03);
        assertEquals(0.30, miner1Selections / (double) numberOfSlots, 0.03);
        assertEquals(0.60, miner2Selections / (double) numberOfSlots, 0.03);
    }
}
