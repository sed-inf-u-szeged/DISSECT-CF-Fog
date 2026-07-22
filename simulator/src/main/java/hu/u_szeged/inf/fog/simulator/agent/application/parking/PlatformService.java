package hu.u_szeged.inf.fog.simulator.agent.application.parking;

import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;

public class PlatformService {

    public final Repository nbiotRepository;
    public final Repository bleRepository;

    public long receivedDataSize;
    public long receivedEventCount;

    public PlatformService(Repository nbiotRepository, Repository bleRepository) {
        this.nbiotRepository = nbiotRepository;
        this.bleRepository = bleRepository;
    }
}