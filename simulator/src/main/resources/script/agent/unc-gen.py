import json

base = {
    "name": "UNC-1",
    "type": "noise-classification",
    "energy": 0.0,
    "price": 0.0,
    "latency": 1.0,
    "bandwidth": 0.0,
    "components": []
}

sensor_templates = {
    1:  {"inside": True,  "sun": False},
    2:  {"inside": False, "sun": False},
    3:  {"inside": False, "sun": False},
    4:  {"inside": False, "sun": False},
    5:  {"inside": False, "sun": False},
    6:  {"inside": False, "sun": True},
    7:  {"inside": False, "sun": True},
    8:  {"inside": False, "sun": True},
    9:  {"inside": False, "sun": True},
    10: {"inside": False,  "sun": True},
}

# 100 sensor
for i in range(1, 101):
    template_idx = ((i - 1) % 10) + 1
    props = sensor_templates[template_idx]

    base["components"].append({
        "id": str(i),
        "requirements": {
            "cpu": 4,
            "memory": 4294967296,
            "edge": True
        },
        "properties": {
            "kind": "noise-sensor",
            "image": "2147483648",
            "inside": props["inside"],
            "sun": props["sun"]
        }
    })

# 1 remote server
base["components"].append({
    "id": "101",
    "requirements": {
        "cpu": 4,
        "memory": 8589934592,
        "storage": 274877906944
    },
    "properties": {
        "kind": "remote-server",
        "image": "2147483648"
    }
})

# ✅ fájlba írás
with open("unc-1-100-sensors.json", "w") as f:
    json.dump(base, f, indent=2)

print("JSON fájl elkészült: unc-1-100-sensors.json")