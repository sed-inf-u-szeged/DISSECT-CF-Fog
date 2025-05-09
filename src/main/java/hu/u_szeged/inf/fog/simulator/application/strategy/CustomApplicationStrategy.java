package hu.u_szeged.inf.fog.simulator.application.strategy;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

/**
 * This class represents a custom application strategy and is
 * only for the DISSECT-CF-Fog-WebApp based simulations.
 */
public class CustomApplicationStrategy {
    
    /**
     * The template into which the user-defined code is added.
     */
    private static final String CUSTOM_APPLICATION_STRATEGY_TEMPLATE =
            "import hu.mta.sztaki.lpds.cloud.simulator.util.SeedSyncer; \n"
          + "import hu.u_szeged.inf.fog.simulator.application.Application; \n"
          + "import hu.u_szeged.inf.fog.simulator.physical.ComputingAppliance; \n"
          + "import hu.u_szeged.inf.fog.simulator.application.strategy.ApplicationStrategy; \n\n" 
          + "class HelperApplicationStrategy extends ApplicationStrategy { \n\n"
          + "public HelperApplicationStrategy(double activationRatio, double transferDivider) { \n"
          + "this.activationRatio = activationRatio; \n"
          + "this.transferDivider = transferDivider; \n"
          + "} \n\n"
          + "@Override \n"
          + "${code} \n"
          + "}";

    /**
     * This replaces the placeholder with the actual code in the template.
     *
     * @param code the code to be pasted
     */
    public static String renderCustomApplicationStrategyTemplate(String code) {
        return CUSTOM_APPLICATION_STRATEGY_TEMPLATE.replace("${code}", code);
    }
    
    /**
     * It loads to custom application strategy runtime, which was submitted 
     * by an user via the DISSECT-CF-Fog-WebApp. 
     *
     * @param code the code representing the custom application strategy
     */    
    public static ApplicationStrategy loadCustomStrategy(double activationRatio, double transferDivider, String code) {
        File sourceFile;
        try {
            sourceFile = File.createTempFile("HelperApplicationStrategy", ".java", new File("."));
            FileWriter writer = new FileWriter(sourceFile);
            writer.write(code);
            JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
            compiler.run(null, null, null, sourceFile.getPath());

            URLClassLoader classLoader = URLClassLoader.newInstance(new URL[]{new File(".").toURI().toURL()});
            Class<?> cls = Class.forName("HelperApplicationStrategy", true, classLoader);

            Constructor<?> constructor = cls.getDeclaredConstructor(double.class, double.class);
            constructor.setAccessible(true);
            ApplicationStrategy instance = (ApplicationStrategy) 
                    constructor.newInstance(activationRatio, transferDivider);

            writer.close();
            sourceFile.deleteOnExit();
           
            return instance;
        } catch (IOException | ClassNotFoundException | NoSuchMethodException 
                | SecurityException | InstantiationException | IllegalAccessException 
                | IllegalArgumentException | InvocationTargetException e) {
            e.printStackTrace();
        }
        return null;        
    }
}