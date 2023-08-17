package hu.u_szeged.inf.fog.simulator.util.xmlhandler;

import hu.u_szeged.inf.fog.simulator.application.Application;
import hu.u_szeged.inf.fog.simulator.application.strategy.ApplicationStrategy;
import hu.u_szeged.inf.fog.simulator.application.strategy.HoldDownApplicationStrategy;
import hu.u_szeged.inf.fog.simulator.application.strategy.PliantApplicationStrategy;
import hu.u_szeged.inf.fog.simulator.application.strategy.PushUpApplicationStrategy;
import hu.u_szeged.inf.fog.simulator.application.strategy.RandomApplicationStrategy;
import hu.u_szeged.inf.fog.simulator.application.strategy.RuntimeAwareApplicationStrategy;
import hu.u_szeged.inf.fog.simulator.iot.mobility.GeoLocation;
import hu.u_szeged.inf.fog.simulator.physical.ComputingAppliance;
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

    public ArrayList<ApplicationModel> applications;
    public ArrayList<ApplianceNeigboursModel> neighbours;

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

    public ArrayList<ApplicationModel> getApplications() {
        return applications;
    }

    @Override
    public String toString() {
        return "ApplianceModel [name=" + name + ", latitude=" + latitude + ", longitude=" + longitude + ", range="
                + range + ", file=" + file + ", applications=" + applications + ", neighbours=" + neighbours + "]";
    }

    @XmlElementWrapper(name = "applications")
    @XmlElement(name = "application")
    public void setApplications(ArrayList<ApplicationModel> applications) {
        this.applications = applications;
    }

    public void add(ApplicationModel applicationModel) {
        if (this.applications == null) {
            this.applications = new ArrayList<ApplicationModel>();
        }
        this.applications.add(applicationModel);
    }
    
    public void add(ApplianceNeigboursModel device) {
        if (this.neighbours == null) {
            this.neighbours = new ArrayList<ApplianceNeigboursModel>();
        }
        this.neighbours.add(device);
    }

    public ArrayList<ApplianceNeigboursModel> getNeighbourAppliances() {
        return neighbours;
    }

    @XmlElementWrapper(name = "neighbours")
    @XmlElement(name = "neighbour")
    public void setNeighbourAppliances(ArrayList<ApplianceNeigboursModel> neighbours) {
        this.neighbours = neighbours;
    }



    public static void loadApplianceXML(String appliancefile, Map<String, String> iaasMapper) throws Exception {
        File file = new File(appliancefile);
        JAXBContext jaxbContext = JAXBContext.newInstance(AppliancesModel.class);
        Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();
        AppliancesModel appliances = (AppliancesModel) jaxbUnmarshaller.unmarshal(file);

        for (ApplianceModel am : appliances.applianceList) {
            ComputingAppliance ca = new ComputingAppliance(iaasMapper.get(am.file), am.name,
                    new GeoLocation(am.latitude, am.longitude), am.range);
            for (ApplicationModel a : am.getApplications()) {
                ca.addApplication(new Application(a.name, a.freq, a.tasksize, a.countOfInstructions, a.canJoin,
                        findApplicationStrategy(a.strategy, a.activationRatio, a.transferDevider),
                        Instance.instances.get(a.instance)));
            }
        }
        for (ApplianceModel am : appliances.applianceList) {
            ComputingAppliance ca = getComputingApplianceByName(am.name);
            if (am.neighbours != null) {
                for (ApplianceNeigboursModel nam : am.neighbours) {
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
        for (ComputingAppliance ca : ComputingAppliance.allComputingAppliances) {
            if (ca.name.equals(name)) {
                return ca;
            }
        }
        return null;
    }

    private static ApplicationStrategy findApplicationStrategy(String strategy, double activationRatio,
            double TransferDevider) {

        /*try {
            FeatureManager.getInstance().sendFeaturesForPrediction();
        } catch (JSONException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }*/

        if (strategy.equals("HoldDownApplicationStrategy")) {
            return new HoldDownApplicationStrategy(activationRatio, TransferDevider);
        } else if (strategy.equals("PliantApplicationStrategy")) {
            return new PliantApplicationStrategy(activationRatio, TransferDevider);
        } else if (strategy.equals("PushUpApplicationStrategy")) {
            return new PushUpApplicationStrategy(activationRatio, TransferDevider);
        } else if (strategy.equals("RandomApplicationStrategy")) {
            return new RandomApplicationStrategy(activationRatio, TransferDevider);
        } else if (strategy.equals("RuntimeAwareApplicationStrategy")) {
            return new RuntimeAwareApplicationStrategy(activationRatio, TransferDevider);
        } else {
            System.err.println("WARNING: the application strategy called " + strategy + " does not exist!");
            System.exit(0);
        }
        return null;
    }

}
