package hu.u_szeged.inf.fog.simulator.util;

import hu.u_szeged.inf.fog.simulator.application.Application;
import hu.u_szeged.inf.fog.simulator.iot.Actuator;
import hu.u_szeged.inf.fog.simulator.iot.Device;
import hu.u_szeged.inf.fog.simulator.iot.EdgeDevice;
import hu.u_szeged.inf.fog.simulator.iot.Sensor;
import hu.u_szeged.inf.fog.simulator.node.ComputingAppliance;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

/**
 * Provides functionality to generate a timeline visualization in HTML format
 * that presents main simulation events.
 */
public class TimelineVisualiser {

    /**
     * Generates a timeline visualization resulting in an HTML file.
     *
     * @param resultDirectory he directory where the HTML file will be generated
     * @return The generated HTML file.
     */
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
                    fw.write("[ '" + ca.name + "', '" + tc.text + "', new Date(0,0,0,0,0,0," + tc.start
                            + "), new Date(0,0,0,0,0,0," + tc.stop + ")],");
                }
            } else {
                for (Application a : ca.applications) {
                    for (TimelineEntry tc : a.timelineEntries) {
                        fw.write("[ '" + a.name + "', '" + tc.text + "', new Date(0,0,0,0,0,0," + tc.start
                                + "), new Date(0,0,0,0,0,0," + tc.stop + ")],");
                    }
                }
            }
        }

        for (Device device : Device.allDevices) {
            if (device instanceof EdgeDevice) {
                EdgeDevice ed = (EdgeDevice) device;
                for (TimelineEntry tc : ed.timelineEntries) {
                    fw.write("[ 'Device-" + tc.text + "', '" + "', new Date(0,0,0,0,0,0," + tc.start
                            + "), new Date(0,0,0,0,0,0," + tc.stop + ")],");
                }
            }
        }

        for (Actuator a : Actuator.allActuators) {
            for (TimelineEntry tc : a.actuatorEventList) {
                fw.write("[ '" + a.name + "', '" + tc.text + "', new Date(0,0,0,0,0,0," + tc.start
                        + "), new Date(0,0,0,0,0,0," + tc.stop + ")],");
            }
        }

        for (TimelineEntry tc : Sensor.sensorEventList) {
            fw.write("[ 'IoT sensors', '" + tc.text + "', new Date(0,0,0,0,0,0," + tc.start + "), new Date(0,0,0,0,0,0,"
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

    /**
     * Represents a single entry in the timeline.
     */
    public static class TimelineEntry {

        /**
         * Start time of the timeline event.
         */
        public long start;
        
        /**
         * End time of the timeline event.
         */
        public long stop;
        
        /**
         * Description of the event.
         */
        public String text;

        /**
         * Constructs a timeline entry with the specified parameters.
         *
         * @param start start time of the timeline entry
         * @param stop  end time of the timeline entry
         * @param text  description of the event
         */
        public TimelineEntry(long start, long stop, String text) {
            super();
            this.start = start;
            this.stop = stop;
            this.text = text;
        }
    }
}