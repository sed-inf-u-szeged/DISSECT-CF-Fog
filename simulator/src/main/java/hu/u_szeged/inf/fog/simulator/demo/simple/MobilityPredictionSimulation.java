package hu.u_szeged.inf.fog.simulator.demo.simple;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.energy.powermodelling.PowerState;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.AlterableResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ResourceConsumption;
import hu.u_szeged.inf.fog.simulator.application.AppVm;
import hu.u_szeged.inf.fog.simulator.application.MobilityTaskMetrics;
import hu.u_szeged.inf.fog.simulator.iot.MobilityDeviceMetrics;
import hu.mta.sztaki.lpds.cloud.simulator.io.VirtualAppliance;
import hu.mta.sztaki.lpds.cloud.simulator.util.PowerTransitionGenerator;
import hu.mta.sztaki.lpds.cloud.simulator.util.SeedSyncer;
import hu.u_szeged.inf.fog.simulator.application.Application;
import hu.u_szeged.inf.fog.simulator.application.strategy.RuntimeAwareApplicationStrategy;
import hu.u_szeged.inf.fog.simulator.demo.ScenarioBase;
import hu.u_szeged.inf.fog.simulator.iot.SmartDevice;
import hu.u_szeged.inf.fog.simulator.iot.mobility.GeoLocation;
import hu.u_szeged.inf.fog.simulator.iot.mobility.MobilityEvent;
import hu.u_szeged.inf.fog.simulator.iot.mobility.NomadicMobilityStrategy;
import hu.u_szeged.inf.fog.simulator.iot.strategy.DeviceStrategy;
import hu.u_szeged.inf.fog.simulator.node.ComputingAppliance;
import hu.u_szeged.inf.fog.simulator.provider.Instance;
import hu.u_szeged.inf.fog.simulator.util.SimLogger;
import hu.u_szeged.inf.fog.simulator.prediction.communication.launchers.MobilityUiLauncher;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/** Runs actual device movement, application connections and shadow coverage forecasts. */
public class MobilityPredictionSimulation {
    private static final long STEP = 60_000;
    private static final long START = 2 * STEP;
    private static final long STOP = START + 120 * STEP;
    private static final long LIMIT = 240 * STEP;

    /** Keep a usable connection until it leaves coverage, then choose a ready application. */
    private static final class StableConnection extends DeviceStrategy {
        @Override
        public void findApplication() {
            ArrayList<Application> available = getAvailableApplications();
            available.removeIf(app -> app.computingAppliance.broker.vm.getState()
                    != VirtualMachine.State.RUNNING);
            chosenApplication = available.contains(device.application) ? device.application
                    : available.stream().findFirst().orElse(null);
            MobilityEvent.refresh(device, chosenApplication);
        }
    }

    /** Scenario-local instrumentation of the existing offload transfer operation. */
    private static final class TransferLedger {
        final Map<Application, Long> sent = new HashMap<>();
        final Map<Application, Long> received = new HashMap<>();
        final Map<Application, Long> previousUploads = new HashMap<>();
        final BufferedWriter events;
        long nextId;
        long eventSequence;
        long inFlightBytes;

        TransferLedger(BufferedWriter events) { this.events = events; }

        long get(Map<Application, Long> values, Application app) {
            return values.getOrDefault(app, 0L);
        }

        void event(long id, String state, Application from, Application to, long bytes) {
            write(events, id + "," + Timed.getFireCount() + "," + state + ","
                    + from.computingAppliance.name + "," + to.computingAppliance.name + "," + bytes
                    + "," + (++eventSequence));
        }

        void snapshot(BufferedWriter writer, ObservedDevice device, String kind) {
            long now = Timed.getFireCount();
            long totalUploads = 0;
            long totalAccounted = inFlightBytes;
            for (Application app : Application.allApplications) {
                long outgoing = get(sent, app);
                long incoming = get(received, app);
                // The only other receivedData writer in this scenario is DeviceDataEvent.
                long uploads = app.receivedData + outgoing - incoming;
                long waiting = app.receivedData - app.processedData;
                long running = 0;
                long completed = 0;
                for (AppVm vm : app.utilisedVms) {
                    if (MobilityTaskMetrics.isRunning(vm)) { running++; }
                    completed += vm.taskCounter;
                }
                if (uploads < get(previousUploads, app) || waiting < 0) {
                    throw new IllegalStateException("Invalid application accounting");
                }
                previousUploads.put(app, uploads);
                totalUploads += uploads;
                totalAccounted += app.receivedData;
                write(writer, now + "," + kind + "," + app.computingAppliance.name + ","
                        + uploads + "," + outgoing + "," + incoming + "," + app.receivedData + ","
                        + app.processedData + "," + waiting + "," + running + "," + completed + ","
                        + app.incomingData + "," + app.deviceList.size() + "," + device.generatedData
                        + "," + MobilityDeviceMetrics.uploadCompletedBytes(device) + "," + inFlightBytes + "," + eventSequence);
            }
            if (totalUploads != MobilityDeviceMetrics.uploadCompletedBytes(device) || totalAccounted != MobilityDeviceMetrics.uploadCompletedBytes(device)) {
                throw new IllegalStateException("Device/application byte conservation failed");
            }
        }
    }

    private static final class TracedRuntimeStrategy extends RuntimeAwareApplicationStrategy {
        private final TransferLedger ledger;

        TracedRuntimeStrategy(TransferLedger ledger) {
            super(0.9, 2.0);
            this.ledger = ledger;
        }

        @Override
        protected void startDataTranfer(Application destination, long bytes) {
            if (destination == null) { return; }
            final Application source = application;
            final long id = ++ledger.nextId;
            final long start = Timed.getFireCount();
            // Same transfer and accounting order as ApplicationStrategy at 29df879.
            source.receivedData -= bytes;
            destination.incomingData++;
            ledger.sent.merge(source, bytes, Long::sum);
            ledger.inFlightBytes += bytes;
            ledger.event(id, "START", source, destination, bytes);
            try {
                NetworkNode.initTransfer(bytes, ResourceConsumption.unlimitedProcessing,
                        source.computingAppliance.iaas.repositories.get(0),
                        destination.computingAppliance.iaas.repositories.get(0),
                        new ResourceConsumption.ConsumptionEvent() {
                            @Override
                            public void conComplete() {
                                if (!destination.isSubscribed()) { destination.subscribeApplication(); }
                                destination.receivedData += bytes;
                                destination.incomingData--;
                                Application.totalTimeOnNetwork += Timed.getFireCount() - start;
                                Application.totalBytesOnNetwork += bytes;
                                ledger.received.merge(destination, bytes, Long::sum);
                                ledger.inFlightBytes -= bytes;
                                ledger.event(id, "COMPLETE", source, destination, bytes);
                            }

                            @Override
                            public void conCancelled(ResourceConsumption transfer) {
                                ledger.event(id, "CANCELLED", source, destination, bytes);
                                throw new IllegalStateException("Offload transfer cancelled");
                            }
                        });
            } catch (NetworkNode.NetworkException error) {
                throw new IllegalStateException("Offload transfer failed", error);
            }
        }
    }

    private static final class Counts {
        long tp;
        long fn;
        long fp;
        long tn;

        void add(boolean alarm, boolean actual) {
            if (actual) {
                if (alarm) { tp++; } else { fn++; }
            } else {
                if (alarm) { fp++; } else { tn++; }
            }
        }

        String row(String model) {
            return model + "," + (tp + fn + fp + tn) + "," + tp + "," + fn + "," + fp + "," + tn;
        }
    }

    private static final class Forecast {
        final long origin;
        final String node;
        final GeoLocation center;
        final double radius;
        final GeoLocation hold;
        final GeoLocation velocity;

        Forecast(long origin, ComputingAppliance appliance, GeoLocation current, GeoLocation previous) {
            this.origin = origin;
            node = appliance.name;
            center = copy(appliance.geoLocation);
            radius = appliance.range * 1000.0;
            hold = copy(current);
            velocity = new GeoLocation(2 * current.latitude - previous.latitude,
                    2 * current.longitude - previous.longitude);
        }
    }

    private static final class ObservedDevice extends SmartDevice {
        private final BufferedWriter observations;
        private final BufferedWriter predictions;
        private final BufferedWriter evaluations;
        private final BufferedWriter loads;
        private final TransferLedger ledger;
        private final Counts holdCounts = new Counts();
        private final Counts velocityCounts = new Counts();
        private GeoLocation previous;
        private long previousTime;
        private Forecast pending;
        private long issued;
        private long matched;

        ObservedDevice(NomadicMobilityStrategy movement, PhysicalMachine machine,
                BufferedWriter observations, BufferedWriter predictions,
                BufferedWriter evaluations, BufferedWriter loads, TransferLedger ledger) {
            super(START, STOP, 100, STEP, movement, new StableConnection(), machine, 50, false);
            this.observations = observations;
            this.predictions = predictions;
            this.evaluations = evaluations;
            this.loads = loads;
            this.ledger = ledger;
        }

        @Override
        public void tick(long fires) {
            String oldNode = nodeName(application);
            super.tick(fires);
            long now = Timed.getFireCount();
            // SmartDevice has now applied movement and refreshed the actual application connection.
            GeoLocation current = copy(geoLocation);
            String currentNode = nodeName(application);
            write(observations, "0," + now + "," + current.latitude + "," + current.longitude
                    + "," + oldNode + "," + currentNode);
            ledger.snapshot(loads, this, "OBSERVATION");
            if (pending != null) {
                if (now != pending.origin + STEP) {
                    throw new IllegalStateException("Forecast target time mismatch");
                }
                double actualDistance = current.calculateDistance(pending.center);
                boolean actualExit = actualDistance > pending.radius;
                boolean holdExit = pending.hold.calculateDistance(pending.center) > pending.radius;
                boolean velocityExit = pending.velocity.calculateDistance(pending.center) > pending.radius;
                holdCounts.add(holdExit, actualExit);
                velocityCounts.add(velocityExit, actualExit);
                write(evaluations, "0," + pending.origin + "," + now + "," + pending.node + ","
                        + currentNode + "," + current.latitude + "," + current.longitude + ","
                        + actualDistance + "," + actualExit + "," + holdExit + "," + velocityExit);
                matched++;
                pending = null;
            }
            if (previous != null && application != null && now < stopTime && isSubscribed()) {
                if (now - previousTime != STEP) {
                    throw new IllegalStateException("History sampling interval mismatch");
                }
                ComputingAppliance node = application.computingAppliance;
                if (current.calculateDistance(node.geoLocation) > node.range * 1000.0) {
                    throw new IllegalStateException("Connected device outside coverage");
                }
                pending = new Forecast(now, node, current, previous);
                write(predictions, "0," + now + "," + (now + STEP) + "," + pending.node
                        + "," + pending.center.latitude + "," + pending.center.longitude + "," + pending.radius
                        + "," + pending.hold.latitude + "," + pending.hold.longitude
                        + "," + pending.velocity.latitude + "," + pending.velocity.longitude);
                issued++;
            }
            previous = current;
            previousTime = now;
        }
    }

    private static String nodeName(Application application) {
        return application == null ? "NONE" : application.computingAppliance.name;
    }

    private static GeoLocation copy(GeoLocation position) {
        return new GeoLocation(position.latitude, position.longitude);
    }

    private static void write(BufferedWriter writer, String row) {
        try {
            writer.write(row);
            writer.newLine();
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }

    private static NomadicMobilityStrategy route(GeoLocation start, GeoLocation east) {
        GeoLocation northEast = east.nextLocation(1200, 0);
        GeoLocation north = start.nextLocation(1200, 0);
        ArrayList<GeoLocation> points = new ArrayList<>();
        for (int lap = 0; lap < 32; lap++) {
            // NomadicMobilityStrategy mutates reached waypoints during the next leg.
            points.add(copy(east));
            points.add(copy(northEast));
            points.add(copy(north));
            points.add(copy(start));
        }
        return new NomadicMobilityStrategy(copy(start), 0.0041, points);
    }

    private static PhysicalMachine deviceMachine() {
        EnumMap<PowerTransitionGenerator.PowerStateKind, Map<String, PowerState>> transitions =
                PowerTransitionGenerator.generateTransitions(0.065, 1.475, 2.0, 1, 2);
        Repository repository = new Repository(4_294_967_296L, "mobility-device", 3250, 3250, 3250,
                new HashMap<>(), transitions.get(PowerTransitionGenerator.PowerStateKind.storage),
                transitions.get(PowerTransitionGenerator.PowerStateKind.network));
        return new PhysicalMachine(2, 0.001, 2_147_483_648L, repository, 0, 0,
                transitions.get(PowerTransitionGenerator.PowerStateKind.host));
    }

    public static void main(String[] args) throws Exception {
        boolean openUi = true;
        ArrayList<String> positional = new ArrayList<>();
        for (String arg : args) {
            if ("--no-ui".equals(arg)) { openUi = false; }
            else if (arg.startsWith("--")) { throw new IllegalArgumentException("Unknown option: " + arg); }
            else { positional.add(arg); }
        }
        if (positional.size() != 0 && positional.size() != 2) {
            throw new IllegalArgumentException("Use [--no-ui] [<node-xml> <output-parent>]");
        }
        Path xml = positional.size() == 2 ? Path.of(positional.get(0)) : Path.of(ScenarioBase.resourcePath, "LPDS_original.xml");
        Path parent = positional.size() == 2 ? Path.of(positional.get(1)) : Path.of(ScenarioBase.resultDirectory);
        if (!Files.isRegularFile(xml)) {
            throw new IllegalArgumentException("Missing node configuration: " + xml);
        }
        Files.createDirectories(parent);
        Path output = Files.createTempDirectory(parent, "mobility-").toAbsolutePath();
        SimLogger.setLogging(2, false);
        SeedSyncer.centralRnd.setSeed(42);
        GeoLocation start = new GeoLocation(47.73, 18.27);
        GeoLocation east = start.nextLocation(1600, 90);
        ComputingAppliance westNode = new ComputingAppliance(xml.toString(), "mobility-west", start, 1);
        ComputingAppliance eastNode = new ComputingAppliance(xml.toString(), "mobility-east", east, 1);
        westNode.addNeighbor(eastNode, 10);
        VirtualAppliance va = new VirtualAppliance("mobility-va", 100, 0, false, 1_073_741_824L);
        AlterableResourceConstraints resources = new AlterableResourceConstraints(2, 0.001, 4_294_967_296L);
        Instance instance = new Instance("mobility-instance", va, resources, 0.0255 / 3_600_000);
        try (BufferedWriter observations = Files.newBufferedWriter(output.resolve("observations.csv"));
             BufferedWriter predictions = Files.newBufferedWriter(output.resolve("predictions.csv"));
             BufferedWriter evaluations = Files.newBufferedWriter(output.resolve("evaluations.csv"));
             BufferedWriter loads = Files.newBufferedWriter(output.resolve("node_load.csv"));
             BufferedWriter transfers = Files.newBufferedWriter(output.resolve("offload_events.csv"))) {
            TransferLedger ledger = new TransferLedger(transfers);
            for (ComputingAppliance node : new ComputingAppliance[] {westNode, eastNode}) {
                node.addApplication(new Application(node.name + "-app", STEP, 250, 2500, true,
                        new TracedRuntimeStrategy(ledger), instance));
            }
            write(transfers, "transfer_id,time_ms,event,source_node,destination_node,bytes,event_sequence");
            write(observations, "device_id,time_ms,latitude,longitude,previous_node,current_node");
            write(predictions, "device_id,origin_ms,target_ms,origin_node,node_latitude,node_longitude,radius_m,"
                    + "hold_latitude,hold_longitude,velocity_latitude,velocity_longitude");
            write(evaluations, "device_id,origin_ms,target_ms,origin_node,actual_node,actual_latitude,"
                    + "actual_longitude,distance_to_origin_node_m,actual_exit,hold_exit,velocity_exit");
            write(loads, "time_ms,sample_kind,node,device_upload_completed_bytes_derived,"
                    + "offload_sent_bytes,offload_received_bytes,net_received_counter_bytes,"
                    + "assigned_to_compute_bytes,waiting_assignment_bytes,running_tasks,completed_tasks,"
                    + "incoming_offload_transfers,connected_devices,device_generated_bytes,"
                    + "device_upload_completed_bytes_total,offload_in_flight_bytes_total,offload_event_sequence");
            ObservedDevice device = new ObservedDevice(route(start, east), deviceMachine(),
                    observations, predictions, evaluations, loads, ledger);
            Timed.simulateUntil(LIMIT);
            if (Timed.getNextFire() != -1 || device.isSubscribed() || device.pending != null
                    || device.issued != device.matched || device.matched == 0
                    || MobilityEvent.changeNodeEventCounter == 0 || MobilityEvent.disconnectFromNodeEventCounter == 0) {
                throw new IllegalStateException("Incomplete simulation or missing mobility events. Results: " + output);
            }
            ledger.snapshot(loads, device, "FINAL");
            long assigned = 0;
            for (Application app : Application.allApplications) {
                assigned += app.processedData;
                if (app.receivedData != app.processedData || app.incomingData != 0
                        || app.utilisedVms.stream().anyMatch(vm -> MobilityTaskMetrics.isRunning(vm))) {
                    throw new IllegalStateException("Application did not finish draining");
                }
            }
            if (ledger.inFlightBytes != 0 || ledger.nextId == 0
                    || assigned != device.generatedData || MobilityDeviceMetrics.uploadCompletedBytes(device) != device.generatedData) {
                throw new IllegalStateException("Final conservation failed");
            }
            String summary = "model,evaluated,tp,fn,fp,tn\n" + device.holdCounts.row("HoldPosition")
                    + "\n" + device.velocityCounts.row("ConstantVelocity") + "\n";
            Files.writeString(output.resolve("coverage_summary.csv"), summary);
            String run = "schema_version=2\nseed=42\ninterval_ms=" + STEP + "\nspeed_m_s=4.1\nnode_radius_m=1000\n"
                    + "route_width_m=1600\nroute_height_m=1200\nissued=" + device.issued
                    + "\nmatched=" + device.matched + "\nnode_changes=" + MobilityEvent.changeNodeEventCounter
                    + "\nconnects=" + MobilityEvent.connectToNodeEventCounter
                    + "\ndisconnects=" + MobilityEvent.disconnectFromNodeEventCounter
                    + "\ngenerated_bytes=" + device.generatedData
                    + "\nupload_completed_bytes=" + MobilityDeviceMetrics.uploadCompletedBytes(device)
                    + "\nassigned_bytes_final=" + assigned
                    + "\noffload_transfers=" + ledger.nextId
                    + "\noffload_bytes=" + Application.totalBytesOnNetwork
                    + "\naccounting_check=passed\n";
            Files.writeString(output.resolve("run.properties"), run);
            System.out.print(summary);
            System.out.print(run);
            System.out.println("MOBILITY_ACCOUNTING_OK");
            System.out.println("MOBILITY_SIMULATION_OK");
            System.out.println("Results: " + output);
        }
        // All CSV writers are closed before the UI reads this exact run.
        if (openUi) { MobilityUiLauncher.open(output); }
    }
}
