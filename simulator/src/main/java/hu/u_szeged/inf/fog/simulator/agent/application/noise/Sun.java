package hu.u_szeged.inf.fog.simulator.agent.application.noise;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.util.SeedSyncer;
import hu.u_szeged.inf.fog.simulator.common.util.ScenarioBase;
import hu.u_szeged.inf.fog.simulator.common.util.SimLogger;

/**
 * Singleton-based model of the Sun for the simulation environment.
 */
public class Sun {
    
    private static Sun sunInstance;
    
    private final double sunrise;
    
    private final double sunset;
    
    private final double peakHour;
    
    private final double gamma;
    
    /**
     * Private constructor to enforce singleton usage.
     *
     * @param sunrise sunrise hour (0–24)
     * @param sunset sunset hour (0–24)
     * @param peekHour hour of maximum sun strength (0–24)
     * @param gamma curve shaping exponent
     */
    private Sun(double sunrise, double sunset, double peekHour, double gamma) {
        this.sunrise = sunrise;
        this.sunset = sunset;
        this.peakHour = peekHour;
        this.gamma = gamma;        
    }
    
    /**
     * Initializes the singleton instance.
     * If the instance has already been initialized, an error is logged
     * and an exception is thrown.
     *
     * @param sunrise sunrise hour (0–24)
     * @param sunset sunset hour (0–24)
     * @param peakHour hour of maximum sun strength (0–24)
     * @param gamma curve shaping exponent
     */
    public static void init(double sunrise, double sunset, double peakHour, double gamma) {
        if (sunInstance != null) {
            SimLogger.logError("The sun for the environment is already initialised!");
        }
        sunInstance = new Sun(sunrise, sunset, peakHour, gamma);
    }
    
    /**
     * Returns the singleton Sun instance.
     */
    public static Sun getInstance() {
        if (sunInstance != null) {
            return sunInstance;
        } else {
            SimLogger.logError("The sun for the environment does not exist!");
        }
        return null;
    }
    
    /**
     * Computes the current sun strength based on the simulation time.
     *
     * @return current sun strength (in the range 0–1)
     */
    public double getSunStrength() {
        long nowMs   = Timed.getFireCount();
        long dayMs   = ScenarioBase.DAY_IN_MILLISECONDS;
        long msInDay  = Math.floorMod(nowMs, dayMs);
        double hour =  /* sunrise + */ msInDay / (double) ScenarioBase.HOUR_IN_MILLISECONDS; 

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
        double amp = 0.5 + SeedSyncer.centralRnd.nextDouble() * 0.5;

        return amp * s;
    }
}
