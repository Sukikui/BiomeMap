package fr.sukikui.biomemap.util;

/**
 * Shared grid/chunk calculations for biome exports.
 */
public final class GridMath {

  public static final int CHUNK_SIZE = 16;

  private GridMath() {
  }

  /**
   * Counts how many chunks are touched by a cell axis.
   */
  public static int countChunksForCells(int origin, int cellCount, int cellSize) {
    if (cellCount <= 0 || cellSize <= 0) {
      throw new IllegalArgumentException("Cell count and size must be positive");
    }

    long minChunk = Math.floorDiv((long) origin, CHUNK_SIZE);
    long maxBlock = (long) origin + ((long) cellCount * cellSize) - 1L;
    long maxChunk = Math.floorDiv(maxBlock, CHUNK_SIZE);
    long span = maxChunk - minChunk + 1L;
    if (span > Integer.MAX_VALUE) {
      throw new IllegalArgumentException("Selection spans too many chunks");
    }
    return Math.max(1, (int) span);
  }

  /**
   * Counts how many chunks are touched by a cell grid.
   */
  public static long countChunksForGrid(
      int originX, int originZ, int width, int height, int cellSize) {
    long columns = countChunksForCells(originX, width, cellSize);
    long rows = countChunksForCells(originZ, height, cellSize);
    return columns * rows;
  }

  /**
   * Returns the chunk coordinate containing the provided block coordinate.
   */
  public static int chunkStart(int origin) {
    return Math.floorDiv(origin, CHUNK_SIZE);
  }
}
