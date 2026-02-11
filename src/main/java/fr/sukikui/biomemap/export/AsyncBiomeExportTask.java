package fr.sukikui.biomemap.export;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import fr.sukikui.biomemap.export.BiomeExporter.BiomeCell;
import fr.sukikui.biomemap.export.BiomeExporter.BiomeMapExport;
import fr.sukikui.biomemap.export.BiomeExporter.Point;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.ChunkSnapshot;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.RemoteConsoleCommandSender;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Processes chunk-level biome sampling and aggregates results per cell.
 */
@SuppressFBWarnings("EI_EXPOSE_REP2")
public final class AsyncBiomeExportTask extends BukkitRunnable {

  private static final int CHUNK_SIZE = 16;
  private static final String CHAT_PREFIX = "§8[§b§lBiomeMap§8] §r";

  private final Plugin plugin;
  private final BiomeExporter exporter;
  private final ChunkSnapshotProvider snapshotProvider;
  private final World world;
  private final int cellSize;
  private final int width;
  private final int height;
  private final int originX;
  private final int originZ;
  private final int selectionMinX;
  private final int selectionMinZ;
  private final int selectionMaxX;
  private final int selectionMaxZ;
  private final File outputFile;
  private final boolean previewEnabled;
  private final CommandSender sender;
  private final Logger logger;
  private final Runnable completionCallback;
  private final BiomeCell[] cells;
  private final boolean subChunkSampling;
  private final int chunksPerCell;
  private final int cellsPerChunk;
  private final int chunkColumns;
  private final int chunkRows;
  private final int chunkStartX;
  private final int chunkStartZ;
  private final String[] chunkBiomes;

  private final int chunksPerTick;
  private final int maxInFlight;
  private final int completionBatchPerTick;
  private final AtomicInteger nextChunkIndex = new AtomicInteger(0);
  private final AtomicInteger inFlight = new AtomicInteger(0);
  private final AtomicLong chunksCompleted = new AtomicLong(0);
  private final long totalChunks;
  private final int progressInterval;

  private final Queue<ChunkCompletion> completedChunks = new ConcurrentLinkedQueue<>();
  private final AtomicBoolean aggregating = new AtomicBoolean(false);
  private final AtomicBoolean finishing = new AtomicBoolean(false);
  private final AtomicBoolean stopRequested = new AtomicBoolean(false);
  private final AtomicBoolean completionNotified = new AtomicBoolean(false);
  private final long startTimeMs = System.currentTimeMillis();
  private volatile File outputPreviewFile;

  /**
   * Creates a new asynchronous export task that pipelines sampling work off the server thread.
   */
  public AsyncBiomeExportTask(
      Plugin plugin,
      BiomeExporter exporter,
      World world,
      int cellSize,
      int width,
      int height,
      int originX,
      int originZ,
      int selectionMinX,
      int selectionMinZ,
      int selectionMaxX,
      int selectionMaxZ,
      File outputFile,
      boolean previewEnabled,
      CommandSender sender,
      Logger logger,
      Runnable completionCallback,
      int chunksPerTick,
      int maxInFlight,
      int maxConcurrentChunks) {
    this.plugin = plugin;
    this.exporter = exporter;
    this.snapshotProvider = new ChunkSnapshotProvider(plugin, world, maxConcurrentChunks);
    this.world = world;
    this.cellSize = cellSize;
    this.width = width;
    this.height = height;
    this.originX = originX;
    this.originZ = originZ;
    this.selectionMinX = selectionMinX;
    this.selectionMinZ = selectionMinZ;
    this.selectionMaxX = selectionMaxX;
    this.selectionMaxZ = selectionMaxZ;
    this.outputFile = outputFile;
    this.previewEnabled = previewEnabled;
    this.sender = sender;
    this.logger = logger;
    this.completionCallback = completionCallback;
    this.cells = new BiomeCell[width * height];
    this.chunksPerTick = Math.max(1, chunksPerTick);
    int desiredMaxInFlight = Math.max(this.chunksPerTick, maxInFlight);
    this.maxInFlight = desiredMaxInFlight;
    this.completionBatchPerTick = Math.max(1, this.chunksPerTick * 2);
    this.subChunkSampling = cellSize < CHUNK_SIZE;
    this.chunksPerCell = subChunkSampling ? 1 : Math.max(1, cellSize / CHUNK_SIZE);
    this.cellsPerChunk = subChunkSampling ? Math.max(1, CHUNK_SIZE / cellSize) : 1;
    if (subChunkSampling) {
      this.chunkColumns = Math.max(1, (width + cellsPerChunk - 1) / cellsPerChunk);
      this.chunkRows = Math.max(1, (height + cellsPerChunk - 1) / cellsPerChunk);
    } else {
      this.chunkColumns = width * chunksPerCell;
      this.chunkRows = height * chunksPerCell;
    }
    this.chunkStartX = Math.floorDiv(originX, CHUNK_SIZE);
    this.chunkStartZ = Math.floorDiv(originZ, CHUNK_SIZE);
    this.chunkBiomes = new String[chunkColumns * chunkRows];
    this.totalChunks = chunkBiomes.length;
    this.progressInterval = Math.max(1, chunkBiomes.length / 10);
  }

  /**
   * Each tick, queue additional chunk samples while respecting the concurrency budget.
   */
  @Override
  public void run() {
    drainCompletedChunks();
    if (stopRequested.get() || finishing.get() || aggregating.get() || isCancelled()) {
      return;
    }

    int scheduledThisTick = 0;
    while (scheduledThisTick < chunksPerTick && inFlight.get() < maxInFlight) {
      int next = nextChunkIndex.get();
      if (next >= chunkBiomes.length) {
        break;
      }
      if (!snapshotProvider.tryReserveChunkPermit()) {
        break;
      }
      int chunkIndex = nextChunkIndex.getAndIncrement();
      if (chunkIndex >= chunkBiomes.length) {
        snapshotProvider.releaseChunkPermit();
        break;
      }
      scheduleChunk(chunkIndex);
      scheduledThisTick++;
    }

    if (nextChunkIndex.get() >= chunkBiomes.length && inFlight.get() == 0) {
      if (subChunkSampling) {
        finishExport();
      } else {
        startAggregation();
      }
    }
  }

  /**
   * Stops the export and removes any generated files.
   */
  public void cancelAndCleanup() {
    stopRequested.set(true);
    cancel();
    cleanupOutputFiles();
    finishTask();
  }

  private void scheduleChunk(int chunkIndex) {
    int row = chunkIndex / chunkColumns;
    int col = chunkIndex % chunkColumns;
    int chunkX = chunkStartX + col;
    int chunkZ = chunkStartZ + row;
    inFlight.incrementAndGet();
    snapshotProvider.snapshotAt(chunkX, chunkZ)
        .thenApply(this::resolveChunkSample)
        .whenComplete((sample, error) -> completedChunks.add(
            new ChunkCompletion(chunkIndex, sample, chunkX, chunkZ, error)));
  }

  private ChunkSample resolveChunkSample(ChunkSnapshot snapshot) {
    if (snapshot == null) {
      return new ChunkSample("minecraft:unknown", null);
    }
    if (subChunkSampling) {
      String[] subCellBiomes = new String[cellsPerChunk * cellsPerChunk];
      int index = 0;
      for (int cellRow = 0; cellRow < cellsPerChunk; cellRow++) {
        int localMinZ = cellRow * cellSize;
        for (int cellCol = 0; cellCol < cellsPerChunk; cellCol++) {
          int localMinX = cellCol * cellSize;
          subCellBiomes[index++] = resolveCellBiome(snapshot, localMinX, localMinZ);
        }
      }
      return new ChunkSample(null, subCellBiomes);
    }
    int localX = 8;
    int localZ = 8;
    int highestY = snapshot.getHighestBlockYAt(localX, localZ);
    Biome biome = snapshot.getBiome(localX, highestY, localZ);
    return new ChunkSample(BiomeExporter.biomeKey(biome), null);
  }

  private String resolveCellBiome(ChunkSnapshot snapshot, int localMinX, int localMinZ) {
    Map<String, Integer> counts = new HashMap<>();
    int[][] offsets = new int[][] {
        {cellSize / 2, cellSize / 2},
        {0, 0},
        {cellSize - 1, 0},
        {0, cellSize - 1},
        {cellSize - 1, cellSize - 1},
    };
    for (int[] offset : offsets) {
      int sampleX = localMinX + offset[0];
      int sampleZ = localMinZ + offset[1];
      int y = snapshot.getHighestBlockYAt(sampleX, sampleZ);
      Biome biome = snapshot.getBiome(sampleX, y, sampleZ);
      String biomeKey = BiomeExporter.biomeKey(biome);
      counts.merge(biomeKey, 1, Integer::sum);
    }

    return counts.entrySet().stream()
        .max(Map.Entry.<String, Integer>comparingByValue()
            .thenComparing(Map.Entry::getKey))
        .map(Map.Entry::getKey)
        .orElse("minecraft:unknown");
  }

  private void fillCellsFromChunk(int chunkX, int chunkZ, String[] subCellBiomes) {
    int baseCol = Math.floorDiv((chunkX * CHUNK_SIZE) - originX, cellSize);
    int baseRow = Math.floorDiv((chunkZ * CHUNK_SIZE) - originZ, cellSize);
    int index = 0;
    for (int subRow = 0; subRow < cellsPerChunk; subRow++) {
      int cellRow = baseRow + subRow;
      for (int subCol = 0; subCol < cellsPerChunk; subCol++) {
        int cellCol = baseCol + subCol;
        String biomeId = subCellBiomes[index++];
        if (cellRow < 0 || cellRow >= height || cellCol < 0 || cellCol >= width) {
          continue;
        }
        if (biomeId == null) {
          biomeId = "minecraft:unknown";
        }
        int cellIndex = (cellRow * width) + cellCol;
        int cellMinX = originX + (cellCol * cellSize);
        int cellMinZ = originZ + (cellRow * cellSize);
        int cellMaxX = cellMinX + cellSize - 1;
        int cellMaxZ = cellMinZ + cellSize - 1;
        BiomeCell.Bounds bounds =
            new BiomeCell.Bounds(new Point(cellMinX, cellMinZ), new Point(cellMaxX, cellMaxZ));
        cells[cellIndex] = new BiomeCell(cellCol, cellRow, bounds, biomeId);
      }
    }
  }

  private void handleChunkCompletion(ChunkCompletion completion) {
    snapshotProvider.releaseChunkPermit();
    final int remaining = inFlight.updateAndGet(value -> Math.max(0, value - 1));
    if (stopRequested.get()) {
      return;
    }

    ChunkSample sample = completion.sample;
    if (subChunkSampling) {
      String[] subCellBiomes = sample != null ? sample.subCellBiomes() : null;
      if (completion.error != null || subCellBiomes == null) {
        logger.log(Level.WARNING,
            String.format("Failed to resolve chunk biomes at (%d,%d).",
                completion.chunkX, completion.chunkZ),
            completion.error);
        subCellBiomes = new String[cellsPerChunk * cellsPerChunk];
        Arrays.fill(subCellBiomes, "minecraft:unknown");
      }
      fillCellsFromChunk(completion.chunkX, completion.chunkZ, subCellBiomes);
    } else {
      String biomeId = sample != null ? sample.biome() : null;
      if (completion.error != null || biomeId == null) {
        logger.log(Level.WARNING,
            String.format("Failed to resolve chunk biome at (%d,%d).",
                completion.chunkX, completion.chunkZ),
            completion.error);
        biomeId = "minecraft:unknown";
      }
      chunkBiomes[completion.chunkIndex] = biomeId;
    }
    long completed = chunksCompleted.incrementAndGet();

    if (completed % progressInterval == 0 || completed == totalChunks) {
      reportProgress(completed);
    }

    if (nextChunkIndex.get() >= chunkBiomes.length && remaining == 0) {
      if (subChunkSampling) {
        finishExport();
      } else {
        startAggregation();
      }
    }
  }

  private void reportProgress(long completedChunks) {
    double percent = (completedChunks * 100.0) / totalChunks;
    sendInfo(String.format(
        Locale.ROOT,
        "Progress §f§l%.1f%%§7 (§f%d/%d§7 chunks)",
        percent,
        completedChunks,
        totalChunks));
    if (!isConsoleSender()) {
      String plainLine = String.format(
          Locale.ROOT, "Progress %.1f%% (%d/%d chunks)", percent, completedChunks, totalChunks);
      logger.info(plainLine);
    }
  }

  private void startAggregation() {
    if (stopRequested.get()) {
      finishTask();
      return;
    }
    if (!aggregating.compareAndSet(false, true)) {
      return;
    }
    CompletableFuture
        .runAsync(this::buildCellsFromChunks)
        .whenComplete((ignored, error) -> Bukkit.getScheduler().runTask(
            plugin,
            () -> handleAggregationResult(error)));
  }

  private void buildCellsFromChunks() {
    Map<String, Integer> counts = new HashMap<>();
    for (int cellIndex = 0; cellIndex < cells.length; cellIndex++) {
      if (stopRequested.get()) {
        return;
      }
      counts.clear();
      int cellRow = cellIndex / width;
      int cellCol = cellIndex % width;
      int chunkBaseRow = cellRow * chunksPerCell;
      int chunkBaseCol = cellCol * chunksPerCell;
      for (int dz = 0; dz < chunksPerCell; dz++) {
        int rowStart = (chunkBaseRow + dz) * chunkColumns;
        for (int dx = 0; dx < chunksPerCell; dx++) {
          int idx = rowStart + chunkBaseCol + dx;
          String biomeId = idx >= 0 && idx < chunkBiomes.length ? chunkBiomes[idx] : null;
          if (biomeId == null) {
            biomeId = "minecraft:unknown";
          }
          counts.merge(biomeId, 1, Integer::sum);
        }
      }
      String dominant = counts.entrySet().stream()
          .max(Map.Entry.<String, Integer>comparingByValue()
              .thenComparing(Map.Entry::getKey))
          .map(Map.Entry::getKey)
          .orElse("minecraft:unknown");

      int cellMinX = originX + (cellCol * cellSize);
      int cellMinZ = originZ + (cellRow * cellSize);
      int cellMaxX = cellMinX + cellSize - 1;
      int cellMaxZ = cellMinZ + cellSize - 1;
      BiomeCell.Bounds bounds =
          new BiomeCell.Bounds(new Point(cellMinX, cellMinZ), new Point(cellMaxX, cellMaxZ));
      cells[cellIndex] = new BiomeCell(cellCol, cellRow, bounds, dominant);
    }
  }

  private void ensureCellsFilled() {
    for (int cellIndex = 0; cellIndex < cells.length; cellIndex++) {
      if (cells[cellIndex] != null) {
        continue;
      }
      int cellRow = cellIndex / width;
      int cellCol = cellIndex % width;
      int cellMinX = originX + (cellCol * cellSize);
      int cellMinZ = originZ + (cellRow * cellSize);
      int cellMaxX = cellMinX + cellSize - 1;
      int cellMaxZ = cellMinZ + cellSize - 1;
      BiomeCell.Bounds bounds =
          new BiomeCell.Bounds(new Point(cellMinX, cellMinZ), new Point(cellMaxX, cellMaxZ));
      cells[cellIndex] = new BiomeCell(cellCol, cellRow, bounds, "minecraft:unknown");
    }
  }

  private void handleAggregationResult(Throwable error) {
    if (stopRequested.get()) {
      cleanupOutputFiles();
      finishTask();
      return;
    }
    if (error != null) {
      logger.log(Level.SEVERE, "Failed to aggregate cells from chunk data", error);
      sendError("Failed to aggregate cell biomes: " + error.getMessage());
      cancel();
      finishTask();
      return;
    }
    finishExport();
  }

  private void finishExport() {
    if (stopRequested.get()) {
      cleanupOutputFiles();
      finishTask();
      return;
    }
    if (!finishing.compareAndSet(false, true)) {
      return;
    }
    cancel();
    if (subChunkSampling) {
      ensureCellsFilled();
    }
    BiomeMapExport export =
        new BiomeMapExport(
            cellSize,
            new Point(selectionMinX, selectionMinZ),
            new Point(selectionMaxX, selectionMaxZ),
            new Point(originX, originZ),
            width,
            height,
            Arrays.asList(cells));
    outputPreviewFile = previewEnabled ? exporter.resolvePreviewOutput(outputFile) : null;
    long requestedChunks = snapshotProvider.getRequestedSnapshots();
    CompletableFuture
        .runAsync(() -> {
          try {
            exporter.writeExport(export, outputFile);
            if (outputPreviewFile != null) {
              exporter.writePreview(export, this.outputPreviewFile);
            }
          } catch (IOException ex) {
            throw new RuntimeException(ex);
          }
        })
        .whenComplete((ignored, error) -> Bukkit.getScheduler().runTask(
            plugin,
            () -> handleExportWriteResult(error, requestedChunks, this.outputPreviewFile)));
  }

  private void handleExportWriteResult(
      Throwable error, long requestedChunks, File outputPreviewFile) {
    try {
      if (stopRequested.get()) {
        cleanupOutputFiles();
        return;
      }
      long durationMs = System.currentTimeMillis() - startTimeMs;
      if (error == null) {
        double durationSeconds = durationMs / 1000.0;
        String jsonPath = toLogPath(outputFile);
        String pngPath = outputPreviewFile == null ? null : toLogPath(outputPreviewFile);
        String summary = String.format(
            Locale.ROOT,
            "Export complete: world=%s cells=%d chunks=%d requested=%d duration=%.2fs "
                + "json=%s%s",
            world.getName(),
            cells.length,
            totalChunks,
            requestedChunks,
            durationSeconds,
            jsonPath,
            pngPath == null ? "" : " png=" + pngPath);
        if (isConsoleSender()) {
          sendSuccess(String.format(
              Locale.ROOT,
              "Export complete in %.2fs (%d cells).",
              durationSeconds,
              cells.length));
          sendInfo("JSON: §f" + jsonPath);
          if (pngPath != null) {
            sendInfo("PNG: §f" + pngPath);
          }
        } else {
          logger.info(summary);
          sendSuccess(String.format(
              Locale.ROOT,
              "Export complete in %.2fs (%d cells).",
              durationSeconds,
              cells.length));
          sendInfo("JSON: §f" + jsonPath);
          if (pngPath != null) {
            sendInfo("PNG: §f" + pngPath);
          }
        }
      } else {
        Throwable root = error instanceof RuntimeException && error.getCause() != null
            ? error.getCause()
            : error;
        cleanupOutputFiles();
        logger.log(Level.SEVERE, "Failed to write biome export", root);
        sendError("Failed to write biome export: " + root.getMessage());
      }
    } finally {
      finishTask();
    }
  }

  private void drainCompletedChunks() {
    int processed = 0;
    ChunkCompletion completion;
    while (processed < completionBatchPerTick
        && (completion = completedChunks.poll()) != null) {
      handleChunkCompletion(completion);
      processed++;
    }
  }

  private record ChunkSample(String biome, String[] subCellBiomes) {
  }

  private record ChunkCompletion(
      int chunkIndex, ChunkSample sample, int chunkX, int chunkZ, Throwable error) {
  }

  private void sendInfo(String message) {
    sender.sendMessage(CHAT_PREFIX + "§7" + message);
  }

  private void sendSuccess(String message) {
    sender.sendMessage(CHAT_PREFIX + "§a§l" + message);
  }

  private void sendError(String message) {
    sender.sendMessage(CHAT_PREFIX + "§c§lError: §c" + message);
  }

  private boolean isConsoleSender() {
    return sender instanceof ConsoleCommandSender || sender instanceof RemoteConsoleCommandSender;
  }

  private String toLogPath(File file) {
    Path absolute = file.toPath().toAbsolutePath().normalize();
    Path serverRoot = plugin.getServer().getWorldContainer().toPath().toAbsolutePath().normalize();
    Path rootParent = serverRoot.getParent();
    if (rootParent != null && absolute.startsWith(rootParent)) {
      return rootParent.relativize(absolute).toString().replace('\\', '/');
    }
    if (absolute.startsWith(serverRoot)) {
      String relative = serverRoot.relativize(absolute).toString().replace('\\', '/');
      return serverRoot.getFileName() + "/" + relative;
    }
    return absolute.toString().replace('\\', '/');
  }

  private void finishTask() {
    snapshotProvider.reset();
    if (completionNotified.compareAndSet(false, true)) {
      completionCallback.run();
    }
  }

  private void cleanupOutputFiles() {
    deleteIfExists(outputFile);
    if (!previewEnabled) {
      return;
    }
    File previewFile = outputPreviewFile;
    if (previewFile == null) {
      try {
        previewFile = exporter.resolvePreviewOutput(outputFile);
      } catch (IllegalArgumentException ex) {
        logger.log(Level.FINE, "Unable to resolve preview path during cancellation", ex);
      }
    }
    deleteIfExists(previewFile);
  }

  private void deleteIfExists(File file) {
    if (file == null) {
      return;
    }
    try {
      Files.deleteIfExists(file.toPath());
    } catch (IOException ex) {
      logger.log(Level.FINE, "Unable to delete cancelled export file " + file, ex);
    }
  }
}
