package fr.sukikui.biomemap.export;

import fr.sukikui.biomemap.export.BiomeExporter.Point;
import java.util.function.BiPredicate;

/** Checks generated terrain incrementally without loading or generating export chunks. */
final class ChunkPreflight {

  private final ExportGrid grid;
  private int checked;
  private Point missing;

  ChunkPreflight(ExportGrid grid) {
    this.grid = grid;
  }

  Point advance(int budget, BiPredicate<Integer, Integer> generated) {
    long deadline = System.nanoTime() + 2_000_000L;
    for (int count = 0; count < budget && !complete() && missing == null; count++) {
      Point chunk = grid.chunkAt(checked);
      if (!generated.test(chunk.x(), chunk.z())) {
        missing = chunk;
        return missing;
      }
      checked++;
      if (System.nanoTime() >= deadline) {
        break;
      }
    }
    return missing;
  }

  int checked() {
    return checked;
  }

  boolean complete() {
    return checked == grid.totalChunks();
  }
}
