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
import hu.u_szeged.inf.fog.simulator.iot.mobility.MobilitySampleRecorder;
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

    /** Retain a ready connection; wake a covered application if all covered brokers are stopped. */
    static final class StableConnection extends DeviceStrategy implements VirtualMachine.StateChange {
        long wakeRequests;
        private final Map<VirtualMachine, Application> waking = new HashMap<>();

        @Override
        public void stateChanged(VirtualMachine vm, VirtualMachine.State oldState, VirtualMachine.State newState) {
            if (newState == VirtualMachine.State.RUNNING && waking.remove(vm) != null) {
                vm.unsubscribeStateChange(this);
                // Attach as soon as startup completes, before an idle application can shut down again.
                // Re-evaluate current coverage; never use a future waypoint or force a stale connection.
                if (device.isSubscribed()) { findApplication(); }
            }
        }

        @Override
        public void findApplication() {
            ArrayList<Application> available = getAvailableApplications();
            if (available.stream().noneMatch(app -> app.computingAppliance.broker.vm.getState()
                    == VirtualMachine.State.RUNNING)) {
                available.stream().filter(app -> app.computingAppliance.broker.vm.getState()
                        == VirtualMachine.State.SHUTDOWN).findFirst().ifPresent(app -> {
                            VirtualMachine vm = app.computingAppliance.broker.vm;
                            if (waking.putIfAbsent(vm, app) == null) { vm.subscribeStateChange(this); }
                            app.subscribeApplication();
                            wakeRequests++;
                        });
            }
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

    /** Synchronous shadow worker: wall-clock inference never advances simulated time. */
    private static final class ShadowBridge implements AutoCloseable {
        private final Process process;
        private final java.io.BufferedReader replies;
        private final BufferedWriter requests;
        private final java.util.concurrent.ExecutorService reader;
        private long predictions;
        private long observations;
        private final BufferedWriter receipts;

        private ShadowBridge(String python, String script, String model, Path output) throws Exception {
            process = new ProcessBuilder(python, "-u", script, "--training", model,
                    "--output", output.toString()).redirectError(output.resolve("shadow-worker.log").toFile()).start();
            replies = new java.io.BufferedReader(new java.io.InputStreamReader(
                    process.getInputStream(), java.nio.charset.StandardCharsets.UTF_8));
            requests = new BufferedWriter(new java.io.OutputStreamWriter(
                    process.getOutputStream(), java.nio.charset.StandardCharsets.UTF_8));
            reader = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
                Thread thread = new Thread(r, "mobility-shadow-reader"); thread.setDaemon(true); return thread;
            });
            BufferedWriter opened = null;
            try {
                opened = Files.newBufferedWriter(output.resolve("shadow_receipts.csv"),
                        java.nio.file.StandardOpenOption.CREATE_NEW);
                opened.write("origin_ms,target_ms,received_at_sim_ms,predicted_latitude,predicted_longitude\n");
                if (!"READY".equals(receive(120))) { throw new IOException("Shadow worker did not become ready"); }
            } catch (Exception error) {
                if (opened != null) { opened.close(); }
                process.destroyForcibly(); reader.shutdownNow(); throw error;
            }
            receipts = opened;
        }

        static ShadowBridge open(Path output) throws Exception {
            String python = System.getenv("MOBILITY_SHADOW_PYTHON");
            String script = System.getenv("MOBILITY_SHADOW_SCRIPT");
            String model = System.getenv("MOBILITY_SHADOW_MODEL");
            if (python == null && script == null && model == null) { return null; }
            if (python == null || script == null || model == null) {
                throw new IllegalArgumentException("All three MOBILITY_SHADOW settings are required");
            }
            return new ShadowBridge(python, script, model, output);
        }

        private String receive(int seconds) throws Exception {
            java.util.concurrent.Future<String> future = reader.submit(() -> replies.readLine());
            try {
                String line = future.get(seconds, java.util.concurrent.TimeUnit.SECONDS);
                if (line == null || line.length() > 4096) { throw new IOException("Invalid shadow reply; see shadow-worker.log"); }
                return line;
            } catch (Exception error) {
                future.cancel(true); process.destroyForcibly(); throw error;
            }
        }

        void observe(String sample, long now, boolean forecastAllowed) throws IOException {
            try {
                requests.write("OBS\t" + forecastAllowed + "\t" + sample); requests.newLine(); requests.flush();
                String response = receive(30); observations++;
                if (response.equals("WAIT\t" + now)) { return; }
                String[] fields = response.split("\t");
                if (fields.length != 5 || !fields[0].equals("PRED") || Long.parseLong(fields[1]) != now
                        || Long.parseLong(fields[2]) != now + STEP || !forecastAllowed) {
                    throw new IOException("Shadow response time/protocol mismatch: " + response);
                }
                double latitude = Double.parseDouble(fields[3]); double longitude = Double.parseDouble(fields[4]);
                if (!Double.isFinite(latitude) || !Double.isFinite(longitude)
                        || Math.abs(latitude) > 90 || Math.abs(longitude) > 180) {
                    throw new IOException("Invalid shadow coordinates");
                }
                receipts.write(now + "," + (now + STEP) + "," + Timed.getFireCount() + ","
                        + latitude + "," + longitude); receipts.newLine(); receipts.flush(); predictions++;
            } catch (Exception error) { throw new IOException("Shadow inference failed; retain run logs", error); }
        }

        void finish() throws Exception {
            requests.write("END"); requests.newLine(); requests.flush();
            String result = receive(30);
            if (!result.equals("DONE\t" + observations + "\t" + predictions)
                    || observations != 121 || predictions != 112) {
                throw new IOException("Incomplete shadow prediction stream: " + result);
            }
            requests.close();
            if (!process.waitFor(30, java.util.concurrent.TimeUnit.SECONDS) || process.exitValue() != 0) {
                throw new IOException("Shadow worker did not exit successfully");
            }
            System.out.println("MOBILITY_SHADOW_INFERENCE_OK observations=" + observations + " predictions=" + predictions);
        }

        @Override
        public void close() throws IOException {
            if (process.isAlive()) { process.destroyForcibly(); }
            reader.shutdownNow(); requests.close(); replies.close(); receipts.close();
        }
    }

    private static final class ObservedDevice extends SmartDevice {
        private final BufferedWriter observations;
        private final MobilitySampleRecorder samples;
        private final ShadowBridge shadow;
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
        private long observationCount;
        private String previousObservedNode = "NONE";

        ObservedDevice(NomadicMobilityStrategy movement, PhysicalMachine machine,
                BufferedWriter observations, BufferedWriter predictions,
                BufferedWriter evaluations, BufferedWriter loads, TransferLedger ledger, MobilitySampleRecorder samples, ShadowBridge shadow) {
            super(START, STOP, 100, STEP, movement, new StableConnection(), machine, 50, false);
            this.observations = observations;
            this.samples = samples;
            this.shadow = shadow;
            this.predictions = predictions;
            this.evaluations = evaluations;
            this.loads = loads;
            this.ledger = ledger;
        }

        @Override
        public void tick(long fires) {
            String oldNode = previousObservedNode;
            super.tick(fires);
            long now = Timed.getFireCount();
            // SmartDevice has now applied movement and refreshed the actual application connection.
            GeoLocation current = copy(geoLocation);
            String currentNode = nodeName(application);
            try {
                // Keep a fixed 120-minute observation span, including its two endpoints.
                // Later movement used to drain buffered uploads remains in observations.csv.
                if (now <= stopTime + STEP) {
                    String sample = samples.recordAndGet("0", now, current.latitude, current.longitude, currentNode);
                    if (shadow != null) { shadow.observe(sample, now, now < stopTime + STEP); }
                }
            } catch (IOException error) {
                throw new UncheckedIOException(error);
            }
            write(observations, "0," + now + "," + current.latitude + "," + current.longitude
                    + "," + oldNode + "," + currentNode);
            previousObservedNode = currentNode;
            observationCount++;
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
        Path scenarioPath = null;
        ArrayList<String> positional = new ArrayList<>();
        for (String arg : args) {
            if ("--no-ui".equals(arg)) { openUi = false; }
            else if (arg.startsWith("--scenario=")) {
                if (scenarioPath != null || arg.length() == "--scenario=".length()) {
                    throw new IllegalArgumentException("Supply one non-empty --scenario=<file>");
                }
                scenarioPath = Path.of(arg.substring("--scenario=".length()));
            }
            else if (arg.startsWith("--")) { throw new IllegalArgumentException("Unknown option: " + arg); }
            else { positional.add(arg); }
        }
        if (positional.size() != 0 && positional.size() != 2) {
            throw new IllegalArgumentException("Use [--no-ui] [--scenario=<file>] [<node-xml> <output-parent>]");
        }
        if (scenarioPath != null && openUi) {
            throw new IllegalArgumentException("Custom scenarios currently require --no-ui; the existing UI assumes two observed nodes");
        }
        MobilityScenarioConfig config = scenarioPath == null
                ? MobilityScenarioConfig.defaults() : MobilityScenarioConfig.load(scenarioPath);
        Path xml = positional.size() == 2 ? Path.of(positional.get(0)) : Path.of(ScenarioBase.resourcePath, "LPDS_original.xml");
        Path parent = positional.size() == 2 ? Path.of(positional.get(1)) : Path.of(ScenarioBase.resultDirectory);
        if (!Files.isRegularFile(xml)) {
            throw new IllegalArgumentException("Missing node configuration: " + xml);
        }
        Files.createDirectories(parent);
        Path output = Files.createTempDirectory(parent, "mobility-").toAbsolutePath();
        config.save(output);
        SimLogger.setLogging(2, false);
        SeedSyncer.centralRnd.setSeed(config.seed());
        GeoLocation start = config.start();
        GeoLocation east = start.nextLocation(1600, 90);
        ComputingAppliance westNode = new ComputingAppliance(xml.toString(), "mobility-west", start, 1);
        ComputingAppliance eastNode = new ComputingAppliance(xml.toString(), "mobility-east", east, 1);
        westNode.addNeighbor(eastNode, 10);
        VirtualAppliance va = new VirtualAppliance("mobility-va", 100, 0, false, 1_073_741_824L);
        AlterableResourceConstraints resources = new AlterableResourceConstraints(2, 0.001, 4_294_967_296L);
        Instance instance = new Instance("mobility-instance", va, resources, 0.0255 / 3_600_000);
        try (ShadowBridge shadow = ShadowBridge.open(output);
             BufferedWriter observations = Files.newBufferedWriter(output.resolve("observations.csv"));
             BufferedWriter predictions = Files.newBufferedWriter(output.resolve("predictions.csv"));
             BufferedWriter evaluations = Files.newBufferedWriter(output.resolve("evaluations.csv"));
             BufferedWriter loads = Files.newBufferedWriter(output.resolve("node_load.csv"));
             BufferedWriter transfers = Files.newBufferedWriter(output.resolve("offload_events.csv"));
             MobilitySampleRecorder samples = new MobilitySampleRecorder(
                     output.resolve("mobility_samples.csv"), output.getFileName().toString())) {
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
            ObservedDevice device = new ObservedDevice(config.movement(LIMIT), deviceMachine(),
                    observations, predictions, evaluations, loads, ledger, samples, shadow);
            Timed.simulateUntil(LIMIT);
            if (Timed.getNextFire() != -1 || device.isSubscribed() || device.pending != null
                    || device.issued != device.matched) {
                throw new IllegalStateException("Incomplete simulation or unmatched forecasts. Results: " + output);
            }
            if (shadow != null) { shadow.finish(); }
            ledger.snapshot(loads, device, "FINAL");
            long assigned = 0;
            for (Application app : Application.allApplications) {
                assigned += app.processedData;
                if (app.receivedData != app.processedData || app.incomingData != 0
                        || app.utilisedVms.stream().anyMatch(vm -> MobilityTaskMetrics.isRunning(vm))) {
                    throw new IllegalStateException("Application did not finish draining");
                }
            }
            if (ledger.inFlightBytes != 0
                    || assigned != device.generatedData || MobilityDeviceMetrics.uploadCompletedBytes(device) != device.generatedData) {
                throw new IllegalStateException("Final conservation failed: generated=" + device.generatedData
                        + ", uploaded=" + MobilityDeviceMetrics.uploadCompletedBytes(device)
                        + ", assigned=" + assigned + ", buffered=" + device.calculateStuckData()
                        + ", offloadInFlight=" + ledger.inFlightBytes);
            }
            String summary = "model,evaluated,tp,fn,fp,tn\n" + device.holdCounts.row("HoldPosition")
                    + "\n" + device.velocityCounts.row("ConstantVelocity") + "\n";
            Files.writeString(output.resolve("coverage_summary.csv"), summary);
            String run = "schema_version=2\nseed=" + config.seed() + "\ninterval_ms=" + STEP
                    + "\nspeed_m_s=" + config.speedMetersPerSecond() + "\nnode_radius_m=1000\n"
                    + "route_width_m=" + config.widthMeters() + "\nroute_height_m=" + config.heightMeters()
                    + "\nscenario_name=" + config.name()
                    + "\nscenario_config_file=scenario-effective.properties\nroute_file=route_waypoints.csv"
                    + "\nroute_strategy=NomadicMobilityStrategy\nroute_repeats=true"
                    + "\nnode_separation_m=1600\nnominal_generation_duration_ms=7200000"
                    + "\nissued=" + device.issued
                    + "\nmatched=" + device.matched + "\nnode_changes=" + MobilityEvent.changeNodeEventCounter
                    + "\nconnects=" + MobilityEvent.connectToNodeEventCounter
                    + "\ndisconnects=" + MobilityEvent.disconnectFromNodeEventCounter
                    + "\ngenerated_bytes=" + device.generatedData
                    + "\nupload_completed_bytes=" + MobilityDeviceMetrics.uploadCompletedBytes(device)
                    + "\nassigned_bytes_final=" + assigned
                    + "\noffload_transfers=" + ledger.nextId
                    + "\noffload_bytes=" + Application.totalBytesOnNetwork
                    + "\naccounting_check=passed\n"
                    + "connection_policy=retain_running_or_wake_covered\nbroker_wake_requests="
                    + ((StableConnection) device.deviceStrategy).wakeRequests
                    + "\nobservation_rows=" + device.observationCount
                    + "\ndrain_observation_rows=" + (device.observationCount - samples.getSampleCount())
                    + "\nsample_window_policy=fixed_121_observations"
                    + "\nsample_window_start_ms=" + (device.startTime + STEP)
                    + "\nsample_window_end_ms=" + (device.stopTime + STEP)
                    + "\nsample_schema_version=1\nsample_file=mobility_samples.csv\nsample_rows=" + samples.getSampleCount()
                    + "\nsample_speed_kind=endpoint_displacement_over_elapsed_time"
                    + "\nsample_heading_kind=initial_bearing_previous_to_current"
                    + "\nsample_heading_min_distance_m=0.000001"
                    + "\nsample_earth_radius_m=6378137.0\n";
            Files.writeString(output.resolve("run.properties"), run);
            System.out.print(summary);
            System.out.print(run);
            System.out.println("MOBILITY_SAMPLES_OK rows=" + samples.getSampleCount());
            System.out.println("MOBILITY_ACCOUNTING_OK");
            System.out.println("MOBILITY_SIMULATION_OK");
            System.out.println("Results: " + output);
        }
        // All CSV writers are closed before the UI reads this exact run.
        if (openUi) { MobilityUiLauncher.open(output); }
    }
}
