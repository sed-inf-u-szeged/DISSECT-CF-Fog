package hu.u_szeged.inf.fog.simulator.util.xml;

import java.util.ArrayList;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * This class is annotated with JAXB annotations to map Java classes to XML representations and vice versa.
 * It is responsible for XML driven simulation, in this case loading IoT devices from a file.
 * Example files are located in: src/main/resources/demo/XML_examples
 */
@XmlRootElement(name = "devices")
@XmlAccessorType(XmlAccessType.PROPERTY)
public class DevicesXmlModel {
    
    ArrayList<DeviceXmlModel> deviceList;

    public ArrayList<DeviceXmlModel> getDevices() {
        return deviceList;
    }

    @XmlElement(name = "device")
    public void setDevices(ArrayList<DeviceXmlModel> devices) {
        this.deviceList = devices;
    }

    @Override
    public String toString() {
        StringBuffer str = new StringBuffer();
        for (DeviceXmlModel device : this.deviceList) {
            str.append(device.toString());
            str.append("\n");
        }
        return str.toString();
    }
}