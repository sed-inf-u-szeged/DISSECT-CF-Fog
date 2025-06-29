import {ServerSideConfigurationObject} from 'src/app/models/configuration';
import {
  ApplicationXml,
  ApplianceXml,
  DeviceXml,
  NeighbourXml,
  XmlBaseConfiguration,
  InstanceXml
} from 'src/app/models/xml-configuration-model';

/**
 * It converts configuration object to xml base interface, which the server can parse to xml.
 * @param object - configured object which contains the necessary data
 * @param email - user email which determines which folder to scan
 */
export function parseConfigurationObjectToXml(object: ServerSideConfigurationObject, email: string): XmlBaseConfiguration {
  const appliances: ApplianceXml[] = [];
  const devices: DeviceXml[] = [];
  const instances: InstanceXml[] = [];

  for (const node of Object.values(object.nodes)) {
    const applications: ApplicationXml[] = [];
    for (const app of Object.values(node.applications)) {
      const applictaion = {
        $name: app.id,
        freq: app.freq,
        tasksize: app.tasksize,
        instance: app.instance.name,
        countOfInstructions: app.numOfInstruction,
        activationRatio : app.activationRatio,
        transferDevider : app.transferDevider,
        strategy: app.strategy,
        canJoin: app.canJoin
      } as ApplicationXml;
      applications.push(applictaion);
    }
    const appliance = {
      $name: node.id,
      latitude: node.x,
      longitude: node.y,
      range: node.range,
      file: node.resource.name,
      applications: { application: applications }
    } as ApplianceXml;

    if (node.neighbours && node.isCloud == false) {
      const neighbours: NeighbourXml[] = [];
      for (const neighbour of Object.values(node.neighbours)) {
        const xmlNeighbour = {
          $name: neighbour.name,
          latency: neighbour.latency
        } as NeighbourXml;
        if (neighbour.parent) {
          xmlNeighbour.parent = neighbour.parent;
        }
        neighbours.push(xmlNeighbour);
      }
      appliance.neighbours = { neighbour: neighbours };
    } else {
      appliance.neighbours = {};
    }
    appliances.push(appliance);
  }

  // : https://www.usna.edu/Users/oceano/pguth/md_help/html/approx_equivalents.htm
  // TODO: check if long/lat is greater or lass than -+90/180
  for (const station of Object.values(object.stations)) {
    for(let i = 0; i < station.quantity; i++) {
      const plusOrMinus = Math.random() < 0.5 ? -1 : 1;
      const modifer = Math.floor(Math.random() * 6);
      const longitude = station.xCoord + plusOrMinus * 0.0001 * modifer;
      const latitude = station.yCoord + plusOrMinus * 0.0001 * modifer;

      const device = {
        $name: station.id + '.' + (i + 1),
        startTime: station.starttime,
        stopTime: station.stoptime,
        fileSize: station.filesize,
        sensorCount: station.sensorCount,
        strategy: station.strategy,
        freq: station.freq,
        latitude: latitude,
        longitude: longitude,
        speed: station.speed,
        radius: station.radius,
        latency: station.latency,
        capacity: station.capacity,
        maxOutBW: station.maxoutbw,
        cores: station.cores,
        perCoreProcessing: station.perCoreProcessing,
        ram: station.ram,
        minpower: station.minpower,
        idlepower: station.idlepower,
        maxpower: station.maxpower
      } as DeviceXml;
      devices.push(device);
    }
  }

  for (const instance of Object.values(object.instances)) {
    const tempInstance = {
      $name: instance.name,
      ram: instance.ram,
      'cpu-cores': instance.cpuCores,
      'core-processing-power': instance.cpuProcessingPower,
      'startup-process': instance.startupProcess,
      'req-disk': instance.reqDisk,
      'price-per-tick': instance.hourlyPrice
    } as InstanceXml;
    instances.push(tempInstance);
}
  const tzOffsetInMin = new Date().getTimezoneOffset();
  const tzOffset = (tzOffsetInMin !== 0 ? tzOffsetInMin / 60 : 0) * -1;
  return {
    configuration: {
      email,
      tzOffset,
      appliances: {
        appliances: {
          appliance: appliances
        }
      },
      devices: {
        devices: {
          device: devices
        }
      },
      instances: {
        instances: {
          instance: instances
        }
      }
    }
  } as XmlBaseConfiguration;
}

function round(value: number, precision: number): number {
  const multiplier = Math.pow(10, precision || 0);
  return Math.round(value * multiplier) / multiplier;
}
