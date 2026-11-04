package hu.u_szeged.inf.fog.simulator.so_smart;

import hu.u_szeged.inf.fog.simulator.distributed_ledger.Miner;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.find_node_strategy.FindNodeStrategy;
import hu.u_szeged.inf.fog.simulator.node.ComputingAppliance;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Selects the blockchain miner associated with the
 * Fog node currently serving a traveler.
 */
public final class FogAwareNodeStrategy implements FindNodeStrategy {

    private final Supplier<ComputingAppliance> servingFogSupplier;

    /**
     * Creates a Fog-aware miner selection strategy.
     *
     * @param servingFogSupplier supplies the currently assigned Fog node
     */
    public FogAwareNodeStrategy(
            Supplier<ComputingAppliance> servingFogSupplier) {

        this.servingFogSupplier = Objects.requireNonNull(
                servingFogSupplier, "Fog node supplier is required.");
    }

    /**
     * Finds the miner running on the currently assigned Fog node.
     *
     * @return miner associated with the serving Fog node
     */
    @Override
    public Miner findNode() {
        ComputingAppliance servingFog = servingFogSupplier.get();

        if (servingFog == null) {
            throw new IllegalStateException(
                    "Traveler has no assigned Fog node.");
        }

        Miner miner = Miner.miners.get(servingFog);

        if (miner == null) {
            throw new IllegalStateException(
                    "No blockchain miner registered for Fog node: "
                            + servingFog.name);
        }

        return miner;
    }
}
