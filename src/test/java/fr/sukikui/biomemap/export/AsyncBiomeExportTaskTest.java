package fr.sukikui.biomemap.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.Color;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import javax.imageio.ImageIO;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.World;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AsyncBiomeExportTaskTest {

  private static final ExportTestServer SERVER = new ExportTestServer();
  @TempDir
  Path directory;
  private final AtomicInteger finished = new AtomicInteger();
  private final List<String> messages = new ArrayList<>();
  private final List<LogRecord> logs = new ArrayList<>();
  private final Logger logger = Logger.getAnonymousLogger();
  private final AtomicBoolean pluginEnabled = new AtomicBoolean(true);
  private final Plugin plugin = TestProxy.create(Plugin.class, (proxy, method, args) ->
      switch (method.getName()) {
        case "isEnabled" -> pluginEnabled.get();
        case "getServer" -> SERVER.server();
        case "getName" -> "BiomeMap";
        default -> throw new AssertionError("Unexpected plugin call: " + method.getName());
      });

  AsyncBiomeExportTaskTest() {
    logger.setUseParentHandlers(false);
    logger.addHandler(new Handler() {
      @Override
      public void publish(LogRecord record) {
        logs.add(record);
      }

      @Override
      public void flush() {
      }

      @Override
      public void close() {
      }
    });
  }

  @BeforeAll
  static void installServerBoundary() throws ReflectiveOperationException {
    // Skip the real server startup banner, which requires Paper's build-info service.
    java.lang.reflect.Field server = Bukkit.class.getDeclaredField("server");
    server.setAccessible(true);
    server.set(null, SERVER.server());
  }

  @AfterAll
  static void removeServerBoundary() throws ReflectiveOperationException {
    java.lang.reflect.Field server = Bukkit.class.getDeclaredField("server");
    server.setAccessible(true);
    server.set(null, null);
  }

  @AfterEach
  void clearScheduler() {
    SERVER.reset();
  }

  @Test
  void rejectsMissingTerrainBeforeAnyLoadOrOutputAndReleasesTheExport() {
    AtomicInteger checked = new AtomicInteger();
    World world = TestProxy.create(World.class, (proxy, method, args) -> {
      if ("isChunkGenerated".equals(method.getName())) {
        return checked.incrementAndGet() == 1;
      }
      if ("getName".equals(method.getName())) {
        return "world";
      }
      throw new AssertionError("No loading permitted during preflight: " + method.getName());
    });
    AsyncBiomeExportTask task = start(world, MissingChunkPolicy.ERROR, 1, 4, 16, 31);
    SERVER.tick();
    assertEquals("checking chunks", task.snapshotStatus().phase());
    assertEquals(1, task.snapshotStatus().completedChunks());
    assertEquals(0, finished.get());
    SERVER.tick();
    assertEquals(1, finished.get());
    assertFalse(Files.exists(output()));
    assertTrue(messages.stream().anyMatch(message -> message.contains("chunk (1,0)")));
    SERVER.tick();
    assertEquals(2, checked.get());
    assertEquals(1, finished.get());
  }

  @Test
  void cancellationStopsPreflightAndReleasesTheExportExactlyOnce() {
    AtomicInteger checked = new AtomicInteger();
    World world = TestProxy.create(World.class, (proxy, method, args) -> {
      assertEquals("isChunkGenerated", method.getName());
      checked.incrementAndGet();
      return true;
    });
    AsyncBiomeExportTask task = start(world, MissingChunkPolicy.ERROR, 1, 4, 16, 31);
    SERVER.tick();
    task.cancelAndCleanup();
    task.cancelAndCleanup();
    SERVER.tick();
    assertEquals(1, checked.get());
    assertEquals(1, finished.get());
    assertFalse(Files.exists(output()));
  }

  @Test
  void unknownModeWritesJsonAndMagentaPreviewWithoutGenerationOrMissingWarnings()
      throws Exception {
    AtomicInteger loads = new AtomicInteger();
    World world = loadingWorld(loads, false, null);
    start(world, MissingChunkPolicy.UNKNOWN, 4, 4, 32, 0);
    awaitFinished();
    assertEquals(4, loads.get());
    JsonObject json = JsonParser.parseString(Files.readString(output())).getAsJsonObject();
    assertEquals("minecraft:unknown", json.getAsJsonArray("cells").get(0)
        .getAsJsonObject().get("biome").getAsString());
    assertEquals(Color.MAGENTA.getRGB(), ImageIO.read(directory.resolve("map.png").toFile())
        .getRGB(0, 0));
    assertTrue(messages.stream().anyMatch(message -> message.contains("4 missing chunks")));
    assertFalse(logs.stream().anyMatch(record -> record.getLevel().intValue()
        >= Level.WARNING.intValue()));
  }

  @Test
  void generateModeRequestsGenerationAndWritesCompleteExport() throws Exception {
    AtomicInteger loads = new AtomicInteger();
    start(loadingWorld(loads, true, existingChunk()), MissingChunkPolicy.GENERATE, 4, 2, 16, 31);
    awaitFinished();
    assertEquals(2, loads.get());
    JsonObject json = JsonParser.parseString(Files.readString(output())).getAsJsonObject();
    assertEquals(2, json.getAsJsonArray("cells").size());
    assertTrue(messages.stream().anyMatch(message -> message.contains("0 missing chunks")));
  }

  @Test
  void strictModeChecksEverythingBeforeLoadingAndNeverEnablesGeneration() throws Exception {
    AtomicInteger checked = new AtomicInteger();
    AtomicInteger loads = new AtomicInteger();
    World loader = loadingWorld(loads, false, existingChunk());
    World world = TestProxy.create(World.class, (proxy, method, args) -> {
      if ("isChunkGenerated".equals(method.getName())) {
        checked.incrementAndGet();
        assertEquals(0, loads.get());
        return true;
      }
      assertEquals(2, checked.get());
      return method.invoke(loader, args);
    });
    AsyncBiomeExportTask task = start(world, MissingChunkPolicy.ERROR, 1, 4, 16, 31);
    SERVER.tick();
    SERVER.tick();
    assertEquals("sampling", task.snapshotStatus().phase());
    assertEquals(0, task.snapshotStatus().completedChunks());
    assertEquals(0, loads.get());
    awaitFinished();
    assertEquals(2, loads.get());
    assertTrue(Files.exists(output()));
  }

  @Test
  void strictModeAbortsIfTerrainDisappearsAfterPreflight() {
    World world = TestProxy.create(World.class, (proxy, method, args) ->
        switch (method.getName()) {
          case "isChunkGenerated" -> true;
          case "getChunkAtAsync" -> {
            assertEquals(false, args[2]);
            yield CompletableFuture.completedFuture(null);
          }
          default -> throw new AssertionError(method.getName());
        });
    start(world, MissingChunkPolicy.ERROR, 1, 4, 16, 0);
    SERVER.tick();
    SERVER.tick();
    SERVER.tick();
    assertEquals(1, finished.get());
    assertFalse(Files.exists(output()));
    assertTrue(messages.stream().anyMatch(message -> message.contains("after verification")));
  }

  @Test
  void oneConcurrencyLimitBoundsRequestsEvenWhenPerTickRateIsHigher() {
    List<CompletableFuture<Chunk>> loads = new ArrayList<>();
    World world = TestProxy.create(World.class, (proxy, method, args) -> {
      assertEquals("getChunkAtAsync", method.getName());
      CompletableFuture<Chunk> future = new CompletableFuture<>();
      loads.add(future);
      return future;
    });
    final AsyncBiomeExportTask task = start(world, MissingChunkPolicy.UNKNOWN, 8, 2, 16, 63);
    SERVER.tick();
    SERVER.tick();
    assertEquals(2, loads.size());
    loads.getFirst().complete(null);
    SERVER.tick();
    assertEquals(3, loads.size());
    task.cancelAndCleanup();
    loads.forEach(future -> future.complete(null));
    SERVER.tick();
    assertEquals(1, finished.get());
    assertEquals(3, loads.size());
    assertFalse(Files.exists(output()));
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void stopOrDisableAfterPublicationBeforeCallbackRollsBackOutputs(boolean disable)
      throws Exception {
    final AsyncBiomeExportTask task = start(loadingWorld(new AtomicInteger(), false, null),
        MissingChunkPolicy.UNKNOWN, 4, 4, 16, 0);
    SERVER.tick();
    SERVER.tick();
    long deadline = System.nanoTime() + 5_000_000_000L;
    while (!Files.exists(output()) && System.nanoTime() < deadline) {
      Thread.sleep(1);
    }
    assertTrue(Files.exists(output()));
    assertEquals(0, finished.get());
    pluginEnabled.set(!disable);
    task.cancelAndCleanup();
    awaitFinished();
    while (Files.exists(output()) && System.nanoTime() < deadline) {
      Thread.sleep(1);
    }
    assertFalse(Files.exists(output()));
    assertFalse(Files.exists(directory.resolve("map.png")));
    assertTrue(messages.stream().noneMatch(message -> message.contains("Export complete")));
    try (var files = Files.list(directory)) {
      assertEquals(0, files.count());
    }
  }

  private AsyncBiomeExportTask start(
      World world, MissingChunkPolicy policy, int perTick, int inFlight, int cellSize, int maxX) {
    ConsoleCommandSender sender = TestProxy.create(
        ConsoleCommandSender.class, (proxy, method, args) -> {
          if ("sendMessage".equals(method.getName())) {
            messages.add((String) args[0]);
            return null;
          }
          if ("getName".equals(method.getName())) {
            return "CONSOLE";
          }
          throw new AssertionError(method.getName());
        });
    AsyncBiomeExportTask task = new AsyncBiomeExportTask(plugin,
        new BiomeExporter(directory.toFile()), world,
        ExportGrid.fromSelection(0, 0, maxX, 0, cellSize), output().toFile(), true, sender, null,
        logger, finished::incrementAndGet, perTick, inFlight, policy);
    task.runTaskTimer(plugin, 1, 1);
    return task;
  }

  private World loadingWorld(AtomicInteger loads, boolean generate, Chunk chunk) {
    return TestProxy.create(World.class, (proxy, method, args) -> {
      if ("getName".equals(method.getName())) {
        return "world";
      }
      assertEquals("getChunkAtAsync", method.getName());
      assertEquals(generate, args[2]);
      loads.incrementAndGet();
      return CompletableFuture.completedFuture(chunk);
    });
  }

  private Chunk existingChunk() {
    ChunkSnapshot snapshot = TestProxy.create(ChunkSnapshot.class, (proxy, method, args) ->
        switch (method.getName()) {
          case "getHighestBlockYAt" -> 64;
          case "getBiome" -> null;
          default -> throw new AssertionError(method.getName());
        });
    return TestProxy.create(Chunk.class, (proxy, method, args) -> {
      assertEquals("getChunkSnapshot", method.getName());
      return snapshot;
    });
  }

  private Path output() {
    return directory.resolve("map.json");
  }

  private void awaitFinished() throws InterruptedException {
    long deadline = System.nanoTime() + 5_000_000_000L;
    while (finished.get() == 0 && System.nanoTime() < deadline) {
      SERVER.tick();
      Thread.sleep(1);
    }
    assertEquals(1, finished.get());
  }
}
