package fr.sukikui.biomemap.export;

import fr.sukikui.biomemap.util.ProgressFormatter;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** Tracks bounded progress updates and the compact chunk completion map for each phase. */
final class ExportProgress {

  private final int chunkColumns;
  private final int chunkRows;
  private final int totalChunks;
  private final BitSet chunkCompletedMap = new BitSet();
  private final Consumer<ProgressFormatter.ProgressSnapshot> reporter;
  private int completed;
  private int nextPercent = 10;
  private long phaseStart = System.currentTimeMillis();
  private long lastReport = phaseStart;
  private boolean reported;

  ExportProgress(ExportGrid grid, Consumer<ProgressFormatter.ProgressSnapshot> reporter) {
    chunkColumns = grid.chunkColumns();
    chunkRows = grid.chunkRows();
    totalChunks = grid.totalChunks();
    this.reporter = reporter;
  }

  void mark(int index) {
    chunkCompletedMap.set(index);
    completed++;
  }

  void reset() {
    chunkCompletedMap.clear();
    completed = 0;
    nextPercent = 10;
    phaseStart = System.currentTimeMillis();
    lastReport = phaseStart;
    reported = false;
  }

  ProgressFormatter.ProgressSnapshot snapshot() {
    return ProgressFormatter.calculate(completed, totalChunks,
        System.currentTimeMillis() - phaseStart);
  }

  void reportIfDue() {
    long now = System.currentTimeMillis();
    ProgressFormatter.ProgressSnapshot progress = snapshot();
    boolean threshold = false;
    while (nextPercent <= 100 && progress.progressPercent() + 0.000001 >= nextPercent) {
      nextPercent += 10;
      threshold = true;
    }
    long interval = reported ? TimeUnit.MINUTES.toMillis(5) : TimeUnit.SECONDS.toMillis(10);
    if (threshold || (completed < totalChunks && now - lastReport >= interval)) {
      reporter.accept(progress);
      reported = true;
      lastReport = now;
    }
  }

  /**
   * Renders a compact ASCII minimap of chunk completion.
   */
  List<String> render(int maxWidth, int maxHeight) {
    int targetWidth = Math.max(1, maxWidth);
    int targetHeight = Math.max(1, maxHeight);
    int[] size = resolveRenderSize(chunkColumns, chunkRows, targetWidth, targetHeight);
    int widthChars = size[0];
    int heightChars = size[1];

    List<String> lines = new ArrayList<>(heightChars + 2);
    lines.add("+" + "-".repeat(widthChars) + "+");
    for (int y = 0; y < heightChars; y++) {
      int sourceRowStart = (int) Math.floor((y * (double) chunkRows) / heightChars);
      int sourceRowEnd = (int) Math.ceil(((y + 1) * (double) chunkRows) / heightChars);
      sourceRowEnd = Math.min(chunkRows, Math.max(sourceRowEnd, sourceRowStart + 1));

      StringBuilder row = new StringBuilder(widthChars + 2);
      row.append('|');
      for (int x = 0; x < widthChars; x++) {
        int sourceColStart = (int) Math.floor((x * (double) chunkColumns) / widthChars);
        int sourceColEnd = (int) Math.ceil(((x + 1) * (double) chunkColumns) / widthChars);
        sourceColEnd = Math.min(chunkColumns, Math.max(sourceColEnd, sourceColStart + 1));

        int total = 0;
        int done = 0;
        for (int sourceRow = sourceRowStart; sourceRow < sourceRowEnd; sourceRow++) {
          int base = sourceRow * chunkColumns;
          for (int sourceCol = sourceColStart; sourceCol < sourceColEnd; sourceCol++) {
            int index = base + sourceCol;
            if (index < 0 || index >= totalChunks) {
              continue;
            }
            total++;
            if (chunkCompletedMap.get(index)) {
              done++;
            }
          }
        }
        double fill = total == 0 ? 0.0 : done / (double) total;
        row.append(fillChar(fill));
      }
      row.append('|');
      lines.add(row.toString());
    }
    lines.add("+" + "-".repeat(widthChars) + "+");
    return lines;
  }

  private int[] resolveRenderSize(int sourceWidth, int sourceHeight, int maxWidth, int maxHeight) {
    if (sourceWidth <= 0 || sourceHeight <= 0) {
      return new int[] {1, 1};
    }
    int width = Math.min(sourceWidth, maxWidth);
    double aspect = sourceHeight / (double) sourceWidth;
    int height = Math.max(1, (int) Math.round(width * aspect));

    if (height > maxHeight) {
      height = maxHeight;
      width = Math.max(1, (int) Math.round(height / aspect));
      width = Math.min(width, maxWidth);
    }

    width = Math.max(1, Math.min(width, sourceWidth));
    height = Math.max(1, Math.min(height, sourceHeight));
    return new int[] {width, height};
  }

  private char fillChar(double fill) {
    if (fill <= 0.0) {
      return '.';
    }
    if (fill >= 1.0) {
      return '#';
    }
    if (fill < 0.2) {
      return ':';
    }
    if (fill < 0.4) {
      return '-';
    }
    if (fill < 0.6) {
      return '=';
    }
    if (fill < 0.8) {
      return '+';
    }
    return '*';
  }

}
