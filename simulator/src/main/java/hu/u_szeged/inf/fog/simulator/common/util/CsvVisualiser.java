package hu.u_szeged.inf.fog.simulator.common.util;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CsvVisualiser {

    private final String fileName;
    private final List<Path> csvFiles = new ArrayList<>();

    private CsvVisualiser(String fileName, Path... csvPaths) {
        this.fileName = fileName;
        if (csvPaths != null) Collections.addAll(this.csvFiles, csvPaths);
    }

    public static CsvVisualiser visualise(String fileName, Path... csvPaths) {
        return new CsvVisualiser(fileName, csvPaths);
    }

    public Path write() {
        Path outDir = Paths.get(ScenarioBase.RESULT_DIRECTORY);
        try { Files.createDirectories(outDir); } catch (Exception ignored) {}

        Path assetsDir = outDir.resolve(fileName + "_assets");
        try { Files.createDirectories(assetsDir); } catch (Exception ignored) {}

        StringBuilder chartDivs = new StringBuilder();
        StringBuilder dataScriptTags = new StringBuilder();
        StringBuilder chartScripts = new StringBuilder();

        int idx = 0;
        for (Path csv : csvFiles) {
            String dataJsName = "data" + idx + ".js";
            Path dataJsPath = assetsDir.resolve(dataJsName);

            writeGoogleDataTableAsJs(csv, dataJsPath, idx);

            chartDivs.append("<div class=\"card\">")
                    .append("<h3>").append(escapeHtml(csv.getFileName().toString())).append("</h3>")
                    .append("<div id=\"ctrl").append(idx).append("\" class=\"controls\"></div>")
                    .append("<div id=\"chart").append(idx).append("\" class=\"chart\"></div>")
                    .append("</div>\n");

            dataScriptTags.append("<script src=\"")
                    .append(escapeHtml(fileName)).append("_assets/")
                    .append(escapeHtml(dataJsName))
                    .append("\"></script>\n");

            chartScripts.append(buildChartJs(idx)).append("\n");

            idx++;
        }

        String html = buildHtml(chartDivs.toString(), dataScriptTags.toString(), chartScripts.toString());
        Path htmlPath = outDir.resolve(fileName + ".html");

        try {
            Files.writeString(htmlPath, html, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (Exception ignored) {}

        return htmlPath;
    }

    private static void writeGoogleDataTableAsJs(Path csv, Path outJs, int idx) {
        try (BufferedReader br = Files.newBufferedReader(csv, StandardCharsets.UTF_8);
             BufferedWriter bw = Files.newBufferedWriter(outJs, StandardCharsets.UTF_8,
                     StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {

            String headerLine = br.readLine();
            String[] headers = headerLine.split(",", -1);

            bw.write("window.__noiseapp_data = window.__noiseapp_data || [];\n");
            bw.write("window.__noiseapp_data[" + idx + "] = {\"cols\":[");
            bw.write("{\"label\":\"" + escapeJson(headers[0].trim()) + "\",\"type\":\"number\"}");
            for (int i = 1; i < headers.length; i++) {
                bw.write(",{\"label\":\"" + escapeJson(headers[i].trim()) + "\",\"type\":\"number\"}");
            }
            bw.write("],\"rows\":[");

            String line;
            boolean first = true;
            while ((line = br.readLine()) != null) {
                if (!first) bw.write(",");
                first = false;

                String[] parts = line.split(",", -1);

                bw.write("{\"c\":[");
                bw.write(valNumber(parts, 0));
                for (int i = 1; i < headers.length; i++) {
                    bw.write(",");
                    bw.write(valNumber(parts, i));
                }
                bw.write("]}");
            }

            bw.write("]};\n");

        } catch (Exception ignored) {}
    }

    private static String buildChartJs(int idx) {
        return ""
                + "(function(){\n"
                + "  var json = window.__noiseapp_data[" + idx + "];\n"
                + "  var dt = new google.visualization.DataTable(json);\n"
                + "  var chartEl = document.getElementById('chart" + idx + "');\n"
                + "  var ctrlEl  = document.getElementById('ctrl" + idx + "');\n"
                + "  var chart   = new google.visualization.LineChart(chartEl);\n"
                + "  var palette = ['#3366cc','#dc3912','#ff9900','#109618','#990099','#0099c6','#dd4477','#66aa00','#b82e2e','#316395','#994499','#22aa99','#aaaa11','#6633cc','#e67300','#8b0707','#651067','#329262','#5574a6','#3b3eac'];\n"
                + "  var colors = [];\n"
                + "  for (var i=1;i<dt.getNumberOfColumns();i++) colors.push(palette[(i-1)%palette.length]);\n"
                + "  var options = {\n"
                + "    curveType: 'function',\n"
                + "    legend: { position: 'none' },\n"
                + "    colors: colors,\n"
                + "    chartArea: { left:70, top:40, width:'78%', height:'70%' },\n"
                + "    hAxis: { title: dt.getColumnLabel(0) }\n"
                + "  };\n"
                + "  var visible = {};\n"
                + "  for (var c=1;c<dt.getNumberOfColumns();c++) visible[c]=false;\n"
                + "  function rebuildControls(){\n"
                + "    var html='';\n"
                + "    for (var c=1;c<dt.getNumberOfColumns();c++){\n"
                + "      var label=dt.getColumnLabel(c);\n"
                + "      var id='tg_" + idx + "_'+c;\n"
                + "      var on=visible[c];\n"
                + "      var style='color:'+colors[c-1]+';'+(on?'font-weight:700;':'font-weight:400;')+'opacity:'+(on?'1':'0.55')+';';\n"
                + "      html += '<button type=\"button\" class=\"pill\" id=\"'+id+'\" style=\"'+style+'\">'+label+'</button>';\n"
                + "    }\n"
                + "    ctrlEl.innerHTML=html;\n"
                + "    for (var c=1;c<dt.getNumberOfColumns();c++){\n"
                + "      (function(col){\n"
                + "        var b=document.getElementById('tg_" + idx + "_'+col);\n"
                + "        b.addEventListener('click', function(){ visible[col]=!visible[col]; rebuildControls(); scheduleDraw(); });\n"
                + "      })(c);\n"
                + "    }\n"
                + "  }\n"
                + "  var t=null;\n"
                + "  function scheduleDraw(){ if(t) clearTimeout(t); t=setTimeout(draw,20); }\n"
                + "  function buildView(){\n"
                + "    var cols=[0];\n"
                + "    for (var c=1;c<dt.getNumberOfColumns();c++){\n"
                + "      if(visible[c]) cols.push(c);\n"
                + "      else (function(ci){ cols.push({type:'number',label:dt.getColumnLabel(ci),calc:function(dtt,row){return null;}}); })(c);\n"
                + "    }\n"
                + "    var view=new google.visualization.DataView(dt);\n"
                + "    view.setColumns(cols);\n"
                + "    return view;\n"
                + "  }\n"
                + "  function draw(){\n"
                + "    var any=false;\n"
                + "    for (var c=1;c<dt.getNumberOfColumns();c++) if(visible[c]) { any=true; break; }\n"
                + "    if(!any){ chart.clearChart(); return; }\n"
                + "    chart.draw(buildView(), options);\n"
                + "  }\n"
                + "  google.visualization.events.addListener(chart, 'select', function(){\n"
                + "    var sel=chart.getSelection();\n"
                + "    if(!sel || !sel.length) return;\n"
                + "    var item=sel[0];\n"
                + "    if(item.row == null && item.column != null && item.column > 0){\n"
                + "      visible[item.column]=!visible[item.column];\n"
                + "      chart.setSelection([]);\n"
                + "      rebuildControls();\n"
                + "      scheduleDraw();\n"
                + "    }\n"
                + "  });\n"
                + "  rebuildControls();\n"
                + "  draw();\n"
                + "  window.addEventListener('resize', scheduleDraw);\n"
                + "})();";
    }

    private static String buildHtml(String chartDivs, String dataScriptTags, String chartScripts) {
        return ""
                + "<!doctype html>\n"
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
                + "    .controls{display:flex;flex-wrap:wrap;gap:8px;margin:6px 8px 10px 8px;font-size:12px}\n"
                + "    .pill{border:1px solid #e5e7eb;background:#fff;border-radius:999px;padding:4px 10px;font:inherit;cursor:pointer;line-height:1.6}\n"
                + "    .pill:hover{border-color:#d1d5db}\n"
                + "    .chart{width:100%;height:360px}\n"
                + "    @media (max-width:900px){ .grid{grid-template-columns:1fr} }\n"
                + "  </style>\n"
                + "</head>\n"
                + "<body>\n"
                + "  <div class=\"grid\">\n"
                + chartDivs
                + "  </div>\n"
                + dataScriptTags
                + "  <script>\n"
                + "    google.charts.load('current', {packages:['corechart']});\n"
                + "    google.charts.setOnLoadCallback(drawAll);\n"
                + "    function drawAll(){\n"
                + chartScripts
                + "    }\n"
                + "  </script>\n"
                + "</body>\n"
                + "</html>\n";
    }

    private static String valNumber(String[] parts, int idx) {
        if (idx >= parts.length) return "{\"v\":null}";
        String s = parts[idx].trim();
        if (s.isEmpty()) return "{\"v\":null}";
        return "{\"v\":" + s + "}";
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String escapeHtml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
