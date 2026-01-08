package hu.u_szeged.inf.fog.simulator.iot;

import hu.mta.sztaki.lpds.cloud.simulator.energy.powermodelling.PowerState;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;
import hu.mta.sztaki.lpds.cloud.simulator.util.PowerTransitionGenerator;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

//singleton osztály amivel generálhatunk repokat az egyes kommunikációs protokollokhoz
public class CommunicationProtocol {
    private static CommunicationProtocol instance = null;

    private static int WiFiRepoCounter = 1;
    private static int _5GRepoCounter = 1;
    private static int LoRaRepoCounter = 1;

    private CommunicationProtocol(){}

    public static CommunicationProtocol getInstance() {
        if (instance == null)
            instance = new CommunicationProtocol();

        return instance;
    }


    /*
    az egyes értékeken még dolgozni kell, mert jelenleg még nem találtam megfelelő forrást amiből egyértelmű adatok jönnének le szóval
    egyelőre a legtöbb dolog placeholder és nincs sok értelmük főleg, hogy a maxpowert nem is tudom hogy kéne belőni mert szinte sose láttom csak az idlet
    */
    public Repository newWifiRepository(){
        EnumMap<PowerTransitionGenerator.PowerStateKind, Map<String, PowerState>> transitions =
                PowerTransitionGenerator.generateTransitions(0.065, 1.55, 2.0, 1, 2);

        final Map<String, PowerState> stTransitions = transitions.get(PowerTransitionGenerator.PowerStateKind.storage);
        final Map<String, PowerState> nwTransitions = transitions.get(PowerTransitionGenerator.PowerStateKind.network);

        return new Repository(4_294_967_296L, "WIFI-Repo" + WiFiRepoCounter++, 18750,18750,18750, new HashMap<>(), stTransitions, nwTransitions); //150 Mbit/s
    }

    public Repository new5GRepository(){
        EnumMap<PowerTransitionGenerator.PowerStateKind, Map<String, PowerState>> transitions =
                PowerTransitionGenerator.generateTransitions(0.065, 1.65, 2.0, 1, 2);

        final Map<String, PowerState> stTransitions = transitions.get(PowerTransitionGenerator.PowerStateKind.storage);
        final Map<String, PowerState> nwTransitions = transitions.get(PowerTransitionGenerator.PowerStateKind.network);

        return new Repository(4_294_967_296L, "5G-Repo" + _5GRepoCounter++, 12500,12500,12500, new HashMap<>(), stTransitions, nwTransitions); //100 Mbit/s
    }

    public Repository newLoRaRepository(){
        EnumMap<PowerTransitionGenerator.PowerStateKind, Map<String, PowerState>> transitions =
                PowerTransitionGenerator.generateTransitions(0.045, 1.45, 2.0, 1, 2);

        final Map<String, PowerState> stTransitions = transitions.get(PowerTransitionGenerator.PowerStateKind.storage);
        final Map<String, PowerState> nwTransitions = transitions.get(PowerTransitionGenerator.PowerStateKind.network);

        return new Repository(4_294_967_296L, "LORA-Repo" + LoRaRepoCounter++, 1,1,1, new HashMap<>(), stTransitions, nwTransitions); //8 Kbit/s?
    }
}
