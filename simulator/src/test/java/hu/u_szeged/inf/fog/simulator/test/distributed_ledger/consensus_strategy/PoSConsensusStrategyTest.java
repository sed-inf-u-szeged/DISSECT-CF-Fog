package hu.u_szeged.inf.fog.simulator.test.distributed_ledger.consensus_strategy;

import hu.mta.sztaki.lpds.cloud.simulator.util.SeedSyncer;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.Block;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.Miner;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.consensus_strategy.ConsensusStrategy;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.consensus_strategy.PoSConsensusStrategy;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.crypto_strategy.RSAStrategy;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.digest_strategy.SHA256Strategy;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.transaction_selection_strategy.RandomStrategy;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.validation_strategy.RandomizedValidation;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.validator.ValidatorRegistry;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.validator.selection.StakeWeightedSelectionStrategy;
import hu.u_szeged.inf.fog.simulator.iot.mobility.GeoLocation;
import hu.u_szeged.inf.fog.simulator.node.ComputingAppliance;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;


public class PoSConsensusStrategyTest {
    private Miner createMiner(String name, ConsensusStrategy consensus) throws Exception {
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

        return new Miner(
                consensus,
                new RandomStrategy(),
                ca,
                new RandomizedValidation(1.0, 1.0, SeedSyncer.centralRnd)
        );
    }

    @Test
    void onlySelectedValidatorShouldBeAllowedToCreateBlock() throws Exception {
        ValidatorRegistry registry = new ValidatorRegistry();
        StakeWeightedSelectionStrategy selectionStrategy =
                new StakeWeightedSelectionStrategy(42L);

        PoSConsensusStrategy consensus = new PoSConsensusStrategy(
                25_000L,
                1000,
                new RSAStrategy(4096),
                new SHA256Strategy(),
                registry,
                selectionStrategy
        );

        Miner miner0 = createMiner("validator-0", consensus);
        Miner miner1 = createMiner("validator-1", consensus);
        Miner miner2 = createMiner("validator-2", consensus);

        registry.register(miner0, 10L);
        registry.register(miner1, 30L);
        registry.register(miner2, 60L);

        long currentSlot = consensus.getCurrentSlot();

        Miner selected = selectionStrategy.selectValidator(
                registry.getValidators(),
                currentSlot
        );

        assertTrue(consensus.canCreateBlock(selected));

        if (selected != miner0) {
            assertFalse(consensus.canCreateBlock(miner0));
        }

        if (selected != miner1) {
            assertFalse(consensus.canCreateBlock(miner1));
        }

        if (selected != miner2) {
            assertFalse(consensus.canCreateBlock(miner2));
        }
    }

    @Test
    void createdBlockShouldContainSelectedProposerAndCurrentSlot() throws Exception {
        ValidatorRegistry registry = new ValidatorRegistry();
        StakeWeightedSelectionStrategy selectionStrategy =
                new StakeWeightedSelectionStrategy(42L);

        PoSConsensusStrategy consensus = new PoSConsensusStrategy(
                25_000L,
                1000,
                new RSAStrategy(4096),
                new SHA256Strategy(),
                registry,
                selectionStrategy
        );

        Miner miner0 = createMiner("validator-0", consensus);
        Miner miner1 = createMiner("validator-1", consensus);
        Miner miner2 = createMiner("validator-2", consensus);

        registry.register(miner0, 10L);
        registry.register(miner1, 30L);
        registry.register(miner2, 60L);

        long currentSlot = consensus.getCurrentSlot();

        Miner selected = selectionStrategy.selectValidator(
                registry.getValidators(),
                currentSlot
        );

        Block block = consensus.createBlock(selected);

        assertEquals(selected.getName(), block.getProposerId());
        assertEquals(currentSlot, block.getSlot());
    }

    @Test
    void shouldValidateBlockBasedOnSelectedProposer() throws Exception {
        ValidatorRegistry registry = new ValidatorRegistry();
        StakeWeightedSelectionStrategy selectionStrategy =
                new StakeWeightedSelectionStrategy(42L);

        PoSConsensusStrategy consensus = new PoSConsensusStrategy(
                25_000L,
                1000,
                new RSAStrategy(4096),
                new SHA256Strategy(),
                registry,
                selectionStrategy
        );

        Miner miner0 = createMiner("validator-0", consensus);
        Miner miner1 = createMiner("validator-1", consensus);
        Miner miner2 = createMiner("validator-2", consensus);

        registry.register(miner0, 10L);
        registry.register(miner1, 30L);
        registry.register(miner2, 60L);

        long currentSlot = consensus.getCurrentSlot();

        Miner selected = selectionStrategy.selectValidator(
                registry.getValidators(),
                currentSlot
        );

        Block block = consensus.createBlock(selected);

        assertTrue(consensus.isConsensusValid(block));

        block.setProposerId("invalid-validator");

        assertFalse(consensus.isConsensusValid(block));
    }
}
