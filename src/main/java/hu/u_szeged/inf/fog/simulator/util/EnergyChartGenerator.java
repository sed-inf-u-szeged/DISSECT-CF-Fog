package hu.u_szeged.inf.fog.simulator.util;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import hu.u_szeged.inf.fog.simulator.physical.ComputingAppliance;

public class EnergyChartGenerator {
	public static void generateApplicationEnergyChart(String resultDirectory) throws IOException {
		
		FileWriter fw = new FileWriter(resultDirectory+File.separator+"applicationEnergy.html");

		fw.write("<!DOCTYPE html><html><head>");
		fw.write("<script type=\'text/javascript\' src=\'https://www.gstatic.com/charts/loader.js\'></script>");
		fw.write("<script type=\'text/javascript\'>");
		fw.write("google.charts.load('current', {packages: ['corechart']});");
		fw.write("google.charts.setOnLoadCallback(drawChart);");
		fw.write("function drawChart() {");
		fw.write("var data = google.visualization.arrayToDataTable([");
		fw.write("['', 'Node', { role: 'style' }],");

		for(ComputingAppliance ca : ComputingAppliance.getAllComputingAppliances()) {
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
}
