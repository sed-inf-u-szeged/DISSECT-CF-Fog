package hu.u_szeged.inf.fog.simulator.prediction;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.json.JSONException;
import org.json.JSONObject;

/** One run-owned, synchronous worker; wall time never advances simulation time. */
final class NodeLoadModelWorker implements NodeLoadLearning.Backend {
    private final String python;
    private final String script;
    private final Path output;
    private final int epochs;
    private Process process;
    private BufferedReader reader;
    private BufferedWriter writer;
    private final ExecutorService receiver = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "node-load-receiver"); thread.setDaemon(true); return thread;
    });

    NodeLoadModelWorker(String python, String script, Path output, int epochs) {
        this.python = python; this.script = script; this.output = output; this.epochs = epochs;
    }

    private JSONObject receive() throws IOException, JSONException {
        try {
            String line = receiver.submit(() -> reader.readLine()).get(600, TimeUnit.SECONDS);
            if (line == null || line.length() > 1000000) {
                throw new IOException("Node-load worker closed or returned an oversized reply; see node_load_worker.log");
            }
            JSONObject result = new JSONObject(line);
            if (!result.getBoolean("ok")) {
                throw new IOException("Node-load worker: " + result.optString("error"));
            }
            return result;
        } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException error) {
            throw new IOException("Node-load worker failed or timed out; see node_load_worker.log", error);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt(); throw new IOException("Node-load worker interrupted", error);
        }
    }

    @Override
    public JSONObject request(JSONObject request) throws IOException, JSONException {
        if (process == null) {
            process = new ProcessBuilder(python, "-u", script, "--output", output.toString(),
                    "--epochs", Integer.toString(epochs))
                    .redirectError(output.resolve("node_load_worker.log").toFile()).start();
            reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
            writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
            if (!receive().getBoolean("ready")) { throw new IOException("Node-load worker not ready"); }
        }
        writer.write(request.toString()); writer.newLine(); writer.flush();
        return receive();
    }

    @Override
    public void close() throws IOException {
        try {
            if (process != null && process.isAlive()) {
                // EOF releases TensorFlow and its native threads without another prediction.
                writer.close();
                if (!process.waitFor(10, TimeUnit.SECONDS)) { process.destroyForcibly(); }
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt(); throw new IOException(error);
        } finally {
            if (process != null && process.isAlive()) { process.destroyForcibly(); }
            receiver.shutdownNow();
        }
    }
}
