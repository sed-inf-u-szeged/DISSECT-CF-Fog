package hu.u_szeged.inf.fog.simulator.agent.urbannoise;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.u_szeged.inf.fog.simulator.agent.SwarmAgent;
import hu.u_szeged.inf.fog.simulator.demo.ScenarioBase;
import hu.u_szeged.inf.fog.simulator.util.SimLogger;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.SplittableRandom;

public class Sun {
    
    private static Sun sunInstance;
    
    private final double sunrise;
    
    private final double sunset;
    
    private final double peakHour;
    
    private final double gamma;
    
    private final Map<Long, Double> ampCache = new HashMap<>();
    
    private Sun(double sunrise, double sunset, double peekHour, double gamma) {
        this.sunrise = sunrise;
        this.sunset = sunset;
        this.peakHour = peekHour;
        this.gamma = gamma;        
    }
    
    public static void init(double sunrise, double sunset, double peakHour, double gamma) {
        if (sunInstance != null) {
            SimLogger.logError("The environment is already initialised!");
            System.exit(0);
        }
        sunInstance = new Sun(sunrise, sunset, peakHour, gamma);
    }
    
    public static Sun getInstance() {
        if (sunInstance != null) {
            return sunInstance;
        } else {
            SimLogger.logError("The environment does not exist!");
            System.exit(0);
        }
        return null;
    }
    
    public double getSunStrength() {
        long nowMs   = Timed.getFireCount();
        long dayMs   = ScenarioBase.aDayInMilisec;
        long dayIndex = Math.floorDiv(nowMs, dayMs);
        long msInDay  = Math.floorMod(nowMs, dayMs);
        double hour   = sunrise + msInDay / 3_600_000.0; 

        if (hour <= sunrise || hour >= sunset) {
            return 0.0;
        }

        double s;
        if (hour <= peakHour) {
            double u = (hour - sunrise) / (peakHour - sunrise);
            s = Math.sin((Math.PI / 2.0) * u);                    
        } else {
            double v = (sunset - hour) / (sunset - peakHour);     
            s = Math.sin((Math.PI / 2.0) * v);                    
        }

        s = Math.pow(Math.max(0.0, s), gamma);

        double amp = 0.5 + new SplittableRandom(dayIndex).nextDouble() * 0.5;

        return amp * s;
    }
}
