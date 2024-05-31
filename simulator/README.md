# Overview

This directory contains the heart of the DISSECT-CF-Fog modular simulation platform, which provides the fundamental discrete-event simulation engine. Although this is a Java-based console application, we recommend using the tool with an IDE ([Eclipse] or [IntelliJ]). However, the build process is handled by the Maven project management tool, so it can be run with the CLI as well.


## Getting started

* [JDK] (version 11 or higher)

* [Maven] (version 3.9.3, [Installation]) 

	* IDEs typically have built-in Maven support, so direct installation may not be needed.

* [Python] (version 3.8 or higher)
 
	* Installing Python is optional because the script's primary function is to generate a map after the simulation ends, which can be useful for debugging. For more details, refer to the `MapVisualiser.mapGenerator(..)` instruction in the demo package. 
	
	* The script (located in the *src/main/resources/script* directory) requires additional installation of dependencies with the following command: `pip install -r requirement.txt`
	
	* For Windows OS, installation of [Graphviz] (version 11) is also required if the workflow visualization script (*DAG.py*) is also executed

### Running simulations using the IDE

* With Eclipse:

	* File -> Import... -> Maven -> Existing Maven Projects -> Browse for pom.xml located in the root of the project

	* To run a simulation, choose one in the demo package -> Right click on the file -> Run As -> Java Application
	
* With IntelliJ:
 	
 	* File -> Open... -> Selecting the simulator submodule
 	
 	* To run a simulation, choose one in the demo package -> Right click on the file -> Run 
	
### Running simulations using the CLI

Run the following command in the root of this module to compile the source code, run the tests, and package the compiled code into a JAR file: 

`mvn clean package`

To execute a user-defined simulation, run the following command in the root directory (version and main class may vary):

`java -cp target/dissect-cf-fog-1.0.0-SNAPSHOT-jar-with-dependencies.jar hu.u_szeged.inf.fog.simulator.demo.IoTSimulation`

### Documentation

Run the following command to generate the documentation for the simulator's java API (it will be generated in the *target/site/apidocs* subfolder of the main directory):

`mvn javadoc:javadoc`

### Examples

The code examples can be found in the following package:

*src.main.java.hu.u_szeged.inf.fog.simulator.demo*


[Eclipse]: https://www.eclipse.org/downloads/
[IntelliJ]: https://www.jetbrains.com/idea/download
[JDK]: https://www.oracle.com/java/technologies/javase/jdk11-archive-downloads.html
[Maven]: https://maven.apache.org/download.cgi
[Installation]: https://maven.apache.org/install.html
[Python]: https://www.python.org/downloads/
[Graphviz]: https://graphviz.org/download/
