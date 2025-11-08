<div align="center">

# BiomeMap
BiomeMap generates a lightweight JSON file mapping each world region to its dominant biome. Ideal for creating stylized biome maps on external web apps.
</div>

## Why BiomeMap?
- Generates `plugins/BiomeMap/data/biome-map.json` covering ±5 000 blocks around the spawn.
- Samples each cell (center + corners) to record the dominant biome ID.
- Output schema stays tiny and web-friendly for quick visualization pipelines.

## Requirements
- PaperMC 1.21.8 server
- Java 21+
- Gradle (wrapper provided)

## Build & Install
```bash
./gradlew build
```
Copy `build/libs/BiomeMap.jar` to your server’s `plugins/` folder and restart/reload.

## Command
| Command | Args | Description |
|---------|------|-------------|
| `/biomemap [world] [cellSize]` | `world` defaults to `world`; `cellSize` defaults to `32` | Scans the target world and writes the JSON export. |

Feedback goes to both console and the caller, e.g.:
```
[BiomeMap] Exporting biomes for world 'world' (cellSize=32)...
[BiomeMap] Export complete: 97344 cells saved to biome-map.json
```

## JSON Layout
```json
{
  "cellSize": 32,
  "origin": { "x": -5000, "z": -5000 },
  "width": 312,
  "height": 312,
  "cells": [
    { "i": 0, "j": 0, "biome": "minecraft:plains" },
    { "i": 1, "j": 0, "biome": "minecraft:forest" }
  ]
}
```
- `i`,`j` index the grid; convert back to world coords via `origin + (index * cellSize)`.
- `biome` is the namespaced biome ID returned by Paper.

## Development Notes
- Project package: `fr.sukikui.biomemap`.
- Build script auto-syncs `plugin.yml` metadata with the Paper API version.
- Source entry point: `src/main/java/fr/sukikui/biomemap/BiomeMap.java`.

Happy mapping! 🎯
