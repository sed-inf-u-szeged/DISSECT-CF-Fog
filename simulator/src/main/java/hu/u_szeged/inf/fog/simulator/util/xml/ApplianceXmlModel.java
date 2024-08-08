package hu.u_szeged.inf.fog.simulator.util.xml;

import hu.u_szeged.inf.fog.simulator.application.Application;
import hu.u_szeged.inf.fog.simulator.application.strategy.ApplicationStrategy;
import hu.u_szeged.inf.fog.simulator.application.strategy.CustomApplicationStrategy;
import hu.u_szeged.inf.fog.simulator.application.strategy.HoldDownApplicationStrategy;
import hu.u_szeged.inf.fog.simulator.application.strategy.PliantApplicationStrategy;
import hu.u_szeged.inf.fog.simulator.application.strategy.PushUpApplicationStrategy;
import hu.u_szeged.inf.fog.simulator.application.strategy.RandomApplicationStrategy;
import hu.u_szeged.inf.fog.simulator.application.strategy.RuntimeAwareApplicationStrategy;
import hu.u_szeged.inf.fog.simulator.iot.mobility.GeoLocation;
import hu.u_szeged.inf.fog.simulator.node.ComputingAppliance;
import hu.u_szeged.inf.fog.simulator.provider.Instance;
import java.io.File;
import java.util.ArrayList;
import java.util.Map;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * This class is annotated with JAXB annotations to map Java classes to XML representations and vice versa.
 * It is responsible for XML driven simulation, in this case loading an IoT application and its
 * Computing Appliance's connection from a file.
 * Example files are located in: src/main/resources/demo/XML_examples
 */
@XmlRootElement(name = "appliance")
@XmlAccessorType(XmlAccessType.PROPERTY)
public class ApplianceXmlModel {

    public String name;
    public double latitude;
    public double longitude;
    public long range;
    public String file;

    public ArrayList<ApplicationXmlModel> applications;
    public ArrayList<ApplianceNeigboursXmlModel> neighbours;

    @XmlAttribute(name = "name", required = true)
    public void setName(String name) {
        this.name = name;
    }

    @XmlElement(name = "latitude")
    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    @XmlElement(name = "longitude")
    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    @XmlElement(name = "range")
    public void setRange(long range) {
        this.range = range;
    }

    @XmlElement(name = "file")
    public void setFile(String file) {
        this.file = file;
    }

    public ArrayList<ApplicationXmlModel> getApplications() {
        return applications;
    }

    @Override
    public String toString() {
        return "ApplianceModel [name=" + name + ", latitude=" + latitude + ", longitude=" + longitude + ", range="
                + range + ", file=" + file + ", applications=" + applications + ", neighbours=" + neighbours + "]";
    }

    @XmlElementWrapper(name = "applications")
    @XmlElement(name = "application")
    public void setApplications(ArrayList<ApplicationXmlModel> applications) {
        this.applications = applications;
    }

    public ArrayList<ApplianceNeigboursXmlModel> getNeighbourAppliances() {
        return neighbours;
    }

    @XmlElementWrapper(name = "neighbours")
    @XmlElement(name = "neighbour")
    public void setNeighbourAppliances(ArrayList<ApplianceNeigboursXmlModel> neighbours) {
        this.neighbours = neighbours;
    }

    /**
     * Loads an appliance XML file and based on that it creates Application and 
     * Computing Appliance objects.
     *
     * @param appliancefile the path to the appliance XML file
     * @param iaasMapper a map that maps IaaS identifiers to their corresponding files
     */
    public static void loadApplianceXml(String appliancefile, Map<String, String> iaasMapper) {
        try {
            loadApplianceXml(appliancefile, iaasMapper, "", false);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Loads an appliance XML file and  based on that it creates Application and 
     * Computing Appliance objects.
     *
     * @param appliancefile the path to the appliance XML file
     * @param iaasMapper a map that maps IaaS identifiers to their corresponding files
     * @param code submitted by the user on the DISSECT-CF-Fog-WebApp
     * @param isApplicationCustom a flag indicating whether the application code is custom (user-based)
     */
    public static void loadApplianceXml(String appliancefile, Map<String, String> iaasMapper, 
            String code, Boolean isApplicationCustom) throws Exception {
        File file = new File(appliancefile);
        JAXBContext jaxbContext = JAXBContext.newInstance(AppliancesXmlModel.class);
        Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();
        AppliancesXmlModel appliances = (AppliancesXmlModel) jaxbUnmarshaller.unmarshal(file);

        for (ApplianceXmlModel am : appliances.applianceList) {
            ComputingAppliance ca = new ComputingAppliance(iaasMapper.get(am.file), am.name,
                    new GeoLocation(am.latitude, am.longitude), am.range);
            for (ApplicationXmlModel a : am.getApplications()) {
                ca.addApplication(new Application(a.name, a.freq, a.tasksize, a.countOfInstructions, a.canJoin,
                        findApplicationStrategy(a.strategy, a.activationRatio, 
                                a.transferDevider, code, isApplicationCustom),
                        Instance.allInstances.get(a.instance)));
            }
        }
        for (ApplianceXmlModel am : appliances.applianceList) {
            ComputingAppliance ca = getComputingApplianceByName(am.name);
            if (am.neighbours != null) {
                for (ApplianceNeigboursXmlModel nam : am.neighbours) {
                    ComputingAppliance friend = getComputingApplianceByName(nam.name);
                    if (Boolean.parseBoolean(nam.parent) && nam.parent != null) {
                        ca.setParent(friend, nam.latency);
                    } else {
                        ca.addNeighbor(friend, nam.latency);
                    }
                }
            }
        }
        System.out.println(appliances);
    }

    private static ComputingAppliance getComputingApplianceByName(String name) {
        for (ComputingAppliance ca : ComputingAppliance.getAllComputingAppliances()) {
            if (ca.name.equals(name)) {
                return ca;
            }
        }
        return null;
    }

    private static ApplicationStrategy findApplicationStrategy(String strategy, double activationRatio,
            double transferDevider, String code, Boolean isCustom) {

        if (!isCustom) {
            code = strategy;
        }

        if (strategy.equals("HoldDownApplicationStrategy")) {
            return new HoldDownApplicationStrategy(activationRatio, transferDevider);
        } else if (strategy.equals("PliantApplicationStrategy")) {
            return new PliantApplicationStrategy(activationRatio, transferDevider);
        } else if (strategy.equals("PushUpApplicationStrategy")) {
            return new PushUpApplicationStrategy(activationRatio, transferDevider);
        } else if (strategy.equals("RandomApplicationStrategy")) {
            return new RandomApplicationStrategy(activationRatio, transferDevider);
        } else if (strategy.equals("RuntimeAwareApplicationStrategy")) {
            return new RuntimeAwareApplicationStrategy(activationRatio, transferDevider);
        } else if (strategy.equals("CustomApplicationStrategy") && !code.isEmpty() && code != null) {
            String fullCode = CustomApplicationStrategy.renderCustomApplicationStrategyTemplate(code);
            return CustomApplicationStrategy.loadCustomStrategy(activationRatio, transferDevider, fullCode);
        } else {
            System.err.println("WARNING: the application strategy called " + strategy + " does not exist!");
            System.exit(0);
        }
        return null;
    }

}
