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

    public static void visualise(String fileName, Path... csvPaths) {
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
            if (lines == null || lines.isEmpty()) {
                continue;
            }

            String[] headers = lines.get(0).split(",");
            String headerRow = "["
                + String.join(", ",
                Arrays.stream(headers)
                    .map(h -> "'" + h.trim().replace("'", "\\'") + "'")
                    .collect(Collectors.toList())
                )
                + "]";

            List<String> jsRows = new ArrayList<>();
            for (int i = 1; i < lines.size(); i++) {
                String[] parts = lines.get(i).split(",");
                if (parts.length == 0) {
                    continue;
                }
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
                  + "  <h3>" + title + "</h3>"
                  + "  <div id=\"ctrl" + idx + "\" class=\"controls\"></div>"
                  + "  <div id=\"chart" + idx + "\" class=\"chart\"></div>"
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
                  + "      hAxis: { title: '" + headers[0].trim().replace("'", "\\'") + "' }\n"
                  + "    };\n"
                  + "    var chartEl = document.getElementById('chart" + idx + "');\n"
                  + "    var ctrlEl  = document.getElementById('ctrl" + idx + "');\n"
                  + "    var chart   = new google.visualization.LineChart(chartEl);\n"
                  + "\n"
                  + "    var visible = {};\n"
                  + "    for (var c = 1; c < " + varName + ".getNumberOfColumns(); c++) visible[c] = false;\n"
                  + "\n"
                  + "    function buildControls(){\n"
                  + "      var html = '';\n"
                  + "      for (var c = 1; c < " + varName + ".getNumberOfColumns(); c++){\n"
                  + "        var label = " + varName + ".getColumnLabel(c);\n"
                  + "        var id = 'cb_" + idx + "_' + c;\n"
                  + "        html += '<label><input type=\"checkbox\" id=\"'+id+'\"> '+label+'</label>';\n"
                  + "      }\n"
                  + "      ctrlEl.innerHTML = html;\n"
                  + "      for (var c = 1; c < " + varName + ".getNumberOfColumns(); c++){\n"
                  + "        (function(col){\n"
                  + "          var cb = document.getElementById('cb_" + idx + "_' + col);\n"
                  + "          cb.checked = visible[col];\n"
                  + "          cb.addEventListener('change', function(){\n"
                  + "            visible[col] = this.checked;\n"
                  + "            draw();\n"
                  + "          });\n"
                  + "        })(c);\n"
                  + "      }\n"
                  + "    }\n"
                  + "\n"
                  + "    function buildView(){\n"
                  + "      var cols = [0]; \n"
                  + "      for (var c = 1; c < " + varName + ".getNumberOfColumns(); c++){\n"
                  + "        if (visible[c]){\n"
                  + "          cols.push(c); \n"
                  + "        } else {\n"
                  + "          (function(ci){\n"
                  + "            cols.push({\n"
                  + "              type: 'number', label: " + varName + ".getColumnLabel(ci),\n"
                  + "              calc: function(dt, row){ return null; }\n"
                  + "            });\n"
                  + "          })(c);\n"
                  + "        }\n"
                  + "      }\n"
                  + "      var view = new google.visualization.DataView(" + varName + ");\n"
                  + "      view.setColumns(cols);\n"
                  + "      return view;\n"
                  + "    }\n"
                  + "\n"
                  + "    function draw(){\n"
                  + "      var view = buildView();\n"
                  + "      chart.draw(view, options);\n"
                  + "    }\n"
                  + "\n"
                  + "    buildControls();\n"
                  + "    draw();\n"
                  + "    window.addEventListener('resize', draw);\n"
                  + "  })();\n"
            );
        }

        String html =
            "<!doctype html>\n"
              + "<html lang=\"en\">\n"
              + "<head>\n"
              + "  <meta charset=\"utf-8\">\n"
              + "  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n"
              + "  <title>Simulation Results</title>\n"
              + "  <script src=\"https://www.gstatic.com/charts/loader.js\"></script>\n"
              + "  <style>\n"
              + "    body{margin:0;font-family:system-ui,Arial,sans-serif;background:#fafafa}\n"
              + "    .grid{display:grid;grid-template-columns:repeat(2, minmax(0,1fr));gap:16px;padding:16px}\n"
              + "    .card{background:#fff;border:1px solid #eee;border-radius:12px;box-shadow:0 1px 3px rgba(0,0,0,.06);padding:12px}\n"
              + "    .card h3{margin:6px 8px 8px 8px;font-size:14px;font-weight:600;color:#333}\n"
              + "    .controls{display:flex;flex-wrap:wrap;gap:10px;margin:6px 8px 10px 8px;font-size:12px;color:#444}\n"
              + "    .controls label{display:inline-flex;align-items:center;gap:6px}\n"
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

        Path htmlPath = Paths.get(ScenarioBase.resultDirectory, fileName + ".html");

        try {
            Files.write(htmlPath, html.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
