package hu.u_szeged.inf.fog.simulator.demo;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.u_szeged.inf.fog.simulator.iot.Device;
import hu.u_szeged.inf.fog.simulator.util.MapVisualiser;
import hu.u_szeged.inf.fog.simulator.util.TimelineVisualiser;
import hu.u_szeged.inf.fog.simulator.util.xml.ApplianceModel;
import hu.u_szeged.inf.fog.simulator.util.xml.DeviceModel;
import hu.u_szeged.inf.fog.simulator.util.xml.InstanceXmlModel;

import java.util.HashMap;
import java.util.Map;

public class XMLSimulation {

    public static void main(String[] args) throws Exception {

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

        // WorkflowJobsModel.loadWorkflowXML(workflowFile);
        InstanceXmlModel.loadInstanceXml(instancefile);
        ApplianceModel.loadApplianceXml(appliancefile, iaasMapper);
        DeviceModel.loadDeviceXml(devicefile);

        long starttime = System.nanoTime();
        Timed.simulateUntilLastEvent();
        long stoptime = System.nanoTime();

        ScenarioBase.calculateIoTCost();
        ScenarioBase.logBatchProcessing(stoptime - starttime);
        TimelineVisualiser.generateTimeline(ScenarioBase.resultDirectory);
        MapVisualiser.mapGenerator(ScenarioBase.scriptPath, ScenarioBase.resultDirectory, Device.allDevices.get(0));
    }
}