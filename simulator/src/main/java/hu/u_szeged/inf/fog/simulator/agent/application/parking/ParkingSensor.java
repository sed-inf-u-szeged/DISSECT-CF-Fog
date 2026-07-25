package hu.u_szeged.inf.fog.simulator.agent.application.parking;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ConsumptionEventAdapter;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;
import hu.mta.sztaki.lpds.cloud.simulator.io.StorageObject;
import hu.mta.sztaki.lpds.cloud.simulator.util.SeedSyncer;

import java.util.ArrayList;

public class ParkingSensor extends Timed {

    public static ArrayList<ParkingSensor> allParkingSensors = new ArrayList<>();

    public enum ParkingMode {
        NBIOT_PUSH(15),

        BLT_POLL(1);

        private final int batteryCost;

        ParkingMode(int batteryCost) {
            this.batteryCost = batteryCost;
        }
    }

    public enum ParkingProfile {

        NORMAL(86_400_000.0 / 22.0), // ~3 927 273 ms
        BUSY(3_600_000.0 / 12.0);    // 300 000 ms

        private final double meanInterval;

        ParkingProfile(double meanInterval) {
            this.meanInterval = meanInterval;
        }
    }

    public String id;
    private ParkingMode mode;
    private ParkingProfile profile;

    private final Repository nbiotRepository;
    private final Repository bleRepository;

    public boolean isTaken = false;
    public int eventCount = 0;

    private final PlatformService platformService;

    public int batteryLevel;

    private double eventRate;

    public ParkingSensor(String id, PlatformService platformService, Repository nbiotRepository, Repository bleRepository, int batteryLevel,
                         ParkingMode mode, ParkingProfile profile) {
        this.id = id;
        this.nbiotRepository = nbiotRepository;
        this.bleRepository = bleRepository;
        this.mode = mode;
        this.batteryLevel = batteryLevel;
        this.profile = profile;
        this.platformService = platformService;
        subscribe(sampleNextEventDelay(profile.meanInterval));
        allParkingSensors.add(this);
        //this.batteryLevel = Config.PARKING_CONFIGURATION.get("batteryCapacity") instanceof Long capacity ? capacity : 0L; TODO:!!
    }

    @Override
    public void tick(long fires) {
        this.isTaken = !this.isTaken;
        eventCount++;

        StorageObject so = new StorageObject(id + "-" + fires, 50, false);

        if (this.mode == ParkingMode.NBIOT_PUSH) {
            nbiotRepository.registerObject(so);
            try {
                nbiotRepository.requestContentDelivery(so.id, platformService.nbiotRepository, new ConsumptionEventAdapter() {

                    @Override
                    public void conComplete() {
                        nbiotRepository.deregisterObject(so.id);
                        platformService.receivedDataSize += so.size;
                        platformService.receivedEventCount++;
                        batteryLevel -= mode.batteryCost;
                    }
                });
            } catch (NetworkNode.NetworkException e) {
                throw new RuntimeException(e);
            }

            updateFrequency(sampleNextEventDelay(this.profile.meanInterval));
        } else {
            throw new UnsupportedOperationException("BLT_POLL mode is not implemented yet.");
        }
    }

    private long sampleNextEventDelay(double meanIntervalMs) {
        double randomValue = Math.max(1.0 - SeedSyncer.centralRnd.nextDouble(), 1e-12);

        return Math.max(1L, Math.round(-Math.log(randomValue) * meanIntervalMs)
        );
    }

    public void stop(){
        unsubscribe();
    }
}