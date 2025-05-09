import folium
from folium import plugins
import sys
import os

# separating the computing nodes
nodeInfo = sys.argv[1].split(";")

# defining 20 different colors
colors = ["red", "blue", "green", "yellow", "purple", "cyan", "magenta", "orange", "brown", "pink", "lime", "teal", "gray", "black", "white", "crimson", "turquoise", "indigo", "gold", "silver"]

# set the map zoom based on the first node's info
map = folium.Map(
    location=[nodeInfo[0].split(",")[1], nodeInfo[0].split(",")[2]], zoom_start=5
)

# iterating the nodes and separating the name[0], latitude[1], longitude[2]
for i, nodes in enumerate(nodeInfo):
    node = nodes.split(",")

    for j in range(0, len(node), 3):
        index1 = j
        index2 = j + 1
        index3 = j + 2
    
        popup = folium.Popup(node[index1], show=True, sticky=True)
        folium.Marker(
            location=[float(node[index2]), float(node[index3])],
            popup=popup,
            icon=folium.Icon(icon="cloud", color=colors[i]),
        ).add_to(map)

# saving the map
map.save(os.path.join(sys.argv[2], "map.html"))
