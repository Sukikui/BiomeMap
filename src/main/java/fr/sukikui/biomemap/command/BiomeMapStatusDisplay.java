package fr.sukikui.biomemap.command;

import fr.sukikui.biomemap.export.AsyncBiomeExportTask;
import fr.sukikui.biomemap.util.ProgressFormatter;
import java.util.Locale;
import java.util.function.Consumer;

/** Renders the status and optional completion map for an authorized command sender. */
final class BiomeMapStatusDisplay {

  private static final int PLAYER_STATUS_MAP_MAX_WIDTH = 28;
  private static final int PLAYER_STATUS_MAP_MAX_HEIGHT = 10;
  private static final int CONSOLE_STATUS_MAP_MAX_WIDTH = 56;
  private static final int CONSOLE_STATUS_MAP_MAX_HEIGHT = 18;

  private BiomeMapStatusDisplay() {
  }

  static void show(
      AsyncBiomeExportTask task, boolean showMap, boolean console, Consumer<String> sendInfo) {
    AsyncBiomeExportTask.ExportStatus status = task.snapshotStatus();
    sendInfo.accept("§b§lExport status§7 (running)");
    sendInfo.accept("Phase=§f" + status.phase() + "§7 missing-chunks=§f"
        + status.missingChunkPolicy() + "§7 missing=§f" + status.missingChunks());
    sendInfo.accept(String.format(
        Locale.ROOT,
        "World=§f%s§7 cell=§f%d§7 preview=§f%s",
        status.worldName(), status.cellSize(), status.previewEnabled() ? "on" : "off"));
    sendInfo.accept(String.format(
        Locale.ROOT,
        "Area=§f[%d,%d -> %d,%d]§7 grid=§f%dx%d§7 cells=§f%d",
        status.selectionMinX(), status.selectionMinZ(),
        status.selectionMaxX(), status.selectionMaxZ(),
        status.gridWidth(), status.gridHeight(), status.totalCells()));
    sendInfo.accept(ProgressFormatter.formatChatLine(
        status.progressPercent(), status.completedChunks(), status.totalChunks(),
        status.elapsedMs(), status.etaMs(), ProgressFormatter.DEFAULT_BAR_WIDTH));

    if (status.initiatorName() != null && !status.initiatorName().isBlank()) {
      String initiatorState = status.initiatorOnline() ? "online" : "offline";
      sendInfo.accept(String.format(
          Locale.ROOT, "Initiator=§f%s§7 (%s)", status.initiatorName(), initiatorState));
    }
    sendInfo.accept("JSON=§f" + status.jsonPath());
    if (status.previewPath() != null) {
      sendInfo.accept("PNG=§f" + status.previewPath());
    }
    if (showMap) {
      int maxWidth = console ? CONSOLE_STATUS_MAP_MAX_WIDTH : PLAYER_STATUS_MAP_MAX_WIDTH;
      int maxHeight = console ? CONSOLE_STATUS_MAP_MAX_HEIGHT : PLAYER_STATUS_MAP_MAX_HEIGHT;
      sendInfo.accept(String.format(
          Locale.ROOT, "Selection map (§f%d×%d§7 chunks, compact view):",
          status.mapWidth(), status.mapHeight()));
      for (String line : task.renderProgressAsciiMap(maxWidth, maxHeight)) {
        sendInfo.accept("§f" + line);
      }
    } else {
      sendInfo.accept("Tip: run §f/biomemap status map§7 for ASCII map.");
    }
  }
}
