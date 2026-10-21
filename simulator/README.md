# Overview

This directory contains the heart of the DISSECT-CF-Fog modular simulation platform, which provides the fundamental
discrete-event simulation engine. Although this is a Java-based console application, we recommend using the tool with an
IDE ([Eclipse] or [IntelliJ]). However, the build process is handled by the Maven project management tool, so it can be
run with the CLI as well.

## Getting started

* [JDK] (version 11 or higher)

* [Maven] (version 3.9.3, [Installation])

    * IDEs typically have built-in Maven support, so direct installation may not be needed.

* [Python] (version 3.8 or higher)

    * Installing Python is optional because the script's primary function is to generate a map after the simulation
      ends, which can be useful for debugging. For more details, refer to the `MapVisualiser.mapGenerator(..)`
      instruction in the demo package.

    * The script (located in the *src/main/resources/script* directory) requires additional installation of dependencies
      with the following command: `pip install -r requirement.txt`

    * For Windows OS, installation of [Graphviz] (version 11) is also required if the workflow visualization script
      (*DAG.py*) is also executed.

### Running simulations using the IDE

* With Eclipse:

    * File -> Import... -> Maven -> Existing Maven Projects -> Browse for pom.xml located in the root of the project

    * To run a simulation, choose one in the demo package -> Right click on the file -> Run As -> Java Application

* With IntelliJ:

    * File -> Open... -> Selecting the simulator submodule

    * To run a simulation, choose one in the demo package -> Right click on the file -> Run

### Running simulations using the CLI

Run the following command in the root of this module to compile the source code, run the tests, and package the compiled
code into a JAR file:

`mvn clean package`

To execute a user-defined simulation, run the following command in the root directory (version and main class may vary):

`java -cp target/dissect-cf-fog-1.0.0-SNAPSHOT-jar-with-dependencies.jar hu.u_szeged.inf.fog.simulator.demo.IoTSimulation`

### Documentation

Run the following command to generate the documentation for the simulator's Java API (it will be generated in the
*target/site/apidocs* subfolder of the main directory):

`mvn javadoc:javadoc`

### Examples

The code examples can be found in the following package:

*src.main.java.hu.u_szeged.inf.fog.simulator.demo*

## SHA-256 CPU benchmarking and calibration

The distributed ledger simulation uses SHA-256 hashing performance to estimate the computational cost of Proof-of-Work
(PoW) mining.

Different processors can calculate hashes at different speeds. To make the mining simulation configurable for different
hardware, users can measure their own CPU's SHA-256 throughput and provide the result to the simulator.

The reference measurement is the number of SHA-256 operations performed per second (hashes per second, H/s) using
80-byte inputs and a single CPU thread.

### SHA-256 CPU benchmark reference

The following results were obtained from measurements on three different processors.

All measurements used:

* SHA-256 hashing
* 80-byte input blocks
* A benchmark duration of approximately 3 seconds
* Single-threaded execution
* Three repeated measurements per processor

| CPU                | OS      | OpenSSL | Average H/s |
|--------------------|---------|---------|------------:|
| Apple M1           | macOS   | 3.5.9   |   8,160,256 |
| Intel Core i3-N305 | Windows | 4.0.3   |   4,222,466 |
| Intel Core i7-8700 | Windows | 4.0.3   |   2,288,285 |

The average throughput was calculated from the three measurements performed on each processor.

These values provide a reference for comparing SHA-256 performance across different CPUs.

The results are not general CPU performance rankings. Hashing performance also depends on the CPU architecture,
hardware acceleration, OpenSSL version, compiler optimizations, and system conditions.

For the most accurate calibration, users should perform the benchmark on their own hardware.

### Tutorial: Measuring SHA-256 performance

This tutorial explains how to measure SHA-256 performance and configure the simulator to use the measured value.

#### Step 1: Install OpenSSL

OpenSSL is used to measure the hashing performance of the processor.

**Windows**

Download and install a Windows version of OpenSSL, for example from:

https://slproweb.com/products/Win32OpenSSL.html

After installation, open PowerShell.

Check the installed OpenSSL version:

```powershell
& "C:\Program Files\OpenSSL-Win64\bin\openssl.exe" version
```

If OpenSSL was installed in another directory, replace the path with the correct location.

If OpenSSL is available in the system PATH, the shorter command can also be used:

```powershell
openssl version
```

**macOS**

Install OpenSSL using Homebrew:

```bash
brew install openssl@3
```

Verify the installation:

```bash
"$(brew --prefix openssl@3)/bin/openssl" version
```

Using the Homebrew executable explicitly ensures that the intended OpenSSL installation is used.

**Linux (Ubuntu/Debian)**

Install OpenSSL:

```bash
sudo apt update
sudo apt install openssl
```

Verify the installation:

```bash
openssl version
```

#### Step 2: Identify the processor

Record the processor model so the benchmark result can be associated with the correct hardware.

**Windows (PowerShell)**

```powershell
(Get-CimInstance Win32_Processor).Name
```

**macOS**

```bash
system_profiler SPHardwareDataType
```

**Linux**

```bash
lscpu
```

Also record the operating system and OpenSSL version.

#### Step 3: Run the SHA-256 benchmark

The simulator uses an 80-byte SHA-256 hashing workload for PoW nonce searching.

Therefore, the benchmark must use 80-byte input blocks.

**Windows (PowerShell)**

Run the following command:

```powershell
& "C:\Program Files\OpenSSL-Win64\bin\openssl.exe" speed -elapsed -seconds 3 -bytes 80 -evp sha256
```

To repeat the measurement three times automatically:

```powershell
1..3 | ForEach-Object {
    & "C:\Program Files\OpenSSL-Win64\bin\openssl.exe" speed -elapsed -seconds 3 -bytes 80 -evp sha256
}
```

**macOS**

Run the following command:

```bash
"$(brew --prefix openssl@3)/bin/openssl" speed -elapsed -seconds 3 -bytes 80 -evp sha256
```

To repeat the measurement three times:

```bash
for i in 1 2 3; do
    "$(brew --prefix openssl@3)/bin/openssl" speed -elapsed -seconds 3 -bytes 80 -evp sha256
done
```

**Linux**

Run the following command:

```bash
openssl speed -elapsed -seconds 3 -bytes 80 -evp sha256
```

To repeat the measurement three times:

```bash
for i in 1 2 3; do
    openssl speed -elapsed -seconds 3 -bytes 80 -evp sha256
done
```

The benchmark uses a single CPU thread by default. Do not use the OpenSSL `-multi` option for this reference
measurement.

For more reliable results, avoid running other CPU-intensive applications during the benchmark.

#### Step 4: Read the benchmark result

OpenSSL produces output similar to:

```text
You have chosen to measure elapsed time instead of user CPU time.
Doing sha256 ops for 3s on 80 size blocks: 6869424 sha256 ops in 3.01s
```

The important values are:

* `6869424`: Total number of SHA-256 operations
* `3.01s`: Actual elapsed measurement time

OpenSSL also displays a summary table showing the number of kilobytes processed per second.

For simulator calibration, use the number of SHA-256 operations and the elapsed time, not the kilobytes-per-second
value.

#### Step 5: Calculate hashes per second

The required reference throughput is calculated using:

```text
hashesPerSecond = totalHashes / elapsedSeconds
```

Using the example above:

```text
hashesPerSecond = 6869424 / 3.01
hashesPerSecond ≈ 2282201
```

The result indicates that the processor performed approximately 2.28 million SHA-256 operations per second.

If the benchmark was repeated three times, calculate the throughput separately for each measurement.

Then calculate the average:

```text
averageHashesPerSecond = (measurement1 + measurement2 + measurement3) / 3
```

For example, the Intel Core i7-8700 measurements produced the following results:

| Measurement | Hashes per second |
|-------------|------------------:|
| 1           |         2,282,201 |
| 2           |         2,277,060 |
| 3           |         2,305,594 |
| Average     |         2,288,285 |

The average value, `2288285`, can be used as the reference throughput when configuring the simulator for this measured
CPU performance.

#### Step 6: Configure the simulator

The `SHA256Strategy` class supports a configurable reference throughput using the following Java system property:

```text
dissect.sha256.hashesPerSecond
```

The property specifies the measured single-threaded SHA-256 performance in hashes per second for 80-byte inputs.

**Option A: IntelliJ IDEA - VM options**

1. Open the project in IntelliJ IDEA.
2. Open `Run > Edit Configurations`.
3. Select the run configuration for `DistributedLedgerSimulation`.
4. Find the `VM options` field.
5. Enter the measured hash throughput using the following format:

```text
-Ddissect.sha256.hashesPerSecond=2288285
```

6. Apply the configuration.
7. Run the simulation.

If the `VM options` field is not visible, enable it from the `Modify options` menu.

The example above uses the measured average throughput of the Intel Core i7-8700.

For the Apple M1 reference measurement, the configuration would be:

```text
-Ddissect.sha256.hashesPerSecond=8160256
```

For the Intel Core i3-N305 reference measurement:

```text
-Ddissect.sha256.hashesPerSecond=4222466
```

Users should replace these examples with the result obtained from their own hardware.

**Option B: IntelliJ IDEA - Environment variables**

If the `VM options` field is not available, the reference throughput can also be configured using an environment
variable.

1. Open `Run > Edit Configurations`.
2. Select the `DistributedLedgerSimulation` run configuration.
3. Find the `Environment variables` field.
4. Add the following environment variable:

```text
Name: JAVA_TOOL_OPTIONS
Value: -Ddissect.sha256.hashesPerSecond=8160256
```

5. Apply the changes.
6. Run the simulation.

The JVM automatically reads the `JAVA_TOOL_OPTIONS` environment variable at startup and applies the configured SHA-256
reference throughput.

A message similar to the following may appear in the console:

```text
Picked up JAVA_TOOL_OPTIONS: -Ddissect.sha256.hashesPerSecond=8160256
```

This message is normal and indicates that the JVM detected the environment variable.

The example uses the measured Apple M1 reference throughput. Replace the number with the throughput measured on your
own processor.

If the `JAVA_TOOL_OPTIONS` variable already contains other JVM options, append the new property separated by a space
instead of replacing the existing options.

Use either the `VM options` method or the `JAVA_TOOL_OPTIONS` method to configure the reference throughput.

**Option C: Command-line execution**

First, build the simulator from the `simulator` directory:

```bash
mvn clean package
```

Then execute the distributed ledger simulation with the custom SHA-256 throughput:

```bash
java -Ddissect.sha256.hashesPerSecond=2288285 -cp target/dissect-cf-fog-1.0.0-SNAPSHOT-jar-with-dependencies.jar hu.u_szeged.inf.fog.simulator.demo.DistributedLedgerSimulation
```

The JVM property must be provided before the main class name.

**Default configuration**

If no custom hash throughput is provided, the simulator uses its built-in reference value of approximately
2,356,048 hashes per second.

This default was obtained from an earlier SHA-256 measurement and is separate from the three CPU reference measurements
listed above.

The configured value must be positive and finite. When entering a decimal value, use a period as the decimal separator.

#### Step 7: How the simulator uses the benchmark

The simulator converts the measured hash throughput into the estimated computational cost of one SHA-256 operation.

For an 80-byte input:

```text
hashCost = 1.0 / hashesPerSecond
```

For example, using a measured throughput of 2,288,285 hashes per second:

```text
hashCost = 1.0 / 2288285
hashCost ≈ 0.0000004370
```

This value represents the normalized computational cost of a single 80-byte SHA-256 operation.

The `SHA256Strategy` class uses this reference together with its existing interpolation data to estimate costs for
different input sizes.

During PoW mining, the `FindNonceTask` simulates nonce searching in batches.

Each batch receives a computational workload based on:

```text
batchWork = numberOfNonceAttempts * hashCost
```

The simulator then schedules this computational workload on the miner's virtual machine.

The nonce-search process is statistical: it models the number and computational cost of attempts without calculating
a real SHA-256 hash for every individual nonce.

The reference throughput describes single-threaded CPU performance. Virtual CPU core counts and processing capacities
are configured separately in the simulator.

When multiple virtual CPU cores are used, the simulation may assume parallel processing of the mining workload.
Actual hardware scaling may differ.

The calculated values are normalized simulation work units rather than actual hardware CPU instruction counts.

#### Step 8: Verify the simulation

After configuring the measured hash throughput:

1. Run the `DistributedLedgerSimulation` example.
2. Open the generated simulation log.
3. Locate the `FindNonceTask` events.
4. Check that nonce-search progress and successful mining events appear.
5. Compare the simulated mining durations with the expected behavior for the configured CPU performance.

Individual mining durations vary because nonce searching is modeled probabilistically.

For meaningful comparisons, evaluate multiple mining operations rather than relying on a single result.

The benchmark calibrates the time-related computational cost of SHA-256 hashing. It does not independently validate
the physical energy consumption of the simulated hardware.

[Eclipse]: https://www.eclipse.org/downloads/
[IntelliJ]: https://www.jetbrains.com/idea/download
[JDK]: https://www.oracle.com/java/technologies/javase/jdk11-archive-downloads.html
[Maven]: https://maven.apache.org/download.cgi
[Installation]: https://maven.apache.org/install.html
[Python]: https://www.python.org/downloads/
[Graphviz]: https://graphviz.org/download/
