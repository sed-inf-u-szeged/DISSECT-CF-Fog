# for Windows: https://graphviz.org/download/
import xml.etree.ElementTree as ET
import pydotplus as ptp
import sys
import os

file_path = sys.argv[1]
output_path = os.path.join(sys.argv[2], "workflow.pdf") 

tree = ET.parse(file_path)
root = tree.getroot()
item = tree.getroot()[0]
nodes = []
labels = []
colors = [
"lightcoral", "gray", "lightgray", "firebrick", "red", "chocolate", "darkorange", "moccasin", "gold", "yellow", "darkolivegreen", "chartreuse", "forestgreen", "mediumaquamarine", "turquoise", "cadetblue", "blue", "slateblue", "blueviolet", "magenta", "lightsteelblue"]
for i in range(len(tree.getroot())):
    item = tree.getroot()[i]
    for child in item:
        if (child.attrib['link']=='output'):
            nodes.append(item.attrib['id'])
            nodes.append(child.attrib['id'])
            if 'size' in child.attrib:
                labels.append(child.attrib['type']+'='+child.attrib['size'])
            elif child.attrib['type']=='actuate': 
                labels.append(child.attrib['type'])
       
graph = ptp.Dot(graph_type='digraph')

j=0
k=0
for i in range(0,len(nodes),2):
     graph.add_edge(ptp.Edge(nodes[i], nodes[i+1], label=labels[j], color=colors[i%len(colors)]))
     j+=1
     k+=1
     if k==len(colors):
        k=0

graph.write_pdf(output_path)