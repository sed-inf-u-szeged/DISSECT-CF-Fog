package hu.u_szeged.inf.fog.simulator.util.xmlhandler;

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



@XmlRootElement(name = "instance")
@XmlAccessorType(XmlAccessType.PROPERTY)
public class InstanceModel {

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

    public static void loadInstanceXml(String datafile) throws JAXBException {
        File file = new File(datafile);
        JAXBContext jaxbContext = JAXBContext.newInstance(InstancesModel.class);
        Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();
        InstancesModel instances = (InstancesModel) jaxbUnmarshaller.unmarshal(file);
        System.out.println(instances);
        for (InstanceModel im : instances.instanceList) {
            new Instance(im.name, new VirtualAppliance(im.name, im.startupProcess, 0, false, im.reqDisk),
                    new AlterableResourceConstraints(im.cpuCores, im.coreProcessingPower, im.ram), im.pricePerTick);

        }

    }

}