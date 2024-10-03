export interface algorithmUploadData{
  ApplicationId: { $oid: string }
  DevicesId: { $oid: string },
  InstancesId: { $oid: string },
  deviceCode: string,
  isDeviceCodeCustom: string,
  applicationCode: string,
  isApplicationCodeCustom: string,
  adminConfigId: string,
  nickname: string
}