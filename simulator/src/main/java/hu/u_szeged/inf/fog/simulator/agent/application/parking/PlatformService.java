package hu.u_szeged.inf.fog.simulator.agent.application.parking;

import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PlatformService {

    public static Map<String, Long> networkTimePerFile = new HashMap<>();

    public static long totalEndToEndLatency;

    public List<Long> latencies = new ArrayList<>();

    public final Repository platformRepo;

    public long receivedDataSize;
    public long receivedEventCount;

    public PlatformService(Repository platformRepo) {
        this.platformRepo = platformRepo;
    }
}