package hu.u_szeged.inf.fog.simulator.util.xml;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * This class is annotated with JAXB annotations to map Java classes to XML representations and vice versa.
 * It is responsible for XML driven simulation, in this case loading computing appliances'
 * neigbours and parent connections from a file.
 * Example files are located in: src/main/resources/demo/XML_examples
 */
@XmlRootElement(name = "device")
@XmlAccessorType(XmlAccessType.PROPERTY)
public class ApplianceNeigboursXmlModel {

    public String name;
    public int latency;
    public String parent; // TODO: bool?

    @XmlAttribute(name = "name")
    public void setName(String name) {
        this.name = name;
    }

    @XmlElement(name = "latency")
    public void setLatency(int latency) {
        this.latency = latency;
    }

    @XmlElement(name = "parent")
    public void setParentApp(String parent) {
        this.parent = parent;
    }

    @Override
    public String toString() {
        return "NeigboursModel [name=" + name + ", latency=" + latency + ", parent=" + parent + "]";
    }
}