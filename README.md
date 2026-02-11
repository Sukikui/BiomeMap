<div align="center">

# BiomeMap

Generates a lightweight JSON file mapping each world region to its dominant biome. Ideal for creating stylized biome maps on external web apps.

</div>

## 📋 Overview

BiomeMap exports the dominant biome of a rectangular selection.

You choose a world and 2 corners (`x/z`), and the plugin builds a grid of cells over that area.  
Then it samples biomes and writes:
- a JSON file
- optionally a PNG preview (`1 pixel = 1 cell`)

It is read-only: no world edits, no database, no extra services.

## ✨ Features

- Rectangular selection defined by two coordinates (`/biomemap world x1 z1 x2 z2 [cellSize] [preview]`)
- Cell size with chunk-friendly alignment (`8`, `16`, `32`, ...)
- Chunk-based sampling to smooth biome transitions
- Asynchronous processing with frequent progress updates (no server freeze)
- One export at a time (global lock)
- Stop command to cancel running exports cleanly
- Structured JSON output with min/max bounds per cell and namespaced biome IDs
- Optional PNG preview output (`1 pixel = 1 cell`) using a biome RGB palette

## 🚀 Installation

1. Install [PaperMC server](https://papermc.io/downloads/paper) with Java 21+
2. Download the latest `biomemap-x.x.x+mcx.x.x.jar` from the [releases page](https://github.com/Sukikui/BiomeMap/releases)
3. Drop the jar into your server’s `plugins/` folder
4. Restart the server or run `/reload confirm`

## 🕹 Command Usage

| Command | Arguments | Description |
| --- | --- | --- |
| `/biomemap <world> <x1> <z1> <x2> <z2> [cellSize] [preview]` | `world` required, `cellSize` default `16`, `preview` optional | Exports the full rectangle between the 2 points. If `preview` is present, also writes a PNG. |
| `/biomemap stop` | none | Stops the current export. |

Notes:
- Good `cellSize` values: `8`, `16`, `32`, `64`, ...
- `preview` can be written as `preview` or `--preview`
- If an export is stopped, files for that run are deleted from `exports/`

Example:
```
/biomemap world -512 -512 320 192 32
```
→ covers the area between `(-512,-512)` and `(320,192)` using 32×32-block cells (chunk-aligned).

Preview example:
```
/biomemap world -512 -512 320 192 32 preview
```
→ same JSON export + PNG preview with one pixel per cell.

### 📁 Output files

Exports are written to `plugins/BiomeMap/exports/`.

JSON files use:
- `<world>_<cellSize>_<index>.json`

If the filename already exists, `index` is incremented (`world_32_1.json`, `world_32_2.json`, ...).

If `preview` is enabled, the plugin also writes PNG files with the same base name as each JSON file:
- `plugins/BiomeMap/exports/<world>_<cellSize>_<index>.png`

Biome colors come from `src/main/java/fr/sukikui/biomemap/export/BiomeColorPalette.java` (vanilla 1.21.11 palette with deterministic fallback for unknown biome ids).

### ⚙️ Configuration

`config.yml` exposes performance throttles.

| Key | Default | Description |
| --- | --- | --- |
| `performance.chunks-per-tick` | `1` | How many new chunk jobs are started each tick. Lower = safer, slower. |
| `performance.max-in-flight` | `4` | Max number of BiomeMap jobs currently in pipeline (queued/running). |
| `performance.max-concurrent-chunks` | `64` | Max real chunk loads at the same time (main server pressure knob). |

Quick tuning guide:
- If players feel lag, lower `max-concurrent-chunks` first.
- If export feels too slow but server is stable, increase `chunks-per-tick` a bit.
- Keep `max-in-flight >= chunks-per-tick`.

## 🗺 JSON Format

```json
{
  "cellSize": 16,
  "selectionMin": { "x": -200, "z": -200 },
  "selectionMax": { "x": -50, "z": -20 },
  "gridOrigin": { "x": -208, "z": -208 },
  "width": 10,
  "height": 12,
  "cells": [
    {
      "i": 0,
      "j": 0,
      "bounds": {
        "min": { "x": -208, "z": -208 },
        "max": { "x": -193, "z": -193 }
      },
      "biome": "minecraft:plains"
    },
    {
      "i": 1,
      "j": 0,
      "bounds": {
        "min": { "x": -192, "z": -208 },
        "max": { "x": -177, "z": -193 }
      },
      "biome": "minecraft:forest"
    }
  ]
}
```

| Field | Type | Description |
| --- | --- | --- |
| `cellSize` | `number` | Cell size in blocks (minimum 8; values above that are aligned to the chunk grid). |
| `selectionMin/Max` | `object` | Raw coordinates provided in the command. |
| `gridOrigin` | `object` | North-west corner of the grid (min X, min Z). |
| `width`, `height` | `number` | Number of cells on the X and Z axes. |
| `cells[].bounds.min/max` | `object` | Inclusive bounds delimiting the cell. |
| `cells[].biome` | `string` | Namespaced biome ID (e.g. `minecraft:savanna`). |

---

<div align="center">
Crafted by
<img src="https://starlightskins.lunareclipse.studio/render/head/_Suki_/full?borderHighlight=true&borderHighlightRadius=7&dropShadow=true" width="20" height="20" style="vertical-align:-3px;">
Sukikui
</div>
