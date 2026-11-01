package fr.sukikui.biomemap.export;

import fr.sukikui.biomemap.export.BiomeExporter.BiomeCell;
import fr.sukikui.biomemap.export.BiomeExporter.Point;
import fr.sukikui.biomemap.util.GridMath;

/** Validated grid geometry shared by command parsing, preflight, and sampling. */
public record ExportGrid(
    int cellSize, Point selectionMin, Point selectionMax, Point origin, int width, int height) {

  private static final int MAX_ARRAY_LENGTH = Integer.MAX_VALUE - 8;

  /** Rejects grids whose aligned bounds or backing array lengths cannot be represented. */
  public ExportGrid {
    if (cellSize < 4 || (cellSize < 16 ? 16 % cellSize != 0 : cellSize % 16 != 0)
        || width <= 0 || height <= 0
        || Math.floorMod(origin.x(), cellSize) != 0
        || Math.floorMod(origin.z(), cellSize) != 0) {
      throw new IllegalArgumentException("Invalid cell size or grid alignment.");
    }
    long maxX = (long) origin.x() + (long) width * cellSize - 1;
    long maxZ = (long) origin.z() + (long) height * cellSize - 1;
    if (maxX > Integer.MAX_VALUE || maxZ > Integer.MAX_VALUE) {
      throw new IllegalArgumentException("Aligned selection exceeds supported coordinates.");
    }
    checkedLength((long) width * height);
    checkedLength(GridMath.countChunksForGrid(origin.x(), origin.z(), width, height, cellSize));
  }

  /** Builds a grid from inclusive corners, accepting reversed and negative coordinates. */
  public static ExportGrid fromSelection(int x1, int z1, int x2, int z2, int cellSize) {
    if (cellSize <= 0) {
      throw new IllegalArgumentException("Cell size must be positive.");
    }
    Point min = new Point(Math.min(x1, x2), Math.min(z1, z2));
    Point max = new Point(Math.max(x1, x2), Math.max(z1, z2));
    long minCol = Math.floorDiv((long) min.x(), cellSize);
    long minRow = Math.floorDiv((long) min.z(), cellSize);
    try {
      return new ExportGrid(cellSize, min, max,
          new Point(Math.toIntExact(minCol * cellSize), Math.toIntExact(minRow * cellSize)),
          Math.toIntExact(Math.floorDiv((long) max.x(), cellSize) - minCol + 1),
          Math.toIntExact(Math.floorDiv((long) max.z(), cellSize) - minRow + 1));
    } catch (ArithmeticException ex) {
      throw new IllegalArgumentException("Selection dimensions exceed supported coordinates.", ex);
    }
  }

  /** Number of cells, checked before allocating export storage. */
  public int totalCells() {
    return checkedLength((long) width * height);
  }

  /** Number of chunk columns touched by the complete aligned grid. */
  public int chunkColumns() {
    return GridMath.countChunksForCells(origin.x(), width, cellSize);
  }

  /** Number of chunk rows touched by the complete aligned grid. */
  public int chunkRows() {
    return GridMath.countChunksForCells(origin.z(), height, cellSize);
  }

  /** Number of chunks, checked before allocating progress or sampling storage. */
  public int totalChunks() {
    return checkedLength((long) chunkColumns() * chunkRows());
  }

  /** World chunk coordinates at a row-major index. */
  public Point chunkAt(int index) {
    return new Point(GridMath.chunkStart(origin.x()) + index % chunkColumns(),
        GridMath.chunkStart(origin.z()) + index / chunkColumns());
  }

  /** Creates a cell with inclusive world bounds using checked wide intermediate arithmetic. */
  public BiomeCell cellAt(int index, String biome) {
    int col = index % width;
    int row = index / width;
    int minX = Math.toIntExact((long) origin.x() + (long) col * cellSize);
    int minZ = Math.toIntExact((long) origin.z() + (long) row * cellSize);
    return new BiomeCell(col, row, new BiomeCell.Bounds(new Point(minX, minZ),
        new Point(Math.toIntExact((long) minX + cellSize - 1),
            Math.toIntExact((long) minZ + cellSize - 1))), biome);
  }

  private static int checkedLength(long length) {
    if (length <= 0 || length > MAX_ARRAY_LENGTH) {
      throw new IllegalArgumentException("Selection requires an unsupported array size.");
    }
    return (int) length;
  }
}
