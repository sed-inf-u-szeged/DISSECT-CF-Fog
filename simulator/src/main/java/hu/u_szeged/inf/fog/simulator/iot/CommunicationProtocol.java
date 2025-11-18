package hu.u_szeged.inf.fog.simulator.iot;

import hu.mta.sztaki.lpds.cloud.simulator.energy.powermodelling.PowerState;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;
import hu.mta.sztaki.lpds.cloud.simulator.util.PowerTransitionGenerator;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

public class CommunicationProtocol {
    //enumba nem megoldható de enumos naming convention mert szerepükben azok
    public static int WiFiRepoCounter = 1;
    public static int _5GRepoCounter = 1;
    public static int LoRaRepoCounter = 1;
    public final Repository WIFI;
    public final Repository _5G;
    public final Repository LORA;

    CommunicationProtocol() {
        this.WIFI = makeWifiRepository();
        this._5G = make5GRepository();
        this.LORA = makeLoRaRepository();
    }

    private Repository makeWifiRepository(){
        EnumMap<PowerTransitionGenerator.PowerStateKind, Map<String, PowerState>> transitions =
                PowerTransitionGenerator.generateTransitions(0.065, 1.475, 2.0, 1, 2);

        final Map<String, PowerState> stTransitions = transitions.get(PowerTransitionGenerator.PowerStateKind.storage);
        final Map<String, PowerState> nwTransitions = transitions.get(PowerTransitionGenerator.PowerStateKind.network);
        //ilyenkor cpu transitiont is visszakéne adni vagy mi az ötlet? előre megadjuk a fogyasztást a repokhoz / commprothoz, de akkor a localmachinenak hogy lesz cpu transition? -> konzultáció

        return new Repository(4_294_967_296L, "WIFI-Repo" + WiFiRepoCounter++, 1,1,1, new HashMap<>(), stTransitions, nwTransitions);
    }

    private Repository make5GRepository(){
        EnumMap<PowerTransitionGenerator.PowerStateKind, Map<String, PowerState>> transitions =
                PowerTransitionGenerator.generateTransitions(0.065, 1.475, 2.0, 1, 2);

        final Map<String, PowerState> stTransitions = transitions.get(PowerTransitionGenerator.PowerStateKind.storage);
        final Map<String, PowerState> nwTransitions = transitions.get(PowerTransitionGenerator.PowerStateKind.network);

        return new Repository(4_294_967_296L, "5G-Repo" + _5GRepoCounter++, 1,1,1, new HashMap<>(), stTransitions, nwTransitions);
    }

    private Repository makeLoRaRepository(){
        EnumMap<PowerTransitionGenerator.PowerStateKind, Map<String, PowerState>> transitions =
                PowerTransitionGenerator.generateTransitions(0.065, 1.475, 2.0, 1, 2);

        final Map<String, PowerState> stTransitions = transitions.get(PowerTransitionGenerator.PowerStateKind.storage);
        final Map<String, PowerState> nwTransitions = transitions.get(PowerTransitionGenerator.PowerStateKind.network);

        return new Repository(4_294_967_296L, "LORA-Repo" + LoRaRepoCounter++, 1,1,1, new HashMap<>(), stTransitions, nwTransitions);
    }
}
