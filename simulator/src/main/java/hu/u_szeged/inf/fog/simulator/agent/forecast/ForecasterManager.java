package hu.u_szeged.inf.fog.simulator.agent.forecast;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class ForecasterManager {

    private final List<ForecasterWorker> workers = new ArrayList<>();
    private final AtomicInteger rr = new AtomicInteger(0);

    public ForecasterManager(String predictorScriptDir, int workerCount, int basePort, String modelPath) {
        for (int i = 0; i < workerCount; i++) {
            int port = basePort + i;
            workers.add(startWorker(predictorScriptDir, modelPath, port));
        }

        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdownAll));
    }

    private ForecasterWorker startWorker(String predictorScriptDir, String modelPath, int port) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "uv", "run", "forecaster_service.py",
                    "--model_path", modelPath,
                    "--port", String.valueOf(port)
            );
            pb.directory(new File(predictorScriptDir));
            pb.redirectErrorStream(true);

            Process process = pb.start();

            CountDownLatch readyLatch = new CountDownLatch(1);

            Thread logger = new Thread(() -> {
                try (BufferedReader r = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = r.readLine()) != null) {
                        System.out.println("[forecaster " + port + "] " + line);

                        if (line.contains("startup complete")) {
                            readyLatch.countDown();
                        }
                    }
                } catch (IOException exception) {
                    exception.printStackTrace();
                }
            }, "forecaster-" + port + "-logger");

            logger.setDaemon(true);
            logger.start();

            boolean success = readyLatch.await(5, TimeUnit.SECONDS);
            if (!success) {
                throw new IllegalStateException("Worker on port " + port + " not ready in time");
            }
            
            return new ForecasterWorker(port, process);
        } catch (IOException e) {
            throw new RuntimeException("Failed to start forecaster on port " + port, e);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return null;
    }

    private ForecasterWorker nextWorker() {
        int index = Math.floorMod(rr.getAndIncrement(), workers.size());
        return workers.get(index);
    }

    public void predict(String inputPath, String outputPath) throws IOException, InterruptedException {
        ForecasterWorker worker = nextWorker();
        worker.predict(inputPath, outputPath);
    }

    public void shutdownAll() {
        for (ForecasterWorker w : workers) {
            System.out.println("Shutting down worker on port " + w.getPort());
            w.getProcess().destroy();
        }
    }
}

