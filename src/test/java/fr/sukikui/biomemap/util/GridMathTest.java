package fr.sukikui.biomemap.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class GridMathTest {

  @Test
  void countsAlignedSubChunkCellsInsideOneChunk() {
    assertEquals(1, GridMath.countChunksForCells(0, 4, 4));
    assertEquals(1, GridMath.countChunksForCells(0, 2, 8));
  }

  @Test
  void countsOffsetSubChunkCellsCrossingChunkBoundary() {
    assertEquals(2, GridMath.countChunksForCells(8, 2, 8));
    assertEquals(2, GridMath.countChunksForCells(12, 3, 4));
  }

  @Test
  void countsLargeCellsAcrossMultipleChunks() {
    assertEquals(4, GridMath.countChunksForCells(0, 2, 32));
    assertEquals(6, GridMath.countChunksForCells(-32, 3, 32));
  }

  @Test
  void rejectsInvalidCellGrid() {
    assertThrows(IllegalArgumentException.class, () -> GridMath.countChunksForCells(0, 0, 4));
    assertThrows(IllegalArgumentException.class, () -> GridMath.countChunksForCells(0, 1, 0));
  }
}
