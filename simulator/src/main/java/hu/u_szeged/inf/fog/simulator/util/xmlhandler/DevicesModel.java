package hu.u_szeged.inf.fog.simulator.util.xmlhandler;

import java.util.ArrayList;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "devices")
@XmlAccessorType(XmlAccessType.PROPERTY)
public class DevicesModel {
    ArrayList<DeviceModel> deviceList;

    public ArrayList<DeviceModel> getDevices() {
        return deviceList;
    }

    @XmlElement(name = "device")
    public void setDevices(ArrayList<DeviceModel> devices) {
        this.deviceList = devices;
    }

    @Override
    public String toString() {
        StringBuffer str = new StringBuffer();
        for (DeviceModel device : this.deviceList) {
            str.append(device.toString());
            str.append("\n");
        }
        return str.toString();
    }

}
