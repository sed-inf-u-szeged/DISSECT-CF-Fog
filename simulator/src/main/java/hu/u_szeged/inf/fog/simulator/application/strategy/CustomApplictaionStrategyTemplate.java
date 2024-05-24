package hu.u_szeged.inf.fog.simulator.application.strategy;

public class CustomApplictaionStrategyTemplate {
    private static final String CUSTOM_APPLICATION_STRATEGY_TEMPLATE =
            "import hu.mta.sztaki.lpds.cloud.simulator.util.SeedSyncer;\n"
          + "import hu.u_szeged.inf.fog.simulator.application.Application;\n"
          + "import hu.u_szeged.inf.fog.simulator.physical.ComputingAppliance;\n"
          + "import hu.u_szeged.inf.fog.simulator.application.strategy.ApplicationStrategy;\n\n" 
          + "class HelperApplicationStrategy extends ApplicationStrategy {\n\n"
          + "public HelperApplicationStrategy(double activationRatio, double transferDivider) {\n"
          + "this.activationRatio = activationRatio;\n"
          + "this.transferDivider = transferDivider;\n"
          + "}\n\n"
          + "@Override\n"
          + "${code}\n"
          + "}";

    public static String renderCustomApplicationStrategyTemplate(String code) {
        return CUSTOM_APPLICATION_STRATEGY_TEMPLATE.replace("${code}", code);
    }
}