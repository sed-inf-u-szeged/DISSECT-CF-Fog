import {MobilityNode, MobilityObservation} from './MobilityRun';

export interface MapPoint {x:number; y:number;}
export interface MapCamera extends MapPoint {width:number;}
export interface MapTrack {id:number; observations:readonly MobilityObservation[];}
export interface MapDevice {id:number; observation:MobilityObservation; point:MapPoint; trail:MapPoint[];}
export const MAP_WIDTH=1000, MAP_HEIGHT=540;
export const NODE_COLOR='#a8b3c0';
const R=6378137, RAD=Math.PI/180;

/** One fixed origin for the entire run, never an origin per selected device. */
export function mapProjection(nodes:readonly MobilityNode[]) {
  const origin=nodes[0] ?? {latitude:0,longitude:0};
  const cos=Math.max(.01,Math.cos(origin.latitude*RAD));
  return (latitude:number,longitude:number):MapPoint => ({
    x:(longitude-origin.longitude)*RAD*R*cos,y:(latitude-origin.latitude)*RAD*R
  });
}
export function orderedMapNodes(nodes:readonly MobilityNode[]):MobilityNode[] {
  return [...nodes].sort((a,b)=>a.name===b.name?0:a.name==='mobility-west'?-1:b.name==='mobility-west'?1:a.name.localeCompare(b.name,'en'));
}
export function hasCoverage(node:MobilityNode):boolean {
  return node.role!=='cloud' && !node.name.endsWith('_cloud') && node.serviceable!==false && node.radius>0;
}
export function inCoverage(o:MobilityObservation,node:MobilityNode):boolean {
  const a=o.latitude*RAD,b=node.latitude*RAD,dlat=(o.latitude-node.latitude)*RAD,dlon=(o.longitude-node.longitude)*RAD;
  return hasCoverage(node) && 2*R*Math.asin(Math.min(1,Math.sqrt(Math.sin(dlat/2)**2+Math.cos(a)*Math.cos(b)*Math.sin(dlon/2)**2)))<=node.radius;
}
export function coveragePoints(node:MobilityNode,project:ReturnType<typeof mapProjection>):MapPoint[] {
  if(!hasCoverage(node)) return [];
  const a=node.latitude*RAD,b=node.longitude*RAD,q=node.radius/R;
  return Array.from({length:97},(_,i)=>{
    const bearing=i*Math.PI/48;
    const lat=Math.asin(Math.sin(a)*Math.cos(q)+Math.cos(a)*Math.sin(q)*Math.cos(bearing));
    const lon=b+Math.atan2(Math.sin(bearing)*Math.sin(q)*Math.cos(a),Math.cos(q)-Math.sin(a)*Math.sin(lat));
    return project(lat/RAD,lon/RAD);
  });
}
/** Sorted recordings; the next observation must never move a marker early. */
export function lastKnownIndex(rows:readonly {time_ms:number}[],time:number):number {
  let lo=0,hi=rows.length;
  while(lo<hi){const mid=(lo+hi)>>>1;if(rows[mid].time_ms<=time)lo=mid+1;else hi=mid;}
  return lo-1;
}
export function mapDevices(tracks:readonly MapTrack[],time:number,minutes:number,project:ReturnType<typeof mapProjection>):MapDevice[] {
  return tracks.flatMap(track=>{
    const end=lastKnownIndex(track.observations,time);
    if(end<0) return [];
    const observation=track.observations[end];
    if(!Number.isFinite(observation.latitude)||!Number.isFinite(observation.longitude))return [];
    const from=minutes>0?observation.time_ms-minutes*60000:-Infinity;
    let first=lastKnownIndex(track.observations,from);
    if(first<0 || track.observations[first].time_ms<from)first++;
    const trail=track.observations.slice(first,end+1).filter(o=>Number.isFinite(o.latitude)&&Number.isFinite(o.longitude)).map(o=>project(o.latitude,o.longitude));
    return [{id:track.id,observation,point:project(observation.latitude,observation.longitude),trail}];
  });
}
export function mapScreen(point:MapPoint,camera:MapCamera):MapPoint {
  const scale=MAP_WIDTH/camera.width;
  return {x:MAP_WIDTH/2+(point.x-camera.x)*scale,y:MAP_HEIGHT/2-(point.y-camera.y)*scale};
}
export function mapWorld(screen:MapPoint,camera:MapCamera):MapPoint {
  const scale=camera.width/MAP_WIDTH;
  return {x:camera.x+(screen.x-MAP_WIDTH/2)*scale,y:camera.y-(screen.y-MAP_HEIGHT/2)*scale};
}
export function fitMap(points:readonly MapPoint[],minimumWidth=200):MapCamera {
  const valid=points.filter(p=>Number.isFinite(p.x)&&Number.isFinite(p.y));
  if(!valid.length)return {x:0,y:0,width:2000};
  let left=Infinity,right=-Infinity,bottom=Infinity,top=-Infinity;
  for(const p of valid){left=Math.min(left,p.x);right=Math.max(right,p.x);bottom=Math.min(bottom,p.y);top=Math.max(top,p.y);}
  return {x:(left+right)/2,y:(bottom+top)/2,width:Math.max(minimumWidth,(right-left)*1.25,(top-bottom)*MAP_WIDTH/MAP_HEIGHT*1.3)};
}
export function zoomMap(camera:MapCamera,anchor:MapPoint,factor:number):MapCamera {
  const width=Math.max(20,Math.min(50000000,camera.width*factor));
  const world=mapWorld(anchor,camera),scale=width/MAP_WIDTH;
  return {width,x:world.x-(anchor.x-MAP_WIDTH/2)*scale,y:world.y+(anchor.y-MAP_HEIGHT/2)*scale};
}
export function panMap(camera:MapCamera,dx:number,dy:number):MapCamera {
  return {...camera,x:camera.x-dx*camera.width/MAP_WIDTH,y:camera.y+dy*camera.width/MAP_WIDTH};
}
