package hu.u_szeged.inf.fog.simulator.so_smart;

import hu.u_szeged.inf.fog.simulator.iot.mobility.GeoLocation;
import hu.u_szeged.inf.fog.simulator.node.ComputingAppliance;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Visualizes planned SO-SMART trips and completed Fog handovers
 * on an interactive Leaflet map.
 * <p>
 * Segment boundaries are approximated using planned distances.
 * Fog service regions represent approximate nearest-node areas,
 * not physical network coverage.
 */
public final class SoSmartTripMapVisualiser {

    private static final int MAX_MAP_TRIPS = 10;

    private SoSmartTripMapVisualiser() {
    }

    public static void generateMap(List<Trip> trips, List<ComputingAppliance> fogNodes, List<HandoverRecord> handovers, Path outputFile) throws IOException {
        Objects.requireNonNull(trips, "trips");
        Objects.requireNonNull(fogNodes, "fogNodes");
        Objects.requireNonNull(outputFile, "outputFile");
        Objects.requireNonNull(handovers, "handovers");

        if (trips.isEmpty()) {
            throw new IllegalArgumentException("At least one trip is required");
        }

        StringBuilder html = new StringBuilder();

        html.append("<!doctype html>\n<html lang='hu'>\n<head>\n")
                .append("<meta charset='utf-8'>\n")
                .append("<meta name='viewport' content='width=device-width,initial-scale=1'>\n")
                .append("<title>SO-SMART utazások</title>\n")
                .append("<link rel='stylesheet' href='https://cdn.jsdelivr.net/npm/leaflet@1.9.3/dist/leaflet.css'>\n")
                .append("<link rel='stylesheet' href='https://cdnjs.cloudflare.com/ajax/libs/Leaflet.awesome-markers/2.0.2/leaflet.awesome-markers.css'>\n")
                .append("<link rel='stylesheet' href='https://netdna.bootstrapcdn.com/bootstrap/3.0.0/css/bootstrap-glyphicons.css'>\n")
                .append("<link rel='stylesheet' href='https://cdn.jsdelivr.net/npm/@fortawesome/fontawesome-free@6.2.0/css/all.min.css'>\n")
                .append("<style>html,body,#map{height:100%;margin:0}")
                .append(".legend{position:absolute;bottom:20px;left:12px;z-index:1000;")
                .append("background:#fff;padding:12px 14px;border-radius:5px;")
                .append("font:13px Arial,sans-serif;line-height:1.6;")
                .append("box-shadow:0 1px 8px #777;max-width:245px}")
                .append(".swatch{display:inline-block;width:24px;height:4px;")
                .append("vertical-align:middle;margin-right:7px}")
                .append(".dot{display:inline-block;width:11px;height:11px;")
                .append("border-radius:50%;vertical-align:middle;margin-right:10px}")
                .append(".latency-label{font:bold 13px Arial,sans-serif;color:blue;")
                .append("text-shadow:1px 1px white,-1px -1px white}")
                .append(".legend small{display:block;margin-top:7px;color:#555}")
                .append(".leaflet-control-layers-expanded{min-width:260px;padding:10px 14px}")
                .append(".leaflet-control-layers label{font-size:17px;line-height:1.8}")
                .append(".leaflet-control-layers-selector{transform:scale(1.2);margin-right:8px}")
                .append(".leaflet-control-layers-list{max-height:65vh;overflow-y:auto}")
                .append(".leaflet-popup-content{font:16px/1.6 Arial,sans-serif;min-width:240px}")
                .append(".leaflet-popup-close-button{font-size:22px!important}")
                .append(".leaflet-control-zoom a{width:36px;height:36px;line-height:36px;font-size:22px}")
                .append("</style>\n</head>\n<body>\n<div id='map'></div>\n")

                // Map legend
                .append("<div class='legend'><b>Jelmagyarázat</b><div>")
                .append("<span class='swatch' style='background:#2563eb'></span>Tömegközlekedés</div><div>")
                .append("<span class='swatch' style='background:#f97316'></span>Autómegosztás</div><div>")
                .append("<span class='swatch' style='background:#9333ea'></span>Kerékpármegosztás</div><div>")
                .append("<span class='swatch' style='background:green'></span>Node-ok közötti kapcsolat</div><div>")
                .append("<span class='dot' style='background:#777'></span>Fog node</div><div>")
                .append("<span class='swatch' style='background:#64748b'></span>")
                .append("Fog kiszolgálási terület (közelítő)</div><div>")
                .append("<span class='dot' style='background:#007def'></span>Cloud node</div><div>")
                .append("<span class='dot' style='background:#d32f2f'></span>Utazás kezdete</div><div>")
                .append("<span class='dot' style='background:#333'></span>Utazás vége</div>")
                .append("<div><span class='dot' style='background:#eab308;")
                .append("border:1px solid #111827'></span>")
                .append("Befejezett Fog-handover</div>")
                .append("<small>A szakaszok sematikus, egyenes vonalak, nem utcai útvonalak.</small></div>\n")

                .append("<script src='https://cdn.jsdelivr.net/npm/leaflet@1.9.3/dist/leaflet.js'></script>\n")
                .append("<script src='https://cdnjs.cloudflare.com/ajax/libs/Leaflet.awesome-markers/2.0.2/leaflet.awesome-markers.js'></script>\n")
                .append("<script>\n")

                // Leaflet map and layers
                .append("const map=L.map('map').setView([46.25,20.12],12);\n")
                .append("L.tileLayer('https://tile.openstreetmap.de/{z}/{x}/{y}.png',")
                .append("{maxZoom:18,attribution:'&copy; OpenStreetMap contributors'}).addTo(map);\n")
                .append("map.createPane('fog-region-pane');\n")
                .append("map.getPane('fog-region-pane').style.zIndex=390;\n")
                .append("const fogRegions=L.layerGroup().addTo(map);\n")
                .append("const nodes=L.layerGroup().addTo(map);\n")
                .append("const links=L.layerGroup().addTo(map);\n")
                .append("const layers={'Fog kiszolgálási területek':fogRegions,")
                .append("'Fog / Cloud node-ok':nodes,")
                .append("'Hálózati kapcsolatok':links};\n")
                .append("const bounds=L.latLngBounds([]);\n")

                // Marker icons matching the existing Fog map
                .append("const fogIcon=L.AwesomeMarkers.icon({icon:'cloud',prefix:'glyphicon',markerColor:'gray'});\n")
                .append("const cloudIcon=L.AwesomeMarkers.icon({icon:'cloud',prefix:'glyphicon',markerColor:'blue'});\n")
                .append("const startIcon=L.AwesomeMarkers.icon({icon:'mobile',prefix:'fa',markerColor:'red'});\n")
                .append("const endIcon=L.AwesomeMarkers.icon({icon:'flag-checkered',prefix:'fa',markerColor:'black'});\n");

        // Include Cloud parents only when they exist in the scenario.
        Map<String, ComputingAppliance> visibleNodes = new LinkedHashMap<>();
        Set<String> fogNames = new HashSet<>();

        for (ComputingAppliance fog : fogNodes) {
            fogNames.add(fog.name);
            visibleNodes.putIfAbsent(fog.name, fog);

            if (fog.parent != null) {
                visibleNodes.putIfAbsent(fog.parent.name, fog.parent);
            }
        }

        // Draw Fog/Cloud node markers.
        for (ComputingAppliance node : visibleNodes.values()) {
            boolean cloud = !fogNames.contains(node.name);

            double lat = node.geoLocation.latitude;
            double lon = node.geoLocation.longitude;

            html.append("L.marker([")
                    .append(lat).append(',').append(lon)
                    .append("],{icon:")
                    .append(cloud ? "cloudIcon" : "fogIcon")
                    .append("}).bindPopup(")
                    .append(js(node.name))
                    .append(").addTo(nodes);\n");

            html.append("bounds.extend([")
                    .append(lat).append(',')
                    .append(lon)
                    .append("]);\n");
        }

        // Draw existing network connections and their latencies.
        Set<String> drawnLinks = new HashSet<>();

        for (ComputingAppliance fog : fogNodes) {
            if (fog.parent != null) {
                addLink(html, fog, fog.parent, drawnLinks);
            }

            for (ComputingAppliance neighbor : fog.neighbors) {
                if (visibleNodes.containsKey(neighbor.name)) {
                    addLink(html, fog, neighbor, drawnLinks);
                }
            }
        }

        // Draw the first trips and their colored travel segments.
        for (int i = 0; i < Math.min(MAX_MAP_TRIPS, trips.size()); i++) {
            Trip trip = trips.get(i);
            String layer = "trip" + i;

            GeoLocation start = trip.getOrigin();
            GeoLocation end = trip.getDestination();

            double totalDistance = trip.getTotalDistanceKm();
            double distanceBefore = 0;

            html.append("const ")
                    .append(layer)
                    .append("=L.layerGroup();\n");

            // Trip origin
            html.append("L.marker([")
                    .append(start.latitude).append(',')
                    .append(start.longitude)
                    .append("],{icon:startIcon}).bindPopup(")
                    .append(js(trip.getId() + " - indulás"))
                    .append(").addTo(")
                    .append(layer)
                    .append(");\n");

            List<TripSegment> segments = trip.getSegments();

            for (int j = 0; j < segments.size(); j++) {
                TripSegment segment = segments.get(j);

                double begin = distanceBefore / totalDistance;
                distanceBefore += segment.getDistanceKm();

                double finish = j == segments.size() - 1
                        ? 1
                        : distanceBefore / totalDistance;

                String popup = String.format(
                        Locale.US,
                        "%s | %d. szakasz: %s | %.2f km | %.1f perc | %.0f Ft",
                        trip.getId(),
                        j + 1,
                        segment.getOperator().getName(),
                        segment.getDistanceKm(),
                        segment.getDurationMs() / 60_000.0,
                        segment.getCost()
                );

                // Segment positions are proportional to segment distances.
                html.append("L.polyline([[")
                        .append(interpolate(start.latitude, end.latitude, begin))
                        .append(',')
                        .append(interpolate(start.longitude, end.longitude, begin))
                        .append("],[")
                        .append(interpolate(start.latitude, end.latitude, finish))
                        .append(',')
                        .append(interpolate(start.longitude, end.longitude, finish))
                        .append("]],")
                        .append("{color:")
                        .append(js(color(segment.getOperator())))
                        .append(",weight:6,opacity:0.9}).bindPopup(")
                        .append(js(popup))
                        .append(").addTo(")
                        .append(layer)
                        .append(");\n");
            }

            // Mark successfully completed Fog handovers.
            for (HandoverRecord handover : handovers) {
                if (!handover.getTripId().equals(trip.getId())) {
                    continue;
                }

                String popup = handover.getTripId()
                        + " | HANDOVER_COMPLETED"
                        + " | " + handover.getFromFog()
                        + " -> " + handover.getToFog()
                        + " | " + handover.getTimestampMs() + " ms";

                html.append("L.circleMarker([")
                        .append(handover.getLatitude()).append(',')
                        .append(handover.getLongitude())
                        .append("],{radius:7,color:'#111827',weight:2,")
                        .append("fillColor:'#eab308',fillOpacity:1})")
                        .append(".bindPopup(")
                        .append(js(popup))
                        .append(").addTo(")
                        .append(layer)
                        .append(");\n");
            }

            // Trip destination
            html.append("L.marker([")
                    .append(end.latitude).append(',')
                    .append(end.longitude)
                    .append("],{icon:endIcon}).bindPopup(")
                    .append(js(trip.getId() + " - érkezés"))
                    .append(").addTo(")
                    .append(layer)
                    .append(");\n");

            html.append("layers[")
                    .append(js(trip.getId()))
                    .append("]=")
                    .append(layer)
                    .append(";\n");

            // Display the first three trips initially.
            if (i < 3) {
                html.append(layer).append(".addTo(map);\n");
            }

            boundsExtend(html, start);
            boundsExtend(html, end);
        }


        // Draw approximate Fog service regions and add layer controls.
        appendFogRegions(html, fogNodes);
        html.append("L.control.layers(null,layers,{collapsed:false}).addTo(map);\n")
                .append("map.fitBounds(bounds,{padding:[40,40],maxZoom:13});\n")
                .append("</script>\n</body>\n</html>\n");

        Path target = outputFile.toAbsolutePath();
        Files.createDirectories(target.getParent());
        Files.writeString(target, html.toString(), StandardCharsets.UTF_8);
    }

    private static void boundsExtend(StringBuilder html, GeoLocation point) {
        html.append("bounds.extend([")
                .append(point.latitude).append(',')
                .append(point.longitude)
                .append("]);\n");
    }

    /**
     * Draws a connection only once, even when both nodes list each other.
     */
    private static void addLink(StringBuilder html,
                                ComputingAppliance a,
                                ComputingAppliance b,
                                Set<String> drawnLinks) {
        if (a == b) {
            return;
        }

        String key = a.name.compareTo(b.name) < 0
                ? a.name + "|" + b.name
                : b.name + "|" + a.name;

        if (!drawnLinks.add(key)) {
            return;
        }

        html.append("L.polyline([[")
                .append(a.geoLocation.latitude).append(',')
                .append(a.geoLocation.longitude)
                .append("],[")
                .append(b.geoLocation.latitude).append(',')
                .append(b.geoLocation.longitude)
                .append("]],{color:'green',weight:2.5}).addTo(links);\n");

        Integer latency = a.iaas.repositories.get(0)
                .getLatencies()
                .get(b.iaas.repositories.get(0).getName());

        if (latency != null) {
            html.append("L.marker([")
                    .append((a.geoLocation.latitude + b.geoLocation.latitude) / 2)
                    .append(',')
                    .append((a.geoLocation.longitude + b.geoLocation.longitude) / 2)
                    .append("],{icon:L.divIcon({className:'latency-label',html:")
                    .append(js(latency.toString()))
                    .append(",iconSize:[45,20]})}).addTo(links);\n");
        }
    }

    private static double interpolate(double a, double b, double ratio) {
        return a + (b - a) * ratio;
    }

    private static String color(Operator operator) {
        switch (operator) {
            case PUBLIC_TRANSPORT:
                return "#2563eb";
            case CAR_SHARING:
                return "#f97316";
            case BIKE_SHARING:
                return "#9333ea";
            default:
                throw new IllegalArgumentException(
                        "Unknown transport operator: " + operator
                );
        }
    }


    /**
     * Draws approximate nearest-Fog service regions using Voronoi cells.
     * The regions describe the selection rule, not radio coverage.
     */
    private static void appendFogRegions(
            StringBuilder html,
            List<ComputingAppliance> fogNodes) {

        String[] colors = {"#64748b", "#789879", "#aa8393"};

        html.append("const fogSites=[");

        for (int i = 0; i < fogNodes.size(); i++) {
            ComputingAppliance fog = fogNodes.get(i);

            if (i > 0) {
                html.append(",");
            }

            html.append("{lat:")
                    .append(fog.geoLocation.latitude)
                    .append(",lon:")
                    .append(fog.geoLocation.longitude)
                    .append(",name:")
                    .append(js(fog.name))
                    .append(",color:")
                    .append(js(colors[i % colors.length]))
                    .append("}");
        }

        html.append("];\n")
                .append("if(fogSites.length>0){\n")
                .append("const scale=Math.max(0.01,Math.cos(")
                .append("fogSites[0].lat*Math.PI/180));\n")
                .append("const regionBounds=bounds.pad(0.3);\n")
                .append("const frame=[")
                .append("[regionBounds.getWest()*scale,regionBounds.getSouth()],")
                .append("[regionBounds.getEast()*scale,regionBounds.getSouth()],")
                .append("[regionBounds.getEast()*scale,regionBounds.getNorth()],")
                .append("[regionBounds.getWest()*scale,regionBounds.getNorth()]")
                .append("];\n")

                .append("function clipRegion(poly,a,b){\n")
                .append("const nx=(b.lon-a.lon)*scale,ny=b.lat-a.lat;\n")
                .append("const mx=(a.lon+b.lon)*scale/2;")
                .append("const my=(a.lat+b.lat)/2;\n")
                .append("const score=p=>(p[0]-mx)*nx+(p[1]-my)*ny;\n")
                .append("const result=[];\n")
                .append("for(let i=0;i<poly.length;i++){\n")
                .append("const prev=poly[(i+poly.length-1)%poly.length];\n")
                .append("const curr=poly[i];\n")
                .append("const v0=score(prev),v1=score(curr);\n")
                .append("const in0=v0<=0,in1=v1<=0;\n")
                .append("if(in0!==in1){\n")
                .append("const t=v0/(v0-v1);\n")
                .append("result.push([")
                .append("prev[0]+(curr[0]-prev[0])*t,")
                .append("prev[1]+(curr[1]-prev[1])*t]);\n")
                .append("}\n")
                .append("if(in1)result.push(curr);\n")
                .append("}\n")
                .append("return result;\n")
                .append("}\n")

                .append("fogSites.forEach((site,i)=>{\n")
                .append("let poly=frame.map(p=>p.slice());\n")
                .append("fogSites.forEach((other,j)=>{\n")
                .append("if(i!==j)poly=clipRegion(poly,site,other);\n")
                .append("});\n")
                .append("if(poly.length>=3){\n")
                .append("L.polygon(")
                .append("poly.map(p=>[p[1],p[0]/scale]),")
                .append("{pane:'fog-region-pane',")
                .append("color:site.color,")
                .append("fillColor:site.color,")
                .append("fillOpacity:0.13,weight:2})")
                .append(".bindPopup(site.name+' - legközelebbi Fog node területe')")
                .append(".addTo(fogRegions);\n")
                .append("}\n")
                .append("});\n")
                .append("}\n");
    }


    private static String js(String value) {
        return "\"" + value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("<", "\\u003c")
                .replace(">", "\\u003e")
                .replace("&", "\\u0026")
                .replace("\r", "\\r")
                .replace("\n", "\\n")
                + "\"";
    }
}
