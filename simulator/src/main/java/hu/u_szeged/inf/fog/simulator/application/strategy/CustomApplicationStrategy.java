package hu.u_szeged.inf.fog.simulator.application.strategy;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;

public class CustomApplicationStrategy {
    public static ApplicationStrategy loadCustomStrategy(double activationRatio, double transferDivider, String code)
            throws IOException, ClassNotFoundException, NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException{
        File sourceFile = File.createTempFile("HelperApplicationStrategy", ".java", new File("."));
        try (FileWriter writer = new FileWriter(sourceFile)) {
            writer.write(code);
        }

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        compiler.run(null, null, null, sourceFile.getPath());

        URLClassLoader classLoader = URLClassLoader.newInstance(new URL[]{new File(".").toURI().toURL()});
        Class<?> cls = Class.forName("HelperApplicationStrategy", true, classLoader);

        Constructor<?> constructor = cls.getDeclaredConstructor(double.class, double.class);
        constructor.setAccessible(true);
        ApplicationStrategy instance = (ApplicationStrategy) constructor.newInstance(activationRatio, transferDivider);

        sourceFile.deleteOnExit();

        return instance;
    }
}