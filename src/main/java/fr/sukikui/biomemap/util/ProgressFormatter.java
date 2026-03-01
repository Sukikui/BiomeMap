package fr.sukikui.biomemap.util;

import java.util.Locale;

/**
 * Shared helpers to compute and render export progress consistently.
 */
public final class ProgressFormatter {

  public static final int DEFAULT_BAR_WIDTH = 20;

  private ProgressFormatter() {
  }

  /**
   * Computes percentage and ETA from chunk counters and elapsed duration.
   */
  public static ProgressSnapshot calculate(long completedChunks, long totalChunks, long elapsedMs) {
    long safeCompletedChunks = Math.max(0L, completedChunks);
    long safeTotalChunks = Math.max(0L, totalChunks);
    long safeElapsedMs = Math.max(0L, elapsedMs);

    double progressPercent;
    if (safeTotalChunks <= 0L) {
      progressPercent = 100.0;
    } else {
      progressPercent = (safeCompletedChunks * 100.0) / safeTotalChunks;
    }
    progressPercent = Math.max(0.0, Math.min(100.0, progressPercent));

    long etaMs = -1L;
    if (safeCompletedChunks > 0L && safeCompletedChunks < safeTotalChunks) {
      double msPerChunk = safeElapsedMs / (double) safeCompletedChunks;
      etaMs = (long) (msPerChunk * (safeTotalChunks - safeCompletedChunks));
    }

    return new ProgressSnapshot(
        safeCompletedChunks,
        safeTotalChunks,
        progressPercent,
        safeElapsedMs,
        etaMs);
  }

  /**
   * Renders the standard colored chat progress line.
   */
  public static String formatChatLine(
      double progressPercent,
      long completedChunks,
      long totalChunks,
      long elapsedMs,
      long etaMs,
      int barWidth) {
    return String.format(
        Locale.ROOT,
        "Progress=§f%s§7 §f%.1f%%§7 chunks=§f%d/%d§7 elapsed=§f%s§7 eta=§f%s",
        buildProgressBar(progressPercent, barWidth),
        progressPercent,
        completedChunks,
        totalChunks,
        formatDuration(elapsedMs),
        formatEta(etaMs));
  }

  /**
   * Renders the plain progress line for logger output.
   */
  public static String formatPlainLine(
      double progressPercent,
      long completedChunks,
      long totalChunks,
      long elapsedMs,
      long etaMs,
      int barWidth) {
    return String.format(
        Locale.ROOT,
        "Progress=%s %.1f%% chunks=%d/%d elapsed=%s eta=%s",
        buildProgressBar(progressPercent, barWidth),
        progressPercent,
        completedChunks,
        totalChunks,
        formatDuration(elapsedMs),
        formatEta(etaMs));
  }

  /**
   * Formats elapsed or remaining duration.
   */
  public static String formatDuration(long durationMs) {
    long totalSeconds = Math.max(0L, durationMs / 1000L);
    long hours = totalSeconds / 3600L;
    long minutes = (totalSeconds % 3600L) / 60L;
    long seconds = totalSeconds % 60L;
    if (hours > 0) {
      return String.format(Locale.ROOT, "%dh %02dm %02ds", hours, minutes, seconds);
    }
    if (minutes > 0) {
      return String.format(Locale.ROOT, "%dm %02ds", minutes, seconds);
    }
    return String.format(Locale.ROOT, "%ds", seconds);
  }

  /**
   * Builds an ASCII progress bar from 0 to 100%.
   */
  public static String buildProgressBar(double percent, int width) {
    int safeWidth = Math.max(1, width);
    double bounded = Math.max(0.0, Math.min(100.0, percent));
    int filled = (int) Math.round((bounded / 100.0) * safeWidth);
    filled = Math.max(0, Math.min(safeWidth, filled));
    return "[" + "#".repeat(filled) + "-".repeat(safeWidth - filled) + "]";
  }

  private static String formatEta(long etaMs) {
    return etaMs < 0 ? "n/a" : formatDuration(etaMs);
  }

  /**
   * Immutable calculation payload for progress rendering.
   */
  public record ProgressSnapshot(
      long completedChunks,
      long totalChunks,
      double progressPercent,
      long elapsedMs,
      long etaMs) {
  }
}
