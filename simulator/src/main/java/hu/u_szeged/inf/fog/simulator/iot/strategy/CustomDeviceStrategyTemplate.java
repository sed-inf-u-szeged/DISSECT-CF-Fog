package hu.u_szeged.inf.fog.simulator.iot.strategy;

public class CustomDeviceStrategyTemplate {
    private static final String CUSTOM_DEVICE_STRATEGY_TEMPLATE =
            "import hu.u_szeged.inf.fog.simulator.application.Application;\n" +
            "import hu.u_szeged.inf.fog.simulator.iot.mobility.MobilityEvent;\n" +
            "import hu.u_szeged.inf.fog.simulator.iot.strategy.DeviceStrategy;\n" +
            "import java.util.ArrayList;\n" +
            "\n" +
                "class HelperDeviceStrategy extends DeviceStrategy {\n" +
                "\n" +
                "@Override\n" +
                "${code}\n" +
                "}";
    public static String renderCustomDeviceStrategyTemplate(String code) {
        return CUSTOM_DEVICE_STRATEGY_TEMPLATE.replace("${code}", code);
    }
}
