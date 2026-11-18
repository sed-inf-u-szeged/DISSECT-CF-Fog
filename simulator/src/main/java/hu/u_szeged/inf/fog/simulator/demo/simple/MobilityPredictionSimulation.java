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
import hu.u_szeged.inf.fog.simulator.application.strategy.PliantApplicationStrategy;
import hu.u_szeged.inf.fog.simulator.demo.CityTopology;
import hu.u_szeged.inf.fog.simulator.demo.ScenarioBase;
import hu.u_szeged.inf.fog.simulator.iot.SmartDevice;
import hu.u_szeged.inf.fog.simulator.iot.mobility.GeoLocation;
import hu.u_szeged.inf.fog.simulator.iot.mobility.MobilityEvent;
import hu.u_szeged.inf.fog.simulator.iot.mobility.MobilitySampleRecorder;
import hu.u_szeged.inf.fog.simulator.iot.mobility.MobilityStrategy;
import hu.u_szeged.inf.fog.simulator.iot.strategy.DeviceStrategy;
import hu.u_szeged.inf.fog.simulator.node.ComputingAppliance;
import hu.u_szeged.inf.fog.simulator.provider.Instance;
import hu.u_szeged.inf.fog.simulator.util.SimLogger;
import hu.u_szeged.inf.fog.simulator.prediction.communication.launchers.MobilityUiLauncher;
import hu.u_szeged.inf.fog.simulator.prediction.lifecycle.LifecycleManager;
import hu.u_szeged.inf.fog.simulator.prediction.mobility.MobilityPositionBaselines;
import hu.u_szeged.inf.fog.simulator.prediction.mobility.MobilityLifecycleAdapter;
import hu.u_szeged.inf.fog.simulator.prediction.FixedIntervalSampler;
import hu.u_szeged.inf.fog.simulator.prediction.NodeLoadLearning;
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
    private static LivePreview livePreview;

    /** Optional append-only preview. Completed result validation remains separate. */
    private static final class LivePreview implements AutoCloseable {
        final Map<String, org.json.JSONObject> comparisons = new HashMap<>();
        final BufferedWriter writer;
        final String runId;
        final Map<String, org.json.JSONObject> lifecycle = new HashMap<>();
        long sequence;
        long flushed = System.nanoTime();
        long loadWritten;

        LivePreview(Path file, MobilityScenarioConfig config, String runId, int count, org.json.JSONArray nodes) throws Exception {
            this.runId = runId;
            writer = Files.newBufferedWriter(file, java.nio.file.StandardOpenOption.CREATE_NEW);
            emit(new org.json.JSONObject().put("type", "META").put("schema_version", 2)
                    .put("run_id", runId).put("device_count", count).put("duration_ms", config.durationMillis())
                    .put("expected_observations", config.observationCount()).put("strategy", config.strategy()).put("nodes", nodes));
            writer.flush();
        }

        void nodeLoad(String kind, org.json.JSONObject record) throws IOException {
            try {
                emit(new org.json.JSONObject().put("type", "NODE_LOAD").put("kind", kind).put("run_id", runId)
                        .put("time_ms", record.getLong("time_ms")).put("data", record));
                // Publish TRAINING before its blocking worker call, and complete vectors immediately.
                if ("EVENT".equals(kind) || "META".equals(kind)
                        || ("VECTOR".equals(kind) && !"Persistence".equals(record.optString("model")))) {
                    writer.flush(); flushed = System.nanoTime();
                }
            } catch (Exception error) { throw new IOException("Cannot publish live node-load record", error); }
        }

        void emit(org.json.JSONObject row) throws Exception {
            row.put("sequence", ++sequence);
            writer.write(row.toString()); writer.newLine();
            if (System.nanoTime() - flushed >= 200_000_000L) { writer.flush(); flushed = System.nanoTime(); }
        }

        void training(int device, long now, MobilityLifecycleAdapter lifecycle, String command) throws Exception {
            org.json.JSONObject control = new org.json.JSONObject(command);
            if (!"TRAIN".equals(control.getString("action"))) return;
            org.json.JSONObject state = lifecycle.preview().put("state", "TRAINING")
                    .put("candidate", control.getString("model_version"));
            emit(new org.json.JSONObject().put("type", "MOBILITY_STATUS").put("device_id", device)
                    .put("time_ms", now).put("lifecycle", state));
            writer.flush(); flushed = System.nanoTime();
        }

        void frame(ObservedDevice device, GeoLocation actual, String node, long now) {
            try {
                boolean sampled = now <= device.stopTime + STEP;
                org.json.JSONObject row = new org.json.JSONObject().put("type", "FRAME")
                        .put("device_id", Integer.parseInt(device.deviceId)).put("time_ms", now).put("sampled", sampled)
                        .put("latitude", actual.latitude).put("longitude", actual.longitude).put("connection", node);
                org.json.JSONObject extra = comparisons.get(device.deviceId);
                if (extra != null) { row.put("comparison", extra); }
                org.json.JSONObject state = lifecycle.get(device.deviceId);
                if (state != null) { row.put("lifecycle", state); }
                if (sampled) { row.put("baselines", device.positionBaselines.preview()); }
                emit(row);
            } catch (Exception e) { throw new IllegalStateException("Cannot write live preview", e); }
        }

        void loads(TransferLedger ledger, String kind, long now) {
            if (!"FINAL".equals(kind) && System.nanoTime() - loadWritten < 200_000_000L) { return; }
            try {
                org.json.JSONArray nodes = new org.json.JSONArray();
                for (Application app : Application.allApplications) {
                    long running = app.utilisedVms.stream().filter(MobilityTaskMetrics::isRunning).count();
                    long completed = app.utilisedVms.stream().mapToLong(vm -> vm.taskCounter).sum();
                    nodes.put(new org.json.JSONObject().put("node", app.computingAppliance.name)
                            .put("waiting_bytes", app.receivedData - app.processedData)
                            .put("running_tasks", running).put("completed_tasks", completed));
                }
                boolean draining = ledger.devices.stream().allMatch(d -> now > d.stopTime + STEP);
                emit(new org.json.JSONObject().put("type", "LOAD").put("time_ms", now)
                        .put("phase", "FINAL".equals(kind) ? "FINAL" : draining ? "DRAINING" : "OBSERVING").put("nodes", nodes));
                loadWritten = System.nanoTime();
                if ("FINAL".equals(kind)) { writer.flush(); }
            } catch (Exception e) { throw new IllegalStateException("Cannot write live load preview", e); }
        }

        @Override public void close() throws IOException { writer.close(); livePreview = null; }
    }

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
        final ArrayList<ObservedDevice> devices = new ArrayList<>();
        private long snapshotTime = -1;
        private String snapshotKind;
        private String bufferedSnapshot;
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
            long generated = devices.stream().mapToLong(d -> d.generatedData).sum();
            long uploaded = devices.stream().mapToLong(MobilityDeviceMetrics::uploadCompletedBytes).sum();
            StringBuilder rows = new StringBuilder();
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
                rows.append(now + "," + kind + "," + app.computingAppliance.name + ","
                        + uploads + "," + outgoing + "," + incoming + "," + app.receivedData + ","
                        + app.processedData + "," + waiting + "," + running + "," + completed + ","
                        + app.incomingData + "," + app.deviceList.size() + "," + generated
                        + "," + uploaded + "," + inFlightBytes + "," + eventSequence + "\n");
            }
            if (totalUploads != uploaded || totalAccounted != uploaded) {
                throw new IllegalStateException("Device/application byte conservation failed");
            }
            if (livePreview != null) { livePreview.loads(this, kind, now); }
            if (devices.size() == 1) {
                write(writer, rows.toString().stripTrailing());
            } else {
                // Keep the last observation snapshot at a shared timestamp, after all device ticks.
                if (snapshotTime != now || !kind.equals(snapshotKind)) { flushSnapshot(writer); }
                snapshotTime = now;
                snapshotKind = kind;
                bufferedSnapshot = rows.toString().stripTrailing();
                if ("FINAL".equals(kind)) { flushSnapshot(writer); }
            }
        }

        void flushSnapshot(BufferedWriter writer) {
            if (bufferedSnapshot != null) { write(writer, bufferedSnapshot); bufferedSnapshot = null; }
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
            tracedTransfer(ledger, application, destination, bytes);
        }
    }

    private static final class TracedPliantStrategy extends PliantApplicationStrategy {
        private final TransferLedger ledger;
        TracedPliantStrategy(TransferLedger ledger) { super(0.9, 2.0); this.ledger = ledger; }
        @Override protected void startDataTranfer(Application destination, long bytes) {
            tracedTransfer(ledger, application, destination, bytes);
        }
    }

    private static void tracedTransfer(TransferLedger ledger, Application source, Application destination, long bytes) {
            if (destination == null) { return; }
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

    /** A shared node clock for load forecasting, independent of device callbacks. */
    private static void simulateWithNodeSamples(TransferLedger ledger, MobilityScenarioConfig config,
            Path output, ComputingAppliance... nodes) throws IOException, org.json.JSONException {
        String runId = output.getFileName().toString();
        long sampleUntil = ledger.devices.stream().mapToLong(d -> d.stopTime + STEP).max().orElseThrow();
        long count;
        try (BufferedWriter writer = Files.newBufferedWriter(output.resolve("node_load_samples.csv"));
                NodeLoadLearning learning = NodeLoadLearning.fromEnvironment(output)) {
            if (livePreview != null) {
                org.json.JSONArray ids = new org.json.JSONArray();
                for (ComputingAppliance node : nodes) { ids.put(node.name); }
                livePreview.nodeLoad("META", learning.liveMetadata(ids));
                learning.setListener(livePreview::nodeLoad);
            }
            write(writer, "run_id,node_id,sample_index,time_ms,sample_phase,waiting_assignment_bytes,"
                    + "running_tasks,completed_tasks_total,connected_devices,device_upload_completed_bytes_derived,"
                    + "offload_sent_bytes_total,offload_received_bytes_total,net_received_counter_bytes,"
                    + "assigned_to_compute_bytes,incoming_offload_transfers");
            count = FixedIntervalSampler.simulate(STEP, sampleUntil, config.simulationLimitMillis(), (index, time) -> {
                for (ComputingAppliance node : nodes) {
                    long net = 0, assigned = 0, sent = 0, received = 0, incoming = 0, running = 0, completed = 0;
                    java.util.HashSet<hu.u_szeged.inf.fog.simulator.iot.Device> connected = new java.util.HashSet<>();
                    for (Application app : node.applications) {
                        net += app.receivedData;
                        assigned += app.processedData;
                        sent += ledger.get(ledger.sent, app);
                        received += ledger.get(ledger.received, app);
                        incoming += app.incomingData;
                        connected.addAll(app.deviceList);
                        for (AppVm vm : app.utilisedVms) {
                            if (MobilityTaskMetrics.isRunning(vm)) { running++; }
                            completed += vm.taskCounter;
                        }
                    }
                    write(writer, runId + "," + node.name + "," + index + "," + time + "," + FixedIntervalSampler.PHASE
                            + "," + (net - assigned) + "," + running + "," + completed + "," + connected.size()
                            + "," + (net + sent - received) + "," + sent + "," + received + "," + net
                            + "," + assigned + "," + incoming);
                    if (livePreview != null) {
                        try {
                            livePreview.nodeLoad("SAMPLE", new org.json.JSONObject().put("node_id", node.name)
                                    .put("time_ms", time).put("index", index).put("value", net - assigned)
                                    .put("running_tasks", running).put("completed_tasks", completed));
                        } catch (org.json.JSONException error) { throw new IOException(error); }
                    }
                    learning.observe(node.name, index, time, net - assigned);
                }
            });
            learning.finish();
        }
        org.json.JSONArray nodeIds = new org.json.JSONArray();
        for (ComputingAppliance node : nodes) { nodeIds.put(node.name); }
        org.json.JSONObject metadata = new org.json.JSONObject().put("schema_version", 1).put("run_id", runId)
                .put("node_ids", nodeIds).put("interval_ms", STEP).put("start_ms", 0)
                .put("sample_until_ms", sampleUntil).put("sample_count_per_node", count)
                .put("last_sample_ms", count == 0 ? org.json.JSONObject.NULL : (count - 1) * STEP)
                .put("rows", count * nodes.length).put("sample_phase", FixedIntervalSampler.PHASE)
                .put("sampling_scope", "CONFIGURED_OBSERVATION_SPAN")
                .put("simulation_end_ms", Timed.getFireCount()).put("simulation_limit_ms", config.simulationLimitMillis());
        Files.writeString(output.resolve("node_load_sampling.json"), metadata.toString(2));
        System.out.println("NODE_LOAD_SAMPLES_RECORDED nodes=" + nodes.length + " samples_per_node=" + count);
        System.out.println("NODE_LOAD_OBSERVATION_INTERVAL_MS=" + STEP);
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

    /** Synchronous shadow worker: wall-clock computation never advances simulated time. */
    private interface SampleSink {
        void observe(String sample, long now, boolean forecastAllowed, GeoLocation actual) throws IOException;
    }

    private static final class ShadowBridge implements AutoCloseable, SampleSink {
        private final Process process;
        private final java.io.BufferedReader replies;
        private final BufferedWriter requests;
        private final java.util.concurrent.ExecutorService reader;
        private long predictions;
        private long observations;
        private final BufferedWriter receipts;
        private final int replyTimeoutSeconds;
        private final int expectedObservations;
        private MobilityLifecycleAdapter lifecycle;
        private final String runId;

        private ShadowBridge(String python, String script, String model, Path output, int expectedObservations) throws Exception {
            this.expectedObservations = expectedObservations;
            runId = output.getFileName().toString();
            String timeout = System.getenv("MOBILITY_SHADOW_REPLY_TIMEOUT_SECONDS");
            replyTimeoutSeconds = timeout == null ? 30 : Integer.parseInt(timeout);
            if (replyTimeoutSeconds < 1 || replyTimeoutSeconds > 300) {
                throw new IllegalArgumentException("Shadow reply timeout must be between 1 and 300 seconds");
            }
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
                String ready = receive(120);
                if (("READY\t" + MobilityLifecycleAdapter.PROTOCOL).equals(ready)
                        || ("READY\t" + MobilityLifecycleAdapter.CLOSED_LOOP_PROTOCOL).equals(ready)) {
                    lifecycle = new MobilityLifecycleAdapter(LifecycleManager.getInstance(), output, runId, "0", expectedObservations,
                            ready.endsWith(MobilityLifecycleAdapter.CLOSED_LOOP_PROTOCOL));
                } else if (!"READY".equals(ready)) {
                    throw new IOException("Shadow worker did not become ready");
                }
            } catch (Exception error) {
                if (opened != null) { opened.close(); }
                process.destroyForcibly(); reader.shutdownNow(); throw error;
            }
            receipts = opened;
        }

        static ShadowBridge open(Path output, int expectedObservations) throws Exception {
            String python = System.getenv("MOBILITY_SHADOW_PYTHON");
            String script = System.getenv("MOBILITY_SHADOW_SCRIPT");
            String model = System.getenv("MOBILITY_SHADOW_MODEL");
            if (python == null && script == null && model == null) { return null; }
            if (python == null || script == null || model == null) {
                throw new IllegalArgumentException("All three MOBILITY_SHADOW settings are required");
            }
            return new ShadowBridge(python, script, model, output, expectedObservations);
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

        public void observe(String sample, long now, boolean forecastAllowed, GeoLocation actual) throws IOException {
            try {
                if (lifecycle != null) {
                    lifecycle.observe(runId, "0", now, actual.latitude, actual.longitude, forecastAllowed);
                }
                requests.write("OBS\t" + forecastAllowed + "\t" + sample); requests.newLine();
                if (lifecycle != null && lifecycle.isClosedLoop()) {
                    requests.write("CONTROL\t" + lifecycle.control()); requests.newLine();
                }
                requests.flush();
                String response = receive(replyTimeoutSeconds);
                int frames = 0;
                while (lifecycle != null && response.startsWith("LIFECYCLE\t")) {
                    if (++frames > 3) { throw new IOException("Too many lifecycle frames per observation"); }
                    lifecycle.accept(response.substring("LIFECYCLE\t".length()));
                    response = receive(replyTimeoutSeconds);
                }
                observations++;
                if (response.equals("WAIT\t" + now)) {
                    if (lifecycle != null) { lifecycle.endObservation(); }
                    return;
                }
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
                if (lifecycle != null) { lifecycle.endObservation(); }
            } catch (Exception error) {
                if (lifecycle != null) { lifecycle.fail(error.toString()); }
                throw new IOException("Shadow inference failed; retain run logs", error);
            }
        }

        void finish() throws Exception {
            requests.write("END"); requests.newLine(); requests.flush();
            String result = receive(30);
            if (!result.equals("DONE\t" + observations + "\t" + predictions)
                    || observations != expectedObservations || predictions != expectedObservations - 9) {
                throw new IOException("Incomplete shadow prediction stream: " + result);
            }
            requests.close();
            if (!process.waitFor(30, java.util.concurrent.TimeUnit.SECONDS) || process.exitValue() != 0) {
                throw new IOException("Shadow worker did not exit successfully");
            }
            if (lifecycle != null) { lifecycle.finish(); }
            System.out.println("MOBILITY_SHADOW_INFERENCE_OK observations=" + observations + " predictions=" + predictions);
        }

        @Override
        public void close() throws IOException {
            if (process.isAlive()) { process.destroyForcibly(); }
            reader.shutdownNow();
            try { if (lifecycle != null) { lifecycle.close(); } }
            finally { requests.close(); replies.close(); receipts.close(); }
        }
    }

    private static final class ObservedDevice extends SmartDevice {
        private final BufferedWriter observations;
        private final MobilitySampleRecorder samples;
        private final MobilityPositionBaselines positionBaselines;
        private final SampleSink shadow;
        private final String deviceId;
        private final DeviceWorkloadProfile workload;
        private final BufferedWriter demand;
        private long measurementIndex;
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

        ObservedDevice(MobilityStrategy movement, PhysicalMachine machine, long stop,
                BufferedWriter observations, BufferedWriter predictions,
                BufferedWriter evaluations, BufferedWriter loads, TransferLedger ledger, MobilitySampleRecorder samples, ShadowBridge shadow,
                DeviceWorkloadProfile workload, BufferedWriter demand, MobilityPositionBaselines positionBaselines) {
            this("0", movement, machine, stop, observations, predictions, evaluations, loads, ledger, samples, shadow, workload, demand, positionBaselines);
        }

        ObservedDevice(String deviceId, MobilityStrategy movement, PhysicalMachine machine, long stop,
                BufferedWriter observations, BufferedWriter predictions, BufferedWriter evaluations,
                BufferedWriter loads, TransferLedger ledger, MobilitySampleRecorder samples, SampleSink shadow,
                DeviceWorkloadProfile workload, BufferedWriter demand, MobilityPositionBaselines positionBaselines) {
            super(START, stop, 100, STEP, movement, new StableConnection(), machine, 50, false);
            this.deviceId = deviceId;
            this.workload = workload;
            this.demand = demand;
            this.observations = observations;
            this.samples = samples;
            this.positionBaselines = positionBaselines;
            this.shadow = shadow;
            this.predictions = predictions;
            this.evaluations = evaluations;
            this.loads = loads;
            this.ledger = ledger;
            ledger.devices.add(this);
        }

        @Override
        public void tick(long fires) {
            String oldNode = previousObservedNode;
            long measurementTime = Timed.getFireCount();
            if (measurementTime >= startTime && measurementTime < stopTime) {
                fileSize = workload.bytesAt(Integer.parseInt(deviceId), measurementIndex);
                write(demand, deviceId + "," + measurementIndex + "," + measurementTime + "," + fileSize);
                measurementIndex++;
            }
            super.tick(fires);
            long now = Timed.getFireCount();
            // SmartDevice has now applied movement and refreshed the actual application connection.
            GeoLocation current = copy(geoLocation);
            String currentNode = nodeName(application);
            try {
                // Keep the configured observation span, including its two endpoints.
                // Later movement used to drain buffered uploads remains in observations.csv.
                if (now <= stopTime + STEP) {
                    String sample = samples.recordAndGet(deviceId, now, current.latitude, current.longitude, currentNode);
                    positionBaselines.observe(now, current.latitude, current.longitude, currentNode, now < stopTime + STEP);
                    if (shadow != null) { shadow.observe(sample, now, now < stopTime + STEP, current); }
                }
            } catch (IOException error) {
                throw new UncheckedIOException(error);
            }
            write(observations, deviceId + "," + now + "," + current.latitude + "," + current.longitude
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
                write(evaluations, deviceId + "," + pending.origin + "," + now + "," + pending.node + ","
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
                write(predictions, deviceId + "," + now + "," + (now + STEP) + "," + pending.node
                        + "," + pending.center.latitude + "," + pending.center.longitude + "," + pending.radius
                        + "," + pending.hold.latitude + "," + pending.hold.longitude
                        + "," + pending.velocity.latitude + "," + pending.velocity.longitude);
                issued++;
            }
            previous = current;
            previousTime = now;
            if (livePreview != null) { livePreview.frame(this, current, currentNode, now); }
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
        return deviceMachine("mobility-device");
    }

    private static PhysicalMachine deviceMachine(String repositoryName) {
        EnumMap<PowerTransitionGenerator.PowerStateKind, Map<String, PowerState>> transitions =
                PowerTransitionGenerator.generateTransitions(0.065, 1.475, 2.0, 1, 2);
        Repository repository = new Repository(4_294_967_296L, repositoryName, 3250, 3250, 3250,
                new HashMap<>(), transitions.get(PowerTransitionGenerator.PowerStateKind.storage),
                transitions.get(PowerTransitionGenerator.PowerStateKind.network));
        return new PhysicalMachine(2, 0.001, 2_147_483_648L, repository, 0, 0,
                transitions.get(PowerTransitionGenerator.PowerStateKind.host));
    }

    /** One Python process; each device has its own Java coordinator and output directory. */
    private static final class MultiBridge implements AutoCloseable {
        private final Process process;
        private final java.io.BufferedReader replies;
        private final BufferedWriter requests;
        private final java.util.concurrent.ExecutorService reader = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "mobility-multi-reader"); t.setDaemon(true); return t;
        });
        private final ArrayList<MobilityLifecycleAdapter> lifecycles = new ArrayList<>();
        private final String runId;
        private final int expected;
        private final long[] observed;
        private final long[] predicted;
        private final long[] bridgeNanos;

        MultiBridge(Path output, int count, int expected) throws Exception {
            this.runId = output.getFileName().toString();
            this.expected = expected;
            observed = new long[count]; predicted = new long[count]; bridgeNanos = new long[count];
            String python = System.getenv("MOBILITY_SHADOW_PYTHON");
            String script = System.getenv("MOBILITY_SHADOW_SCRIPT");
            String policy = System.getenv("MOBILITY_SHADOW_MODEL");
            if (python == null || script == null || policy == null) {
                reader.shutdownNow();
                throw new IllegalArgumentException("Multi-device mode requires the multi-device Python runner");
            }
            for (int i = 0; i < count; i++) { Files.createDirectories(output.resolve("devices").resolve(Integer.toString(i))); }
            process = new ProcessBuilder(python, "-u", script, "--training", policy, "--output", output.toString(),
                    "--devices", Integer.toString(count), "--run-id", runId, "--observations", Integer.toString(expected))
                    .redirectError(output.resolve("shadow-worker.log").toFile()).start();
            replies = new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream(), java.nio.charset.StandardCharsets.UTF_8));
            requests = new BufferedWriter(new java.io.OutputStreamWriter(process.getOutputStream(), java.nio.charset.StandardCharsets.UTF_8));
            try {
                if (!"READY\tMOBILITY_MULTI_CLOSED_LOOP_V1".equals(receive(120))) { throw new IOException("Multi-device worker did not become ready"); }
                for (int i = 0; i < count; i++) {
                    lifecycles.add(new MobilityLifecycleAdapter(LifecycleManager.getInstance(),
                            output.resolve("devices").resolve(Integer.toString(i)), runId, Integer.toString(i), expected, true));
                }
            } catch (Exception error) { close(); throw error; }
        }

        private String receive(int seconds) throws Exception {
            java.util.concurrent.Future<String> task = reader.submit(() -> replies.readLine());
            try {
                String line = task.get(seconds, java.util.concurrent.TimeUnit.SECONDS);
                if (line == null || line.length() > 8192) { throw new IOException("Invalid multi-device worker reply; see shadow-worker.log"); }
                return line;
            } catch (Exception error) { task.cancel(true); process.destroyForcibly(); throw error; }
        }

        SampleSink session(int id) {
            return (sample, now, forecastAllowed, actual) -> {
                long started = System.nanoTime();
                MobilityLifecycleAdapter lifecycle = lifecycles.get(id);
                try {
                    lifecycle.observe(runId, Integer.toString(id), now, actual.latitude, actual.longitude, forecastAllowed);
                    requests.write("DEVICE\t" + id); requests.newLine();
                    requests.write("OBS\t" + forecastAllowed + "\t" + sample); requests.newLine();
                    String command = lifecycle.control();
                    if (livePreview != null) livePreview.training(id, now, lifecycle, command);
                    requests.write("CONTROL\t" + command); requests.newLine(); requests.flush();
                    String prefix = "DEVICE\t" + id + "\t";
                    int messages = 0;
                    org.json.JSONObject comparison = null;
                    while (true) {
                        String response = receive(300);
                        if (!response.startsWith(prefix)) { throw new IOException("Worker reply belongs to another device"); }
                        response = response.substring(prefix.length());
                        if (response.startsWith("COMPARISON\t")) {
                            if (comparison != null) { throw new IOException("Duplicate comparison preview"); }
                            comparison = new org.json.JSONObject(response.substring("COMPARISON\t".length()));
                            if (comparison.getLong("time_ms") != now) { throw new IOException("Stale comparison preview"); }
                            continue;
                        }
                        if (response.startsWith("LIFECYCLE\t")) {
                            if (++messages > 3) { throw new IOException("Excess lifecycle messages"); }
                            lifecycle.accept(response.substring("LIFECYCLE\t".length()));
                            continue;
                        }
                        if (!response.equals("WAIT\t" + now)) {
                            String[] fields = response.split("\t");
                            if (fields.length != 5 || !fields[0].equals("PRED") || !forecastAllowed
                                    || Long.parseLong(fields[1]) != now || Long.parseLong(fields[2]) != now + STEP
                                    || !Double.isFinite(Double.parseDouble(fields[3])) || Math.abs(Double.parseDouble(fields[3])) > 90
                                    || !Double.isFinite(Double.parseDouble(fields[4])) || Math.abs(Double.parseDouble(fields[4])) > 180) {
                                throw new IOException("Multi-device prediction time or coordinates differ");
                            }
                            predicted[id]++;
                        }
                        lifecycle.endObservation();
                        if (livePreview != null) {
                            livePreview.lifecycle.put(Integer.toString(id), lifecycle.preview());
                            if (comparison != null) { livePreview.comparisons.put(Integer.toString(id), comparison); }
                        }
                        observed[id]++;
                        break;
                    }
                } catch (Exception error) {
                    lifecycle.fail(error.toString());
                    throw new IOException("Device " + id + " prediction failed; retain run logs", error);
                } finally { bridgeNanos[id] += System.nanoTime() - started; }
            };
        }

        void finish(Path output) throws Exception {
            requests.write("END"); requests.newLine(); requests.flush();
            String result = receive(120);
            int count = observed.length;
            if (!result.equals("DONE\t" + count + "\t" + ((long) count * expected) + "\t" + ((long) count * (expected - 9)))) {
                throw new IOException("Incomplete multi-device worker: " + result);
            }
            requests.close();
            if (!process.waitFor(30, java.util.concurrent.TimeUnit.SECONDS) || process.exitValue() != 0) { throw new IOException("Multi-device worker failed"); }
            StringBuilder timings = new StringBuilder("device_id,observations,prediction_origins,bridge_wall_seconds\n");
            for (int i = 0; i < count; i++) {
                if (observed[i] != expected || predicted[i] != expected - 9) { throw new IOException("Device stream incomplete: " + i); }
                lifecycles.get(i).finish();
                timings.append(i).append(',').append(observed[i]).append(',').append(predicted[i]).append(',')
                        .append(bridgeNanos[i] / 1e9).append('\n');
            }
            Files.writeString(output.resolve("bridge-timings.csv"), timings.toString());
        }

        @Override public void close() throws IOException {
            if (process.isAlive()) { process.destroyForcibly(); }
            reader.shutdownNow();
            IOException failure = null;
            for (MobilityLifecycleAdapter lifecycle : lifecycles) {
                try { lifecycle.close(); } catch (IOException error) { failure = error; }
            }
            try { requests.close(); } catch (IOException error) { failure = error; }
            try { replies.close(); } catch (IOException error) { failure = error; }
            if (failure != null) { throw failure; }
        }
    }

    private static final class RunResources implements AutoCloseable {
        private final ArrayList<AutoCloseable> values = new ArrayList<>();
        <T extends AutoCloseable> T keep(T value) { values.add(value); return value; }
        BufferedWriter writer(Path path) throws IOException { return keep(Files.newBufferedWriter(path)); }
        @Override public void close() throws Exception {
            Exception failure = null;
            for (int i = values.size() - 1; i >= 0; i--) {
                try { values.get(i).close(); } catch (Exception error) { if (failure == null) { failure = error; } else { failure.addSuppressed(error); } }
            }
            if (failure != null) { throw failure; }
        }
    }

    private static org.json.JSONObject drainState(MobilityScenarioConfig config, TransferLedger ledger) throws Exception {
        org.json.JSONArray nodes = new org.json.JSONArray();
        for (Application app : Application.allApplications) {
            nodes.put(new org.json.JSONObject().put("node", app.computingAppliance.name)
                    .put("waiting_assignment_bytes", app.receivedData - app.processedData)
                    .put("running_tasks", app.utilisedVms.stream().filter(MobilityTaskMetrics::isRunning).count())
                    .put("incoming_offload_transfers", app.incomingData));
        }
        return new org.json.JSONObject().put("simulation_time_ms", Timed.getFireCount())
                .put("simulation_limit_ms", config.simulationLimitMillis()).put("drain_allowance_ms", config.drainMillis())
                .put("next_event_ms", Timed.getNextFire())
                .put("subscribed_devices", ledger.devices.stream().filter(d -> d.isSubscribed()).count())
                .put("pending_device_forecasts", ledger.devices.stream().filter(d -> d.pending != null).count())
                .put("unmatched_device_forecasts", ledger.devices.stream().mapToLong(d -> d.issued - d.matched).sum())
                .put("generated_bytes", ledger.devices.stream().mapToLong(d -> d.generatedData).sum())
                .put("upload_completed_bytes", ledger.devices.stream().mapToLong(MobilityDeviceMetrics::uploadCompletedBytes).sum())
                .put("offload_in_flight_bytes", ledger.inFlightBytes).put("nodes", nodes);
    }

    private static org.json.JSONObject topology(MobilityScenarioConfig config, String runId,
            java.util.List<ComputingAppliance> infrastructure) throws org.json.JSONException {
        org.json.JSONArray nodes = new org.json.JSONArray(), links = new org.json.JSONArray();
        for (ComputingAppliance node : infrastructure) {
            boolean serviceable = node.applications.stream().anyMatch(a -> a.serviceable);
            Application app = node.applications.get(0);
            nodes.put(new org.json.JSONObject().put("name", node.name)
                    .put("latitude", node.geoLocation.latitude).put("longitude", node.geoLocation.longitude)
                    .put("radius", serviceable ? node.range * 1000 : 0).put("serviceable", serviceable)
                    .put("role", serviceable ? "fog" : "cloud")
                    .put("resources", new org.json.JSONObject()
                            .put("host_count", node.iaas.machines.size())
                            .put("total_cpu_cores", node.iaas.getCapacities().getRequiredCPUs())
                            .put("total_memory_bytes", node.iaas.getCapacities().getRequiredMemory())
                            .put("vm_cpu_cores", app.instance.arc.getRequiredCPUs())
                            .put("vm_memory_bytes", app.instance.arc.getRequiredMemory()))
                    .put("application", new org.json.JSONObject().put("task_bytes", app.tasksize)
                            .put("instructions_per_task", config.isCityTopology() ? 480 : 2500).put("interval_ms", STEP)));
        }
        if (config.isCityTopology()) {
            for (CityTopology.LinkSpec link : CityTopology.LINKS) {
                links.put(new org.json.JSONObject().put("source", link.source).put("target", link.target)
                        .put("kind", link.parent ? "parent" : "peer").put("latency_ms", link.latency));
            }
        } else {
            links.put(new org.json.JSONObject().put("source", "mobility-west").put("target", "mobility-east")
                    .put("kind", "peer").put("latency_ms", 10));
        }
        return new org.json.JSONObject().put("schema_version", 1).put("run_id", runId)
                .put("infrastructure", config.infrastructure()).put("nodes", nodes).put("links", links)
                .put("coverage_semantics", "synthetic device access; cloud has no direct device coverage");
    }

    private static void runMulti(MobilityScenarioConfig config, Path xml, Path parent, int count, boolean openUi) throws Exception {
        if (config.observationCount() < 105) { throw new IllegalArgumentException("Multi-device lifecycle requires at least 104 duration_minutes"); }
        Files.createDirectories(parent);
        Path output = Files.createTempDirectory(parent, "mobility-multi-").toAbsolutePath();
        String runId = output.getFileName().toString();
        config.save(output);
        org.json.JSONObject manifest = new org.json.JSONObject().put("schema_version", 1).put("status", "RUNNING")
                .put("run_id", runId).put("device_count", count).put("expected_observations_per_device", config.observationCount())
                .put("duration_ms", config.durationMillis()).put("drain_allowance_ms", config.drainMillis())
                .put("simulation_limit_ms", config.simulationLimitMillis())
                .put("connection_control", false).put("worker_processes", 1).put("scheduling", "SEQUENTIAL_SHARED_WORKER")
                .put("random_stream", "SHARED_SEED_SYNCER");
        Path manifestPath = output.resolve("multi-device.json");
        Files.writeString(manifestPath, manifest.toString(2));
        long started = System.nanoTime();
        try (RunResources resources = new RunResources()) {
            SimLogger.setLogging(2, false);
            SeedSyncer.centralRnd.setSeed(config.seed());
            BufferedWriter loads = resources.writer(output.resolve("node_load.csv"));
            BufferedWriter transfers = resources.writer(output.resolve("offload_events.csv"));
            BufferedWriter demand = resources.writer(output.resolve("device_demand_schedule.csv"));
            write(demand, "device_id,measurement_index,measurement_started_ms,bytes");
            TransferLedger ledger = new TransferLedger(transfers);
            java.util.List<ComputingAppliance> nodes;
            if (config.isCityTopology()) {
                // The shared infrastructure is reused; forecast-driven control is a later experiment.
                if (hu.u_szeged.inf.fog.simulator.prediction.PredictionConfigurator.PREDICTION_ENABLED) {
                    throw new IllegalStateException("City integration requires reactive Pliant (predictions are observational)");
                }
                nodes = CityTopology.create(xml, xml.toAbsolutePath().getParent(), config.cityCoverageKm(),
                        5120, 480, () -> new TracedPliantStrategy(ledger));
            } else {
                GeoLocation start = config.start();
                ComputingAppliance west = new ComputingAppliance(xml.toString(), "mobility-west", start, 1);
                ComputingAppliance east = new ComputingAppliance(xml.toString(), "mobility-east", start.nextLocation(1600, 90), 1);
                west.addNeighbor(east, 10);
                nodes = java.util.List.of(west, east);
                VirtualAppliance va = new VirtualAppliance("mobility-va", 100, 0, false, 1_073_741_824L);
                Instance instance = new Instance("mobility-instance", va,
                        new AlterableResourceConstraints(2, 0.001, 4_294_967_296L), 0.0255 / 3_600_000);
                for (ComputingAppliance node : nodes) {
                    node.addApplication(new Application(node.name + "-app", STEP, 250, 2500, true, new TracedRuntimeStrategy(ledger), instance));
                }
            }
            org.json.JSONObject topology = topology(config, runId, nodes);
            Files.writeString(output.resolve("node_topology.json"), topology.toString(2));
            String liveFile = System.getenv("MOBILITY_LIVE_FILE");
            if (liveFile != null && !liveFile.isBlank()) {
                livePreview = resources.keep(new LivePreview(Path.of(liveFile), config, runId, count, topology.getJSONArray("nodes")));
            }
            write(transfers, "transfer_id,time_ms,event,source_node,destination_node,bytes,event_sequence");
            write(loads, "time_ms,sample_kind,node,device_upload_completed_bytes_derived,offload_sent_bytes,offload_received_bytes,net_received_counter_bytes,"
                    + "assigned_to_compute_bytes,waiting_assignment_bytes,running_tasks,completed_tasks,incoming_offload_transfers,connected_devices,"
                    + "device_generated_bytes,device_upload_completed_bytes_total,offload_in_flight_bytes_total,offload_event_sequence");
            MultiBridge bridge = resources.keep(new MultiBridge(output, count, config.observationCount()));
            org.json.JSONArray entries = new org.json.JSONArray();
            for (int i = 0; i < count; i++) {
                String id = Integer.toString(i);
                Path child = output.resolve("devices").resolve(id);
                BufferedWriter observations = resources.writer(child.resolve("observations.csv"));
                BufferedWriter predictions = resources.writer(child.resolve("predictions.csv"));
                BufferedWriter evaluations = resources.writer(child.resolve("evaluations.csv"));
                write(observations, "device_id,time_ms,latitude,longitude,previous_node,current_node");
                write(predictions, "device_id,origin_ms,target_ms,origin_node,node_latitude,node_longitude,radius_m,hold_latitude,hold_longitude,velocity_latitude,velocity_longitude");
                write(evaluations, "device_id,origin_ms,target_ms,origin_node,actual_node,actual_latitude,actual_longitude,distance_to_origin_node_m,actual_exit,hold_exit,velocity_exit");
                MobilitySampleRecorder samples = resources.keep(new MobilitySampleRecorder(child.resolve("mobility_samples.csv"), runId));
                new ObservedDevice(id, config.movement(config.simulationLimitMillis(), config.deviceStart(i)), deviceMachine("mobility-device-" + id), START + config.durationMillis(),
                        observations, predictions, evaluations, loads, ledger, samples, bridge.session(i), config.deviceWorkload(), demand,
                        resources.keep(new MobilityPositionBaselines(child, runId, id)));
                GeoLocation origin = config.deviceStart(i);
                org.json.JSONObject entry = new org.json.JSONObject().put("device_id", id).put("path", "devices/" + id)
                        .put("start_latitude", origin.latitude).put("start_longitude", origin.longitude);
                if (config.isCityTopology()) { entry.put("start_node", CityTopology.deviceOrigin(config.cityPlacement(), i).name); }
                entries.put(entry);
            }
            simulateWithNodeSamples(ledger, config, output, nodes.toArray(new ComputingAppliance[0]));
            org.json.JSONObject drain = drainState(config, ledger);
            Files.writeString(output.resolve("drain-state.json"), drain.toString(2));
            manifest.put("drain_state", drain);
            if (Timed.getNextFire() != -1 || ledger.devices.stream().anyMatch(d -> d.isSubscribed() || d.pending != null || d.issued != d.matched)) {
                System.err.println("Incomplete drain state: " + drain);
                throw new IllegalStateException("Incomplete multi-device simulation at " + Timed.getFireCount()
                        + " ms (limit " + config.simulationLimitMillis()
                        + " ms). Inspect drain-state.json; unfinished work must not be marked COMPLETE: " + output);
            }
            bridge.finish(output);
            ledger.snapshot(loads, ledger.devices.get(0), "FINAL");
            long assigned = 0;
            for (Application app : Application.allApplications) {
                assigned += app.processedData;
                if (app.receivedData != app.processedData || app.incomingData != 0
                        || app.utilisedVms.stream().anyMatch(MobilityTaskMetrics::isRunning)) { throw new IllegalStateException("Node did not finish draining"); }
            }
            long generated = ledger.devices.stream().mapToLong(d -> d.generatedData).sum();
            long uploaded = ledger.devices.stream().mapToLong(MobilityDeviceMetrics::uploadCompletedBytes).sum();
            if (ledger.inFlightBytes != 0 || assigned != generated || uploaded != generated) { throw new IllegalStateException("Multi-device final byte conservation failed"); }
            String common = "schema_version=2\nposition_baselines=early-position-v1\nrun_id=" + runId + "\nseed=" + config.seed() + "\ninterval_ms=" + STEP
                    + (config.isCityTopology() ? "" : "\nnode_radius_m=1000\nnode_separation_m=1600")
                    + "\nscenario_name=" + config.name() + config.metadata()
                    + "\nsimulation_limit_ms=" + config.simulationLimitMillis() + "\nfinal_settlement_time_ms=" + Timed.getFireCount()
                    + "\ngenerated_bytes=" + generated + "\nupload_completed_bytes=" + uploaded + "\nassigned_bytes_final=" + assigned
                    + "\noffload_transfers=" + ledger.nextId + "\noffload_bytes=" + Application.totalBytesOnNetwork
                    + "\naccounting_check=passed\ndevice_count=" + count + "\nload_scope=ALL_DEVICES\n";
            long issued = 0, matched = 0;
            for (ObservedDevice device : ledger.devices) {
                Path child = output.resolve("devices").resolve(device.deviceId);
                Files.writeString(child.resolve("run.properties"), common + "device_start_latitude_deg=" + config.deviceStart(Integer.parseInt(device.deviceId)).latitude
                        + "\ndevice_start_longitude_deg=" + config.deviceStart(Integer.parseInt(device.deviceId)).longitude
                        + "\ndevice_id=" + device.deviceId + "\nissued=" + device.issued
                        + "\nmatched=" + device.matched + "\ndevice_generated_bytes=" + device.generatedData
                        + "\ndevice_uploaded_bytes=" + MobilityDeviceMetrics.uploadCompletedBytes(device) + "\n");
                Files.writeString(child.resolve("coverage_summary.csv"), "model,evaluated,tp,fn,fp,tn\n"
                        + device.holdCounts.row("HoldPosition") + "\n" + device.velocityCounts.row("ConstantVelocity") + "\n");
                issued += device.issued; matched += device.matched;
            }
            Files.writeString(output.resolve("run.properties"), common + "issued=" + issued + "\nmatched=" + matched + "\n");
            manifest.put("devices", entries).put("generated_bytes", generated).put("upload_completed_bytes", uploaded)
                    .put("issued", issued).put("matched", matched).put("accounting_check", "passed");
        } catch (Exception error) {
            manifest.put("status", "FAILED").put("error", error.toString());
            Files.writeString(manifestPath, manifest.toString(2));
            throw error;
        }
        manifest.put("status", "COMPLETE").put("java_wall_seconds", (System.nanoTime() - started) / 1e9);
        Files.writeString(manifestPath, manifest.toString(2));
        System.out.println("MOBILITY_MULTI_DEVICE_SIMULATION_OK devices=" + count);
        System.out.println("Results: " + output);
        if (openUi) { MobilityUiLauncher.open(output); }
    }

    public static void main(String[] args) throws Exception {
        boolean openUi = true;
        Path scenarioPath = null;
        int multiDevices = 0;
        ArrayList<String> positional = new ArrayList<>();
        for (String arg : args) {
            if ("--no-ui".equals(arg)) { openUi = false; }
            else if (arg.startsWith("--devices=")) {
                if (multiDevices != 0) { throw new IllegalArgumentException("Supply --devices once"); }
                multiDevices = Integer.parseInt(arg.substring("--devices=".length()));
                if (multiDevices < 1 || multiDevices > 100) { throw new IllegalArgumentException("devices must be 1-100"); }
            }
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
        if (multiDevices > 0) {
            runMulti(config, xml, parent, multiDevices, openUi);
            return;
        }
        if (config.isCityTopology()) { throw new IllegalArgumentException("City scenarios require --devices=<count>"); }
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
        try (MobilityPositionBaselines positionBaselines = new MobilityPositionBaselines(output, output.getFileName().toString(), "0");
             ShadowBridge shadow = ShadowBridge.open(output, config.observationCount());
             BufferedWriter observations = Files.newBufferedWriter(output.resolve("observations.csv"));
             BufferedWriter predictions = Files.newBufferedWriter(output.resolve("predictions.csv"));
             BufferedWriter evaluations = Files.newBufferedWriter(output.resolve("evaluations.csv"));
             BufferedWriter loads = Files.newBufferedWriter(output.resolve("node_load.csv"));
             BufferedWriter transfers = Files.newBufferedWriter(output.resolve("offload_events.csv"));
             BufferedWriter demand = Files.newBufferedWriter(output.resolve("device_demand_schedule.csv"));
             MobilitySampleRecorder samples = new MobilitySampleRecorder(
                     output.resolve("mobility_samples.csv"), output.getFileName().toString())) {
            TransferLedger ledger = new TransferLedger(transfers);
            for (ComputingAppliance node : new ComputingAppliance[] {westNode, eastNode}) {
                node.addApplication(new Application(node.name + "-app", STEP, 250, 2500, true,
                        new TracedRuntimeStrategy(ledger), instance));
            }
            write(transfers, "transfer_id,time_ms,event,source_node,destination_node,bytes,event_sequence");
            write(demand, "device_id,measurement_index,measurement_started_ms,bytes");
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
            ObservedDevice device = new ObservedDevice(config.movement(config.simulationLimitMillis()), deviceMachine(), START + config.durationMillis(),
                    observations, predictions, evaluations, loads, ledger, samples, shadow, config.deviceWorkload(), demand, positionBaselines);
            simulateWithNodeSamples(ledger, config, output, westNode, eastNode);
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
            String run = "schema_version=2\nposition_baselines=early-position-v1\nseed=" + config.seed() + "\ninterval_ms=" + STEP
                    + "\nnode_radius_m=1000\n"
                    + "route_width_m=" + config.widthMeters() + "\nroute_height_m=" + config.heightMeters()
                    + "\nscenario_name=" + config.name()
                    + "\nscenario_config_file=scenario-effective.properties" + config.metadata()
                    + "\nnode_separation_m=1600\nnominal_generation_duration_ms=" + config.durationMillis()
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
                    + "\nsample_window_policy=fixed_" + config.observationCount() + "_observations"
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
