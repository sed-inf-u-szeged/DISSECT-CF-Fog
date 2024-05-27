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

@XmlRootElement(name = "appliance")
@XmlAccessorType(XmlAccessType.PROPERTY)
public class ApplianceModel {

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

    public void add(ApplicationXmlModel applicationModel) {
        if (this.applications == null) {
            this.applications = new ArrayList<ApplicationXmlModel>();
        }
        this.applications.add(applicationModel);
    }
    
    public void add(ApplianceNeigboursXmlModel device) {
        if (this.neighbours == null) {
            this.neighbours = new ArrayList<ApplianceNeigboursXmlModel>();
        }
        this.neighbours.add(device);
    }

    public ArrayList<ApplianceNeigboursXmlModel> getNeighbourAppliances() {
        return neighbours;
    }

    @XmlElementWrapper(name = "neighbours")
    @XmlElement(name = "neighbour")
    public void setNeighbourAppliances(ArrayList<ApplianceNeigboursXmlModel> neighbours) {
        this.neighbours = neighbours;
    }


    public static void loadApplianceXml(String appliancefile, Map<String, String> iaasMapper) {
        try {
            loadApplianceXml(appliancefile,iaasMapper,"",false);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void loadApplianceXml(String appliancefile, Map<String, String> iaasMapper, 
            String code, Boolean isApplicationCustome) throws Exception {
        File file = new File(appliancefile);
        JAXBContext jaxbContext = JAXBContext.newInstance(AppliancesXmlModel.class);
        Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();
        AppliancesXmlModel appliances = (AppliancesXmlModel) jaxbUnmarshaller.unmarshal(file);

        for (ApplianceModel am : appliances.applianceList) {
            ComputingAppliance ca = new ComputingAppliance(iaasMapper.get(am.file), am.name,
                    new GeoLocation(am.latitude, am.longitude), am.range);
            for (ApplicationXmlModel a : am.getApplications()) {
                ca.addApplication(new Application(a.name, a.freq, a.tasksize, a.countOfInstructions, a.canJoin,
                        findApplicationStrategy(a.strategy, a.activationRatio, 
                                a.transferDevider, code,isApplicationCustome),
                        Instance.allInstances.get(a.instance)));
            }
        }
        for (ApplianceModel am : appliances.applianceList) {
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

        /*
         TODO: feature manager!
         try {
            FeatureManager.getInstance().sendFeaturesForPrediction();
        } catch (JSONException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }*/

        if (!isCustom) {
            strategy = code;
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
            return CustomApplicationStrategy.loadCustomStrategy(activationRatio,transferDevider,fullCode);
        } else {
            System.err.println("WARNING: the application strategy called " + strategy + " does not exist!");
            System.exit(0);
        }
        return null;
    }

}
