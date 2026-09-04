package hu.u_szeged.inf.fog.simulator.fl.cosim.trackb;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;

/**
 * Track-B per-round request written by the simulator (§8.4 Track B). The
 * simulator's peer sets are authoritative and passed here, so the worker simply
 * executes the round it is told to — both sides are trivially consistent.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class RoundRequest {

    public int round;
    public String mode = "gossip";
    public List<Integer> participants;
    /** node id (as string) -> its selected peer ids. */
    public Map<String, List<Integer>> peerSets;
    public String mergeRule;
    public double gamma;
    public String configHash;
    public double timeoutS;

    public RoundRequest() {
    }
}
