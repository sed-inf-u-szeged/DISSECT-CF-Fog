package hu.u_szeged.inf.fog.simulator.result;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class DataVolume {

    private long generatedDataInBytes;
    private long processedDataInBytes;
    private long arrivedDataInBytes;
}
