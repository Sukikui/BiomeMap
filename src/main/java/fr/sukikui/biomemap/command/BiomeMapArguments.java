package fr.sukikui.biomemap.command;

import fr.sukikui.biomemap.util.GridMath;

/** Parses numeric command arguments and aligns the requested cell resolution. */
final class BiomeMapArguments {

  private static final int MIN_CELL_SIZE = 4;

  private BiomeMapArguments() {
  }

  static Integer parseInteger(String raw) {
    try {
      return Integer.parseInt(raw);
    } catch (NumberFormatException ex) {
      return null;
    }
  }

  static Integer parsePositiveInteger(String raw) {
    Integer value = parseInteger(raw);
    if (value == null || value <= 0) {
      return null;
    }
    return value;
  }

  static int alignCellSize(int requestedSize) {
    if (requestedSize <= MIN_CELL_SIZE) {
      return MIN_CELL_SIZE;
    }
    if (requestedSize < GridMath.CHUNK_SIZE) {
      int alignedSize = MIN_CELL_SIZE;
      while (alignedSize < GridMath.CHUNK_SIZE && requestedSize > alignedSize) {
        alignedSize *= 2;
      }
      return alignedSize;
    }
    int remainder = requestedSize % GridMath.CHUNK_SIZE;
    if (remainder == 0) {
      return requestedSize;
    }
    long aligned = (long) requestedSize + GridMath.CHUNK_SIZE - remainder;
    if (aligned > Integer.MAX_VALUE) {
      throw new IllegalArgumentException("Cell size is too large to align to the chunk grid.");
    }
    return (int) aligned;
  }
}
