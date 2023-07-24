package hu.u_szeged.inf.fog.simulator.executor.model.result;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class Cost {

    private double totalCostInEuro;
    private String IBM;
    private String AWS;
    private String azure;
}
