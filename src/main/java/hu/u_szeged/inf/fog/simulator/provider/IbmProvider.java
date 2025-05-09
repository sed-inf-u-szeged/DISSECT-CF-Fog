package hu.u_szeged.inf.fog.simulator.provider;

import hu.u_szeged.inf.fog.simulator.application.Application;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * The class represents a service provider mimicking IBM Cloud.
 * It calculates the cost based on data tiers and the size
 * of the processed data.
 */
public class IbmProvider extends Provider {

    /**
     * Represents a data tier with corresponding size range and its cost.
     */
    public static class DataTier {
        
        /**
         * The lower bound of the size range (in MBs).
         */
        double from;
        
        /**
         * The upper bound of the size range (in MBs).
         */
        double to;      
        
        /**
         * The cost associated with the data tier.
         */
        double cost;

        /**
         * Constructs an data interval (tier) with the specified range and cost.
         *
         * @param from the lower bound of the size range
         * @param to   the upper bound of the size range
         * @param cost the cost associated with the data tier
         */
        public DataTier(double from, double to, double cost) {
            this.to = to;
            this.from = from;
            this.cost = cost;
        }
    }

    /**
     * The list of data tiers for IBM Cloud.
     */
    static ArrayList<DataTier> dataTiers = new ArrayList<>();

    /**
     * The default list of data tiers for IBM Cloud.
     */
    static final ArrayList<DataTier> defaultDataTiers = new ArrayList<>(
            Arrays.asList(new DataTier(0, 449_999, 0.001), 
                          new DataTier(450_000, 6_999_999, 0.0007),
                          new DataTier(7_000_000, Double.MAX_VALUE, 0.00014)));

    /**
     * Constructs a provider with default data tiers.
     */
    public IbmProvider() {
        this.name = "IBM";
        Provider.allProviders.add(this);
    }

    /**
     * Constructs a provider with the specified data tiers.
     *
     * @param dataTier the list of data tiers
     */
    public IbmProvider(ArrayList<DataTier> dataTier) {
        this.name = "IBM";
        IbmProvider.dataTiers = dataTier;
        Provider.allProviders.add(this);
    }

    /**
     * Calculates and returns the cost based on data tiers and total processed size.
     */
    @Override
    public double calculate() {

        double totalprocessedSizeInMb = (double) Application.totalProcessedSize / 1048576; // 1 MB
        double cost = 0.0;

        ArrayList<DataTier> intervals = dataTiers.isEmpty() ? defaultDataTiers
                : IbmProvider.dataTiers;

        for (DataTier dataTier : intervals) {
            if (totalprocessedSizeInMb <= dataTier.to && totalprocessedSizeInMb >= dataTier.from) {
                cost = dataTier.cost;
            }
        }

        this.cost = totalprocessedSizeInMb * cost;
        return this.cost;
    }
}