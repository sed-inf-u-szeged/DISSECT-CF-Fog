import folium
from folium import plugins
import sys
import os

# separating the computing nodes
nodeInfo = sys.argv[1].split(";")

# set the map zoom based on the first node's info
map = folium.Map(
    location=[nodeInfo[0].split(",")[1], nodeInfo[0].split(",")[2]], zoom_start=5
)

# iterating the nodes and separating the name[0], latitude[1], longitude[2] and parent[3] and range[4]
for i in nodeInfo:
    node = i.split(",")

    if node[3] == "0":
        continue
    elif node[3] == "1":  # if cloud node
        popup = folium.Popup(node[0], show=True, sticky=True)
        folium.Marker(
            location=[node[1], node[2]],
            #popup=node[0],
            popup=popup,
            icon=folium.Icon(icon="cloud", color="blue"),
        ).add_to(map)

        folium.Circle(
            location=[node[1], node[2]],
            radius=float(node[4]) * 1000,
            color="#007DEF",
            fill=True,
            fill_color="blue",
        ).add_to(map)
    elif node[3] == "2":  # if fog node
        popup = folium.Popup(node[0], show=True, sticky=True)
        folium.Marker(
            location=[node[1], node[2]],
            popup=popup,
            icon=folium.Icon(icon="cloud", color="gray"),
        ).add_to(map)

        folium.Circle(
            location=[node[1], node[2]],
            radius=float(node[4]) * 1000,
            color="gray",
            fill=True,
            fill_color="gray",
        ).add_to(map)
    elif node[3] == "3":  # if fog node
        popup = folium.Popup(node[0], show=True, sticky=True)
        folium.Marker(
            location=[node[1], node[2]],
            popup=popup,
            icon=folium.Icon(icon="cloud", color="red"),
        ).add_to(map)

        folium.Circle(
            location=[node[1], node[2]],
            radius=float(node[4]) * 1000,
            color="red",
            fill=True,
            fill_color="red",
        ).add_to(map)
    elif node[3] == "4":  # if fog node
        popup = folium.Popup(node[0], show=True, sticky=True)
        folium.Marker(
            location=[node[1], node[2]],
            popup=popup,
            icon=folium.Icon(icon="cloud", color="purple"),
        ).add_to(map)

        folium.Circle(
            location=[node[1], node[2]],
            radius=float(node[4]) * 1000,
            color="purple",
            fill=True,
            fill_color="purple",
        ).add_to(map)
    elif node[3] == "5":  # if fog node
        popup = folium.Popup(node[0], show=True, sticky=True)
        folium.Marker(
            location=[node[1], node[2]],
            popup=popup,
            icon=folium.Icon(icon="cloud", color="green"),
        ).add_to(map)

        folium.Circle(
            location=[node[1], node[2]],
            radius=float(node[4]) * 1000,
            color="green",
            fill=True,
            fill_color="green",
        ).add_to(map)
    elif node[3] == "6":  # if fog node
        popup = folium.Popup(node[0], show=True, sticky=True)
        folium.Marker(
            location=[node[1], node[2]],
            popup=popup,
            icon=folium.Icon(icon="cloud", color="orange"),
        ).add_to(map)

        folium.Circle(
            location=[node[1], node[2]],
            radius=float(node[4]) * 1000,
            color="orange",
            fill=True,
            fill_color="orange",
        ).add_to(map)
    elif node[3] == "7":  # if fog node
        popup = folium.Popup(node[0], show=True, sticky=True)
        folium.Marker(
            location=[node[1], node[2]],
            popup=popup,
            icon=folium.Icon(icon="cloud", color="orange"),
        ).add_to(map)

        folium.Circle(
            location=[node[1], node[2]],
            radius=float(node[4]) * 1000,
            color="orange",
            fill=True,
            fill_color="orange",
        ).add_to(map)


# reading from file the geolocation info of the device
for i in range(int(sys.argv[4])):

    pathFile = os.path.join(sys.argv[3], "devicePath-" + str(i) + ".csv")

    route_lats_long = []
    count = 0
    file = open(pathFile, "r")
    lines = file.readlines()

    for line in lines:
        if count == 0:
            coord = line.split(",")

            folium.Circle(
                location=[coord[1], coord[2]],
                radius=coord[3],
                color="yellow",
                fill=True,
                fill_color="gray",
            ).add_to(map)

            #popup = folium.Popup("device" + str(i), show=True, sticky=False)
            folium.Marker(
                location=[coord[1], coord[2]],
                popup="device" + str(i),
                icon=folium.Icon(prefix="fa", icon="mobile", color="red"),
            ).add_to(map)

        if count > 0:
            coord = line.split(",")
            route_lats_long.append([float(coord[0]), float(coord[1])])
        count += 1

    file.close()

    # adding the route to the map
    if len(route_lats_long) > 0:
        folium.PolyLine(route_lats_long).add_to(map)

# saving the map
map.save(os.path.join(sys.argv[3], "map.html"))
