package hu.u_szeged.inf.fog.simulator.agent.application.parking;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ConsumptionEventAdapter;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;
import hu.mta.sztaki.lpds.cloud.simulator.io.StorageObject;
import hu.mta.sztaki.lpds.cloud.simulator.util.SeedSyncer;
import hu.u_szeged.inf.fog.simulator.common.util.ScenarioBase;
import hu.u_szeged.inf.fog.simulator.common.util.SimLogger;

import java.util.ArrayList;

public class ParkingSensor extends Timed {

    public static ArrayList<ParkingSensor> allParkingSensors = new ArrayList<>();

    public static int totalParkingEvents = 0;

    public enum ParkingMode {
        NBIOT_PUSH(15),

        BLE_POLL(1);

        final int batteryCost;

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

    public enum ParkingZone {

        COMMERCIAL,
        LOADING,
        RESIDENTIAL
    }

    public enum TimePeriod {

        NIGHT(0, 6),
        MORNING_PEAK(6, 9),
        DAYTIME(9, 16),
        EVENING_PEAK(16, 19),
        LATE_EVENING(19, 24);

        private final int startHour;
        private final int endHour;

        TimePeriod(int startHour, int endHour) {
            this.startHour = startHour;
            this.endHour = endHour;
        }

        public int getStartHour() {
            return startHour;
        }

        public int getEndHour() {
            return endHour;
        }
    }

    public String id;
    ParkingMode mode;
    private ParkingProfile profile;
    private final ParkingZone zone;

    public final Repository nbiotRepository;
    public final Repository bleRepository;

    public boolean isTaken = false;
    public int eventCount = 0;

    private final PlatformService platformService;

    public int batteryLevel;

    public long stopTime;

    private double eventRate;

    public ParkingSensor(String id, PlatformService platformService, Repository nbiotRepository, Repository bleRepository, int batteryLevel,
                         ParkingMode mode, ParkingProfile profile, ParkingZone zone) {
        this.id = id;
        this.nbiotRepository = nbiotRepository;
        this.bleRepository = bleRepository;
        this.mode = mode;
        this.batteryLevel = batteryLevel;
        this.profile = profile;
        this.zone = zone;
        this.platformService = platformService;
        this.stopTime = 0;
        subscribe(sampleNextEventDelay(profile.meanInterval));
        allParkingSensors.add(this);
        //this.batteryLevel = Config.PARKING_CONFIGURATION.get("batteryCapacity") instanceof Long capacity ? capacity : 0L; TODO:!!
    }

    @Override
    public void tick(long fires) {
        this.isTaken = !this.isTaken;
        eventCount++;
        totalParkingEvents++;

        StorageObject so = new StorageObject(id + "-" + fires, 50, false);
        PlatformService.networkTimePerFile.put(so.id, Timed.getFireCount());
        if (this.mode == ParkingMode.NBIOT_PUSH) {
            nbiotRepository.registerObject(so);
            this.batteryLevel = Math.max(0, this.batteryLevel - ParkingMode.NBIOT_PUSH.batteryCost);
            try {
                nbiotRepository.requestContentDelivery(so.id, platformService.platformRepo, new ConsumptionEventAdapter() {

                    @Override
                    public void conComplete() {
                        nbiotRepository.deregisterObject(so.id);
                        platformService.platformRepo.deregisterObject(so.id);
                        platformService.receivedDataSize += so.size;
                        platformService.receivedEventCount++;

                        long latency =  Timed.getFireCount() - PlatformService.networkTimePerFile.remove(so.id);
                        platformService.latencies.add(latency);
                        PlatformService.totalEndToEndLatency += latency;

                        SimLogger.logRun("File received in " + (Timed.getFireCount() - fires) + " ms. from " + id + " with NBIoT mode at "
                                + Timed.getFireCount() / ScenarioBase.MINUTE_IN_MILLISECONDS + " min.");
                    }
                });
            } catch (NetworkNode.NetworkException e) {
                throw new RuntimeException(e);
            }
        } else {
            bleRepository.registerObject(so);
        }

        updateFrequency(sampleNextEventDelay(this.profile.meanInterval));

        if (this.batteryLevel <= 0) {
            unsubscribe();
            this.stopTime = Timed.getFireCount();
            SimLogger.logRun("Sensor stopped at " + this.stopTime / ScenarioBase.MINUTE_IN_MILLISECONDS + " min.");
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

    public static TimePeriod resolveTimePeriod(long simulationTimeMs) {

        long timeOfDay = simulationTimeMs % (24 * ScenarioBase.HOUR_IN_MILLISECONDS);

        if (timeOfDay < 6 * ScenarioBase.HOUR_IN_MILLISECONDS) {
            return TimePeriod.NIGHT;
        }

        if (timeOfDay < 9 * ScenarioBase.HOUR_IN_MILLISECONDS) {
            return TimePeriod.MORNING_PEAK;
        }

        if (timeOfDay < 16 * ScenarioBase.HOUR_IN_MILLISECONDS) {
            return TimePeriod.DAYTIME;
        }

        if (timeOfDay < 19 * ScenarioBase.HOUR_IN_MILLISECONDS) {
            return TimePeriod.EVENING_PEAK;
        }

        return TimePeriod.LATE_EVENING;
    }
}