package hu.u_szeged.inf.fog.simulator.agent.application.parking;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ConsumptionEventAdapter;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;
import hu.mta.sztaki.lpds.cloud.simulator.io.StorageObject;
import hu.u_szeged.inf.fog.simulator.agent.demo.Config;
import hu.u_szeged.inf.fog.simulator.common.util.ScenarioBase;
import hu.u_szeged.inf.fog.simulator.common.util.SimLogger;

import java.util.ArrayList;

public class ParkingGateway extends Timed {

    public static ArrayList<ParkingGateway> allParkingGateways = new ArrayList<>();

    public Repository gatewayRepo;

    PlatformService platformService;

    public ArrayList<ParkingSensor> observedSensors;

    public String id;

    public ParkingGateway(String id, Repository gatewayRepo, PlatformService platformService, ArrayList<ParkingSensor> observedSensors){
        this.id = id;
        this.gatewayRepo = gatewayRepo;
        this.platformService = platformService;
        this.observedSensors = observedSensors;
        subscribe((long) Config.PARKING_CONFIGURATION.get("gatewayFreq"));
        allParkingGateways.add(this);
    }

    public void addSensor(ParkingSensor parkingSensor){
        observedSensors.add(parkingSensor);
    }

    @Override
    public void tick(long fires) {
        for (ParkingSensor parkingSensor : this.observedSensors) {
            if (parkingSensor.mode != ParkingSensor.ParkingMode.BLE_POLL || parkingSensor.batteryLevel <= 0) {
                continue;
            }

            parkingSensor.batteryLevel = Math.max(0, parkingSensor.batteryLevel - ParkingSensor.ParkingMode.BLE_POLL.batteryCost);
            if (parkingSensor.batteryLevel <= 0) {
                parkingSensor.stop();
                parkingSensor.stopTime = Timed.getFireCount();
                SimLogger.logRun("Sensor stopped at " + parkingSensor.stopTime / ScenarioBase.MINUTE_IN_MILLISECONDS + " min.");
            }

            for (StorageObject so : parkingSensor.bleRepository.contents()){
                try {
                    parkingSensor.bleRepository.requestContentDelivery(so.id, this.gatewayRepo,  new ConsumptionEventAdapter() {

                        @Override
                        public void conComplete() {
                            parkingSensor.bleRepository.deregisterObject(so.id);
                            try {
                                gatewayRepo.requestContentDelivery(so.id, platformService.platformRepo, new ConsumptionEventAdapter() {
                                    @Override
                                    public void conComplete() {
                                        gatewayRepo.deregisterObject(so.id);
                                        platformService.platformRepo.deregisterObject(so.id);
                                        platformService.receivedDataSize += so.size;
                                        platformService.receivedEventCount++;

                                        long latency =  Timed.getFireCount() - PlatformService.networkTimePerFile.remove(so.id);
                                        platformService.latencies.add(latency);
                                        PlatformService.totalEndToEndLatency += latency;

                                        SimLogger.logRun("File received in " + (Timed.getFireCount() - fires) + " ms. from " + id + " with BLE mode at " +
                                                Timed.getFireCount() / ScenarioBase.MINUTE_IN_MILLISECONDS + " min.");
                                    }
                                });

                            } catch (NetworkNode.NetworkException e) {
                                throw new RuntimeException(e);
                            }

                        }
                    });
                } catch (NetworkNode.NetworkException e) {
                    throw new RuntimeException(e);
                }
            }
        }

    }
}
