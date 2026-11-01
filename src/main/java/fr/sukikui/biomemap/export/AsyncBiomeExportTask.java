package fr.sukikui.biomemap.export;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import fr.sukikui.biomemap.export.BiomeExporter.Point;
import fr.sukikui.biomemap.export.ExportSamples.ChunkSample;
import fr.sukikui.biomemap.util.ProgressFormatter;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

/** Coordinates preflight, bounded chunk sampling, and asynchronous export finalization. */
@SuppressFBWarnings("EI_EXPOSE_REP2")
public final class AsyncBiomeExportTask extends BukkitRunnable {

  private final Plugin plugin;
  private final World world;
  private final ExportGrid grid;
  private final MissingChunkPolicy policy;
  private final ChunkSnapshotProvider snapshots;
  private final ChunkPreflight preflight;
  private final ExportProgress progress;
  private final ExportFeedback feedback;
  private final ExportFiles files;
  private final Logger logger;
  private final Runnable completionCallback;
  private final int chunksPerTick;
  private final int maxInFlight;
  private final long startedAt = System.currentTimeMillis();
  private final Queue<ChunkCompletion> completions = new ConcurrentLinkedQueue<>();
  private final AtomicBoolean stopped = new AtomicBoolean();
  private final AtomicBoolean notified = new AtomicBoolean();
  private ExportSamples samples;
  private String phase;
  private int nextChunk;
  private final AtomicInteger inFlight = new AtomicInteger();
  private final AtomicInteger missingChunks = new AtomicInteger();

  /** Creates a task from geometry already validated before any export storage allocation. */
  public AsyncBiomeExportTask(
      Plugin plugin, BiomeExporter exporter, World world, ExportGrid grid,
      File outputFile, boolean preview, CommandSender sender, UUID playerId,
      Logger logger, Runnable completionCallback, int chunksPerTick, int maxInFlight,
      MissingChunkPolicy policy) {
    this.plugin = plugin;
    this.world = world;
    this.grid = grid;
    this.policy = policy;
    this.logger = logger;
    this.completionCallback = completionCallback;
    this.chunksPerTick = Math.max(1, chunksPerTick);
    this.maxInFlight = Math.max(1, maxInFlight);
    feedback = new ExportFeedback(plugin, sender, playerId, logger);
    files = new ExportFiles(exporter, outputFile, preview, logger);
    snapshots = new ChunkSnapshotProvider(world, policy,
        action -> Bukkit.getScheduler().runTask(plugin, action));
    preflight = new ChunkPreflight(grid);
    phase = policy == MissingChunkPolicy.ERROR ? "checking chunks" : "sampling";
    progress = new ExportProgress(grid, snapshot -> feedback.progress(phase, snapshot));
  }

  @Override
  public void run() {
    if (stopped.get() || notified.get() || "finalizing".equals(phase)) {
      return;
    }
    try {
      if ("checking chunks".equals(phase)) {
        checkTerrain();
        return;
      }
      if (samples == null) {
        samples = new ExportSamples(grid);
      }
      drainCompletions();
      if (stopped.get()) {
        return;
      }
      for (int count = 0; count < chunksPerTick && inFlight.get() < maxInFlight
          && nextChunk < grid.totalChunks(); count++) {
        scheduleChunk(nextChunk++);
      }
      progress.reportIfDue();
      if (nextChunk == grid.totalChunks() && inFlight.get() == 0) {
        finalizeExport();
      }
    } catch (RuntimeException ex) {
      logger.log(Level.SEVERE, "Biome export failed during " + phase, ex);
      fail("Unable to continue export: " + ex.getMessage());
    }
  }

  private void checkTerrain() {
    int previous = preflight.checked();
    Point missing = preflight.advance(chunksPerTick, world::isChunkGenerated);
    for (int index = previous; index < preflight.checked(); index++) {
      progress.mark(index);
    }
    progress.reportIfDue();
    if (missing != null) {
      fail("Export rejected: chunk (" + missing.x() + "," + missing.z()
          + ") is not generated. No export files were created.");
    } else if (preflight.complete()) {
      phase = "sampling";
      progress.reset();
      feedback.info("Chunk verification complete. Starting biome sampling.");
    }
  }

  private void scheduleChunk(int index) {
    Point chunk = grid.chunkAt(index);
    ExportSamples sampler = samples;
    inFlight.incrementAndGet();
    snapshots.snapshotAt(chunk.x(), chunk.z()).thenApply(sampler::sample)
        .whenComplete((sample, error) -> {
          if (!stopped.get()) {
            completions.add(new ChunkCompletion(index, sample, error));
          }
        });
  }

  private void drainCompletions() {
    long budget = (long) chunksPerTick * 2;
    for (long count = 0; count < budget; count++) {
      ChunkCompletion completion = completions.poll();
      if (completion == null) {
        return;
      }
      inFlight.decrementAndGet();
      ChunkSample sample = completion.sample();
      if (completion.error() != null) {
        Point chunk = grid.chunkAt(completion.index());
        logger.log(Level.WARNING, "Failed to sample chunk " + chunk, completion.error());
        if (policy == MissingChunkPolicy.ERROR) {
          fail("Unable to read chunk (" + chunk.x() + "," + chunk.z() + "). Export aborted.");
          return;
        }
        sample = samples.unknown(false);
      }
      if (sample.missing()) {
        if (policy == MissingChunkPolicy.ERROR) {
          Point chunk = grid.chunkAt(completion.index());
          fail("Chunk (" + chunk.x() + "," + chunk.z()
              + ") became unavailable after verification. Export aborted.");
          return;
        }
        missingChunks.incrementAndGet();
      }
      samples.accept(completion.index(), sample);
      progress.mark(completion.index());
    }
  }

  private void finalizeExport() {
    phase = "finalizing";
    ExportSamples sampler = samples;
    CompletableFuture.runAsync(() -> {
      try {
        BiomeExporter.BiomeMapExport export = sampler.build(stopped::get);
        if (export != null) {
          files.write(export, stopped::get);
        }
      } catch (IOException ex) {
        throw new CompletionException(ex);
      } finally {
        if (stopped.get()) {
          files.cleanup();
        }
      }
    }).whenComplete((ignored, error) -> dispatchWriteResult(error));
  }

  private void dispatchWriteResult(Throwable error) {
    if (!plugin.isEnabled()) {
      files.cleanup();
      return;
    }
    try {
      Bukkit.getScheduler().runTask(plugin, () -> handleWriteResult(error));
    } catch (RuntimeException ex) {
      files.cleanup();
      logger.log(Level.FINE, "Unable to deliver export result during plugin shutdown", ex);
    }
  }

  private void handleWriteResult(Throwable error) {
    try {
      if (stopped.get()) {
        files.cleanup();
      } else if (error != null) {
        files.cleanup();
        logger.log(Level.SEVERE, "Failed to write biome export", error);
        feedback.error("Failed to write biome export: " + error.getMessage());
      } else if (files.commit()) {
        feedback.complete(grid, files, System.currentTimeMillis() - startedAt,
            snapshots.getRequestedSnapshots(), missingChunks.get());
      }
    } finally {
      finishTask();
    }
  }

  /** Cancels preflight or sampling immediately; an active writer finishes cleanup before unlock. */
  public void cancelAndCleanup() {
    stopped.set(true);
    files.cleanup();
    cancel();
    if (!"finalizing".equals(phase) || !plugin.isEnabled()) {
      finishTask();
    }
  }

  private void fail(String message) {
    feedback.error(message);
    cancelAndCleanup();
  }

  private void finishTask() {
    if (notified.compareAndSet(false, true)) {
      cancel();
      completions.clear();
      completionCallback.run();
    }
  }

  /** Returns phase-specific progress, including preflight, for the regular status command. */
  public ExportStatus snapshotStatus() {
    ProgressFormatter.ProgressSnapshot snapshot = progress.snapshot();
    return new ExportStatus(world.getName(), grid.selectionMin().x(), grid.selectionMin().z(),
        grid.selectionMax().x(), grid.selectionMax().z(), grid.cellSize(),
        grid.width(), grid.height(), grid.totalCells(), files.preview() != null,
        grid.chunkColumns(), grid.chunkRows(), snapshot.completedChunks(), grid.totalChunks(),
        snapshot.progressPercent(), snapshot.elapsedMs(), snapshot.etaMs(),
        feedback.path(files.json()),
        files.preview() == null ? null : feedback.path(files.preview()),
        feedback.initiatorName(), feedback.initiatorOnline(), phase, policy.configValue(),
        missingChunks.get());
  }

  /** Renders checked chunks during preflight and sampled chunks during export. */
  public List<String> renderProgressAsciiMap(int maxWidth, int maxHeight) {
    return progress.render(maxWidth, maxHeight);
  }

  private record ChunkCompletion(int index, ChunkSample sample, Throwable error) {
  }

  /** Immutable status payload for command rendering. */
  public record ExportStatus(
      String worldName,
      int selectionMinX,
      int selectionMinZ,
      int selectionMaxX,
      int selectionMaxZ,
      int cellSize,
      int gridWidth,
      int gridHeight,
      int totalCells,
      boolean previewEnabled,
      int mapWidth,
      int mapHeight,
      long completedChunks,
      long totalChunks,
      double progressPercent,
      long elapsedMs,
      long etaMs,
      String jsonPath,
      String previewPath,
      String initiatorName,
      boolean initiatorOnline,
      String phase,
      String missingChunkPolicy,
      int missingChunks) {
  }
}
