package hu.u_szeged.inf.fog.simulator.util.result;

/**
 * Helper class for saving the results of a simulation to the database used 
 * by DISSECT-CF-Fog-WebApp and the executor module.
 */
@SuppressWarnings("unused")
public class DataVolume {

    private long generatedDataInBytes;
    private long processedDataInBytes;
    private long arrivedDataInBytes;
    
    /**
     * Constructs an object with the specified amount of data belonging to various states.
     *
     * @param generatedDataInBytes the volume of data generated in bytes
     * @param processedDataInBytes the volume of data processed in bytes
     * @param arrivedDataInBytes   the volume of data arrived in bytes
     */
    public DataVolume(long generatedDataInBytes, long processedDataInBytes, long arrivedDataInBytes) {
        this.generatedDataInBytes = generatedDataInBytes;
        this.processedDataInBytes = processedDataInBytes;
        this.arrivedDataInBytes = arrivedDataInBytes;
    }
}