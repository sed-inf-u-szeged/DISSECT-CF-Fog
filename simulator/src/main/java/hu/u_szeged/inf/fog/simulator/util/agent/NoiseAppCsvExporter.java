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
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class NoiseAppCsvExporter {

    private static NoiseAppCsvExporter instance = null;

    Sun sun;
    
    public File noiseSensorTemperature;
    
    File sunIntensity;
        
    File cpuLoad;
    
    File noOfNoiseSensorClassifiers;
    
    File noOfFilesToProcess;
    
    File noOfFileMigrations;
    
    File soundValues;
    
    public static NoiseAppCsvExporter getInstance() {
        if (instance == null) {
            instance = new NoiseAppCsvExporter(Sun.getInstance());
        }
        return instance;
    }
        
    private NoiseAppCsvExporter(Sun sun) {
        this.sunIntensity = new File(ScenarioBase.resultDirectory + "/sun-intensity.csv");
        this.cpuLoad = new File(ScenarioBase.resultDirectory + "/cpu-load.csv");
        this.noOfNoiseSensorClassifiers = new File(ScenarioBase.resultDirectory + "/no-of-noise-sensor-classifiers.csv");
        this.noiseSensorTemperature = new File(ScenarioBase.resultDirectory + "/noise-sensor-temperature.csv");
        this.noOfFilesToProcess = new File(ScenarioBase.resultDirectory + "/no-of-files-to-process.csv");
        this.noOfFileMigrations = new File(ScenarioBase.resultDirectory + "/no-of-file-migrations.csv");
        this.soundValues = new File(ScenarioBase.resultDirectory + "/sound-values.csv");
        
        this.sun = sun;
    }
    
    public static void visualise() {
        AgentVisualiser.visualise("res-sun-sound", instance.sunIntensity.toPath(), instance.soundValues.toPath());
        AgentVisualiser.visualise("res-cpu-temp-files", instance.cpuLoad.toPath(), instance.noOfNoiseSensorClassifiers.toPath(),
            instance.noiseSensorTemperature.toPath(), instance.noOfFilesToProcess.toPath(), 
            instance.noOfFileMigrations.toPath());
            //, Paths.get(ScenarioBase.resultDirectory, "energy.csv"));
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

    public static void log() {
        double time = Timed.getFireCount() / 1000.0 / 60.0 / 60.0;
    	
        // sun intensity
        try (PrintWriter writer = new PrintWriter(new FileWriter(instance.sunIntensity.getAbsolutePath(), true))) {
            if (instance.sunIntensity.length() == 0) {
                writer.println("time,sun_intensity"); 
            }

            StringBuilder row = new StringBuilder();
            row.append(String.format(Locale.ROOT, "%.3f", time));
            row.append(",");
            row.append(String.format(Locale.ROOT, "%.3f", instance.sun.getSunStrength())); 
            writer.println(row.toString());
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (!SwarmAgent.allSwarmAgents.isEmpty()) {
            SwarmAgent sa = SwarmAgent.allSwarmAgents.get(0);
            
            // cpu load
            try (PrintWriter writer = new PrintWriter(new FileWriter(instance.cpuLoad.getAbsolutePath(), true))) {
                if (instance.cpuLoad.length() == 0) {
                    writer.println(instance.generateHeader() + ",avg-cpu-load"); 
                }
                
                StringBuilder row = new StringBuilder();
                row.append(String.format(Locale.ROOT, "%.3f", time));
                
                int classifierCount = 0;
                double avgLoad = 0;
                for (Object o : sa.components) {
                    if (o instanceof NoiseSensor) {
                        NoiseSensor ns = (NoiseSensor) o;
                        if (ns.noOfprocessedFiles > 0) {
                            double load = 1.0 + 99.0
                                    * (ns.noOfprocessedFiles * sa.app.configuration.get("lengthOfProcessing").doubleValue() / 10_000);
                            load = Math.min(load, 100.0);
                            avgLoad += load;
                            classifierCount++;
                            row.append(",");
                            row.append(String.format(Locale.ROOT, "%.3f", load));
                        } else {
                            row.append(",");
                            row.append(String.format(Locale.ROOT, "%.3f", 0.0));
                        }
                    }
                }
                row.append(",");
                double value = classifierCount == 0 ? 0 : avgLoad / classifierCount;
                row.append(String.format(Locale.ROOT, "%.3f", classifierCount == 0 ? 0 : avgLoad / classifierCount));
                writer.println(row.toString());
            } catch (IOException e) {
                e.printStackTrace();
            }
            
            // sound values
            try (PrintWriter writer = new PrintWriter(new FileWriter(instance.soundValues.getAbsolutePath(), true))) {
                if (instance.soundValues.length() == 0) {
                    writer.println(instance.generateHeader()); 
                }
                StringBuilder row = new StringBuilder();
                row.append(String.format(Locale.ROOT, "%.3f", time));
                for (Object o : sa.components) {
                    if (o instanceof NoiseSensor) {
                        NoiseSensor ns = (NoiseSensor) o;
                        row.append(",");
                        row.append(String.format(Locale.ROOT, "%d", ns.prevSoundValue));
                    }
                }
                writer.println(row.toString());
            } catch (IOException e) {
                e.printStackTrace();
            }
            
            // noise sensor temperature
            try (PrintWriter writer = new PrintWriter(new FileWriter(instance.noiseSensorTemperature.getAbsolutePath(), true))) {
                if (instance.noiseSensorTemperature.length() == 0) {
                    writer.println(instance.generateHeader()); 
                }
                StringBuilder row = new StringBuilder();
                row.append(String.format(Locale.ROOT, "%.3f", time));
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

            // noise sensor classifier count
            try (PrintWriter writer = new PrintWriter(new FileWriter(instance.noOfNoiseSensorClassifiers.getAbsolutePath(), true))) {
                if (instance.noOfNoiseSensorClassifiers.length() == 0) {
                    writer.println("time,no-of-classifiers,no-of-available-sensors"); 
                }
                StringBuilder row = new StringBuilder();
                row.append(String.format(Locale.ROOT, "%.3f", time));
                
                int availableSensors = 0;
                for (Object o : sa.components) {
                    if (o instanceof NoiseSensor) {
                        NoiseSensor ns = (NoiseSensor) o;
                        if (ns.cpuTemp < sa.app.configuration.get("cpuTempTreshold").doubleValue()) {
                            availableSensors++;
                        }
                    }
                }
                row.append(",");
                row.append(String.format(Locale.ROOT, "%d", sa.noiseSensorsWithClassifier.size()));
                row.append(",");
                row.append(String.format(Locale.ROOT, "%d", availableSensors));
                writer.println(row.toString());
            } catch (IOException e) {
                e.printStackTrace();
            }
            
            
            // no. of files to process
            try (PrintWriter writer = new PrintWriter(new FileWriter(instance.noOfFilesToProcess.getAbsolutePath(), true))) {
                if (instance.noOfFilesToProcess.length() == 0) {
                    writer.println(instance.generateHeader() + ",sum-of-files-to-process"); 
                }
                StringBuilder row = new StringBuilder();
                row.append(String.format(Locale.ROOT, "%.3f", time));
                int filesToProcessCount = 0;
                for (Object o : sa.components) {
                    if (o instanceof NoiseSensor) {
                        NoiseSensor ns = (NoiseSensor) o;
                        row.append(",");
                        row.append(String.format(Locale.ROOT, "%d", ns.noOfprocessedFiles));
                        filesToProcessCount += ns.noOfprocessedFiles;
                        ns.noOfprocessedFiles = 0;
                    }
                }
                row.append(",");
                row.append(String.format(Locale.ROOT, "%d", filesToProcessCount));
                writer.println(row.toString());
            } catch (IOException e) {
                e.printStackTrace();
            }

            // no. of file migrations
            try (PrintWriter writer = new PrintWriter(new FileWriter(instance.noOfFileMigrations.getAbsolutePath(), true))) {
                if (instance.noOfFileMigrations.length() == 0) {
                    writer.println(instance.generateHeader() + ",sum-of-file-migrations"); 
                }
                StringBuilder row = new StringBuilder();
                row.append(String.format(Locale.ROOT, "%.3f", time));
                int migrationCount = 0;
                for (Object o : sa.components) {
                    if (o instanceof NoiseSensor) {
                        NoiseSensor ns = (NoiseSensor) o;
                        row.append(",");
                        row.append(String.format(Locale.ROOT, "%d", ns.noOfmigratedFiles));
                        migrationCount += ns.noOfmigratedFiles;
                        ns.noOfmigratedFiles = 0;
                    }
                }
                row.append(",");
                row.append(String.format(Locale.ROOT, "%d", migrationCount));
                writer.println(row.toString());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
