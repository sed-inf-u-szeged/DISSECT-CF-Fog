package hu.u_szeged.inf.fog.simulator.util;

import hu.u_szeged.inf.fog.simulator.aco.ACOC;
import hu.u_szeged.inf.fog.simulator.iot.Device;
import hu.u_szeged.inf.fog.simulator.iot.mobility.GeoLocation;
import hu.u_szeged.inf.fog.simulator.iot.mobility.NomadicMobilityStrategy;
import hu.u_szeged.inf.fog.simulator.iot.mobility.RandomWalkMobilityStrategy;
import hu.u_szeged.inf.fog.simulator.iot.mobility.StaticMobilityStrategy;
import hu.u_szeged.inf.fog.simulator.physical.ComputingAppliance;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.ProcessBuilder.Redirect;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;



public class MapVisualiser {
    
   static boolean isContain(ArrayList<Pair> list, ComputingAppliance left, ComputingAppliance right) {
       for(Pair pair : list) {
           if( (pair.left == left && pair.right == right) || (pair.left == right && pair.right == left)) {
               return true;
           }
       }
    return false;
       
   }

    public static void mapGenerator(String scriptPath, String resultDirectory, Device... devices) throws IOException {
        MapVisualiser.generateMap(scriptPath, resultDirectory, new ArrayList<>(Arrays.asList(devices)));
    }
    
    public static void mapGenerator(String scriptPath, String resultDirectory, ArrayList<Device> devices) throws IOException {
        MapVisualiser.generateMap(scriptPath, resultDirectory, devices);
    }

    public static void generateMap(String scriptPath, String resultDirectory, ArrayList<Device> devices)
            throws IOException {

        String nodeInfoForMapScript = "";
        String latencyInfoForMapScript = "";

       // HashMap<ComputingAppliance, ComputingAppliance> checkedAppliances = new HashMap<ComputingAppliance, ComputingAppliance>();
        ArrayList<Pair> checkedAppliances = new ArrayList<Pair>();

        for (ComputingAppliance ca : ComputingAppliance.getAllComputingAppliances()) {
            String parentName = ca.parent != null ? ca.parent.name : "null";

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
                    checkedAppliances.add(new Pair(coApp,ca));
                    
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

        nodeInfoForMapScript = nodeInfoForMapScript.replaceAll(".$", "");
        latencyInfoForMapScript = latencyInfoForMapScript.replaceAll(".$", "");

        for (int i = 0; i < devices.size(); i++) {
            FileWriter fw = new FileWriter(resultDirectory + File.separator + "devicePath-" + i + ".csv");

            Device device = devices.get(i);
            if (device.mobilityStrategy.getClass().getSimpleName()
                    .equals(RandomWalkMobilityStrategy.class.getSimpleName())) {
                RandomWalkMobilityStrategy ms = (RandomWalkMobilityStrategy) device.mobilityStrategy;
                fw.write(device.mobilityStrategy.getClass().getSimpleName() + "," + ms.startPosition.latitude + ","
                        + ms.startPosition.longitude + "," + ms.radius + "\n");
            } else if (device.mobilityStrategy.getClass().getSimpleName()
                    .equals(NomadicMobilityStrategy.class.getSimpleName())) {
                NomadicMobilityStrategy ms = (NomadicMobilityStrategy) device.mobilityStrategy;
                fw.write(device.mobilityStrategy.getClass().getSimpleName() + "," + ms.startPosition.latitude + ","
                        + ms.startPosition.longitude + "," + 0 + "\n");
            } else if (device.mobilityStrategy.getClass().getSimpleName()
                    .equals(StaticMobilityStrategy.class.getSimpleName())) {
                StaticMobilityStrategy ms = (StaticMobilityStrategy) device.mobilityStrategy;
                fw.write(device.mobilityStrategy.getClass().getSimpleName() + "," + ms.startPosition.latitude + ","
                        + ms.startPosition.longitude + "," + 0 + "\n");
            }

            for (GeoLocation geoLocation : device.devicePath) {
                fw.write(geoLocation.latitude + "," + geoLocation.longitude + "\n");
            }

            fw.close();
        }

        if (System.getProperty("os.name").contains("Windows")) {
            ProcessBuilder pb = new ProcessBuilder("python", scriptPath + "map.py", nodeInfoForMapScript,
                    latencyInfoForMapScript, resultDirectory, Integer.toString(devices.size()));
            // System.out.println(pb.command());

            pb.redirectOutput(Redirect.INHERIT);
            pb.redirectError(Redirect.INHERIT);
            pb.start();
        } else {
            ProcessBuilder pb = new ProcessBuilder("python3", scriptPath + "map.py", nodeInfoForMapScript,
                    latencyInfoForMapScript, resultDirectory, Integer.toString(devices.size()));

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