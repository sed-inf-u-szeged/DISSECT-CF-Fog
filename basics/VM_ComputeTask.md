---
title: VM and ComputeTask
parent: Basics
nav_order: 4
permalink: /basics/vm
---

# Virtual Machine and ComputeTask
{: .no_toc }

## Table of contents
{: .no_toc .text-delta }

- TOC
{:toc}

---

## Virtual Machines in clouds

A cloud is a parallel and distributed system where a network of interconnected **virtualized computers** provides unified **computing resources**.

Such resources may include, for example, **storage services** or the rental of **computing capacity**, which is typically achieved by creating a [virtual machine].

### Steps to making a virtual machine
{: .no_toc}

- Upload [VirtualAppliance]{:target="_blank"} (OS, software components) to a [Repository]{:target="_blank"}.
- Copy the [VirtualAppliance]{:target="_blank"} to the [PhysicalMachine]{:target="_blank"}'s repository.
- Install the [VirtualMachine]{:target="_blank"} based on the given instance type ([AlterableResourceConstraints]{:target="_blank"}), which defines the amount of vCPU and vRAM allocated to the VM.
- Provide user access so that computing tasks can be created.

We already know how to do the first step from the [previous example](repository#example) (since VirtualAppliances are just specific
type of StorageObjects), but haven't really worked with the **PhysicalMachine** class, so let's take a look at that.

## [PhysicalMachine]{:target="_blank"}

This class represents a single Physical machine with computing resources as well as local disks and network connections.

The PM is a central part of the infrastructure simulation, it allows VM management, direct access to its resources and also provides several power management operations.

Here are some of the most important and commonly used functions of the PhysicalMachine class:

#### `PhysicalMachine - constructor`
{: .text-beta}

```java
public PhysicalMachine(double cores, double perCoreProcessing, long memory, Repository disk,
                       int onD, int offD, Map<String, PowerState> cpuPowerTransitions) {...}
```

**Description:**  
Defines a new physical machine, ensures that there are no VMs running so far.

**Parameters:**
- `cores` - defines the number of CPU cores this machine has under control.
- `perCoreProcessing` - defines the processing capabilities of a single CPU core in this machine (in instructions/tick).
- `memory` - defines the total physical memory this machine has under control (in bytes).
- `disk` - defines the local physical disk and networking this machine has under control (**repository**).
- `onD` - defines the time delay between the machine's switch on and the first time it can serve VM requests.
- `offD` - defines the time delay the machine needs to shut down all of its operations while it does not serve any more VMs.
- `cpuPowerTransitions` - parameter for energy data collection.


----------------


#### `turnon`
{: .text-beta}

```java
public void turnon() {...}
```

**Description:**  
Turns on the physical machine so it allows energy and resource consumption (i.e. compute tasks) and opens the possibility to receive VM requests.


----------------


#### `requestVM`
{: .text-beta}

```java
public VirtualMachine[] requestVM(final VirtualAppliance va, final ResourceConstraints rc,
			  final Repository vaSource, final int count){...}
```

**Description:**
Initiates a VM on this physical machine. If the physical machine cannot host VMs for some reason an exception is thrown, if the machine cannot host this particular VA then a null VM is returned.

**Parameters:**
- `va` - the appliance for the VM to be created. 
- `rc` - the resource requirements of the VM.
- `vaSource` - the storage where the VA resides.
- `count` - the number of VMs to be created with the above specification.

**Return:**
The virtual machine(s) that will be instantiated on the PM. Null if the constraints specify VMs that cannot fit the available resources of the machine.

----------------

These few things should be more than enough from this class for now.
This class is huge, so getting familiar with its other methods - not mentioned here - could be really beneficial.
But in the upcoming examples, we will use only these functions. 

Before that, we have to mention [AlterableResourceConstraints]{:target="_blank"} as well.

## [AlterableResourceConstraints]{:target="_blank"}

This class is used to define the virtual resources of a VM instance.

The class has several smaller functions and multiple constructors, but in most cases, only one of them is used.

#### `AlterableResourceConstraints - constructor`
{: .text-beta}

```java
public AlterableResourceConstraints(final double cpu, final double processing, final long memory){...}
```

**Description:**  
A constructor to define resource constraints with exact amount of resources to start with.

**Parameters:**
- `cpu` - number of cores.
- `processing` - per core processing power in instructions/tick.
- `memory` - number of bytes.

---

Before the examples, the only thing we have left to talk about is the actual VM we will create.

## [VirtualMachine]{:target="_blank"}

This class represents a single virtual machine in the system. It simulates its behavior.

As we saw these VMs will be inside [PhysicalMachines](#physicalmachine) created by the
[requestVM](#requestvm) function, so we typically won’t use this class’s constructor directly.

Like [PhysicalMachine]{:target="_blank"}, this class has many methods, so we will focus only on the most important one.

#### `newComputeTask`
{: .text-beta}

```java
public ResourceConsumption newComputeTask(final double total, final double limit,
			          final ResourceConsumption.ConsumptionEvent e){...}
```

**Description:**  
This is the function that users are expected to use to create computing tasks on the VMs (not using ResourceConsumptions directly).
The computing tasks created with this function are going to utilize CPUs and if there is a background load defined for the
VM then during the processing of the CPU related activities there will be constant network activities modelled as well.

**Parameters:**
- `total` - the amount of processing to be done (in number of instructions).
- `limit` - the amount of processing this new compute task is allowed to do in a single tick (in instructions/tick).
            If there should be no limit for the processing then one can use the constant named ResourceConsumption.unlimitedProcessing.
- `e` - the object to be notified about the completion of the computation ordered here. (Same thing we did for [requestContentDelivery](repository#requestcontentdelivery))

**Return:**
The resource consumption object that will represent the CPU consumption. Could return null if the consumption cannot be registered or when there is no resource for the VM.

----------------


## Example

Now with every knowledge of knowing how to create VMs and use them, let's look at an actual [example]{:target="_blank"}. 

```java
package hu.u_szeged.inf.fog.simulator.demo.simple;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.energy.powermodelling.PowerState;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VMManager.VMManagementException;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.AlterableResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ConsumptionEventAdapter;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ResourceConsumption;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;
import hu.mta.sztaki.lpds.cloud.simulator.io.VirtualAppliance;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode.NetworkException;
import hu.mta.sztaki.lpds.cloud.simulator.util.PowerTransitionGenerator;

public class VmTaskExample {

    public static void main(String[] args) throws NetworkException, VMManagementException {

        long storageSize = 107_374_182_400L; // 100 GB
        long bandwidth = 12_500; // 100 Mbps
        
        final EnumMap<PowerTransitionGenerator.PowerStateKind, Map<String, PowerState>> transitions =
                PowerTransitionGenerator.generateTransitions(20, 200, 300, 10, 20);
        
        Repository repo = new Repository(storageSize, "repo", bandwidth, bandwidth, bandwidth, new HashMap<String, Integer>(), 
                transitions.get(PowerTransitionGenerator.PowerStateKind.storage),
                transitions.get(PowerTransitionGenerator.PowerStateKind.network));
       
        repo.setState(NetworkNode.State.RUNNING);

        /** Creating a PM with 8 CPU cores, 8 GB RAM     **/
        /** 1 instruction/tick processing speed per core **/
        /** 10-10 seconds boot time and shutdown time    **/
        PhysicalMachine pm = new PhysicalMachine(8, 1, 8_589_934_592L, repo, 10_000, 10_000,
                transitions.get(PowerTransitionGenerator.PowerStateKind.host));

        pm.turnon();

        Timed.simulateUntilLastEvent();

        System.out.println("Time: " + Timed.getFireCount() + " PM-state: " + pm.getState());

        /** VM "image" file with deploy time of 800 instructions **/
        VirtualAppliance va = new VirtualAppliance("va", 800, 0, false, 1_073_741_824L);
        repo.registerObject(va);
        
        /** VM resource requirements **/
        AlterableResourceConstraints arc = new AlterableResourceConstraints(4, 1, 4_294_967_296L);
        
        VirtualMachine vm = pm.requestVM(va, arc, repo, 1)[0];

        Timed.simulateUntilLastEvent();

        System.out.println("Time: " + Timed.getFireCount() + " PM-state: " + pm.getState() + " VM-state: " + vm.getState());

        /** Simulating 100_000 instructions to be processed by the VM **/
        vm.newComputeTask(100_000, ResourceConsumption.unlimitedProcessing, new ConsumptionEventAdapter() {

            @Override
            public void conComplete() {
                System.out.println("Time: " + Timed.getFireCount());
            }
        });

        Timed.simulateUntilLastEvent();
    }   
}
```

In this example we can see a simple implementation of VM creation and the use of newComputeTask.

- Since this example uses only the original classes and their methods, we can start building the simulation in main right away.
    - The example follows the [steps to making a virtual machine](#steps-to-making-a-virtual-machine) defined at the start of this section (although not in the exact order).
        - We create our repository just like in the previous [example](repository#example), then our physical machine.
          The PM's onD and offD parameters will be 10000, which means it takes 10,000 ticks to start up and shut down the machine.
        - To turn on the machine we call the [turnon](#turnon) function and use [simulateUntilLastEvent](time#simulateuntillastevent) to bring the simulation's clock to where the PM is already running.
        - We create the image file and register it in the repository.
        - After defining the ARC of our VM, we can call the PM's [requestVM](#requestvm) function to create the VM, again using simulateUntilLastEvent.
    - As the final step, we make the VM process 100000 instructions with no processing limit and define a completion event that prints the time when the task finishes.

If we run the program, the output should look like this:

```text
Time: 10001 PM-state: RUNNING
Time: 182601 PM-state: RUNNING VM-state: RUNNING
Time: 207601

Process finished with exit code 0
```

The first print happens after starting the PM, which takes 10,000 ticks, so this works as expected.

The second line appears after requesting (and starting) the VM. Here, 172,600 ticks have passed - but why?

The startup process of the VirtualAppliance requires 800 instructions, and its disk image size is 1,073,741,824 bytes (~1 GB).
Transferring this image over a 12,500 bytes/tick bandwidth takes: 1,073,741,824 / 12,500 ≈ 85,899 ticks.
During VM provisioning, the VA is transferred twice:
    -from the Repository to the PhysicalMachine
    -then duplicated into the VM’s context
So the transfer time is: 2 × 85,899 = 171,798 ticks

Adding the VA startup cost: 171,798 + 800 ≈ 172,598 ticks  
This matches the observed ~172,600 tick delay between the first and second print.

The third print is more straightforward. We assign a VM with 4 cores and 1 instruction/tick processing power a task of 100,000 instructions: 100,000 / 4 = 25,000 ticks
This exactly matches the time difference between the second and third print.

---

After this section, we should be able to handle VMs on a basic level and assign tasks using the newComputeTask method.

[Repository]: https://github.com/sed-inf-u-szeged/DISSECT-CF-Fog/blob/master/simulator/src/main/java/hu/mta/sztaki/lpds/cloud/simulator/io/Repository.java
[VirtualAppliance]: https://github.com/sed-inf-u-szeged/DISSECT-CF-Fog/blob/master/simulator/src/main/java/hu/mta/sztaki/lpds/cloud/simulator/io/VirtualAppliance.java
[VirtualMachine]: https://github.com/sed-inf-u-szeged/DISSECT-CF-Fog/blob/master/simulator/src/main/java/hu/mta/sztaki/lpds/cloud/simulator/iaas/VirtualMachine.java
[virtual machine]: https://github.com/sed-inf-u-szeged/DISSECT-CF-Fog/blob/master/simulator/src/main/java/hu/mta/sztaki/lpds/cloud/simulator/iaas/VirtualMachine.java
[PhysicalMachine]: https://github.com/sed-inf-u-szeged/DISSECT-CF-Fog/blob/master/simulator/src/main/java/hu/mta/sztaki/lpds/cloud/simulator/iaas/PhysicalMachine.java
[StorageObjects]: https://github.com/sed-inf-u-szeged/DISSECT-CF-Fog/blob/master/simulator/src/main/java/hu/mta/sztaki/lpds/cloud/simulator/io/StorageObject.java
[PowerTransitionGenerator]: https://github.com/sed-inf-u-szeged/DISSECT-CF-Fog/blob/master/simulator/src/main/java/hu/mta/sztaki/lpds/cloud/simulator/util/PowerTransitionGenerator.java
[AlterableResourceConstraints]: https://github.com/sed-inf-u-szeged/DISSECT-CF-Fog/blob/master/simulator/src/main/java/hu/mta/sztaki/lpds/cloud/simulator/iaas/constraints/AlterableResourceConstraints.java
[example]: https://github.com/sed-inf-u-szeged/DISSECT-CF-Fog/blob/master/simulator/src/main/java/hu/u_szeged/inf/fog/simulator/demo/simple/VmTaskExample.java