package hu.u_szeged.inf.fog.simulator.energyprovider;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmscheduling.Scheduler;
import hu.u_szeged.inf.fog.simulator.demo.ScenarioBase;
import hu.u_szeged.inf.fog.simulator.workflow.scheduler.RenewableScheduler;
import hu.u_szeged.inf.fog.simulator.workflow.scheduler.WorkflowScheduler;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class RenewableVisualiser {

    public static void visualiseGraph () throws IOException {

        RenewableScheduler scheduler = (RenewableScheduler) WorkflowScheduler.schedulers.get(0);

        FileWriter fw = new FileWriter(ScenarioBase.resultDirectory +"/stored.html");

        fw.write("<html>");
        fw.write("<head>");
        fw.write("<script type=\"text/javascript\" src=\"https://www.gstatic.com/charts/loader.js\"></script>");
        fw.write("<script type=\"text/javascript\">");
        fw.write("google.charts.load('current', {'packages':['corechart']});");
        fw.write("google.charts.setOnLoadCallback(drawChart);");
        fw.write("function drawChart() {");
        fw.write(" var data = google.visualization.arrayToDataTable([");
        fw.write("  ['Time', 'Fossil', 'Renewable'],");
        fw.write("  ["+'0'+",  "+ scheduler.provider.fossilSource.production +",      "+  scheduler.provider.batteryStartingCharge+"],");

        for (float[] asd : scheduler.visualiser){
            fw.write("  ["+asd[0]+",  "+ scheduler.provider.fossilSource.production +",      "+  asd[1]+"],\n");
        }

        fw.write("   ]);");

        fw.write(" var options = {\n" +
                "          title: 'Stored Energy',\n" +
                "          hAxis: {title: 'Ticks',  titleTextStyle: {color: '#333'}},\n" +
                "          vAxis: {minValue: 0},\n" +
                "          isStacked: true\n" +
                "        };");

        fw.write("var chart = new google.visualization.AreaChart(document.getElementById('chart_div'));\n" +
                "        chart.draw(data, options);");
        fw.write(" }");
        fw.write("</script> </head>");
        fw.write("<body>\n" +
                "    <div id=\"chart_div\" style=\"width: 100%; height: 500px;\"></div>\n" +
                "  </body>");

        fw.write("</html>");
        fw.close();

        new File(ScenarioBase.resultDirectory +"/stored.html");


    }

    public static void visualiseCharge(Charge charge) throws IOException {

        FileWriter fw = new FileWriter(ScenarioBase.resultDirectory +"/charge.html");

        fw.write("<html>");
        fw.write("<head>");
        fw.write("<script type=\"text/javascript\" src=\"https://www.gstatic.com/charts/loader.js\"></script>");
        fw.write("<script type=\"text/javascript\">");
        fw.write("google.charts.load('current', {'packages':['corechart']});");
        fw.write("google.charts.setOnLoadCallback(drawChart);");
        fw.write("function drawChart() {");
        fw.write(" var data = google.visualization.arrayToDataTable([");
        fw.write("  ['Time', 'Charge'],");
        fw.write("  ["+'0'+",  "+  charge.provider.batteryStartingCharge+"],");

        for (float[] line : charge.charges){
            fw.write("  ["+line[0]+",  "+ line[1]+"],\n");
        }

        fw.write("   ]);");

        fw.write(" var options = {\n" +
                "          title: 'Stored Energy',\n" +
                "          hAxis: {title: 'Ticks',  titleTextStyle: {color: '#333'}},\n" +
                "          vAxis: {minValue: 0},\n" +
                "          isStacked: true\n" +
                "        };");

        fw.write("var chart = new google.visualization.AreaChart(document.getElementById('chart_div'));\n" +
                "        chart.draw(data, options);");
        fw.write(" }");
        fw.write("</script> </head>");
        fw.write("<body>\n" +
                "    <div id=\"chart_div\" style=\"width: 100%; height: 500px;\"></div>\n" +
                "  </body>");

        fw.write("</html>");
        fw.close();

        new File(ScenarioBase.resultDirectory +"/charge.html");

    }

    public static void visualiseSolar(Charge charge) throws IOException {

        FileWriter fw = new FileWriter(ScenarioBase.resultDirectory +"/solar.html");

        fw.write("<html>");
        fw.write("<head>");
        fw.write("<script type=\"text/javascript\" src=\"https://www.gstatic.com/charts/loader.js\"></script>");
        fw.write("<script type=\"text/javascript\">");
        fw.write("google.charts.load('current', {'packages':['corechart']});");
        fw.write("google.charts.setOnLoadCallback(drawChart);");
        fw.write("function drawChart() {");
        fw.write(" var data = google.visualization.arrayToDataTable([");
        fw.write("  ['Time', 'Production'],");

        for (float[] line : charge.solarProd){
            fw.write("  ["+line[0]+",  "+ line[1]+"],\n");
        }

        fw.write("   ]);");

        fw.write(" var options = {\n" +
                "          title: 'Produced power',\n" +
                "          hAxis: {title: 'Ticks',  titleTextStyle: {color: '#333'}},\n" +
                "          vAxis: {minValue: 0},\n" +
                "          isStacked: true\n" +
                "        };");

        fw.write("var chart = new google.visualization.AreaChart(document.getElementById('chart_div'));\n" +
                "        chart.draw(data, options);");
        fw.write(" }");
        fw.write("</script> </head>");
        fw.write("<body>\n" +
                "    <div id=\"chart_div\" style=\"width: 100%; height: 500px;\"></div>\n" +
                "  </body>");

        fw.write("</html>");
        fw.close();

        new File(ScenarioBase.resultDirectory +"/solar.html");

    }

    public static void visualiseWind(Charge charge) throws IOException {

        FileWriter fw = new FileWriter(ScenarioBase.resultDirectory +"/wind.html");

        fw.write("<html>");
        fw.write("<head>");
        fw.write("<script type=\"text/javascript\" src=\"https://www.gstatic.com/charts/loader.js\"></script>");
        fw.write("<script type=\"text/javascript\">");
        fw.write("google.charts.load('current', {'packages':['corechart']});");
        fw.write("google.charts.setOnLoadCallback(drawChart);");
        fw.write("function drawChart() {");
        fw.write(" var data = google.visualization.arrayToDataTable([");
        fw.write("  ['Time', 'Production'],");

        for (float[] line : charge.windProd){
            fw.write("  ["+line[0]+",  "+ line[1]+"],\n");
        }

        fw.write("   ]);");

        fw.write(" var options = {\n" +
                "          title: 'Produced power',\n" +
                "          hAxis: {title: 'Ticks',  titleTextStyle: {color: '#333'}},\n" +
                "          vAxis: {minValue: 0},\n" +
                "          isStacked: true\n" +
                "        };");

        fw.write("var chart = new google.visualization.AreaChart(document.getElementById('chart_div'));\n" +
                "        chart.draw(data, options);");
        fw.write(" }");
        fw.write("</script> </head>");
        fw.write("<body>\n" +
                "    <div id=\"chart_div\" style=\"width: 100%; height: 500px;\"></div>\n" +
                "  </body>");

        fw.write("</html>");
        fw.close();

        new File(ScenarioBase.resultDirectory +"/wind.html");

    }

}
