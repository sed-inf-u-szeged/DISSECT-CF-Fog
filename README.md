# DISSECT-CF-Fog simulator for modelling IoT-Fog-Cloud systems

[![CodeFactor](https://www.codefactor.io/repository/github/andrasmarkus/dissect-cf/badge)](https://www.codefactor.io/repository/github/andrasmarkus/dissect-cf) [![Maintainability](https://api.codeclimate.com/v1/badges/26b5b9604c501e3d7dde/maintainability)](https://codeclimate.com/github/andrasmarkus/dissect-cf/maintainability) [![Build Status](https://app.travis-ci.com/andrasmarkus/dissect-cf.svg?branch=master)](https://app.travis-ci.com/andrasmarkus/dissect-cf) [![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0) ![lastcommit](https://img.shields.io/github/last-commit/andrasmarkus/dissect-cf) ![GitHub commit activity](https://img.shields.io/github/commit-activity/y/andrasmarkus/dissect-cf)

## Main features

 - Simulating hundreds of fog and cloud nodes
    * Measuring energy consumption of the nodes
 - Simulating IoT applications utilising the physical resources by virtual machines 
    * Measuring makespan of the IoT applications
 - Modelling thousands of IoT devices and sensors 
    * Measuring energy consumption of the IoT devices 
    * Detailed sensor characteristics
 - Modelling IoT actuator events and IoT device mobility
    * Sensor characteristics and device behaviour manipulation
    * Device position and network connection manipulation
 - Considering device strategies to map an IoT device to an IoT application 
 - Considering IoT application strategies to determine task allocations on the nodes
 - Using exisiting IoT trace files to ensure a more realistic simulation
 - Calculating IoT and cloud/fog side utilising costs by considering real provider schemas such as
    * AWS, Microsoft Azure, IBM Cloud, Oracle Cloud
 - Providing XML based configuration options
 
## Publications

DISSECT-CF-Fog ensures the evaluation phase of numerous conference papers, journal articles and book chapters: 
 - [Actuator behaviour modelling in IoT-Fog-Cloud simulation]
 - [Modelling Energy Consumption of IoT Devices in DISSECT-CF-Fog]
 - [Location-aware Task Allocation Strategies for IoT-Fog-Cloud Environments]
 - [Investigating IoT Application Behaviour in Simulated Fog Environments]
 - [Develop or Dissipate Fogs? Evaluating an IoT Application in Fog and Cloud Simulations]
 - [A Survey and Taxonomy of Simulation Environments Modelling Fog Computing]
 - [Multi-Cloud Management Strategies for Simulating IoT Applications]
 - [Cost-aware IoT extension of DISSECT-CF]
 - [Simulating IoT Cloud systems: A meteorological case study]
 - [Flexible Representation of IoT Sensors for Cloud Simulators]

## Getting started
- Clone the repository: https://github.com/andrasmarkus/dissect-cf
- Import the Maven-based project into your IDE (e.g. Eclipse or IntelliJ)
- The demo package contains the most relevant use cases of DISSECT-CF-Fog 
- For more information look at the description of the core simulator: [DISSECT-CF] 

## History

DISSECT-CF-Fog has been evolving since 2016 in the [IoT Cloud Research Group] at the University of Szeged, Hungary. It is built upon [DISSECT-CF] infrastructure cloud simulator. During this time many colleagues and students contributed to the software, special thanks to 
 - Attila  Kertesz, PhD
 - Gabor Kecskemeti, PhD
 - Jozsef Daniel Dombi, PhD
 - Peter Gacsi
 - Mate Biro
 - Rudolf Bun
 - Marton Varga
 - Akos Fuzesi

## Contact and license

For any inquiries please contact by email to: markusa@inf.u-szeged.hu

License: [GNU General Public License v3.0]

<p align="center">
<img src="http://users.iit.uni-miskolc.hu/~kecskemeti/DISSECT-CF/logo.jpg"/>
</p>

[DISSECT-CF]: <https://github.com/kecskemeti/dissect-cf>
[IoT Cloud Research Group]: <https://www.sed.inf.u-szeged.hu/iotcloud>
[GNU General Public License v3.0]: <https://www.gnu.org/licenses/gpl-3.0.html>
[Apache Maven 3]: http://maven.apache.org/

[Modelling Energy Consumption of IoT Devices in DISSECT-CF-Fog]: <https://doi.org/10.5220/0010500003200327>
[Location-aware Task Allocation Strategies for IoT-Fog-Cloud Environments]: <https://doi.org/10.1109/PDP52278.2021.00037>
[Investigating IoT Application Behaviour in Simulated Fog Environments]: <https://doi.org/10.1007/978-3-030-72369-9_11>
[Develop or Dissipate Fogs? Evaluating an IoT Application in Fog and Cloud Simulations]: <https://doi.org/10.5220/0009590401930203>
[A Survey and Taxonomy of Simulation Environments Modelling Fog Computing]: <https://doi.org/10.1016/j.simpat.2019.102042>
[Multi-Cloud Management Strategies for Simulating IoT Applications]: <https://doi.org/10.14232/actacyb.24.1.2019.7>
[Cost-aware IoT extension of DISSECT-CF]: <https://doi.org/10.3390/fi9030047>
[Simulating IoT Cloud systems: A meteorological case study]: <https://doi.org/10.1109/FMEC.2017.7946426>
[Flexible Representation of IoT Sensors for Cloud Simulators]: <https://doi.org/10.1109/PDP.2017.87>
[Actuator behaviour modelling in IoT-Fog-Cloud simulation]:  <https://doi.org/10.7717/peerj-cs.651>
