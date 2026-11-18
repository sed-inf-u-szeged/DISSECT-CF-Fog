import {MAP_HEIGHT, MAP_WIDTH, MapPoint} from './mobility-map';

export interface MapLabelBox {x:number; y:number; width:number; height:number;}
export interface DeviceMapLabel extends MapLabelBox {
  key:string; text:string; ids:number[]; anchor:MapPoint; leader:MapPoint;
}
export function boxesOverlap(a:MapLabelBox,b:MapLabelBox,gap=4):boolean {
  return a.x<b.x+b.width+gap && a.x+a.width+gap>b.x && a.y<b.y+b.height+gap && a.y+a.height+gap>b.y;
}

/** Screen-space labels only. Observation coordinates and map framing never move.
 * Small groups get individually clickable labels; dense groups get a picker.
 * Stable ID order makes layout independent of the stream/track insertion order. */
export function layoutDeviceLabels(devices:readonly {id:number;point:MapPoint}[],reserved:MapLabelBox[]=[]):{
  labels:DeviceMapLabel[]; groups:number[][];
} {
  const sorted=[...devices].sort((a,b)=>a.id-b.id),parents=sorted.map((_,i)=>i);
  const root=(i:number):number=>parents[i]===i ? i : (parents[i]=root(parents[i]));
  for(let i=0;i<sorted.length;i++) for(let j=0;j<i;j++) {
    if(Math.hypot(sorted[i].point.x-sorted[j].point.x,sorted[i].point.y-sorted[j].point.y)<28) parents[root(i)]=root(j);
  }
  const groups=new Map<number,typeof sorted>();
  sorted.forEach((d,i)=>{const key=root(i);groups.set(key,[...(groups.get(key)??[]),d]);});
  const obstacles=[...reserved,...sorted.map(d=>({x:d.point.x-9,y:d.point.y-9,width:18,height:18}))];
  const labels:DeviceMapLabel[]=[];
  function place(text:string,ids:number[],anchor:MapPoint):void {
    const width=text.length*8+16,height=26;
    const candidates:MapLabelBox[]=[];
    for(const radius of [18,42,68,96,130,172]) {
      for(const [dx,dy] of [[1,-1],[-1,-1],[1,1],[-1,1],[1,0],[-1,0],[0,-1],[0,1]]) {
        candidates.push({x:anchor.x+dx*radius-(dx<0?width:dx===0?width/2:0),
          y:anchor.y+dy*radius-(dy<0?height:dy===0?height/2:0),width,height});
      }
    }
    // Rare fallback for widely distributed markers near edges. A missing label
    // remains accessible through the "Choose device" list, never overlaps one.
    const valid=(b:MapLabelBox)=>b.x>=12 && b.x+b.width<=MAP_WIDTH-12 && b.y>=40 && b.y+b.height<=MAP_HEIGHT-60
      && !obstacles.some(o=>boxesOverlap(b,o));
    let box=candidates.find(valid);
    if(!box) {
      const grid:MapLabelBox[]=[];
      for(let y=40;y<MAP_HEIGHT-86;y+=32) for(let x=12;x<MAP_WIDTH-width-12;x+=40) grid.push({x,y,width,height});
      box=grid.filter(valid).sort((a,b)=>Math.hypot(a.x+width/2-anchor.x,a.y+height/2-anchor.y)-Math.hypot(b.x+width/2-anchor.x,b.y+height/2-anchor.y))[0];
    }
    if(!box)return;
    const leader={x:Math.max(box.x,Math.min(box.x+box.width,anchor.x)),y:Math.max(box.y,Math.min(box.y+box.height,anchor.y))};
    labels.push({...box,key:ids.join('-'),text,ids,anchor,leader});obstacles.push(box);
  }
  for(const group of groups.values()) {
    if(group.length<=4) group.forEach(d=>place('D'+d.id,[d.id],d.point));
    else place(group.length+' devices',group.map(d=>d.id),{
      x:group.reduce((n,d)=>n+d.point.x,0)/group.length,y:group.reduce((n,d)=>n+d.point.y,0)/group.length});
  }
  return {labels,groups:[...groups.values()].map(g=>g.map(d=>d.id))};
}
