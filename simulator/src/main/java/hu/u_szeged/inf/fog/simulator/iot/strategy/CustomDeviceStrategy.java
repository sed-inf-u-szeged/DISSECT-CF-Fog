package hu.u_szeged.inf.fog.simulator.iot.strategy;

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
 * This class represents a custom device strategy and is
 * only for the DISSECT-CF-Fog-WebApp based simulations.
 */
public class CustomDeviceStrategy {
    
    /**
     * The template into which the user-defined code is added.
     */
    private static final String CUSTOM_DEVICE_STRATEGY_TEMPLATE =
            "import hu.u_szeged.inf.fog.simulator.application.Application; \n" 
          + "import hu.u_szeged.inf.fog.simulator.iot.mobility.MobilityEvent; \n"
          + "import hu.u_szeged.inf.fog.simulator.iot.strategy.DeviceStrategy; \n"
          + "import java.util.ArrayList; \n\n"
          + "class HelperDeviceStrategy extends DeviceStrategy { \n\n"
          + "@Override \n"
          + "${code} \n" 
          + "}";
    
    /**
     * This replaces the placeholder with the actual code in the template.
     *
     * @param code the code to be pasted
     */
    public static String renderCustomDeviceStrategyTemplate(String code) {
        return CUSTOM_DEVICE_STRATEGY_TEMPLATE.replace("${code}", code);
    }
    
    /**
     * It loads to custom device strategy runtime, which was submitted 
     * by an user via the DISSECT-CF-Fog-WebApp. 
     *
     * @param code the code representing the custom device strategy
     */
    public static DeviceStrategy loadCustomStrategy(String code) {
        File sourceFile;
        try {
            sourceFile = File.createTempFile("HelperDeviceStrategy", ".java", new File("."));
            FileWriter writer = new FileWriter(sourceFile);
            writer.write(code);
            
            JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
            compiler.run(null, null, null, sourceFile.getPath());

            URLClassLoader classLoader = URLClassLoader.newInstance(new URL[]{new File(".").toURI().toURL()});
            Class<?> cls = Class.forName("HelperDeviceStrategy", true, classLoader);

            Constructor<?> constructor = cls.getDeclaredConstructor();
            constructor.setAccessible(true);

            writer.close();
            sourceFile.deleteOnExit();

            return (DeviceStrategy) constructor.newInstance();
        } catch (IOException | ClassNotFoundException | NoSuchMethodException 
                | SecurityException | InstantiationException | IllegalAccessException 
                | IllegalArgumentException | InvocationTargetException e) {
            e.printStackTrace();
        }
        return null;
    }
}