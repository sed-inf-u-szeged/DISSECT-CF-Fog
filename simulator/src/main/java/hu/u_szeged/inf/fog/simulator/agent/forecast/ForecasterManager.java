package hu.u_szeged.inf.fog.simulator.agent.forecast;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
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

    public static String predictorScriptDir; 
    
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

    private ForecasterManager(String predictorScriptDir, int workerCount, int basePort, String modelPath) {
        ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
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
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
            }
        }
        executor.shutdown();

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
                        if (line.contains("NNPACK.cpp") || line.contains("Use CPU")) {
                            continue;
                        }
                        
                        if (line.contains("startup complete")) {
                            readyLatch.countDown();
                        }
                        System.out.println("\t[forecaster " + port + "] " + line);
                    }
                } catch (IOException e) {
                    if (!shuttingDown) {
                        e.printStackTrace();
                    }
                }
            }, "forecaster-" + port + "-logger");

            logger.setDaemon(true);
            logger.start();

            boolean success = readyLatch.await(10, TimeUnit.SECONDS);
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
        //System.out.println("Dispatching " + inputPath + " to worker " + worker.getPort() + " " + System.currentTimeMillis());
        worker.predict(inputPath, outputPath);
    }

    public void shutdownAll() {
        for (ForecasterWorker w : workers) {
            System.out.println("Shutting down worker on port " + w.getPort());
            Process p = w.getProcess();
            shuttingDown = true;

            p.destroy();
            try {
                if (!p.waitFor(3, TimeUnit.SECONDS)) {
                    System.out.println("Worker on port " + w.getPort() + " did not stop in time, destroying forcibly");
                    p.destroyForcibly();
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
                p.destroyForcibly();
            }
        }
    }

}

