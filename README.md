<div align="center">

# BiomeMap

BiomeMap generates a lightweight JSON file mapping each world region to its dominant biome. Ideal for creating stylized biome maps on external web apps.

</div>

## 📋 Overview

BiomeMap scans a configurable grid (default ±5000 blocks around spawn, sampled in 32×32 cells) and writes the dominant 
biome for each cell to `plugins/BiomeMap/data/biome-map.json`.
This JSON is meant for external dashboards or stylized maps—no world edits, 
no database, just a clean file your frontend can colorize.

## ✨ Features

- Samples center + corners of every cell to smooth biome transitions
- Handles `/biomemap [world] [cellSize]` with tab completion
- Pretty-printed JSON with origin, dimensions, and biome ids (`minecraft:plains`, etc.)
- Logs progress to console and in-game so you can monitor long exports
- Read-only: the world is never modified

## 🚀 Installation

1. Install [PaperMC server](https://papermc.io/downloads/paper) with Java 21+
2. Download the latest `biomemap-x.x.x+mcx.x.x.jar` from the [releases page](https://github.com/Sukikui/BiomeMap/releases)
3. Drop the jar into your server’s `plugins/` folder
4. Restart the server or run `/reload confirm`

## 🕹 Command Usage

| Command                        | Arguments                                                | Description                                                      |
|--------------------------------|----------------------------------------------------------|------------------------------------------------------------------|
| `/biomemap [world] [cellSize]` | `world` defaults to `world`, `cellSize` defaults to `32` | Generates/overwrites `data/biome-map.json` for the target world. |

Typical console output:
```
[BiomeMap] Exporting biomes for world 'world' (cellSize=32)...
[BiomeMap] Export complete: 97,344 cells saved to biome-map.json
```

## 🗺 JSON Format

```jsonc
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

| Field                   | Type     | Description                                               |
|-------------------------|----------|-----------------------------------------------------------|
| `cellSize`              | `number` | Width/height of each grid cell in blocks.                 |
| `origin.x`,`origin.z`   | `number` | Southwest corner of the scanned square (spawn − radius).  |
| `width`,`height`        | `number` | Number of cells sampled on each axis.                     |
| `cells[].i`,`cells[].j` | `number` | Grid indices; world coords = `origin + index * cellSize`. |
| `cells[].biome`         | `string` | Namespaced biome id returned by Paper.                    |

---

<div align="center">
Crafted by
<img src="https://starlightskins.lunareclipse.studio/render/head/_Suki_/full?borderHighlight=true&borderHighlightRadius=7&dropShadow=true" width="20" height="20" style="vertical-align:-3px;">
Sukikui
</div>
