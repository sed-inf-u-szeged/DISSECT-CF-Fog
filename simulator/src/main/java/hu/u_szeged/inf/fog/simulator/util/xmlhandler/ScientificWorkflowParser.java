package hu.u_szeged.inf.fog.simulator.util.xmlhandler;

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

public class ScientificWorkflowParser {

    static Map<String, Integer> dependencyCounter = new HashMap<>();

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

    public static String parseToIoTWorkflow(String workflowfile) throws IOException {
        String new_file = Paths.get(workflowfile).getParent().toString() + "/IoT_"
                + Paths.get(workflowfile).getFileName().toString();
        FileWriter fw = new FileWriter(new_file);

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
                String jobID = jobElements.get(i).attributeValue("id");
                fw.write("\t<job id='" + jobID + "' runtime='" + jobElements.get(i).attributeValue("runtime") + "'>\n");
                int amount = dependencyCounter.get(jobID) != null ? dependencyCounter.get(jobID) : 0;
                fw.write("\t\t<uses link='input' type='compute' amount='" + amount + "' />\n");

                for (Map<String, String> dependecyMap : dependencyList) {

                    if (dependecyMap.get(jobID) != null) {

                        long size = 0;
                        for (int j = 0; j < jobElements.get(i).elements().size(); j++) {
                            if (jobElements.get(i).elements().get(j).attributeValue("link").equals("output")) {
                                size += Long.parseLong(jobElements.get(i).elements().get(j).attributeValue("size"));

                            }
                        }
                        fw.write("\t\t<uses link='output' " + "id='" + dependecyMap.get(jobID) + "' type='data' size='"
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

        return new_file;
    }

}