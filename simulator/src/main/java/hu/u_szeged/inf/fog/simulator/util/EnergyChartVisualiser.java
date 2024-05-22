package hu.u_szeged.inf.fog.simulator.util;

import hu.u_szeged.inf.fog.simulator.iot.Device;
import hu.u_szeged.inf.fog.simulator.node.ComputingAppliance;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;

public class EnergyChartVisualiser {

    public static void generateApplicationEnergyChart(String resultDirectory) throws IOException {

        FileWriter fw = new FileWriter(resultDirectory + File.separator + "applicationEnergy.html");

        fw.write("<!DOCTYPE html><html><head>");
        fw.write("<script type=\'text/javascript\' src=\'https://www.gstatic.com/charts/loader.js\'></script>");
        fw.write("<script type=\'text/javascript\'>");
        fw.write("google.charts.load('current', {packages: ['corechart']});");
        fw.write("google.charts.setOnLoadCallback(drawChart);");
        fw.write("function drawChart() {");
        fw.write("var data = google.visualization.arrayToDataTable([");
        fw.write("['', 'Node', { role: 'style' }],");

        for (ComputingAppliance ca : ComputingAppliance.getAllComputingAppliances()) {
            fw.write("['" + ca.name + "', " + ca.energyConsumption / 1000 / 3_600_000 + ", '#f2a03d'],");
        }

        fw.write("]);");
        fw.write("var options = {title: 'Energy Consumption of the nodes (kWh)', legend: { position: 'none' }};");
        fw.write("var chart = new google.visualization.ColumnChart(document.getElementById('container'));");
        fw.write("chart.draw(data, options);");
        fw.write("}");
        fw.write("</script>");
        fw.write("</head><body>");
        fw.write("<div id=\"container\" style=\"height: 800px; width=100%;\"></div>");
        fw.write(" </body></html>");
        fw.close();
    }

    public static void generateDeviceEnergyChart(String resultDirectory) throws IOException {

        FileWriter fw = new FileWriter(resultDirectory + File.separator + "deviceEnergy.html");

        ArrayList<Double> consumptions = new ArrayList<Double>();

        fw.write("<!DOCTYPE html><html><head>");
        fw.write("<script type=\'text/javascript\' src=\'https://www.gstatic.com/charts/loader.js\'></script>");
        fw.write("<script type=\'text/javascript\'>");
        fw.write("google.charts.load('current', {packages: ['corechart']});");
        fw.write("google.charts.setOnLoadCallback(drawChart);");
        fw.write("function drawChart() {");
        fw.write("var data = google.visualization.arrayToDataTable([");
        fw.write("['Consumption', 'Count', { role: 'style' }],");

        for (Device device : Device.allDevices) {
            consumptions.add(device.energyConsumption);
        }

        Collections.sort(consumptions);

        int length = removeMultipleElements(consumptions);

        for (int i = 0; i < length; i++) {
            fw.write("[" + String.valueOf(consumptions.get(i) / 1000 / 3_600_000) + ","
                    + Collections.frequency(consumptions, consumptions.get(i)) + ", '#a2f03c'],");
        }

        fw.write("]);");

        fw.write("var options = {title: 'Energy Consumption of the IoT devices (kWh)', legend: { position: 'none' }};");

        fw.write("var chart = new google.visualization.ColumnChart(document.getElementById('container'));");
        fw.write("chart.draw(data, options);");
        fw.write("}");
        fw.write("</script>");
        fw.write("</head><body>");
        fw.write("<div id=\"container\" style=\"height: 800px; width=100%;\"></div>");
        fw.write("</body></html>");
        fw.close();
    }

    private static int removeMultipleElements(ArrayList<Double> al) {
        if (al.size() == 0 || al.size() == 1) {
            return al.size();
        }

        int j = 0;

        for (int i = 0; i < al.size() - 1; i++) {
            if (al.get(i) != al.get(i + 1)) {
                al.set(j++, al.get(i));
            }
        }

        al.set(j++, al.get(al.size() - 1));

        return j;
    }

}
