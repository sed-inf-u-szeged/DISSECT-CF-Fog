<p align="center">
<img width="222" src="https://www.inf.u-szeged.hu/~markusa/dcf-logo-min.png"/>
</p>

# DISSECT-CF-Fog Executor

## Setup Guide

 - Build the jar file in simulator's root directory (simulator/): ```mvn clean install```

 - Install the dependency of the simulator's jar: 
 
   ```
   mvn install:install-file \
    -Dfile=<absolute-path-to>/DISSECT-CF-Fog/simulator/target/dissect-cf-fog-1.0.0-SNAPSHOT.jar \
    -DgroupId=hu.u_szeged.inf.sed.fog.simulator \
    -DartifactId=dissect-cf-fog \
    -Dversion=1.0.0-SNAPSHOT \
    -Dpackaging=jar```
