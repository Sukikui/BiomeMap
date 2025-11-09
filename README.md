<div align="center">

# BiomeMap

BiomeMap generates a lightweight JSON file mapping each world region to its dominant biome. 
Ideal for creating stylized biome maps on external web apps.

</div>

## 📋 Overview

BiomeMap walks a configurable grid (default 32×32 blocks) around a world’s spawn, samples the biome at the 
center and corners of every cell, and records the dominant biome ID. 
The result is a compact JSON file (`plugins/BiomeMap/data/biome-map.json`) ready for web renderers, 
dashboards, or custom tooling—no world edits required.

## ✨ Features

- ±5000 block coverage per world, set once via constants
- Multi-point sampling per cell to smooth biome borders
- JSON schema designed for lightweight web consumption
- `/biomemap` command with tab completion and validation
- Read-only by design, never writes back to world data

## 🚀 Installation

1. Run PaperMC **1.21.8** on **Java 21+**
2. Build the plugin
   ```bash
   ./gradlew build
   ```
3. Drop `build/libs/BiomeMap.jar` into your server’s `plugins/` folder
4. Restart the server (or `/reload confirm`)

## 🕹 Command Reference

| Command | Arguments | Description |
|---------|-----------|-------------|
| `/biomemap [world] [cellSize]` | `world` defaults to `world`, `cellSize` defaults to `32` | Generates the biome export for the specified world. |

Console and caller receive status updates:
```
[BiomeMap] Exporting biomes for world 'world' (cellSize=32)...
[BiomeMap] Export complete: 97344 cells saved to biome-map.json
```

## 📦 JSON Schema

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

| Field | Description |
|-------|-------------|
| `cellSize` | Block width/height for each grid square. |
| `origin` | World coordinates of the southwest corner of the grid. |
| `width`, `height` | Number of cells exported on X/Z. |
| `cells[].i`, `cells[].j` | Grid indices (convert with `origin + index * cellSize`). |
| `cells[].biome` | Namespaced biome ID reported by Paper (e.g., `minecraft:mangrove_swamp`). |

