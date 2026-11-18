package hu.u_szeged.inf.fog.simulator.demo;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.AlterableResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.io.VirtualAppliance;
import hu.u_szeged.inf.fog.simulator.application.Application;
import hu.u_szeged.inf.fog.simulator.application.strategy.ApplicationStrategy;
import hu.u_szeged.inf.fog.simulator.iot.mobility.GeoLocation;
import hu.u_szeged.inf.fog.simulator.node.ComputingAppliance;
import hu.u_szeged.inf.fog.simulator.provider.Instance;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** Shared infrastructure from PredictionSimulationDefinition. Distances passed here are km. */
public final class CityTopology {
    private CityTopology() { }

    public static final class NodeSpec {
        public final String name;
        public final double latitude;
        public final double longitude;
        /** 0 = cloud, 1 = regional fog, 2 = local fog. */
        public final int level;
        private NodeSpec(String name, double latitude, double longitude, int level) {
            this.name = name; this.latitude = latitude; this.longitude = longitude; this.level = level;
        }
        public GeoLocation location() { return new GeoLocation(latitude, longitude); }
    }

    public static final class LinkSpec {
        public final String source;
        public final String target;
        public final int latency;
        public final boolean parent;
        private LinkSpec(String source, String target, int latency, boolean parent) {
            this.source = source; this.target = target; this.latency = latency; this.parent = parent;
        }
    }

    public static final List<NodeSpec> NODES = List.of(
            new NodeSpec("Budapest_cloud", 47.5, 19.1, 0),
            new NodeSpec("Sopron_fog2", 47.7, 16.6, 2),
            new NodeSpec("Szombathely_fog2", 47.2, 16.6, 2),
            new NodeSpec("Gyor_fog1", 47.6, 17.6, 1),
            new NodeSpec("Szeged_fog2", 46.2, 20.1, 2),
            new NodeSpec("Pecs_fog2", 46.0, 18.2, 2),
            new NodeSpec("Kecskemet_fog1", 46.8, 19.7, 1),
            new NodeSpec("Miskolc_fog2", 48.1, 20.7, 2),
            new NodeSpec("Debrecen_fog2", 47.5, 21.6, 2),
            new NodeSpec("Gyongyos_fog1", 47.7, 19.9, 1));

    public static final List<LinkSpec> LINKS = List.of(
            new LinkSpec("Gyor_fog1", "Gyongyos_fog1", 33, false),
            new LinkSpec("Gyor_fog1", "Kecskemet_fog1", 35, false),
            new LinkSpec("Kecskemet_fog1", "Gyongyos_fog1", 34, false),
            new LinkSpec("Kecskemet_fog1", "Budapest_cloud", 25, true),
            new LinkSpec("Gyongyos_fog1", "Budapest_cloud", 25, true),
            new LinkSpec("Gyor_fog1", "Budapest_cloud", 27, true),
            new LinkSpec("Sopron_fog2", "Szombathely_fog2", 45, false),
            new LinkSpec("Sopron_fog2", "Gyor_fog1", 43, true),
            new LinkSpec("Szombathely_fog2", "Gyor_fog1", 44, true),
            new LinkSpec("Szeged_fog2", "Pecs_fog2", 44, false),
            new LinkSpec("Szeged_fog2", "Kecskemet_fog1", 49, true),
            new LinkSpec("Pecs_fog2", "Kecskemet_fog1", 47, true),
            new LinkSpec("Miskolc_fog2", "Debrecen_fog2", 41, false),
            new LinkSpec("Miskolc_fog2", "Gyongyos_fog1", 44, true),
            new LinkSpec("Debrecen_fog2", "Gyongyos_fog1", 48, true));

    /** The cloud is an offload destination, never a device starting/access node. */
    public static NodeSpec deviceOrigin(String placement, int deviceIndex) {
        if (deviceIndex < 0) { throw new IllegalArgumentException("Negative device index"); }
        if ("distributed".equals(placement)) { return NODES.get(1 + deviceIndex % 9); }
        return NODES.stream().filter(n -> n.level != 0 && n.name.equals(placement)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown fog city: " + placement));
    }

    /** Preserve node order, XML hardware, VM sizes/prices and network links of the original scenario. */
    public static List<ComputingAppliance> create(Path cloudXml, Path resourceRoot, long coverageKm,
            long taskSize, double instructions, Supplier<ApplicationStrategy> strategies) throws Exception {
        if (coverageKm <= 0) { throw new IllegalArgumentException("Coverage must be positive km"); }
        VirtualAppliance va = new VirtualAppliance("va", 100, 0, false, 1073741824L);
        Instance[] instances = {
            new Instance("a1.2xlarge", va, new AlterableResourceConstraints(8, 0.001, 17179869184L), 0.204 / 60 / 60 / 1000),
            new Instance("a1.xlarge", va, new AlterableResourceConstraints(4, 0.001, 8589934592L), 0.102 / 60 / 60 / 1000),
            new Instance("a1.large", va, new AlterableResourceConstraints(2, 0.001, 4294967296L), 0.051 / 60 / 60 / 1000)
        };
        Map<String, ComputingAppliance> nodes = new LinkedHashMap<>();
        for (NodeSpec spec : NODES) {
            Path xml = spec.level == 0 ? cloudXml : resourceRoot.resolve("XML_examples/LPDS_" + (spec.level == 1 ? "32" : "16") + ".xml");
            nodes.put(spec.name, new ComputingAppliance(xml.toString(), spec.name, spec.location(), coverageKm));
        }
        for (LinkSpec link : LINKS) {
            if (link.parent) { nodes.get(link.source).setParent(nodes.get(link.target), link.latency); }
            else { nodes.get(link.source).addNeighbor(nodes.get(link.target), link.latency); }
        }
        for (NodeSpec spec : NODES) {
            nodes.get(spec.name).addApplication(new Application(spec.name + "_app", 60000, taskSize,
                    instructions, spec.level != 0, strategies.get(), instances[spec.level]));
        }
        return new ArrayList<>(nodes.values());
    }
}
