package fr.sukikui.biomemap.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import fr.sukikui.biomemap.export.BiomeExporter.Point;
import java.util.Random;
import org.junit.jupiter.api.Test;

class ExportGridTest {

  @Test
  void preservesSingleBlockAndReversedSelections() {
    ExportGrid single = ExportGrid.fromSelection(-1, -1, -1, -1, 4);
    assertEquals(1, single.totalCells());
    assertEquals(new Point(-4, -4), single.origin());
    assertEquals(new Point(-1, -1), single.cellAt(0, "test:biome").bounds().max());
    assertEquals(ExportGrid.fromSelection(-17, -9, 32, 21, 8),
        ExportGrid.fromSelection(32, 21, -17, -9, 8));
  }

  @Test
  void handlesRepresentableIntegerEdgesWithoutOverflow() {
    ExportGrid low = ExportGrid.fromSelection(
        Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, 16);
    assertEquals(new Point(Integer.MIN_VALUE, Integer.MIN_VALUE), low.origin());
    ExportGrid high = ExportGrid.fromSelection(
        Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, 16);
    assertEquals(new Point(Integer.MAX_VALUE, Integer.MAX_VALUE),
        high.cellAt(0, "test:biome").bounds().max());
  }

  @Test
  void rejectsOverflowingCountsAndAlignedBoundsBeforeAllocation() {
    assertThrows(IllegalArgumentException.class, () -> ExportGrid.fromSelection(
        Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, 4));
    // Cells fit in an int, but the number of chunks required by large cells does not.
    assertThrows(IllegalArgumentException.class,
        () -> ExportGrid.fromSelection(0, 0, 1_000_000, 1_000_000, 32));
    assertThrows(IllegalArgumentException.class, () -> ExportGrid.fromSelection(
        Integer.MIN_VALUE, 0, Integer.MIN_VALUE, 0, 48));
    assertThrows(IllegalArgumentException.class, () -> ExportGrid.fromSelection(
        Integer.MAX_VALUE, 0, Integer.MAX_VALUE, 0, 48));
    assertThrows(IllegalArgumentException.class,
        () -> ExportGrid.fromSelection(0, 0, 1, 1, 0));
    assertThrows(IllegalArgumentException.class,
        () -> ExportGrid.fromSelection(0, 0, 1, 1, 6));
  }

  @Test
  @SuppressFBWarnings(value = "PREDICTABLE_RANDOM",
      justification = "Fixed seed deliberately makes geometry regression tests reproducible")
  void alignedGridsCoverRandomSelectionsAndExactChunkExtents() {
    Random random = new Random(42);
    int[] sizes = {4, 8, 16, 32, 48, 64};
    for (int attempt = 0; attempt < 1000; attempt++) {
      int x = random.nextInt(2000) - 1000;
      int z = random.nextInt(2000) - 1000;
      ExportGrid grid = ExportGrid.fromSelection(
          x, z, x + random.nextInt(100), z + random.nextInt(100), sizes[attempt % sizes.length]);
      Point last = grid.cellAt(grid.totalCells() - 1, "test:biome").bounds().max();
      assertTrue(grid.origin().x() <= grid.selectionMin().x());
      assertTrue(grid.origin().z() <= grid.selectionMin().z());
      assertTrue(last.x() >= grid.selectionMax().x());
      assertTrue(last.z() >= grid.selectionMax().z());
      int firstX = Math.floorDiv(grid.origin().x(), 16);
      int firstZ = Math.floorDiv(grid.origin().z(), 16);
      assertEquals(Math.floorDiv(last.x(), 16) - firstX + 1, grid.chunkColumns());
      assertEquals(Math.floorDiv(last.z(), 16) - firstZ + 1, grid.chunkRows());
      assertEquals(new Point(firstX, firstZ), grid.chunkAt(0));
      assertEquals(new Point(Math.floorDiv(last.x(), 16), Math.floorDiv(last.z(), 16)),
          grid.chunkAt(grid.totalChunks() - 1));
    }
  }
}
