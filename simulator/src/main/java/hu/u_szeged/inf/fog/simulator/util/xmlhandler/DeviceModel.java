package hu.u_szeged.inf.fog.simulator.util.xmlhandler;

import hu.mta.sztaki.lpds.cloud.simulator.energy.powermodelling.PowerState;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;
import hu.mta.sztaki.lpds.cloud.simulator.util.PowerTransitionGenerator;
import hu.u_szeged.inf.fog.simulator.iot.SmartDevice;
import hu.u_szeged.inf.fog.simulator.iot.mobility.GeoLocation;
import hu.u_szeged.inf.fog.simulator.iot.mobility.RandomWalkMobilityStrategy;
import hu.u_szeged.inf.fog.simulator.iot.strategy.CostAwareDeviceStrategy;
import hu.u_szeged.inf.fog.simulator.iot.strategy.CustomDeviceStrategy;
import hu.u_szeged.inf.fog.simulator.iot.strategy.CustomDeviceStrategyTemplate;
import hu.u_szeged.inf.fog.simulator.iot.strategy.DeviceStrategy;
import hu.u_szeged.inf.fog.simulator.iot.strategy.DistanceBasedDeviceStrategy;
import hu.u_szeged.inf.fog.simulator.iot.strategy.LoadBalancedDeviceStrategy;
import hu.u_szeged.inf.fog.simulator.iot.strategy.PliantDeviceStrategy;
import hu.u_szeged.inf.fog.simulator.iot.strategy.RandomDeviceStrategy;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "device")
@XmlAccessorType(XmlAccessType.PROPERTY)
public class DeviceModel {

    public String name;
    public long startTime;
    public long stopTime;
    public long fileSize;
    public int sensorCount;
    public String strategy;
    public long freq;
    public double latitude;
    public double longitude;
    public double speed;
    public long radius;
    public int latency;
    public long capacity;
    public long maxOutBw;
    public double cores;
    public double perCorePocessing;
    public long ram;
    public double minpower;
    public double idlepower;
    public double maxpower;

    @XmlAttribute(name = "name", required = true)
    public void setName(String name) {
        this.name = name;
    }

    @XmlElement(name = "startTime")
    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }

    @XmlElement(name = "stopTime")
    public void setStopTime(long stopTime) {
        this.stopTime = stopTime;
    }

    @XmlElement(name = "fileSize")
    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    @XmlElement(name = "sensorCount")
    public void setSensorCount(int sensorCount) {
        this.sensorCount = sensorCount;
    }

    @XmlElement(name = "strategy")
    public void setStrategy(String strategy) {
        this.strategy = strategy;
    }

    @XmlElement(name = "freq")
    public void setFreq(long freq) {
        this.freq = freq;
    }

    @XmlElement(name = "latitude")
    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    @XmlElement(name = "longitude")
    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    @XmlElement(name = "speed")
    public void setSpeed(double speed) {
        this.speed = speed;
    }

    @XmlElement(name = "radius")
    public void setRadius(long radius) {
        this.radius = radius;
    }

    @XmlElement(name = "latency")
    public void setLatency(int latency) {
        this.latency = latency;
    }

    @XmlElement(name = "capacity")
    public void setCapacity(long capacity) {
        this.capacity = capacity;
    }

    @XmlElement(name = "maxOutBW")
    public void setMaxOutBw(long maxOutBw) {
        this.maxOutBw = maxOutBw;
    }

    @XmlElement(name = "cores")
    public void setCores(double cores) {
        this.cores = cores;
    }

    @XmlElement(name = "perCoreProcessing")
    public void setPerCorePocessing(double perCorePocessing) {
        this.perCorePocessing = perCorePocessing;
    }

    @XmlElement(name = "ram")
    public void setRam(long ram) {
        this.ram = ram;
    }

    @XmlElement(name = "minpower")
    public void setMinpower(double minpower) {
        this.minpower = minpower;
    }

    @XmlElement(name = "idlepower")
    public void setIdlepower(double idlepower) {
        this.idlepower = idlepower;
    }

    @XmlElement(name = "maxpower")
    public void setMaxpower(double maxpower) {
        this.maxpower = maxpower;
    }

    @Override
    public String toString() {
        return "DeviceModel [name=" + name + ", startTime=" + startTime + ", stopTime=" + stopTime + ", fileSize="
                + fileSize + ", sensorCount=" + sensorCount + ", strategy=" + strategy + ", freq=" + freq
                + ", latitude=" + latitude + ", longitude=" + longitude + ", speed=" + speed + ", radius=" + radius
                + ", latency=" + latency + ", capacity=" + capacity + ", maxOutBW=" + maxOutBw + ", cores=" + cores
                + ", perCorePocessing=" + perCorePocessing + ", ram=" + ram + ", minpower=" + minpower + ", idlepower="
                + idlepower + ", maxpower=" + maxpower + "]";
    }

    public static void loadDeviceXml(String stationfile) throws JAXBException, IOException, 
        ClassNotFoundException, InvocationTargetException, NoSuchMethodException, 
        IllegalAccessException, InstantiationException {
        loadDeviceXml(stationfile,"",false);
    }

    public static void loadDeviceXml(String stationfile, String code, Boolean isDeviceCustom) 
            throws JAXBException, IOException, ClassNotFoundException, InvocationTargetException,
            NoSuchMethodException, IllegalAccessException, InstantiationException {
        File file = new File(stationfile);
        JAXBContext jaxbContext = JAXBContext.newInstance(DevicesModel.class);
        Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();
        DevicesModel devices = (DevicesModel) jaxbUnmarshaller.unmarshal(file);
        System.out.println(devices.deviceList);
        for (DeviceModel dm : devices.deviceList) {
            HashMap<String, Integer> latencyMap = new HashMap<String, Integer>();
            EnumMap<PowerTransitionGenerator.PowerStateKind, Map<String, PowerState>> transitions;

            transitions = PowerTransitionGenerator.generateTransitions(dm.minpower, dm.idlepower, dm.maxpower, 10, 20);
            Map<String, PowerState> cpuTransitions = transitions.get(PowerTransitionGenerator.PowerStateKind.host);
            Map<String, PowerState> stTransitions = transitions.get(PowerTransitionGenerator.PowerStateKind.storage);
            Map<String, PowerState> nwTransitions = transitions.get(PowerTransitionGenerator.PowerStateKind.network);
            PhysicalMachine localMachine = new PhysicalMachine(dm.cores, dm.perCorePocessing, dm.ram,
                    new Repository(dm.capacity, dm.name, dm.maxOutBw, dm.maxOutBw, dm.maxOutBw, latencyMap,
                            stTransitions, nwTransitions),
                    0, 0, cpuTransitions);
            GeoLocation gl = new GeoLocation(dm.latitude, dm.longitude);
            new SmartDevice(dm.startTime, dm.stopTime, dm.fileSize, dm.freq,
                    new RandomWalkMobilityStrategy(gl, dm.speed, 2 * dm.speed, dm.radius),
                    findDeviceStrategy(dm.strategy, code, isDeviceCustom), localMachine, dm.latency, true);

        }

    }
    
    private static DeviceStrategy findDeviceStrategy(String strategy, String code, Boolean isCustom) {
        if (!isCustom) {
            strategy = code;
        }

        if (strategy.equals("CostAwareDeviceStrategy")) {
            return new CostAwareDeviceStrategy();
        } else if (strategy.equals("DistanceBasedDeviceStrategy")) {
            return new DistanceBasedDeviceStrategy();
        } else if (strategy.equals("PliantDeviceStrategy")) {
            return new PliantDeviceStrategy();
        } else if (strategy.equals("RandomDeviceStrategy")) {
            return new RandomDeviceStrategy();
        } else if (strategy.equals("LoadBalancedDeviceStrategy")) {
            return new LoadBalancedDeviceStrategy();
        } else if (strategy.equals("CustomDeviceStrategy")) {
            if (code.equals("") || code == null) {
                throw new IllegalArgumentException("Application code can not be empty!");
            }
            String fullCode = CustomDeviceStrategyTemplate.renderCustomDeviceStrategyTemplate(code);
            try {
                return CustomDeviceStrategy.loadCustomStrategy(fullCode);
            } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InvocationTargetException
                    | InstantiationException | IOException e) {
                e.printStackTrace();
            }
        } else {
            System.err.println("WARNING: the device strategy called " + strategy + " does not exist!");
            System.exit(0);
        }
        return null;
    }
}