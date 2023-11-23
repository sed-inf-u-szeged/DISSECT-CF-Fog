package hu.u_szeged.inf.fog.simulator.util;

import hu.u_szeged.inf.fog.simulator.application.Application;
import hu.u_szeged.inf.fog.simulator.iot.Actuator;
import hu.u_szeged.inf.fog.simulator.iot.Device;
import hu.u_szeged.inf.fog.simulator.iot.EdgeDevice;
import hu.u_szeged.inf.fog.simulator.iot.Sensor;
import hu.u_szeged.inf.fog.simulator.physical.ComputingAppliance;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;



public class TimelineVisualiser {

    public static File generateTimeline(String resultDirectory) throws IOException {

        FileWriter fw = new FileWriter(resultDirectory + File.separator + "timeline.html");

        fw.write("<!DOCTYPE html><html><head>");
        fw.write("<script type=\'text/javascript\' src=\'https://www.gstatic.com/charts/loader.js\'></script>");
        fw.write("<script type=\'text/javascript\'>");
        fw.write("google.charts.load(\'current\', {packages:[\'timeline\']});");
        fw.write("google.charts.setOnLoadCallback(drawChart);");
        fw.write("function drawChart(){");
        fw.write("var container = document.getElementById('example');");
        fw.write("var chart = new google.visualization.Timeline(container);");
        fw.write("var dataTable = new google.visualization.DataTable();");
        fw.write("dataTable.addColumn({ type: 'string', id: 'Application' });");
        fw.write("dataTable.addColumn({ type: 'string', id: 'VM' });");
        fw.write("dataTable.addColumn({ type: 'date', id: 'Start' });");
        fw.write("dataTable.addColumn({ type: 'date', id: 'End' });");
        fw.write("dataTable.addRows([");

        for (ComputingAppliance ca : ComputingAppliance.getAllComputingAppliances()) {
            if (ca.applications.isEmpty()) {
                for (TimelineEntry tc : ca.timelineList) {
                    fw.write("[ '" + ca.name + "', '" + tc.vmId + "', new Date(0,0,0,0,0,0," + tc.start
                            + "), new Date(0,0,0,0,0,0," + tc.stop + ")],");
                }
            } else {
                for (Application a : ca.applications) {
                    for (TimelineEntry tc : a.timelineEntries) {
                        fw.write("[ '" + a.name + "', '" + tc.vmId + "', new Date(0,0,0,0,0,0," + tc.start
                                + "), new Date(0,0,0,0,0,0," + tc.stop + ")],");
                    }
                }
            }
        }

        for (Device device : Device.allDevices) {
            if (device instanceof EdgeDevice) {
                EdgeDevice ed = (EdgeDevice) device;
                for (TimelineEntry tc : ed.timelineEntries) {
                    fw.write("[ 'Device-" + tc.vmId + "', '" + "', new Date(0,0,0,0,0,0," + tc.start
                            + "), new Date(0,0,0,0,0,0," + tc.stop + ")],");
                }
            }
        }

        for (Actuator a : Actuator.allActuators) {
            for (TimelineEntry tc : a.actuatorEventList) {
                fw.write("[ '" + a.name + "', '" + tc.vmId + "', new Date(0,0,0,0,0,0," + tc.start
                        + "), new Date(0,0,0,0,0,0," + tc.stop + ")],");
            }
        }

        for (TimelineEntry tc : Sensor.sensorEventList) {
            fw.write("[ 'IoT sensors', '" + tc.vmId + "', new Date(0,0,0,0,0,0," + tc.start + "), new Date(0,0,0,0,0,0,"
                    + tc.stop + ")],");
        }

        fw.write("]);");
        fw.write("chart.draw(dataTable);");
        fw.write("}</script>");
        fw.write("</head><body>");
        fw.write("<div id=\"example\" style=\"height: 1500px; width=100%;\"></div>");
        fw.write("</body></html>");
        fw.close();

        return new File(resultDirectory + File.separator + "timeline.html");

    }

    public static class TimelineEntry {

        public TimelineEntry(long start, long stop, String vmId) {
            super();
            this.start = start;
            this.stop = stop;
            this.vmId = vmId;
        }

        public long start;
        public long stop;
        public String vmId;
    }
}
