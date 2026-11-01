package fr.sukikui.biomemap.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.sukikui.biomemap.export.BiomeExporter.Point;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChunkPreflightTest {

  @Test
  void checksIncrementallyAndStopsAtTheFirstMissingChunk() {
    ChunkPreflight preflight = new ChunkPreflight(
        ExportGrid.fromSelection(-16, 0, 31, 15, 16));
    List<Point> requests = new ArrayList<>();
    assertNull(preflight.advance(1, (x, z) -> {
      requests.add(new Point(x, z));
      return true;
    }));
    assertEquals(1, preflight.checked());
    assertFalse(preflight.complete());
    assertEquals(new Point(0, 0), preflight.advance(1, (x, z) -> {
      requests.add(new Point(x, z));
      return false;
    }));
    assertEquals(List.of(new Point(-1, 0), new Point(0, 0)), requests);
    assertEquals(new Point(0, 0), preflight.advance(10, (x, z) -> {
      throw new AssertionError("No checks may continue after rejection");
    }));
  }

  @Test
  void checksTheAlignedGridIncludingChunksOutsideRawSelection() {
    ExportGrid grid = ExportGrid.fromSelection(8, 8, 8, 8, 32);
    ChunkPreflight preflight = new ChunkPreflight(grid);
    List<Point> checked = new ArrayList<>();
    while (!preflight.complete()) {
      assertNull(preflight.advance(1, (x, z) -> {
        checked.add(new Point(x, z));
        return true;
      }));
    }
    assertEquals(List.of(new Point(0, 0), new Point(1, 0), new Point(0, 1), new Point(1, 1)),
        checked);
    assertEquals(4, preflight.checked());
    assertTrue(preflight.complete());
  }
}
