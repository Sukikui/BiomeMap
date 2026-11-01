package fr.sukikui.biomemap.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import fr.sukikui.biomemap.export.ExportSamples.ChunkSample;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ExportSamplesTest {

  @ParameterizedTest
  @ValueSource(ints = {4, 8, 16, 32, 48})
  void missingTerrainProducesCompleteUnknownCellsAtAllResolutions(int size) {
    ExportGrid grid = ExportGrid.fromSelection(-7, -3, 35, 19, size);
    ExportSamples samples = new ExportSamples(grid);
    for (int index = 0; index < grid.totalChunks(); index++) {
      samples.accept(index, samples.sample(null));
    }
    BiomeExporter.BiomeMapExport export = samples.build(() -> false);
    assertNotNull(export);
    assertEquals(grid.totalCells(), export.cells().size());
    for (int index = 0; index < grid.totalCells(); index++) {
      assertEquals(grid.cellAt(index, ExportSamples.UNKNOWN), export.cells().get(index));
    }
  }

  @Test
  void oneMissingChunkMakesWholeLargeCellUnknownEvenWhenOtherChunksAgree() {
    ExportSamples samples = new ExportSamples(ExportGrid.fromSelection(0, 0, 0, 0, 32));
    for (int index = 0; index < 3; index++) {
      samples.accept(index, new ChunkSample("minecraft:plains", null, false));
    }
    samples.accept(3, samples.unknown(true));
    assertEquals(ExportSamples.UNKNOWN,
        samples.build(() -> false).cells().getFirst().biome());
  }

  @Test
  void completeCellsKeepMajorityAndDeterministicTies() {
    ExportSamples samples = new ExportSamples(ExportGrid.fromSelection(0, 0, 0, 0, 32));
    samples.accept(0, new ChunkSample("minecraft:forest", null, false));
    samples.accept(1, new ChunkSample("minecraft:forest", null, false));
    samples.accept(2, new ChunkSample("minecraft:plains", null, false));
    samples.accept(3, new ChunkSample("minecraft:plains", null, false));
    assertEquals("minecraft:plains", samples.build(() -> false).cells().getFirst().biome());
    samples.accept(3, new ChunkSample("minecraft:forest", null, false));
    assertEquals("minecraft:forest", samples.build(() -> false).cells().getFirst().biome());
  }

  @Test
  void offsetSubChunkGridUsesTheCorrectSnapshotCells() {
    ExportGrid grid = ExportGrid.fromSelection(-12, -12, -1, -1, 4);
    ExportSamples samples = new ExportSamples(grid);
    String[] subCells = new String[16];
    for (int index = 0; index < subCells.length; index++) {
      subCells[index] = "test:biome_" + index;
    }
    samples.accept(0, new ChunkSample(null, subCells, false));
    BiomeExporter.BiomeMapExport export = samples.build(() -> false);
    assertNotNull(export);
    for (int index = 0; index < grid.totalCells(); index++) {
      int snapshotIndex = (index / grid.width() + 1) * 4 + index % grid.width() + 1;
      assertEquals("test:biome_" + snapshotIndex, export.cells().get(index).biome());
    }
  }

  @Test
  void missingChunkDoesNotChangeNeighboringCompleteCells() {
    ExportSamples samples = new ExportSamples(ExportGrid.fromSelection(0, 0, 63, 31, 32));
    for (int index = 0; index < 8; index++) {
      samples.accept(index, new ChunkSample("minecraft:forest", null, false));
    }
    samples.accept(3, samples.unknown(true));
    BiomeExporter.BiomeMapExport export = samples.build(() -> false);
    assertNotNull(export);
    assertEquals("minecraft:forest", export.cells().getFirst().biome());
    assertEquals(ExportSamples.UNKNOWN, export.cells().get(1).biome());
  }

  @Test
  void cancellationStopsAggregation() {
    ExportSamples samples = new ExportSamples(ExportGrid.fromSelection(0, 0, 0, 0, 16));
    assertNull(samples.build(() -> true));
  }
}
