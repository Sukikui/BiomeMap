package fr.sukikui.biomemap.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BiomeExporterPathsTest {

  @TempDir
  Path directory;

  @Test
  void skipsExistingJsonAndOrphanPreviewWithoutCreatingFiles() throws Exception {
    Path exports = Files.createDirectory(directory.resolve("exports"));
    Files.writeString(exports.resolve("world_16_1.json"), "previous export");
    Files.writeString(exports.resolve("world_16_2.png"), "orphaned complete preview");
    BiomeExporter exporter = new BiomeExporter(directory.toFile());
    Path candidate = exporter.resolveSelectionOutput("world", 16).toPath();
    assertEquals(exports.resolve("world_16_3.json"), candidate);
    assertFalse(Files.exists(candidate));
    assertEquals("previous export", Files.readString(exports.resolve("world_16_1.json")));
    assertEquals("orphaned complete preview", Files.readString(exports.resolve("world_16_2.png")));
  }

  @Test
  void treatsDanglingSymlinksAsOccupiedNames() throws Exception {
    Path exports = Files.createDirectory(directory.resolve("exports"));
    Files.createSymbolicLink(exports.resolve("world_16_1.json"), exports.resolve("absent.json"));
    Files.createSymbolicLink(exports.resolve("world_16_2.png"), exports.resolve("absent.png"));
    assertEquals(exports.resolve("world_16_3.json"),
        new BiomeExporter(directory.toFile()).resolveSelectionOutput("world", 16).toPath());
  }
}
