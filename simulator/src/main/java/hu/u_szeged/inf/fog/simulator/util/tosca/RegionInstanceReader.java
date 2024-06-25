package hu.u_szeged.inf.fog.simulator.util.tosca;

import hu.u_szeged.inf.fog.simulator.demo.ScenarioBase;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Iterator;
import org.json.JSONException;
import org.json.JSONObject;

public class RegionInstanceReader {

    private static final String path = new StringBuilder(ScenarioBase.resourcePath)
            .append("TOSCA_examples").append(File.separator).toString();
    
    static void read() throws IOException, JSONException {
        BufferedReader reader = new BufferedReader(new FileReader(path + "regions_instances_mapping.json"));
        StringBuilder stringBuilder = new StringBuilder();
        String line = null;
        String ls = System.getProperty("line.separator");
        while ((line = reader.readLine()) != null) {
            stringBuilder.append(line);
            stringBuilder.append(ls);
        }
        reader.close();

        String content = stringBuilder.toString();
        JSONObject jsonObject  = new JSONObject(content);
        
        Iterator<String> regions = jsonObject.keys();
        while (regions.hasNext()) {
            String region = regions.next();
            JSONObject regionDetails = jsonObject.getJSONObject(region);
            JSONObject coordinates = regionDetails.getJSONObject("coordinates");
            System.out.println("Region: " + region);
            System.out.println("Coordinates: ");
            System.out.println("  Latitude: " + coordinates.getString("latitude"));
            System.out.println("  Longitude: " + coordinates.getString("longitude"));
            
            JSONObject instances = regionDetails.getJSONObject("instances");
            Iterator<String> instanceTypes = instances.keys();
            
            while (instanceTypes.hasNext()) {
                String instanceType = instanceTypes.next();
                JSONObject instanceDetails = instances.getJSONObject(instanceType);
                System.out.println("  Instance Type: " + instanceType);
                System.out.println("    CPU: " + instanceDetails.getString("cpu"));
                System.out.println("    Memory: " + instanceDetails.getString("memory"));
                System.out.println("    Price: " + instanceDetails.getString("price"));
            }
        }
    }
    
    // temp. main for testing
    public static void main(String[] args) {
        try {
            RegionInstanceReader.read();
        } catch (IOException | JSONException e) {
            e.printStackTrace();
        }
    }    
}