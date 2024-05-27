package hu.u_szeged.inf.fog.simulator.util.xml;

import java.util.ArrayList;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * This class is annotated with JAXB annotations to map Java classes to XML representations and vice versa.
 * It is responsible for XML driven simulation, in this case loading instances from a file.
 * Example files are located in: src/main/resources/demo/XML_examples
 */
@XmlRootElement(name = "instances")
@XmlAccessorType(XmlAccessType.PROPERTY)
public class InstancesXmlModel {
    
    ArrayList<InstanceXmlModel> instanceList;

    public ArrayList<InstanceXmlModel> getInstances() {
        return instanceList;
    }

    @XmlElement(name = "instance")
    public void setInstances(ArrayList<InstanceXmlModel> instances) {
        this.instanceList = instances;
    }
    
    /*
    public void add(InstanceModel instances) {
        if (this.instanceList == null) {
            this.instanceList = new ArrayList<InstanceModel>();
        }
        this.instanceList.add(instances);
    }
    */

    @Override
    public String toString() {
        StringBuffer str = new StringBuffer();
        for (InstanceXmlModel instance : this.instanceList) {
            str.append(instance.toString());
            str.append("\n");
        }
        return str.toString();
    }
}