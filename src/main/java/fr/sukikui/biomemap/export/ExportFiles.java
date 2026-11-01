package fr.sukikui.biomemap.export;

import fr.sukikui.biomemap.export.BiomeExporter.BiomeMapExport;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.function.BooleanSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Owns the paths and output files created by one export. */
final class ExportFiles {

  private final BiomeExporter exporter;
  private final File json;
  private final File preview;
  private final Logger logger;
  private volatile boolean started;

  ExportFiles(BiomeExporter exporter, File json, boolean preview, Logger logger) {
    this.exporter = exporter;
    this.json = json;
    this.preview = preview ? exporter.resolvePreviewOutput(json) : null;
    this.logger = logger;
  }

  File json() {
    return json;
  }

  File preview() {
    return preview;
  }

  void write(BiomeMapExport export, BooleanSupplier cancelled) throws IOException {
    if (cancelled.getAsBoolean()) {
      return;
    }
    started = true;
    exporter.writeExport(export, json);
    if (preview != null && !cancelled.getAsBoolean()) {
      exporter.writePreview(export, preview);
    }
  }

  void cleanup() {
    if (started) {
      delete(json);
      delete(preview);
    }
  }

  private void delete(File file) {
    if (file == null) {
      return;
    }
    try {
      Files.deleteIfExists(file.toPath());
    } catch (IOException ex) {
      logger.log(Level.WARNING, "Unable to delete export file " + file, ex);
    }
  }
}
