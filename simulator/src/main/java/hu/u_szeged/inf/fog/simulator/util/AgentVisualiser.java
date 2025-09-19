package hu.u_szeged.inf.fog.simulator.util;

import hu.u_szeged.inf.fog.simulator.demo.ScenarioBase;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class AgentVisualiser {

    public static void visualise(Path... csvPaths) {
        List<Path> csvFiles = Arrays.asList(csvPaths);
        List<String> chartDivs = new ArrayList<>();
        List<String> chartDatas = new ArrayList<>();

        for (int idx = 0; idx < csvFiles.size(); idx++) {
            Path csv = csvFiles.get(idx);
            List<String> lines = null;
            try {
                lines = Files.readAllLines(csv, StandardCharsets.UTF_8);
            } catch (IOException e) {
                e.printStackTrace();
            }
            if (lines.isEmpty()) {
                continue;
            }

            String[] headers = lines.get(0).split(",");
            String headerRow = "["
                + String.join(", ",
                Arrays.stream(headers)
                .map(h -> "'" + h.trim() + "'")
                .collect(Collectors.toList())
                )
                + "]";

            List<String> jsRows = new ArrayList<>();
            for (int i = 1; i < lines.size(); i++) {
                String[] parts = lines.get(i).split(",");
                StringBuilder r = new StringBuilder();
                r.append("[").append(parts[0].trim()); 
                for (int j = 1; j < parts.length; j++) {
                    r.append(", ").append(parts[j].trim());
                }
                r.append("]");
                jsRows.add(r.toString());
            }

            String varName = "data" + idx;
            String title   = csv.getFileName().toString();

            chartDivs.add(
                "<div class=\"card\">"
                + "<h3>" + title + "</h3>"
                + "<div id=\"chart" + idx + "\" class=\"chart\"></div>"
                + "</div>"
            );

            chartDatas.add(
                " (function(){\n"
                + "    var " + varName + " = google.visualization.arrayToDataTable([\n"
                + "      " + headerRow + ",\n"
                + "      " + String.join(",\n      ", jsRows) + "\n"
                + "    ]);\n"
                + "    var options = {\n"
                + "      curveType: 'function', legend: { position: 'bottom' },\n"
                + "      chartArea: { left:70, top:40, width:'78%', height:'70%' },\n"
                + "      hAxis: { title: " + "'" + headers[0].trim() + "'" + " }\n"
                + "    };\n"
                + "    var chart = new google.visualization.LineChart(document.getElementById('chart" + idx + "'));\n"
                + "    chart.draw(" + varName + ", options);\n"
                + "    window.addEventListener('resize', function(){ chart.draw(" + varName + ", options); });\n"
                + "  })();\n"
            );
        }

        String html =
            "<!doctype html>\n"
            + "<html lang=\"en\">\n"
            + "<head>\n"
            + "  <meta charset=\"utf-8\">\n"
            + "  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n"
            + "  <title>" + "Simulation Results" + "</title>\n"
            + "  <script src=\"https://www.gstatic.com/charts/loader.js\"></script>\n"
            + "  <style>\n"
            + "    body{margin:0;font-family:system-ui,Arial,sans-serif;background:#fafafa}\n"
            + "    .grid{display:grid;grid-template-columns:repeat(2, minmax(0,1fr));gap:16px;padding:16px}\n"
            + "    .card{background:#fff;border:1px solid #eee;border-radius:12px;box-shadow:0 1px 3px rgba(0,0,0,.06);padding:12px}\n"
            + "    .card h3{margin:6px 8px 8px 8px;font-size:14px;font-weight:600;color:#333}\n"
            + "    .chart{width:100%;height:360px}\n"
            + "    @media (max-width:900px){ .grid{grid-template-columns:1fr} }\n"
            + "  </style>\n"
            + "</head>\n"
            + "<body>\n"
            + "  <div class=\"grid\">\n"
            + String.join("\n", chartDivs) + "\n"
            + "  </div>\n"
            + "  <script>\n"
            + "    google.charts.load('current', {packages:['corechart']});\n"
            + "    google.charts.setOnLoadCallback(drawAll);\n"
            + "    function drawAll(){\n"
            + String.join("", chartDatas)
            + "    }\n"
            + "  </script>\n"
            + "</body>\n"
            + "</html>\n";

        Path htmlPath = Paths.get(ScenarioBase.resultDirectory, "simulation-results.html");

        try {
            Files.write(htmlPath, html.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
