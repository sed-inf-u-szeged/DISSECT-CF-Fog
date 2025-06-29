export interface adminConfiguration {
  _id: { $oid: string };
  user: { $oid: string };
  time: string;
  configFiles: {
    APPLIANCES_FILE: { $oid: string };
    DEVICES_FILE: { $oid: string };
    INSTANCES_FILE: { $oid: string };
  };
}
