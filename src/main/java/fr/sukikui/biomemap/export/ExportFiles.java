package fr.sukikui.biomemap.export;

import fr.sukikui.biomemap.export.BiomeExporter.BiomeMapExport;
import java.io.File;
import java.io.IOException;
import java.util.function.BooleanSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Stages one export, publishes complete files, and rolls back only files owned by this run. */
final class ExportFiles {

  private final BiomeExporter exporter;
  private final File json;
  private final File preview;
  private final Logger logger;
  private final StagedExportFile.Publisher publisher;
  private StagedExportFile stagedJson;
  private StagedExportFile stagedPreview;
  private boolean writing;
  private boolean ready;
  private boolean cancelled;
  private boolean committed;

  ExportFiles(BiomeExporter exporter, File json, boolean preview, Logger logger) {
    this(exporter, json, preview, logger, StagedExportFile::publishLink);
  }

  ExportFiles(BiomeExporter exporter, File json, boolean preview, Logger logger,
      StagedExportFile.Publisher publisher) {
    this.exporter = exporter;
    this.json = json;
    this.preview = preview ? exporter.resolvePreviewOutput(json) : null;
    this.logger = logger;
    this.publisher = publisher;
  }

  File json() {
    return json;
  }

  File preview() {
    return preview;
  }

  void write(BiomeMapExport export, BooleanSupplier cancelled) throws IOException {
    if (!beginWrite()) {
      return;
    }
    boolean complete = false;
    try {
      if (shouldStop(cancelled)) {
        return;
      }
      stagedJson = new StagedExportFile(json.toPath(), publisher);
      exporter.writeExport(export, stagedJson.temporary().toFile());
      if (shouldStop(cancelled)) {
        return;
      }
      if (preview != null) {
        stagedPreview = new StagedExportFile(preview.toPath(), publisher);
        exporter.writePreview(export, stagedPreview.temporary().toFile());
        if (shouldStop(cancelled)) {
          return;
        }
        stagedPreview.publish();
      }
      if (shouldStop(cancelled)) {
        return;
      }
      // JSON is the completion marker and is always published after the optional PNG.
      stagedJson.publish();
      complete = !shouldStop(cancelled);
    } finally {
      finishWrite(complete);
    }
  }

  /** Called on the server thread only after successful writing and before the success message. */
  synchronized boolean commit() {
    if (!ready || cancelled) {
      cleanup();
      return false;
    }
    committed = true;
    discard(stagedJson);
    discard(stagedPreview);
    return true;
  }

  /** Requests rollback without waiting for a writer that may still be doing disk I/O. */
  synchronized void cleanup() {
    cancelled = true;
    if (!writing) {
      discard(stagedJson);
      discard(stagedPreview);
    }
  }

  private synchronized boolean beginWrite() {
    if (cancelled) {
      return false;
    }
    if (writing || ready || committed) {
      throw new IllegalStateException("Export output has already been written");
    }
    writing = true;
    return true;
  }

  private synchronized boolean shouldStop(BooleanSupplier request) {
    cancelled |= request.getAsBoolean();
    return cancelled;
  }

  private synchronized void finishWrite(boolean complete) {
    writing = false;
    ready = complete;
    if (!complete || cancelled) {
      cleanup();
    }
  }

  private void discard(StagedExportFile file) {
    if (file == null) {
      return;
    }
    try {
      if (committed) {
        file.releaseTemporary();
      } else {
        file.discard();
      }
    } catch (IOException ex) {
      logger.log(Level.WARNING, "Unable to clean export staging file " + file.temporary(), ex);
    }
  }
}
