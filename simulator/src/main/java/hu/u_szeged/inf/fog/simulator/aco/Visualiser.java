package hu.u_szeged.inf.fog.simulator.aco;

import hu.u_szeged.inf.fog.simulator.aco.ACOC;
import hu.u_szeged.inf.fog.simulator.iot.Device;
import hu.u_szeged.inf.fog.simulator.iot.mobility.GeoLocation;
import hu.u_szeged.inf.fog.simulator.iot.mobility.NomadicMobilityStrategy;
import hu.u_szeged.inf.fog.simulator.iot.mobility.RandomWalkMobilityStrategy;
import hu.u_szeged.inf.fog.simulator.iot.mobility.StaticMobilityStrategy;
import hu.u_szeged.inf.fog.simulator.physical.ComputingAppliance;
import hu.u_szeged.inf.fog.simulator.provider.Instance;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.ProcessBuilder.Redirect;
import java.util.*;


public class Visualiser {

    static boolean isContain(ArrayList<Pair> list, ComputingAppliance left, ComputingAppliance right) {
        for(Pair pair : list) {
            if( (pair.left == left && pair.right == right) || (pair.left == right && pair.right == left)) {
                return true;
            }
        }
        return false;

    }

    //public static void mapGenerator(String scriptPath, String resultDirectory, Device... devices) throws IOException {
    //    Visualiser.generateMap(scriptPath, resultDirectory, new ArrayList<>(Arrays.asList(devices)));
    //}

    public static void mapGenerator(String scriptPath, String resultDirectory, List<Ant> ants) throws IOException {
        Visualiser.generateMap(scriptPath, resultDirectory, ants);
    }

    public static void generateMap(String scriptPath, String resultDirectory, List<Ant> ants)
            throws IOException {

        String nodeInfoForMapScript = "";
        String latencyInfoForMapScript = "";

        ArrayList<Pair> checkedAppliances = new ArrayList<Pair>();

        for (Ant ant : ants){
            if(ant.clusterNumber!=0) {
                ComputingAppliance ca = (ComputingAppliance) ant.node;
                String parentName = String.valueOf(ant.clusterNumber);

                nodeInfoForMapScript = nodeInfoForMapScript.concat(ca.name).concat(",")
                        .concat(String.valueOf(ca.geoLocation.latitude)).concat(",")
                        .concat(String.valueOf(ca.geoLocation.longitude)).concat(",").concat(parentName).concat(",")
                        .concat(String.valueOf(ca.range)).concat(";");
                if (ca.parent != null) {
                    latencyInfoForMapScript = latencyInfoForMapScript.concat(String.valueOf(ca.geoLocation.latitude))
                            .concat(",").concat(String.valueOf(ca.geoLocation.longitude)).concat(",")
                            .concat(String.valueOf(ca.parent.geoLocation.latitude)).concat(",")
                            .concat(String.valueOf(ca.parent.geoLocation.longitude)).concat(",")
                            .concat(String.valueOf(ca.iaas.repositories.get(0).getLatencies()
                                    .get(ca.parent.iaas.repositories.get(0).getName())))
                            .concat(";");
                }

                for (ComputingAppliance coApp : ca.neighbors) {
                    if (!isContain(checkedAppliances, coApp, ca)) {
                        checkedAppliances.add(new Pair(coApp, ca));
                        latencyInfoForMapScript = latencyInfoForMapScript.concat(String.valueOf(ca.geoLocation.latitude))
                                .concat(",").concat(String.valueOf(ca.geoLocation.longitude)).concat(",")
                                .concat(String.valueOf(coApp.geoLocation.latitude)).concat(",")
                                .concat(String.valueOf(coApp.geoLocation.longitude)).concat(",")
                                .concat(String.valueOf(ca.iaas.repositories.get(0).getLatencies()
                                        .get(coApp.iaas.repositories.get(0).getName())))
                                .concat(";");
                    }
                }
            }
        }

        nodeInfoForMapScript = nodeInfoForMapScript.replaceAll(".$", "");
        latencyInfoForMapScript = latencyInfoForMapScript.replaceAll(".$", "");


        if (System.getProperty("os.name").contains("Windows")) {
            ProcessBuilder pb = new ProcessBuilder("python", scriptPath + "clusterMap.py", nodeInfoForMapScript,
                    latencyInfoForMapScript, resultDirectory, Integer.toString(0));

            pb.redirectOutput(Redirect.INHERIT);
            pb.redirectError(Redirect.INHERIT);
            pb.start();
        } else {
            ProcessBuilder pb = new ProcessBuilder("python3", scriptPath + "clusterMap.py", nodeInfoForMapScript,
                    latencyInfoForMapScript, resultDirectory, Integer.toString(0));

            pb.redirectOutput(Redirect.INHERIT);
            pb.redirectError(Redirect.INHERIT);
            pb.start();
        }
    }

    public static void mapGenerator(String scriptPath, String resultDirectory, ArrayList<LinkedHashMap<ComputingAppliance, Instance>> workflowArchitectures) throws IOException {
        Visualiser.generateMap(scriptPath, resultDirectory, workflowArchitectures);
    }

    public static void generateMap(String scriptPath, String resultDirectory, ArrayList<LinkedHashMap<ComputingAppliance, Instance>> workflowArchitectures)
            throws IOException {

        String nodeInfoForMapScript = "";
        String latencyInfoForMapScript = "";

        ArrayList<Pair> checkedAppliances = new ArrayList<Pair>();

        int i=0;
        for (LinkedHashMap<ComputingAppliance, Instance> workflowArchiteture : workflowArchitectures){
            i++;
            for(Map.Entry<ComputingAppliance, Instance> entry : workflowArchiteture.entrySet()){
                ComputingAppliance ca = entry.getKey();
                String parentName = String.valueOf(i);

                nodeInfoForMapScript = nodeInfoForMapScript.concat(ca.name).concat(",")
                        .concat(String.valueOf(ca.geoLocation.latitude)).concat(",")
                        .concat(String.valueOf(ca.geoLocation.longitude)).concat(",").concat(parentName).concat(",")
                        .concat(String.valueOf(ca.range)).concat(";");
                if (ca.parent != null) {
                    latencyInfoForMapScript = latencyInfoForMapScript.concat(String.valueOf(ca.geoLocation.latitude))
                            .concat(",").concat(String.valueOf(ca.geoLocation.longitude)).concat(",")
                            .concat(String.valueOf(ca.parent.geoLocation.latitude)).concat(",")
                            .concat(String.valueOf(ca.parent.geoLocation.longitude)).concat(",")
                            .concat(String.valueOf(ca.iaas.repositories.get(0).getLatencies()
                                    .get(ca.parent.iaas.repositories.get(0).getName())))
                            .concat(";");
                }

                for (ComputingAppliance coApp : ca.neighbors) {
                    if (!isContain(checkedAppliances, coApp, ca)) {
                        checkedAppliances.add(new Pair(coApp, ca));
                        latencyInfoForMapScript = latencyInfoForMapScript.concat(String.valueOf(ca.geoLocation.latitude))
                                .concat(",").concat(String.valueOf(ca.geoLocation.longitude)).concat(",")
                                .concat(String.valueOf(coApp.geoLocation.latitude)).concat(",")
                                .concat(String.valueOf(coApp.geoLocation.longitude)).concat(",")
                                .concat(String.valueOf(ca.iaas.repositories.get(0).getLatencies()
                                        .get(coApp.iaas.repositories.get(0).getName())))
                                .concat(";");
                    }
                }
            }
        }

        nodeInfoForMapScript = nodeInfoForMapScript.replaceAll(".$", "");
        latencyInfoForMapScript = latencyInfoForMapScript.replaceAll(".$", "");


        if (System.getProperty("os.name").contains("Windows")) {
            ProcessBuilder pb = new ProcessBuilder("python", scriptPath + "clusterMap.py", nodeInfoForMapScript,
                    latencyInfoForMapScript, resultDirectory, Integer.toString(0));

            pb.redirectOutput(Redirect.INHERIT);
            pb.redirectError(Redirect.INHERIT);
            pb.start();
        } else {
            ProcessBuilder pb = new ProcessBuilder("python3", scriptPath + "clusterMap.py", nodeInfoForMapScript,
                    latencyInfoForMapScript, resultDirectory, Integer.toString(0));

            pb.redirectOutput(Redirect.INHERIT);
            pb.redirectError(Redirect.INHERIT);
            pb.start();
        }
    }
}

class Pair{

    Pair(ComputingAppliance left, ComputingAppliance right){
        this.left=left;
        this.right=right;
    }

    ComputingAppliance left;
    ComputingAppliance right;
}