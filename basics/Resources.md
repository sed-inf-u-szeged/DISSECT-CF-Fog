---
title: Resources
parent: Basics
nav_order: 8
permalink: /basics/resources
---

# Resources
{: .no_toc }

## Table of contents
{: .no_toc .text-delta }

- TOC
{:toc}

---

## Reusable simulations

The [resources folder] contains many useful extra utilities to use with the simulator, one of many is the demos.
These demos are premade files to help build and inicialize simulations.

These files have many variations, most ones, like the ones from the original DISSECT-CF, are XML files.
While the newer ones used by the DISSECT-CF-Fog extensions are JSON or YAML files.
This means as long as you have a way to load them you could also make your own simulations like this, so they can be reused.

Once you have it setup (or if you just use the already available ones) you can load in an entire cloud with many physical machines related
to it with just two lines of code, as previously seen in the [IoT Simulation](iot_example#example) and [Fog Simulation](fog_example#example) examples:
```java
String cloudfile = ScenarioBase.resourcePath + "LPDS_original.xml";
ComputingAppliance cloud1 = new ComputingAppliance(cloudfile, "cloud1", new GeoLocation(47.45, 21.3), 0);
```
 
---
 
## Scripts

There also scripts found in the resources. These are small Python scripts that give extra utility such as map making or making directed acyclic graphs.
Check them out, see if you find them useful or make your own ones!: [DAG.py]{:target="_blank"}, [clusterMap.py]{:target="_blank"}, [map.py]{:target="_blank"}

---

## Checkstyle - Contribution

Lastly the [checkstyle folder] contains configuration files used to enforce and manage code style rules within the simulator project.
The checkstyle_rules.xml defines the coding standards that contributors are expected to follow, such as formatting conventions, naming rules,
and structural guidelines. Meanwhile, the checkstyle-suppressions.xml allows specific files or cases to bypass certain rules when strict enforcement
is impractical or would cause unnecessary warnings. Together, these files help maintain a consistent codebase while still allowing flexibility where needed.


[resources folder]: https://github.com/sed-inf-u-szeged/DISSECT-CF-Fog/tree/master/simulator/src/main/resources
[DAG.py]: https://github.com/sed-inf-u-szeged/DISSECT-CF-Fog/blob/master/simulator/src/main/resources/script/DAG.py
[clusterMap.py]: https://github.com/sed-inf-u-szeged/DISSECT-CF-Fog/blob/master/simulator/src/main/resources/script/clusterMap.py
[map.py]: https://github.com/sed-inf-u-szeged/DISSECT-CF-Fog/blob/master/simulator/src/main/resources/script/map.py
[checkstyle folder]: https://github.com/sed-inf-u-szeged/DISSECT-CF-Fog/tree/master/simulator/src/main/resources/checkstyle