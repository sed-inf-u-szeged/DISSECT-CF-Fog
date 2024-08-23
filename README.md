# DISSECT-CF-Fog: A Simulation Environment for Analysing the Cloud-to-Thing Continuum

<p align="center">
<img width="200" src="https://www.inf.u-szeged.hu/~markusa/dcf-logo-min.png"/>
</p>

## About
The evolution of the DISSECT-CF-Fog simulator has been underway since 2016 within the [IoT Cloud Research Group] at the University of Szeged, Hungary. It is built upon the [DISSECT-CF] infrastructure cloud simulator. The stable version is able to investigate the trade-offs of offloading algorithms as well as execute the scheduling of IoT workflow jobs. Main features:

 - Simulating hundreds of fog and cloud nodes
    * Measuring energy consumption of the nodes
 - Simulating IoT applications utilising the physical resources by virtual machines 
    * Measuring makespan of the IoT applications
 - Modelling thousands of IoT devices and sensors
    * Measuring energy consumption of the IoT devices 
    * Detailed characteristics of the IoT entities
 - Modelling IoT actuator events and IoT device mobility
    * Task execution on Edge devices
 - Calculating IoT and cloud costs by considering real provider's (AWS, Azure) schemas 

The project currently consists of five modules; for further details please follow the links provided below:

#### 1. Simulator

The discrete-event, core [simulator module] focusing on the interoperation and analysis of IoT-Fog-Cloud systems.

&nbsp;&nbsp;&nbsp; ⚠️ *Important*: The core simulator can run independently without the need to install the other modules!

#### 2. Web Application

An Angular-based [web application module] that can be used to set up a simulation and its parameters without programming.

#### 3. Executor

A Spring Boot-based [executor module] that is able to load and execute configurations stored in a MongoDB, created by the [web application module]. 

#### 4. Predictor UI

An Electron.js-based [desktop application module] that helps configure and manage scenarios utilising the time series analysis functionality of the core simulator.

#### 5. Converter

Initial version of a [converter module] that is able to transform simple CloudSim and iFogSim scenarios to simulation in DISSECT-CF-Fog, and vice versa.

## Relevant Publications

- A. Markus, A. Al-Haboobi, G. Kecskemeti and Attila Kertesz. [Simulating IoT Workflows in DISSECT-CF-Fog]. Sensors 23, no. 3: 1294, 2023. DOI: 10.3390/s23031294

- A. Markus, J. D. Dombi and A. Kertesz. [Location-aware Task Allocation Strategies for IoT-Fog-Cloud Environments]. 29th Euromicro International Conference on Parallel, Distributed and Network-Based Processing, 185-192, 2021. DOI: 10.1109/PDP52278.2021.00037

- A. Markus, P. Gacsi and A. Kertesz. [Develop or Dissipate Fogs? Evaluating an IoT Application in Fog and Cloud Simulations]. 10th International Conference on Cloud Computing and Services Science, 193-203, 2020. DOI: 10.5220/0009590401930203

## Contact

For any inquiries please contact by email to: markusa@inf.u-szeged.hu

[DISSECT-CF]: <https://github.com/kecskemeti/dissect-cf>
[IoT Cloud Research Group]: <https://www.sed.inf.u-szeged.hu/iotcloud>

[simulator module]: https://github.com/sed-inf-u-szeged/DISSECT-CF-Fog/tree/master/simulator
[web application module]: <https://github.com/sed-inf-u-szeged/DISSECT-CF-Fog/tree/master/webapp>
[executor module]: https://github.com/sed-inf-u-szeged/DISSECT-CF-Fog/tree/master/executor
[desktop application module]: <https://github.com/sed-inf-u-szeged/DISSECT-CF-Fog/tree/master/predictor-ui>
[converter module]: <https://github.com/sed-inf-u-szeged/DISSECT-CF-Fog/tree/master/converter>

[Simulating IoT Workflows in DISSECT-CF-Fog]: <https://doi.org/10.3390/s23031294>
[Location-aware Task Allocation Strategies for IoT-Fog-Cloud Environments]: <https://doi.org/10.1109/PDP52278.2021.00037>
[Develop or Dissipate Fogs? Evaluating an IoT Application in Fog and Cloud Simulations]: <https://doi.org/10.5220/0009590401930203>
