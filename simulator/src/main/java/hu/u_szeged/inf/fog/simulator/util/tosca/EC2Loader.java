package hu.u_szeged.inf.fog.simulator.util.tosca;

import hu.u_szeged.inf.fog.simulator.demo.ScenarioBase;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.Map;
import org.yaml.snakeyaml.Yaml;

public class EC2Loader {
    
    private static final String path = new StringBuilder(ScenarioBase.resourcePath)
            .append("TOSCA_examples").append(File.separator).toString();
    
    static void read() {
        Yaml yaml = new Yaml();
        
        InputStream input = null;
        try {
            input = new FileInputStream(new File(path + "ec2.yaml"));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        
        Map<String, Object> obj = yaml.load(input);
        System.out.println(obj);
    }
    
    // temp. main for testing
    public static void main(String[] args) {
        EC2Loader.read();
    }    
}
