package hu.u_szeged.inf.fog.simulator.energyprovider;

import hu.u_szeged.inf.fog.simulator.demo.ScenarioBase;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;

public class RenewableVisualiser {

    public static void visualiseStoredEnergy(ArrayList<Provider> providers) throws IOException {

        for (Provider provider : providers) {

            File file = new File(ScenarioBase.resultDirectory +"/Provider-"+ provider.id +"/stored.html");
            file.getParentFile().mkdirs();
            FileWriter fw = new FileWriter(ScenarioBase.resultDirectory +"/Provider-"+ provider.id +"/stored.html");

            fw.write("<html>");
            fw.write("<head>");
            fw.write("<script type=\"text/javascript\" src=\"https://www.gstatic.com/charts/loader.js\"></script>");
            fw.write("<script type=\"text/javascript\">");
            fw.write("google.charts.load('current', {'packages':['corechart']});");
            fw.write("google.charts.setOnLoadCallback(drawChart);");
            fw.write("function drawChart() {");
            fw.write(" var data = google.visualization.arrayToDataTable([");
            fw.write("  ['Time', 'Fossil', 'Renewable'],");

            fw.write("[" + '0' + "," + provider.fossilSource.production + "," + provider.batteryStartingCharge + "],");

            for (float[] record : provider.energyRecords) {
                fw.write("[" + record[0] + "," + provider.fossilSource.production + "," + record[1] + "],\n");
            }

            fw.write("]);");

            fw.write(" var options = {\n" 
                    + "          title: 'Stored Energy',\n" 
                    + "          hAxis: {title: 'Ticks',  titleTextStyle: {color: '#333'}},\n" 
                    + "          vAxis: {minValue: 0},\n" 
                    + "          isStacked: true\n" 
                    + "        };");

            fw.write("var chart = new google.visualization.AreaChart(document.getElementById('chart_div'));\n"
                    + "        chart.draw(data, options);");
            fw.write(" }");
            fw.write("</script> </head>");
            fw.write("<body>\n"
                    + "    <div id=\"chart_div\" style=\"width: 100%; height: 500px;\"></div>\n"
                    + "  </body>");

            fw.write("</html>");
            fw.close();

        }
    }

    public static void visualiseSolar(ArrayList<Provider> providers) throws IOException {

        for (Provider provider : providers) {
            File file = new File(ScenarioBase.resultDirectory + "/Provider-" + provider.id + "/Solar.html");
            file.getParentFile().mkdirs();
            FileWriter fw = new FileWriter(ScenarioBase.resultDirectory + "/Provider-" + provider.id + "/Solar.html");

            fw.write("<html>");
            fw.write("<head>");
            fw.write("<script type=\"text/javascript\" src=\"https://www.gstatic.com/charts/loader.js\"></script>");
            fw.write("<script type=\"text/javascript\">");
            fw.write("google.charts.load('current', {'packages':['corechart']});");
            fw.write("google.charts.setOnLoadCallback(drawChart);");
            fw.write("function drawChart() {");
            fw.write(" var data = google.visualization.arrayToDataTable([");
            fw.write("  ['Time', 'Production'],");

            for (float[] record : provider.solarRecords) {
                fw.write("[" + record[0] + "," + record[1] + "],\n");
            }

            fw.write("]);");

            fw.write(" var options = {\n"
                    + "          title: 'Produced power (Wh)',\n"
                    + "          hAxis: {title: 'Ticks',  titleTextStyle: {color: '#333'}},\n"
                    + "          vAxis: {minValue: 0},\n"
                    + "          isStacked: true\n"
                    + "        };");

            fw.write("var chart = new google.visualization.AreaChart(document.getElementById('chart_div'));\n"
                    + "        chart.draw(data, options);");
            fw.write(" }");
            fw.write("</script> </head>");
            fw.write("<body>\n"
                    + "    <div id=\"chart_div\" style=\"width: 100%; height: 500px;\"></div>\n"
                    + "  </body>");

            fw.write("</html>");
            fw.close();

            new File(ScenarioBase.resultDirectory + "/Provider-" + provider.id + "/Solar.html");
        }
    }

    public static void visualiseWind(ArrayList<Provider> providers) throws IOException {

        for (Provider provider : providers) {
            File file = new File(ScenarioBase.resultDirectory + "/Provider-" + provider.id + "/Wind.html");
            file.getParentFile().mkdirs();
            FileWriter fw = new FileWriter(ScenarioBase.resultDirectory + "/Provider-" + provider.id + "/Wind.html");

            fw.write("<html>");
            fw.write("<head>");
            fw.write("<script type=\"text/javascript\" src=\"https://www.gstatic.com/charts/loader.js\"></script>");
            fw.write("<script type=\"text/javascript\">");
            fw.write("google.charts.load('current', {'packages':['corechart']});");
            fw.write("google.charts.setOnLoadCallback(drawChart);");
            fw.write("function drawChart() {");
            fw.write(" var data = google.visualization.arrayToDataTable([");
            fw.write("  ['Time', 'Production'],");

            for (float[] record : provider.windRecords) {
                fw.write("[" + record[0] + "," + record[1] + "],\n");
            }

            fw.write("]);");

            fw.write(" var options = {\n"
                    + "          title: 'Produced power (Wh)',\n"
                    + "          hAxis: {title: 'Ticks',  titleTextStyle: {color: '#333'}},\n"
                    + "          vAxis: {minValue: 0},\n"
                    + "          isStacked: true\n"
                    + "        };");

            fw.write("var chart = new google.visualization.AreaChart(document.getElementById('chart_div'));\n"
                    + "        chart.draw(data, options);");
            fw.write(" }");
            fw.write("</script> </head>");
            fw.write("<body>\n"
                    + "    <div id=\"chart_div\" style=\"width: 100%; height: 500px;\"></div>\n"
                    + "  </body>");

            fw.write("</html>");
            fw.close();


        }
    }

}
