package fr.sukikui.biomemap.export;

import fr.sukikui.biomemap.export.BiomeExporter.BiomeCell;
import fr.sukikui.biomemap.export.BiomeExporter.BiomeMapExport;
import fr.sukikui.biomemap.export.BiomeExporter.Point;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;
import org.bukkit.ChunkSnapshot;

/** Samples snapshots and aggregates complete cells without accessing the live world. */
final class ExportSamples {

  static final String UNKNOWN = "minecraft:unknown";
  private final ExportGrid grid;
  private final BiomeCell[] cells;
  private final String[] chunkBiomes;
  private final int cellsPerChunk;
  private final int chunksPerCell;

  ExportSamples(ExportGrid grid) {
    this.grid = grid;
    cells = new BiomeCell[grid.totalCells()];
    cellsPerChunk = grid.cellSize() < 16 ? 16 / grid.cellSize() : 1;
    chunksPerCell = grid.cellSize() < 16 ? 1 : grid.cellSize() / 16;
    chunkBiomes = grid.cellSize() < 16 ? new String[0] : new String[grid.totalChunks()];
  }

  ChunkSample sample(ChunkSnapshot snapshot) {
    if (snapshot == null) {
      return unknown(true);
    }
    if (grid.cellSize() >= 16) {
      return new ChunkSample(biomeAt(snapshot, 8, 8), null, false);
    }
    String[] subCells = new String[cellsPerChunk * cellsPerChunk];
    for (int row = 0; row < cellsPerChunk; row++) {
      for (int col = 0; col < cellsPerChunk; col++) {
        subCells[row * cellsPerChunk + col] = sampleCell(
            snapshot, col * grid.cellSize(), row * grid.cellSize());
      }
    }
    return new ChunkSample(null, subCells, false);
  }

  ChunkSample unknown(boolean missing) {
    String[] subCells = new String[cellsPerChunk * cellsPerChunk];
    Arrays.fill(subCells, UNKNOWN);
    return new ChunkSample(UNKNOWN, subCells, missing);
  }

  void accept(int chunkIndex, ChunkSample sample) {
    if (grid.cellSize() >= 16) {
      // Null records absent terrain, distinct from a technical sampling failure.
      chunkBiomes[chunkIndex] = sample.missing() ? null : sample.biome();
      return;
    }
    Point chunk = grid.chunkAt(chunkIndex);
    int baseCol = Math.toIntExact(Math.floorDiv((long) chunk.x() * 16 - grid.origin().x(),
        grid.cellSize()));
    int baseRow = Math.toIntExact(Math.floorDiv((long) chunk.z() * 16 - grid.origin().z(),
        grid.cellSize()));
    for (int row = 0; row < cellsPerChunk; row++) {
      for (int col = 0; col < cellsPerChunk; col++) {
        int cellCol = baseCol + col;
        int cellRow = baseRow + row;
        if (cellCol >= 0 && cellCol < grid.width() && cellRow >= 0 && cellRow < grid.height()) {
          int index = cellRow * grid.width() + cellCol;
          cells[index] = grid.cellAt(index, sample.subCells()[row * cellsPerChunk + col]);
        }
      }
    }
  }

  BiomeMapExport build(BooleanSupplier cancelled) {
    for (int index = 0; index < cells.length; index++) {
      if (cancelled.getAsBoolean()) {
        return null;
      }
      if (grid.cellSize() >= 16) {
        cells[index] = grid.cellAt(index, aggregateCell(index));
      } else if (cells[index] == null) {
        cells[index] = grid.cellAt(index, UNKNOWN);
      }
    }
    return new BiomeMapExport(grid.cellSize(), grid.selectionMin(), grid.selectionMax(),
        grid.origin(), grid.width(), grid.height(), Arrays.asList(cells));
  }

  private String aggregateCell(int index) {
    int chunkCol = (index % grid.width()) * chunksPerCell;
    int chunkRow = (index / grid.width()) * chunksPerCell;
    Map<String, Integer> counts = new HashMap<>();
    for (int dz = 0; dz < chunksPerCell; dz++) {
      int rowStart = (chunkRow + dz) * grid.chunkColumns();
      for (int dx = 0; dx < chunksPerCell; dx++) {
        String biome = chunkBiomes[rowStart + chunkCol + dx];
        if (biome == null) {
          return UNKNOWN;
        }
        counts.merge(biome, 1, Integer::sum);
      }
    }
    return dominant(counts);
  }

  private String sampleCell(ChunkSnapshot snapshot, int minX, int minZ) {
    int size = grid.cellSize();
    int[][] offsets = {{size / 2, size / 2}, {0, 0}, {size - 1, 0},
        {0, size - 1}, {size - 1, size - 1}};
    Map<String, Integer> counts = new HashMap<>();
    for (int[] offset : offsets) {
      counts.merge(biomeAt(snapshot, minX + offset[0], minZ + offset[1]), 1, Integer::sum);
    }
    return dominant(counts);
  }

  private String biomeAt(ChunkSnapshot snapshot, int x, int z) {
    return BiomeExporter.biomeKey(snapshot.getBiome(x, snapshot.getHighestBlockYAt(x, z), z));
  }

  private String dominant(Map<String, Integer> counts) {
    return counts.entrySet().stream()
        .max(Map.Entry.<String, Integer>comparingByValue().thenComparing(Map.Entry::getKey))
        .map(Map.Entry::getKey).orElse(UNKNOWN);
  }

  record ChunkSample(String biome, String[] subCells, boolean missing) {
  }
}
