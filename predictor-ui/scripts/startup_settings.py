import json
import sys
from pathlib import Path


def load_startup_settings(argv=None):
    args = list(sys.argv[1:] if argv is None else argv)
    if len(args) == 2 and args[0] == "--settings-file":
        with Path(args[1]).open("r", encoding="utf-8-sig") as stream:
            result = json.load(stream)
    elif len(args) == 1 and args[0] != "--settings-file":
        result = json.loads(args[0])
    else:
        raise ValueError("Expected --settings-file PATH or one JSON argument")
    if not isinstance(result, dict):
        raise ValueError("Startup settings must be a JSON object")
    return result
