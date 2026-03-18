---
title: Repository
parent: Basics
nav_order: 3
permalink: /basics/repository
---

# Repository
{: .no_toc }

## Table of contents
{: .no_toc .text-delta }

- TOC
{:toc}

---

## Network and Storage

In the DISSECT-CF-Fog simulator **storage** is implemented by the **[Repository]{:target="_blank"}** class.
In cloud environments such as those simulated here, these repositories store **[virtual appliances]{:target="_blank"}** ([pre-configured virtual machine image]{:target="_blank"}),
which then can be used to create **[virtual machines]{:target="_blank"}** - more on those [later](vm).

In the simulator **network communication** is also handled by the **[Repository]{:target="_blank"}** class.
Repositories can be standalone or part of [physical machines]{:target="_blank"} acting as their storage - more on these [later](vm) as well.  
This means that the **communication** between physical machines (or virtual machines running on them) is performed **via their repositories**.

These repositories can store **[StorageObjects]{:target="_blank"}** and the previously mentioned **[VirtualAppliances]{:target="_blank"}**.

---

## [Repository]{:target="_blank"}

Since the repository handles so many things in the simulator using it is a little more complex compared to the previously introduced Timed or DeferredEvent.

First, we will take a look at the constructor. For that, we need to briefly discuss the simulator's ability to measure energy consumption.  
Since this topic is beyond the scope of the basics due to its complexity, we will not go into detail. However, because the Repository constructor requires
values related to this functionality, we will use the [PowerTransitionGenerator]{:target="_blank"} in the upcoming example (`generateTransitions` function), so it is useful to have a basic understanding of it.

{: .note}
It is also important to mention that both DISSECT-CF and DISSECT-CF-Fog provide classes for energy data collection.  
For class initialization, we will use DISSECT-CF's utility class, [PowerTransitionGenerator]{:target="_blank"}.  
For actual energy data collection, however, we will use DISSECT-CF-Fog's [EnergyDataCollector]{:target="_blank"}.


#### `Repository - constructor`
{: .text-beta}

```java
public Repository(final long capacity, final String id,
                  final long maxInBW, final long maxOutBW, final long diskBW,
                  final Map<String, Integer> latencyMap, Map<String, PowerState> diskPowerTransitions,
                  Map<String, PowerState> networkPowerTransitions) {...}
```

**Description:**  
Constructor for repository objects.

**Parameters:**
- `capacity` - the storage capacity of the repository.
- `id` - the repository's id.
- `maxInBW` - the input network bandwidth of the repository.
- `maxOutBW` - the output network bandwidth of the repository.
- `diskBW` - the disk bandwidth of the repository.
- `latencyMap` - the direct network connections of this network node. Contents of the map:
    - The key of this map lists the names of the network nodes to which this particular network node is connected.
    - The value of this map lists the latencies in ticks between this network node and the node named in the key.
- `diskPowerTransitions` - parameter for energy data collection.
- `networkPowerTransitions` - parameter for energy data collection.

{: .important}
The values for maxInBW, maxOutBW and diskBW should be consistent.


----------------


Some other noteworthy functions in the Repository class:

#### `registerObject`
{: .text-beta}

```java
public boolean registerObject(final StorageObject so) {...}
```

**Description:**
This function is designed to simulate the save function of the repository.

**Parameters:**
- `so` - is the object to be stored.

**Return:**
**True** if the requested object was stored, **false** when there is not enough space to store the object.


---------------------------------------------------


#### `deregisterObject`
{: .text-beta}

```java
public boolean deregisterObject(final StorageObject so) {...}
```

**Description:**  
This function is designed to simulate the erase function of the repository given that its user knows the StorageObject to be dropped.

**Parameters:**
- `so` - is the object to be removed.

**Return:**
**True** if the requested object was dropped, **false** if there are ongoing transfers involving the object.


{: .note}
There's another deRegisterObject function where instead of giving a StorageObject to remove, you can give a storage object identifier (string).

---------------------------------------------------


#### `requestContentDelivery`
{: .text-beta}

```java
public ResourceConsumption requestContentDelivery(final String id, final Repository target,
			final ResourceConsumption.ConsumptionEvent ev) {...}
```

**Description:**
Initiates transfer from a remote location.

**Parameters:**
- `id` - the storage object id that will be transferred.
- `target` - the target repository where the transferred data will reside.
- `ev` - the event to be fired if the transfer is completed.

**Return:**
The consumption object that represents the appropriate data transfer or null if it is not possible to initiate. (In the upcoming example this won't matter.)

## Example

Let's look at an actual data [transfer example]{:target="_blank"}!

```java
package hu.u_szeged.inf.fog.simulator.demo.simple;
import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.energy.powermodelling.PowerState;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ConsumptionEventAdapter;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode.NetworkException;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;
import hu.mta.sztaki.lpds.cloud.simulator.io.StorageObject;
import hu.mta.sztaki.lpds.cloud.simulator.util.PowerTransitionGenerator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

public class TransferExample {

    Repository from;
    Repository to;
    StorageObject so;
    long startTime;

    public TransferExample(Repository from, Repository to, StorageObject so) throws NetworkException {
        this.from = from;
        this.to = to;
        this.so = so;
        this.from.registerObject(so);
        this.startTime = Timed.getFireCount();

        this.from.requestContentDelivery(so.id, to, new ConsumptionEventAdapter() {
            @Override
            public void conComplete() {
                from.deregisterObject(so);
                System.out.println("Start: " + startTime + " from: " + from.getName() + " to: " + to.getName() + " end: " +Timed.getFireCount());
            }
        });
    }

    public static void main(String[] args) throws NetworkException {
      
        int fileSize = 100;
        long storageSize = 500;
        long bandwidth = 500;

        final EnumMap<PowerTransitionGenerator.PowerStateKind, Map<String, PowerState>> transitions =
                PowerTransitionGenerator.generateTransitions(20, 200, 300, 10, 20);

        HashMap<String, Integer> latencyMap = new HashMap<>();

        Repository repo1 = new Repository(storageSize, "repo1", bandwidth, bandwidth, bandwidth, latencyMap, 
                transitions.get(PowerTransitionGenerator.PowerStateKind.storage),
                transitions.get(PowerTransitionGenerator.PowerStateKind.network));

        Repository repo2 = new Repository(storageSize, "repo2", bandwidth, bandwidth, bandwidth, latencyMap,
                transitions.get(PowerTransitionGenerator.PowerStateKind.storage),
                transitions.get(PowerTransitionGenerator.PowerStateKind.network));

        repo1.setState(NetworkNode.State.RUNNING);
        repo2.setState(NetworkNode.State.RUNNING);

        latencyMap.put("repo1", 5);
        latencyMap.put("repo2", 6);

        new TransferExample(repo1, repo2, new StorageObject("file1", fileSize, false));
        new TransferExample(repo2, repo1, new StorageObject("file2", fileSize, false));

        Timed.simulateUntilLastEvent();

        System.out.println("repo1: " + repo1.contents());
        System.out.println("repo2: " + repo2.contents());
    }
}
```

In this example we can see a simple implementation of data transfer between repositories.

Let's unpack what is actually happening here.

- There is the example class with four attributes:
  - A repository where the data will be transferred **from**,
  - A repository where the data will be transferred **to**,
  - The **storage object** we will be sent from the first to the second,
  - And the **start time**, which is used to monitor the transfer process.
- In the constructor besides initializing the values:
  - We use **[registerObject](#registerobject)** to store the StorageObject into the first repository.
  - We set the transfer's start time to the current time using [getFireCount](time#getfirecount).
  - We initiate the transfer with **[requestContentDelivery](#requestcontentdelivery)**. Here we also have to create a new `ConsumptionEventAdapter`
    and override it's **conComplete()** function to define an event that occurs when the transfer is completed. 
  - In this example, this completion event will remove the storage object from the first repository and prints an output to the console.

{: .note}
The ˙ConsumptionEventAdapter` is an implementation of consumption events, which provide basic functions to determine if a resource consumption has already been completed.

- The only thing left to do is to set up the simulation in the main function:
  - For the sake of simplicity, we will use constant values for **file size**, **storage size** and **bandwith**.
  - We have to create the repositories:
    - First, we generate the **power transitions** using the [PowerTransitionGenerator]{:target="_blank"}'s `generateTransitions` method (the parameters are not important for this example).
    - The repository requires a **latencyMap**, this will be a simple `HashMap` (values will be put into it later).
    - With everything prepared, we can construct the two repositories.
  - After this, we must **turn on** the repositories using the **setState** function.
  - Once everything setup we can run the examples. The only missing component is the StorageObject which we create inside the example constructors
    with a name, size and vary parameter (the last parameter would be `true` if the size could vary; in this case, it will be `false`).
  - Finally, we can use the [simulateUntilLastEvent](time#simulateuntillastevent) function to run the simulation.
  - After the simulation, we print the contents of the repositories to verify that the transfer was successful.

{: .important}
Unless we turn on the repositories with the `setState()` function, we will not be able to communicate with them.

If we run the program, the output should look like this:

```text
Start: 0 from: repo2 to: repo1 end: 5
Start: 0 from: repo1 to: repo2 end: 6
repo1: [SO(id:file2 size:100)]
repo2: [SO(id:file1 size:100)]

Process finished with exit code 0
```

Inspecting the output, we can see that the second example completes first.  
This is because the latency from repo1 to repo2 is 6, while the latency from repo2 to repo1 is 5.  
Since both transfers start at the same time, the transfer in the repo2 → repo1 direction finishes earlier due to the lower latency.

After printing the contents, we can see that file2 is in repo1 and file1 is in repo2, as expected.
The transfer was successful, and the conComplete() method from the completion event removed the file from the original repository, resulting in each repository containing the file received from the other.

---

After this section, you should now be familiar with how repositories store objects and initiate data transfers through their networks.

[Repository]: https://github.com/sed-inf-u-szeged/DISSECT-CF-Fog/blob/master/simulator/src/main/java/hu/mta/sztaki/lpds/cloud/simulator/io/Repository.java
[virtual appliances]: https://github.com/sed-inf-u-szeged/DISSECT-CF-Fog/blob/master/simulator/src/main/java/hu/mta/sztaki/lpds/cloud/simulator/io/VirtualAppliance.java
[VirtualAppliances]: https://github.com/sed-inf-u-szeged/DISSECT-CF-Fog/blob/master/simulator/src/main/java/hu/mta/sztaki/lpds/cloud/simulator/io/VirtualAppliance.java
[virtual machines]: https://github.com/sed-inf-u-szeged/DISSECT-CF-Fog/blob/master/simulator/src/main/java/hu/mta/sztaki/lpds/cloud/simulator/iaas/VirtualMachine.java
[physical machines]: https://github.com/sed-inf-u-szeged/DISSECT-CF-Fog/blob/master/simulator/src/main/java/hu/mta/sztaki/lpds/cloud/simulator/iaas/PhysicalMachine.java
[StorageObjects]: https://github.com/sed-inf-u-szeged/DISSECT-CF-Fog/blob/master/simulator/src/main/java/hu/mta/sztaki/lpds/cloud/simulator/io/StorageObject.java
[PowerTransitionGenerator]: https://github.com/sed-inf-u-szeged/DISSECT-CF-Fog/blob/master/simulator/src/main/java/hu/mta/sztaki/lpds/cloud/simulator/util/PowerTransitionGenerator.java
[EnergyDataCollector]: https://github.com/sed-inf-u-szeged/DISSECT-CF-Fog/blob/master/simulator/src/main/java/hu/u_szeged/inf/fog/simulator/util/EnergyDataCollector.java
[transfer example]: https://github.com/sed-inf-u-szeged/DISSECT-CF-Fog/blob/master/simulator/src/main/java/hu/u_szeged/inf/fog/simulator/demo/simple/TransferExample.java
[pre-configured virtual machine image]: https://en.wikipedia.org/wiki/Virtual_appliance