export interface InfoPanelData {
  title: string;
  text: string;
}

export function getResourceFilesInfoData(): InfoPanelData {
  return {
    title: 'Resource files',
    text:
      'A resource file defines how many CPUs and RAMs the actual cloud/fog node deals with. <br/>' +
      'A node considered to be in the lowest fog layer typically utilises the least resource. <br/>' +
      '<ul>' + 
        '<li><b>LPDS_T2</b>: 12 CPU cores and 24 GB RAMs</li>' + 
        '<li><b>LPDS_16</b>: 16 CPU cores and 64 GB RAMs</li>' + 
        '<li><b>LPDS_T1</b>: 24 CPU cores and 48 GB RAMs</li>' + 
        '<li><b>LPDS_32</b>: 32 CPU cores and 64 GB RAMs</li>' + 
        '<li><b>LPDS_original</b>: 48 CPU cores and 96 GB RAMs</li>' + 
        '<li><b>LPDS_original</b>: 48 CPU cores and 96 GB RAMs</li>' + 
      '</ul>' + 
      'The processing power of each CPU core is set to 0.001, which means it processes 0.001 instruction during one tick, which is the'
      + ' smallest unit of the simulation time. In this environment it is considered to measure time in milliseconds. <br/>'
  } as InfoPanelData;
}

export function getInstanceInfoData(): InfoPanelData {
  return {
    title: 'Instance settings',
    text:
      '<b>RAM</b>: memory size of the VM instance (byte) <br/><br/>'+
      '<b>CPU cores</b>: number of cores of the VM instance (pc.) <br/><br/>'+
      '<b>Core processing power</b>: processing power of one CPU core (instructions/tick) <br/><br/>'+
      '<b>Startup process</b>: number of processing instructions on startup <br/><br/>'+
      '<b>Network load</b>_ background network load while running tasks on a VM that has storage backed by a remote repository<br/><br/>'+
      '<b>Required disk</b>: the size of the disk image to host the virtual appliance<br/><br/>'+
      '<b>Price per tick</b>: defines the price of one unit of time. <br/><br/>'+
      '<b>Instance name</b>: defines the name of the instance. <br/><br/>'
  } as InfoPanelData;
}

export function getApplicationInfoData(): InfoPanelData {
  return {
    title: 'Application settings',
    text:
      'The <b>task size</b> attribute tells the highest amount of unprocessed data that can be packaged in one compute task to be executed by virtual machines.'
      +'Suggested value: >5 000 bytes <br/>' +
      'A daemon service checks regularly the repository for unprocessed data based on the <b>frequency value.</b>.'
      + 'Suggested value: >6 000 ms <br/>' +
      'The <b>number of instruction</b> defines the maximum value which one task can represent. Suggested value: >1 000 <br/>' +
      'The <b>threshold</b> determines how many unprocessed task can be hold in the actual application'
        +'the further tasks are forwarded according to the strategy of the application. Suggested value: 1-5 <br/>' +
      'The <b>can join</b> allows IoT devices to send unprocessed data directly into the application. <br/>' +
      'The VM flavor is used for executing the compute tasks, which can be specified in the <b>instance</b> tag. <br/>' +
      'You have the following possibilites in case of the strategy value: <br/>'+
      'The <b>Random</b> strategy always chooses one from the connected nodes randomly. <br/>' +
      'The <b>Push Up</b> strategy always chooses the connected parent node (i.e. a node from a higher layer), if available. <br/>' +
      'The <b>Hold Down</b> aims to keep application data as close to the end-user as possible. <br/>'+
      'The <b>Runtime</b> strategy ranks the available parent nodes, and all neighbour nodes (from its own layer) by network latency' 
        +'and by the ratio of the available CPU capacity and the total CPU capacity. The algorithm picks the node with the highest rank. <br/>' +
      '<b>Pliant</b> strategy is based on Fuzzy logic, thus load, cost and unprocessed data of a node are considered. <br/>'
  } as InfoPanelData;
}

export function getStationInfoData(): InfoPanelData {
  return {
    title: 'Stations',
    text:
    'We can configure the life time of the device (<b>starttime, stoptime</b> [>1,stoptime>starttime (ms)]), the number of sensors it has (<b>sensor</b> [>0]),' 
      + 'the size of the generated data (<b>filesize</b> [>49 bytes]) and the generation and sending <b>frequency</b> [>6000 ms]. <br/>' +
    'The network  settings  of  the  local  repository  are defined by the <b>maxinbw, maxoutbw</b> and <b>diskbw</b> [>1 byte/tick] tags,'
        + 'and the size of the repository is determined by the <b>reposize</b> [>5000 bytes] field. <br/>' +
    'The <b>radius</b> defines a range where a set of IoT devices are randomly placed,' 
      +'the number of the devices located at the range is defined by the <b>number</b> attribute. <br/>'+
    'You have the following possibilites in case of the strategy value: <br/>'+
    'The <b>Random</b> strategy chooses one from the available applications randomly. <br/>' +
    'The <b>Cost</b> strategy looks for the cheapest available application running in any fog/cloud node. <br/>'+
    'The <b>Runtime</b> strategy takes into account the actual load of the available nodes. <br/>' +
    'The <b>Fuzzy</b> startegy takes in consideration many parameters, such as cost, workload, number of VMs and connected devices, etc. <br/>'
  } as InfoPanelData;
}

export function getConnectionInfoData(): InfoPanelData {
  return {
    title: 'Connections',
    text:
      'Parent connection can be made between a fog and a cloud node.'+
      'Anyways you can use the simple connection to create a route among the fog nodes.'
  } as InfoPanelData;
}

export function getConfigurationErrorData(): InfoPanelData {
  return {
    title: 'Configuration errors',
    text:
      'Various exceptions can appear in the system, the most common problem is the badly chosen parameters. We suggest to check those or look into the console application for further details.'
  } as InfoPanelData;
}
