package fr.sukikui.biomemap.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import fr.sukikui.biomemap.export.BiomeExporter.BiomeCell;
import fr.sukikui.biomemap.export.BiomeExporter.BiomeMapExport;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.AbstractList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExportFilesTest {

  @TempDir
  Path directory;
  private final Logger logger = Logger.getLogger("ExportFilesTest");

  @Test
  void publishesCompletePreviewBeforeJsonAndRemovesStagingOnCommit() throws Exception {
    AtomicBoolean previewPublished = new AtomicBoolean();
    ExportFiles files = files(true, (temporary, target) -> {
      assertEquals(directory, temporary.getParent());
      assertFalse(Files.exists(target));
      if (target.equals(preview())) {
        assertEquals(1, ImageIO.read(temporary.toFile()).getWidth());
        assertFalse(Files.exists(json()));
        previewPublished.set(true);
      } else {
        assertTrue(previewPublished.get());
        assertEquals(1, JsonParser.parseString(Files.readString(temporary))
            .getAsJsonObject().getAsJsonArray("cells").size());
      }
      StagedExportFile.publishLink(temporary, target);
    });
    files.write(export(), () -> false);
    assertTrue(Files.exists(json()));
    assertTrue(Files.exists(preview()));
    assertTrue(files.commit());
    assertEquals(List.of("map.json", "map.png"), entries());
    // Late repeated cleanup must never remove an acknowledged successful export.
    files.cleanup();
    assertEquals(List.of("map.json", "map.png"), entries());
  }

  @Test
  void jsonOnlyExportPublishesWithoutTouchingAnUnrelatedPreview() throws Exception {
    Files.writeString(preview(), "existing preview");
    ExportFiles files = files(false, StagedExportFile::publishLink);
    files.write(export(), () -> false);
    assertTrue(files.commit());
    assertEquals("existing preview", Files.readString(preview()));
    assertEquals(List.of("map.json", "map.png"), entries());
  }

  @Test
  void serializationFailureLeavesNoPartialFinalOrTemporaryFile() throws Exception {
    BiomeMapExport valid = export();
    List<BiomeCell> broken = new AbstractList<>() {
      @Override
      public BiomeCell get(int index) {
        if (index == 0) {
          return valid.cells().getFirst();
        }
        throw new IllegalStateException("serialization interrupted");
      }

      @Override
      public int size() {
        return 2;
      }
    };
    BiomeMapExport invalid = new BiomeMapExport(valid.cellSize(), valid.selectionMin(),
        valid.selectionMax(), valid.gridOrigin(), 2, 1, broken);
    ExportFiles files = files(true, StagedExportFile::publishLink);
    assertThrows(IllegalStateException.class, () -> files.write(invalid, () -> false));
    files.cleanup();
    assertTrue(entries().isEmpty());
  }

  @Test
  void previewRenderingFailureDoesNotPublishTheAlreadyWrittenJson() throws Exception {
    BiomeMapExport valid = export();
    BiomeMapExport invalid = new BiomeMapExport(valid.cellSize(), valid.selectionMin(),
        valid.selectionMax(), valid.gridOrigin(), 0, 1, valid.cells());
    ExportFiles files = files(true, StagedExportFile::publishLink);
    assertThrows(IOException.class, () -> files.write(invalid, () -> false));
    assertTrue(entries().isEmpty());
  }

  @Test
  void failurePublishingJsonRollsBackPreviewAndPreservesTheExistingJson() throws Exception {
    Files.writeString(json(), "existing export");
    ExportFiles files = files(true, StagedExportFile::publishLink);
    assertThrows(FileAlreadyExistsException.class, () -> files.write(export(), () -> false));
    files.cleanup();
    assertEquals("existing export", Files.readString(json()));
    assertEquals(List.of("map.json"), entries());
  }

  @Test
  void existingPreviewIsNeverOverwrittenOrRemovedOnFailure() throws Exception {
    Files.writeString(preview(), "existing preview");
    ExportFiles files = files(true, StagedExportFile::publishLink);
    assertThrows(FileAlreadyExistsException.class, () -> files.write(export(), () -> false));
    files.cleanup();
    assertEquals("existing preview", Files.readString(preview()));
    assertEquals(List.of("map.png"), entries());
  }

  @Test
  void unsupportedAtomicPublicationFailsWithoutCopyingToTheFinalPath() throws Exception {
    ExportFiles files = files(true, (temporary, target) -> {
      throw new UnsupportedOperationException("hard links unavailable");
    });
    IOException failure = assertThrows(IOException.class, () -> files.write(export(), () -> false));
    assertTrue(failure.getMessage().contains("hard-link"));
    assertTrue(entries().isEmpty());
  }

  @Test
  void cancellationBeforeWritingCreatesNothingAndPreservesExistingOutputs() throws Exception {
    Files.writeString(json(), "existing export");
    ExportFiles files = files(true, StagedExportFile::publishLink);
    files.write(export(), () -> true);
    files.cleanup();
    assertFalse(files.commit());
    assertEquals("existing export", Files.readString(json()));
    assertEquals(List.of("map.json"), entries());
  }

  @Test
  void cancellationBetweenPublicationsRollsBackTheCompletedPreview() throws Exception {
    AtomicBoolean cancelled = new AtomicBoolean();
    ExportFiles files = files(true, (temporary, target) -> {
      assertEquals(preview(), target);
      StagedExportFile.publishLink(temporary, target);
      cancelled.set(true);
    });
    files.write(export(), cancelled::get);
    files.cleanup();
    assertFalse(files.commit());
    assertTrue(entries().isEmpty());
  }

  @Test
  void cancellationAfterPublicationBeforeAcknowledgementRemovesOnlyThisRun() throws Exception {
    ExportFiles files = files(true, StagedExportFile::publishLink);
    files.write(export(), () -> false);
    Files.delete(json());
    Files.writeString(json(), "replacement from another writer");
    files.cleanup();
    files.cleanup();
    assertFalse(files.commit());
    assertEquals("replacement from another writer", Files.readString(json()));
    assertEquals(List.of("map.json"), entries());
  }

  @Test
  void cancellationDoesNotWaitForAnActiveWriterAndPreventsLaterPublication() throws Exception {
    CountDownLatch publishing = new CountDownLatch(1);
    CountDownLatch resume = new CountDownLatch(1);
    ExportFiles files = files(true, (temporary, target) -> {
      publishing.countDown();
      try {
        if (!resume.await(5, TimeUnit.SECONDS)) {
          throw new IOException("Test writer was not released");
        }
      } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
        throw new IOException(ex);
      }
      StagedExportFile.publishLink(temporary, target);
    });
    CompletableFuture<Void> writer = CompletableFuture.runAsync(() -> {
      try {
        files.write(export(), () -> false);
      } catch (IOException ex) {
        throw new java.util.concurrent.CompletionException(ex);
      }
    });
    try {
      assertTrue(publishing.await(5, TimeUnit.SECONDS));
      files.cleanup();
      assertFalse(writer.isDone());
    } finally {
      resume.countDown();
      writer.get(5, TimeUnit.SECONDS);
    }
    assertFalse(files.commit());
    assertTrue(entries().isEmpty());
  }

  private ExportFiles files(boolean preview, StagedExportFile.Publisher publisher) {
    return new ExportFiles(new BiomeExporter(directory.toFile()), json().toFile(), preview,
        logger, publisher);
  }

  private BiomeMapExport export() {
    ExportGrid grid = ExportGrid.fromSelection(0, 0, 0, 0, 16);
    return new BiomeMapExport(16, grid.selectionMin(), grid.selectionMax(), grid.origin(), 1, 1,
        List.of(grid.cellAt(0, "minecraft:plains")));
  }

  private Path json() {
    return directory.resolve("map.json");
  }

  private Path preview() {
    return directory.resolve("map.png");
  }

  private List<String> entries() throws IOException {
    try (var files = Files.list(directory)) {
      return files.map(path -> Objects.requireNonNull(path.getFileName()).toString())
          .sorted().toList();
    }
  }
}
