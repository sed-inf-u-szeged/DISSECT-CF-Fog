package hu.u_szeged.inf.fog.simulator.agent.forecast;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.u_szeged.inf.fog.simulator.common.util.ScenarioBase;
import hu.u_szeged.inf.fog.simulator.common.util.SimLogger;
import org.apache.commons.lang3.SystemUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class ForecasterManager {

    private final List<ForecasterWorker> workers = new ArrayList<>();
    
    private final AtomicInteger rr = new AtomicInteger(0);
    
    private static ForecasterManager instance;

    static boolean shuttingDown = false;
    
    public static ForecasterManager getInstance() {
        return instance;
    }
    
    public static ForecasterManager getInstance(String predictorScriptDir, int workerCount, int basePort, String modelPath) {
        if (instance == null) {
            instance = new ForecasterManager(predictorScriptDir, workerCount, basePort, modelPath);
        } 
        return instance;
    }

    /* sequential creation of workers
    private ForecasterManager(String predictorScriptDir, int workerCount, int basePort, String modelPath) {
        for (int i = 0; i < workerCount; i++) {
            int port = basePort + i;
            try {
                ForecasterWorker w = startWorker(predictorScriptDir, modelPath, port);
                workers.add(w);
            } catch (RuntimeException e) {
                e.printStackTrace();
                throw e;
            }
        }

        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdownAll));
    }
     */

    private ForecasterManager(String predictorScriptDir, int workerCount, int basePort, String modelPath) {
        ExecutorService executor = Executors.newFixedThreadPool(2); // threads for workers, used only the beginning to make the AI services startup faster
        List<Future<ForecasterWorker>> futures = new ArrayList<>();
        
        for (int i = 0; i < workerCount; i++) {
            final int port = basePort + i;
            futures.add(executor.submit(() ->
                    startWorker(predictorScriptDir, modelPath, port)
            ));
        }
        
        for (Future<ForecasterWorker> f : futures) {
            try {
                ForecasterWorker w = f.get();
                workers.add(w);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                executor.shutdownNow();
                shutdownAll();
                throw new IllegalStateException("Forecaster initialization interrupted", e);
            } catch (ExecutionException e) {
                executor.shutdownNow();
                shutdownAll();
                throw new IllegalStateException("Failed to initialize forecaster worker", e.getCause());
            }
        }
        executor.shutdown();
        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdownAll));
    }

    private ForecasterWorker startWorker(String predictorScriptDir, String modelPath, int port) {
        if (isPortReachable(port)) {
            throw new IllegalStateException("Forecaster port " + port + " is already in use");
        }
        Process process = null;
        try {
            ProcessBuilder pb = null;
            if (SystemUtils.IS_OS_LINUX) {
                pb = new ProcessBuilder(
                        "uv", "run", "forecaster_service.py",
                        "--model_path", modelPath,
                        "--port", String.valueOf(port)
                );
                pb.directory(new File(predictorScriptDir));
            } else if (SystemUtils.IS_OS_WINDOWS) {
                pb = new ProcessBuilder("uv", "run", "forecaster_service.py", "--model_path", modelPath, "--port", String.valueOf(port));
                pb.directory(new File(predictorScriptDir));
            } else {
                SimLogger.logError("Unsupported OS: " + SystemUtils.OS_NAME);
            }

            pb.redirectErrorStream(true);
            pb.environment().put("PYTHONUNBUFFERED", "1");

            process = pb.start();
            final Process runningProcess = process;

            Thread logger = new Thread(() -> {
                try (BufferedReader r = new BufferedReader(new InputStreamReader(runningProcess.getInputStream()))) {
                    String line;
                    while ((line = r.readLine()) != null) {
                        if (line.contains("NNPACK.cpp") || line.contains("Use CPU")) {
                            continue;
                        }

                        SimLogger.logRun("\t[forecaster " + port + "] " + line + " ("
                                + Timed.getFireCount() / (double) ScenarioBase.MINUTE_IN_MILLISECONDS + " min.)");
                    }
                } catch (IOException e) {
                    if (!shuttingDown) {
                        e.printStackTrace();
                    }
                }
            }, "forecaster-" + port + "-logger");

            logger.setDaemon(true);
            logger.start();

            waitUntilReady(process, port, 120_000);
            return new ForecasterWorker(port, process);
        } catch (IOException | RuntimeException e) {
            stopProcess(process);
            throw new RuntimeException("Failed to start forecaster on port " + port, e);
        }
    }

    private void stopProcess(Process process) {
        if (process == null) {
            return;
        }

        var descendants = process.descendants().toList();

        descendants.forEach(child -> {
            if (child.isAlive()) {
                child.destroy();
            }
        });

        if (process.isAlive()) {
            process.destroy();
        }

        try {
            if (process.isAlive()) {
                process.waitFor(3, TimeUnit.SECONDS);
            }

            descendants.forEach(child -> {
                if (child.isAlive()) {
                    child.destroyForcibly();
                }
            });

            if (process.isAlive()) {
                process.destroyForcibly();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();

            descendants.forEach(child -> {
                if (child.isAlive()) {
                    child.destroyForcibly();
                }
            });

            if (process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    private ForecasterWorker nextWorker() {
        int index = Math.floorMod(rr.getAndIncrement(), workers.size());
        return workers.get(index);
    }

    public String predict(String json) throws IOException, InterruptedException {
        ForecasterWorker worker = nextWorker();
        return worker.predict(json);
    }

    public void predict(String inputPath, String outputPath) throws IOException, InterruptedException {
        ForecasterWorker worker = nextWorker();
        //System.out.println("Dispatching " + inputPath + " to worker " + worker.getPort() + " " + System.currentTimeMillis());
        worker.predict(inputPath, outputPath);
    }

    private void waitUntilReady(Process process, int port, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;

        while (System.currentTimeMillis() < deadline) {
            if (!process.isAlive()) {
                throw new IllegalStateException("Forecaster process terminated during startup on port " + port);
            }

            if (isPortReachable(port)) {
                try {
                    Thread.sleep(300);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted while validating forecaster startup", e);
                }

                if (!process.isAlive()) {
                    throw new IllegalStateException("Forecaster process terminated after startup on port " + port);
                }

                return;
            }

            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for forecaster on port " + port, e);
            }
        }

        throw new IllegalStateException("Forecaster did not become ready on port " + port + " within " + timeoutMs + " ms");
    }

    private boolean isPortReachable(int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", port), 500);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public void shutdownAll() {
        if (shuttingDown) {
            return;
        }
        shuttingDown = true;

        for (ForecasterWorker w : workers) {
            System.out.println("Shutting down worker on port " + w.getPort());
            stopProcess(w.getProcess());
        }
    }
}
