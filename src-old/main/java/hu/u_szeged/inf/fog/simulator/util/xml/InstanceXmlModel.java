package hu.u_szeged.inf.fog.simulator.util.xml;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.AlterableResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.io.VirtualAppliance;
import hu.u_szeged.inf.fog.simulator.provider.Instance;
import java.io.File;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * This class is annotated with JAXB annotations to map Java classes to XML representations and vice versa.
 * It is responsible for XML driven simulation, in this case loading one instance type from a file.
 * Example files are located in: src/main/resources/demo/XML_examples
 */
@XmlRootElement(name = "instance")
@XmlAccessorType(XmlAccessType.PROPERTY)
public class InstanceXmlModel {

    public String name;
    public long ram;
    public int cpuCores;
    public double coreProcessingPower;
    public long startupProcess;
    public long reqDisk;
    public double pricePerTick;

    @XmlAttribute(name = "name", required = true)
    public void setName(String name) {
        this.name = name;
    }

    @XmlElement(name = "ram")
    public void setRam(long ram) {
        this.ram = ram;
    }

    @XmlElement(name = "cpu-cores")
    public void setCpuCores(int cpuCores) {
        this.cpuCores = cpuCores;
    }

    @XmlElement(name = "core-processing-power")
    public void setCorProcessingPower(double coreProcessingPower) {
        this.coreProcessingPower = coreProcessingPower;
    }

    @XmlElement(name = "startup-process")
    public void setStartupProcess(long startupProcess) {
        this.startupProcess = startupProcess;
    }

    @XmlElement(name = "req-disk")
    public void setReqDisk(long reqDisk) {
        this.reqDisk = reqDisk;
    }

    @XmlElement(name = "price-per-tick")
    public void setPricePerTick(double pricePerTick) {
        this.pricePerTick = pricePerTick;
    }

    @Override
    public String toString() {
        return "InstanceModel [name=" + name + ", ram=" + ram + ", cpuCores=" + cpuCores + ", coreProcessingPower="
                + coreProcessingPower + ", startupProcess=" + startupProcess + ", reqDisk=" + reqDisk
                + ", pricePerTick=" + pricePerTick + "]";
    }

    /**
     * Entry point for loading instance data from an XML file 
     * and creates instance types accordingly.
     *
     * @param datafile the path to the XML data file
     */
    public static void loadInstanceXml(String datafile) throws JAXBException {
        File file = new File(datafile);
        JAXBContext jaxbContext = JAXBContext.newInstance(InstancesXmlModel.class);
        Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();
        InstancesXmlModel instances = (InstancesXmlModel) jaxbUnmarshaller.unmarshal(file);
        //System.out.println(instances);
        for (InstanceXmlModel im : instances.instanceList) {
            new Instance(im.name, new VirtualAppliance(im.name, im.startupProcess, 0, false, im.reqDisk),
                    new AlterableResourceConstraints(im.cpuCores, im.coreProcessingPower, im.ram), im.pricePerTick);
        }
    }
}