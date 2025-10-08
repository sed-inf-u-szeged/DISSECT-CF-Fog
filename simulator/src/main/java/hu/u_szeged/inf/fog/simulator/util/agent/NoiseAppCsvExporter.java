package hu.u_szeged.inf.fog.simulator.util.agent;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.u_szeged.inf.fog.simulator.agent.SwarmAgent;
import hu.u_szeged.inf.fog.simulator.agent.urbannoise.NoiseSensor;
import hu.u_szeged.inf.fog.simulator.agent.urbannoise.Sun;
import hu.u_szeged.inf.fog.simulator.demo.ScenarioBase;
import hu.u_szeged.inf.fog.simulator.util.AgentVisualiser;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class NoiseAppCsvExporter extends Timed {

    Sun sun;
    
    File fileSunIntensity;
    
    File avgCpuLoad;
    
    File noOfNoiseSensorClassifiers;
    
    public File noiseSensorTemperature;
    
    File noOfFilesToProcess;
    
    File noOfFileMigrations;
        
    public NoiseAppCsvExporter(Sun sun) {
        this.fileSunIntensity = new File(ScenarioBase.resultDirectory + "/sun-intensity.csv");
        this.avgCpuLoad = new File(ScenarioBase.resultDirectory + "/avg-cpu-load.csv");
        this.noOfNoiseSensorClassifiers = new File(ScenarioBase.resultDirectory + "/no-of-noise-sensor-classifiers.csv");
        this.noiseSensorTemperature = new File(ScenarioBase.resultDirectory + "/noise-sensor-temperature.csv");
        this.noOfFilesToProcess = new File(ScenarioBase.resultDirectory + "/no-of-files-to-process.csv");
        this.noOfFileMigrations = new File(ScenarioBase.resultDirectory + "/no-of-file-migrations.csv");
    	
        this.sun = sun;
        subscribe(10_000);
    }
    
    public void visualise() {
        AgentVisualiser.visualise(fileSunIntensity.toPath(), avgCpuLoad.toPath(), noOfNoiseSensorClassifiers.toPath(),
            this.noiseSensorTemperature.toPath(), this.noOfFilesToProcess.toPath(), this.noOfFileMigrations.toPath());
    }
    
    private String generateHeader() {
        List<String> names = new ArrayList<>();
        for (Object o : SwarmAgent.allSwarmAgents.get(0).components) {
            if (o instanceof NoiseSensor) {
                NoiseSensor ns = (NoiseSensor) o;
                names.add(SwarmAgent.allSwarmAgents.get(0).app.getComponentName(ns.util.resource.name)); 
            }
        }

        String header = "time";
        if (!names.isEmpty()) {
            header += "," + String.join(",", names);
        }
        return header;
    }

    @Override
    public void tick(long fires) {
        double time = Timed.getFireCount() / 1000.0 / 60.0 / 60.0;
    	
        // sun intensity
        try (PrintWriter writer = new PrintWriter(new FileWriter(fileSunIntensity.getAbsolutePath(), true))) {
            if (fileSunIntensity.length() == 0) {
                writer.println("time,sun_intensity"); 
            }

            StringBuilder row = new StringBuilder();
            row.append(String.format(Locale.ROOT, "%.3f", time));
            row.append(",");
            row.append(String.format(Locale.ROOT, "%.3f", sun.getSunStrength())); 
            writer.println(row.toString());
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (SwarmAgent.allSwarmAgents.size() > 0) {
            SwarmAgent sa = SwarmAgent.allSwarmAgents.get(0);

            // avg cpu load
            try (PrintWriter writer = new PrintWriter(new FileWriter(avgCpuLoad.getAbsolutePath(), true))) {
                if (avgCpuLoad.length() == 0) {
                    writer.println(this.generateHeader()); 
                }
                
                StringBuilder row = new StringBuilder();
                row.append(String.format(Locale.ROOT, "%.3f", time));
                    
                for (Object o : sa.components) {
                    if (o instanceof NoiseSensor) {
                        NoiseSensor ns = (NoiseSensor) o;
                        row.append(",");
                        if (ns.util.vm.isProcessing()) {
                            row.append(String.format(Locale.ROOT, "%d", 100));
                        } else if (ns.isClassificationRunning) {
                            row.append(String.format(Locale.ROOT, "%d", 1));
                        } else {
                            row.append(String.format(Locale.ROOT, "%d", 0));
                        }
                    }
                }
                //row.append(",");
                //row.append(String.format(Locale.ROOT, "%.3f", sa.avgCpu()));
                writer.println(row.toString());
            } catch (IOException e) {
                e.printStackTrace();
            }

            // noise sensor classifier count
            try (PrintWriter writer = new PrintWriter(new FileWriter(noOfNoiseSensorClassifiers.getAbsolutePath(), true))) {
                if (noOfNoiseSensorClassifiers.length() == 0) {
                    writer.println("time,no-of-classifiers"); 
                }
                StringBuilder row = new StringBuilder();
                row.append(String.format(Locale.ROOT, "%.3f", time));
                row.append(",");
                row.append(String.format(Locale.ROOT, "%d", sa.noiseSensorsWithClassifier.size()));
                writer.println(row.toString());
            } catch (IOException e) {
                e.printStackTrace();
            }

            // no. of files to process
            try (PrintWriter writer = new PrintWriter(new FileWriter(noOfFilesToProcess.getAbsolutePath(), true))) {
                if (noOfFilesToProcess.length() == 0) {
                    writer.println(this.generateHeader()); 
                }
                StringBuilder row = new StringBuilder();
                row.append(String.format(Locale.ROOT, "%.3f", time));
                for (Object o : sa.components) {
                    if (o instanceof NoiseSensor) {
                        NoiseSensor ns = (NoiseSensor) o;
                        row.append(",");
                        row.append(String.format(Locale.ROOT, "%d", ns.filesToBeProcessed.size()));
                    }
                }
                writer.println(row.toString());
            } catch (IOException e) {
                e.printStackTrace();
            }

            // no. of file migrations
            try (PrintWriter writer = new PrintWriter(new FileWriter(noOfFileMigrations.getAbsolutePath(), true))) {
                if (noOfFileMigrations.length() == 0) {
                    writer.println(this.generateHeader()); 
                }
                StringBuilder row = new StringBuilder();
                row.append(String.format(Locale.ROOT, "%.3f", time));
                for (Object o : sa.components) {
                    if (o instanceof NoiseSensor) {
                        NoiseSensor ns = (NoiseSensor) o;
                        row.append(",");
                        row.append(String.format(Locale.ROOT, "%d", ns.underMigration));
                    }
                }
                writer.println(row.toString());
            } catch (IOException e) {
                e.printStackTrace();
            }

            // noise sensor temperature
            try (PrintWriter writer = new PrintWriter(new FileWriter(noiseSensorTemperature.getAbsolutePath(), true))) {
                if (noiseSensorTemperature.length() == 0) {
                    writer.println(this.generateHeader()); 
                }
                StringBuilder row = new StringBuilder();
                row.append(String.format(Locale.ROOT, "%.3f", time));
                System.out.println("lol");
                for (Object o : sa.components) {
                    if (o instanceof NoiseSensor) {
                        NoiseSensor ns = (NoiseSensor) o;
                        row.append(",");
                        row.append(String.format(Locale.ROOT, "%.3f", ns.cpuTemp));
                    }
                }
                writer.println(row.toString());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
