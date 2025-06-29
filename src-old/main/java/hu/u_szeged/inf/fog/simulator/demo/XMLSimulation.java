package hu.u_szeged.inf.fog.simulator.demo;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.u_szeged.inf.fog.simulator.iot.Device;
import hu.u_szeged.inf.fog.simulator.util.MapVisualiser;
import hu.u_szeged.inf.fog.simulator.util.SimLogger;
import hu.u_szeged.inf.fog.simulator.util.TimelineVisualiser;
import hu.u_szeged.inf.fog.simulator.util.xml.ApplianceXmlModel;
import hu.u_szeged.inf.fog.simulator.util.xml.DeviceXmlModel;
import hu.u_szeged.inf.fog.simulator.util.xml.InstanceXmlModel;

import java.util.HashMap;
import java.util.Map;

public class XMLSimulation {

    public static void main(String[] args) throws Exception {

        SimLogger.setLogging(1, true);
        
        String cloudfile = ScenarioBase.resourcePath + "LPDS_original.xml";
        String fogfile1 = ScenarioBase.resourcePath + "XML_examples/LPDS_32.xml";
        String fogfile2 = ScenarioBase.resourcePath + "XML_examples/LPDS_16.xml";

        String instancefile = ScenarioBase.resourcePath + "XML_examples/instances.xml";
        String appliancefile = ScenarioBase.resourcePath + "XML_examples/applications.xml";
        String devicefile = ScenarioBase.resourcePath + "XML_examples/devices.xml";

        Map<String, String> iaasMapper = new HashMap<String, String>();
        iaasMapper.put("LPDS_original", cloudfile);
        iaasMapper.put("LPDS_32", fogfile1);
        iaasMapper.put("LPDS_16", fogfile2);

        InstanceXmlModel.loadInstanceXml(instancefile);
        ApplianceXmlModel.loadApplianceXml(appliancefile, iaasMapper);
        DeviceXmlModel.loadDeviceXml(devicefile);

        long starttime = System.nanoTime();
        Timed.simulateUntilLastEvent();
        long stoptime = System.nanoTime();

        ScenarioBase.calculateIoTCost();
        ScenarioBase.logBatchProcessing(stoptime - starttime);
        TimelineVisualiser.generateTimeline(ScenarioBase.resultDirectory);
        MapVisualiser.mapGenerator(ScenarioBase.scriptPath, ScenarioBase.resultDirectory, Device.allDevices);
    }
}