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

public class CustomDeviceStrategy {
    
    public static DeviceStrategy loadCustomStrategy(String code)
            throws IOException, ClassNotFoundException, NoSuchMethodException, 
            IllegalAccessException, InvocationTargetException, InstantiationException {
        File sourceFile = File.createTempFile("HelperDeviceStrategy", ".java", new File("."));
        try (FileWriter writer = new FileWriter(sourceFile)) {
            writer.write(code);
        }

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        compiler.run(null, null, null, sourceFile.getPath());

        URLClassLoader classLoader = URLClassLoader.newInstance(new URL[]{new File(".").toURI().toURL()});
        Class<?> cls = Class.forName("HelperDeviceStrategy", true, classLoader);

        Constructor<?> constructor = cls.getDeclaredConstructor();
        constructor.setAccessible(true);

        sourceFile.deleteOnExit();

        return (DeviceStrategy) constructor.newInstance();
    }
}
