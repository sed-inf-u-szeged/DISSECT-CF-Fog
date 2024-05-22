package hu.u_szeged.inf.fog.simulator.util.result;

public class DataVolume {

    private long generatedDataInBytes;
    private long processedDataInBytes;
    private long arrivedDataInBytes;
    
    public DataVolume(long generatedDataInBytes, long processedDataInBytes, long arrivedDataInBytes) {
        this.generatedDataInBytes = generatedDataInBytes;
        this.processedDataInBytes = processedDataInBytes;
        this.arrivedDataInBytes = arrivedDataInBytes;
    }
}
