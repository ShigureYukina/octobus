#!/usr/bin/env python3
"""
Regenerate presets.json from the remote model pricing API.

This script fetches the same data source that the Go version uses
(https://models.dev/api.json) and writes java/src/main/resources/presets.json.

Usage:
    python scripts/updatePresets.py

The output file is a flat map of model_name -> {input, output, cache_read, cache_write}
matching the ModelInfo record structure used by the Java backend.
"""

import json
import os
import sys
import urllib.request

REMOTE_URL = "https://models.dev/api.json"
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
OUTPUT = os.path.normpath(os.path.join(SCRIPT_DIR, "..", "src", "main", "resources", "presets.json"))

# Only these providers are included (mirrors Go's Provider list)
PROVIDERS = [
    "openai",
    "anthropic",
    "google",
    "deepseek",
    "xai",
    "alibaba",
    "zhipuai",
    "minimax",
    "moonshotai",
    "v0",
]


def main():
    print(f"Fetching pricing data from {REMOTE_URL}...", file=sys.stderr)
    # The API requires a browser-like User-Agent (same as Go's updatePrice.py)
    headers = {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                      "AppleWebKit/537.36 (KHTML, like Gecko) "
                      "Chrome/91.0.4472.124 Safari/537.36"
    }
    try:
        req = urllib.request.Request(REMOTE_URL, headers=headers)
        with urllib.request.urlopen(req, timeout=30) as resp:
            raw = json.loads(resp.read().decode("utf-8"))
    except Exception as e:
        print(f"ERROR: Failed to fetch remote data: {e}", file=sys.stderr)
        print("The existing presets.json is unchanged.", file=sys.stderr)
        return 1

    presets = {}
    count = 0
    for provider in PROVIDERS:
        provider_data = raw.get(provider)
        if not provider_data:
            continue
        models = provider_data.get("models") or {}
        for model_id, model_info in models.items():
            model_name = (model_info.get("id") or model_id).strip().lower()
            cost = model_info.get("cost") or {}
            presets[model_name] = {
                "input": float(cost.get("Input", 0)),
                "output": float(cost.get("Output", 0)),
                "cache_read": float(cost.get("CacheRead", 0)),
                "cache_write": float(cost.get("CacheWrite", 0)),
            }
            count += 1

    os.makedirs(os.path.dirname(OUTPUT), exist_ok=True)
    with open(OUTPUT, "w", encoding="utf-8") as f:
        json.dump(presets, f, indent=2, ensure_ascii=False)

    print(f"Wrote {count} presets to {OUTPUT}", file=sys.stderr)
    return 0


if __name__ == "__main__":
    sys.exit(main())
