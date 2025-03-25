package hu.u_szeged.inf.fog.simulator.node;

import java.io.FileWriter;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public class NetworkVisualiser {

    public static void exportGraphToHtml(String filename) {
        StringBuilder nodesData = new StringBuilder();
        StringBuilder edgesData = new StringBuilder();
        Set<String> uniqueEdges = new HashSet<>();

        for (ComputingAppliance node : ComputingAppliance.allComputingAppliances) {
            nodesData.append("{\"id\": \"").append(node.name).append("\", \"label\": \"").append(node.name).append("\"},\n");
        }

        for (ComputingAppliance node : ComputingAppliance.allComputingAppliances) {
            for (ComputingAppliance neighbor : node.neighbors) {
                String edgeKey = node.name + "-" + neighbor.name;
                String reverseEdgeKey = neighbor.name + "-" + node.name;

                if (!uniqueEdges.contains(reverseEdgeKey)) {
                    edgesData.append("{\"source\": \"").append(node.name)
                            .append("\", \"target\": \"").append(neighbor.name).append("\"},\n");
                    uniqueEdges.add(edgeKey);
                }
            }
        }

        String htmlContent =
                "<!DOCTYPE html>\n"
                        + "<html lang=\"en\">\n"
                        + "<head>\n"
                        + "    <meta charset=\"UTF-8\">\n"
                        + "    <script src=\"https://d3js.org/d3.v6.min.js\"></script>\n"
                        + "    <title>Graph Visualization</title>\n"
                        + "</head>\n"
                        + "<body>\n"
                        + "    <svg width=\"800\" height=\"600\"></svg>\n"
                        + "    <script>\n"
                        + "        const graph = {\n"
                        + "            \"nodes\": [\n"
                        + "                " + nodesData + "\n"
                        + "            ],\n"
                        + "            \"edges\": [\n"
                        + "                " + edgesData + "\n"
                        + "            ]\n"
                        + "        };\n"
                        + "\n"
                        + "        const width = 800, height = 600;\n"
                        + "        const svg = d3.select(\"svg\");\n"
                        + "\n"
                        + "        const simulation = d3.forceSimulation(graph.nodes)\n"
                        + "            .force(\"link\", d3.forceLink(graph.edges).id(d => d.id).distance(75))\n"
                        + "            .force(\"charge\", d3.forceManyBody().strength(-75))\n"
                        + "            .force(\"center\", d3.forceCenter(width / 2, height / 2))\n"
                        + "            .force(\"collide\", d3.forceCollide(30))\n"
                        + "            .force(\"x\", d3.forceX(width / 2).strength(0.1))\n"
                        + "            .force(\"y\", d3.forceY(height / 2).strength(0.1));\n"
                        + "\n"
                        + "        const link = svg.selectAll(\"line\")\n"
                        + "            .data(graph.edges)\n"
                        + "            .enter().append(\"line\")\n"
                        + "            .style(\"stroke\", \"#aaa\")\n"
                        + "            .style(\"stroke-width\", 2);\n"
                        + "\n"
                        + "        const node = svg.selectAll(\"circle\")\n"
                        + "            .data(graph.nodes)\n"
                        + "            .enter().append(\"circle\")\n"
                        + "            .attr(\"r\", 8)\n"
                        + "            .style(\"fill\", \"steelblue\")\n"
                        + "            .call(drag(simulation));\n"
                        + "\n"
                        + "        // Node labels\n"
                        + "        const labels = svg.selectAll(\"text\")\n"
                        + "            .data(graph.nodes)\n"
                        + "            .enter().append(\"text\")\n"
                        + "            .attr(\"x\", d => d.x)\n"
                        + "            .attr(\"y\", d => d.y)\n"
                        + "            .attr(\"dy\", -12)\n"
                        + "            .attr(\"text-anchor\", \"middle\")\n"
                        + "            .style(\"font-size\", \"12px\")\n"
                        + "            .style(\"fill\", \"black\")\n"
                        + "            .text(d => d.label);\n"
                        + "\n"
                        + "        function drag(simulation) {\n"
                        + "            return d3.drag()\n"
                        + "                .on(\"start\", (event, d) => {\n"
                        + "                    if (!event.active) simulation.alphaTarget(0.3).restart();\n"
                        + "                    d.fx = d.x;\n"
                        + "                    d.fy = d.y;\n"
                        + "                })\n"
                        + "                .on(\"drag\", (event, d) => {\n"
                        + "                    d.fx = event.x;\n"
                        + "                    d.fy = event.y;\n"
                        + "                })\n"
                        + "                .on(\"end\", (event, d) => {\n"
                        + "                    if (!event.active) simulation.alphaTarget(0);\n"
                        + "                    d.fx = null;\n"
                        + "                    d.fy = null;\n"
                        + "                });\n"
                        + "        }\n"
                        + "\n"
                        + "        simulation.on(\"tick\", () => {\n"
                        + "            link\n"
                        + "                .attr(\"x1\", d => d.source.x)\n"
                        + "                .attr(\"y1\", d => d.source.y)\n"
                        + "                .attr(\"x2\", d => d.target.x)\n"
                        + "                .attr(\"y2\", d => d.target.y);\n"
                        + "\n"
                        + "            node\n"
                        + "                .attr(\"cx\", d => d.x)\n"
                        + "                .attr(\"cy\", d => d.y);\n"
                        + "\n"
                        + "            labels\n"
                        + "                .attr(\"x\", d => d.x)\n"
                        + "                .attr(\"y\", d => d.y);\n"
                        + "        });\n"
                        + "    </script>\n"
                        + "</body>\n"
                        + "</html>";
        try (FileWriter file = new FileWriter(filename)) {
            file.write(htmlContent);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}