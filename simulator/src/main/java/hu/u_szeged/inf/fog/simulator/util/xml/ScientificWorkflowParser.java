package hu.u_szeged.inf.fog.simulator.util.xml;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;

/**
 * Parses a scientific workflow XML file and transforms it into an IoT workflow XML file.
 * Example files are located in: src/main/resources/demo/WORKFLOW_examples/
 */
public class ScientificWorkflowParser {

    /**
     * A map to store the number of dependencies for each job. 
     */
    static Map<String, Integer> dependencyCounter = new HashMap<>();

    /**
     * Parses the input XML file to extract and return with links between parent and child elements.
     *
     * @param filePath the path to the input XML file
     */
    private static List<Map<String, String>> getLinks(String filePath) {
        List<Map<String, String>> links = new ArrayList<>();
        SAXReader reader = new SAXReader();
        try {
            Document doc = reader.read(new File(filePath));
            Element root = doc.getRootElement();
            List<Element> childElements = root.elements("child");
            final int N = childElements.size();
            for (int i = 0; i < N; i++) {
                String target = childElements.get(i).attributeValue("ref");
                List<Element> parents = childElements.get(i).elements("parent");
                dependencyCounter.put(childElements.get(i).attributeValue("ref"),
                        childElements.get(i).elements().size());
                for (Element parent : parents) {
                    Map<String, String> map = new HashMap<>();
                    String source = parent.attributeValue("ref");
                    map.put(source, target);
                    links.add(map);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return links;
    }

    /**
     * Parses the input scientific workflow XML file and generates an IoT workflow XML file.
     *
     * @param workflowfile the path to the input scientific workflow XML file
     * @return the path to the generated IoT workflow XML file
     */
    public static String parseToIotWorkflow(String workflowfile) throws IOException {
        String newFile = Paths.get(workflowfile).getParent().toString() + "/IoT_"
                + Paths.get(workflowfile).getFileName().toString();
        FileWriter fw = new FileWriter(newFile);

        fw.write("<?xml version='1.0' encoding='UTF-8'?>\n");
        fw.write("<adag name='App1' repeat='1'>\n");

        SAXReader reader = new SAXReader();
        try {
            Document doc = reader.read(new File(Paths.get(workflowfile).getParent().toString() + "/"
                    + Paths.get(workflowfile).getFileName().toString()));
            Element root = doc.getRootElement();
            List<Element> jobElements = root.elements("job");
            List<Map<String, String>> dependencyList = ScientificWorkflowParser
                    .getLinks(Paths.get(workflowfile).getParent().toString() + "/"
                            + Paths.get(workflowfile).getFileName().toString());

            for (int i = 0; i < jobElements.size(); i++) {
                String jobId = jobElements.get(i).attributeValue("id");
                fw.write("\t<job id='" + jobId + "' runtime='" + jobElements.get(i).attributeValue("runtime") + "'>\n");
                int amount = dependencyCounter.get(jobId) != null ? dependencyCounter.get(jobId) : 0;
                fw.write("\t\t<uses link='input' type='compute' amount='" + amount + "' />\n");

                for (Map<String, String> dependecyMap : dependencyList) {
                    if (dependecyMap.get(jobId) != null) {
                        long size = 0;
                        for (int j = 0; j < jobElements.get(i).elements().size(); j++) {
                            if (jobElements.get(i).elements().get(j).attributeValue("link").equals("output")) {
                                size += Long.parseLong(jobElements.get(i).elements().get(j).attributeValue("size"));

                            }
                        }
                        fw.write("\t\t<uses link='output' " + "id='" + dependecyMap.get(jobId) + "' type='data' size='"
                                + size + "'/>\n");
                    }
                }
                fw.write("\t</job>\n");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        fw.write("</adag>\n");
        fw.close();

        return newFile;
    }
}