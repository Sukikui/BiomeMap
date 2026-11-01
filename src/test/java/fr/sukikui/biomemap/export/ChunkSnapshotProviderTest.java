package fr.sukikui.biomemap.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.World;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ChunkSnapshotProviderTest {

  @ParameterizedTest
  @EnumSource(MissingChunkPolicy.class)
  void passesTheGenerationFlagAndDistinguishesMissingTerrain(MissingChunkPolicy policy) {
    List<Boolean> flags = new ArrayList<>();
    World world = TestProxy.create(World.class, (proxy, method, args) -> {
      assertEquals("getChunkAtAsync", method.getName());
      assertEquals(-2, args[0]);
      assertEquals(3, args[1]);
      flags.add((Boolean) args[2]);
      return CompletableFuture.completedFuture(null);
    });
    ChunkSnapshotProvider provider = new ChunkSnapshotProvider(world, policy, action -> {
      throw new AssertionError("Missing terrain must not schedule a snapshot");
    });
    if (policy == MissingChunkPolicy.GENERATE) {
      assertThrows(CompletionException.class, () -> provider.snapshotAt(-2, 3).join());
    } else {
      assertNull(provider.snapshotAt(-2, 3).join());
    }
    assertEquals(List.of(policy == MissingChunkPolicy.GENERATE), flags);
    assertEquals(1, provider.getRequestedSnapshots());
  }

  @Test
  void capturesAnExistingChunkOnTheMainThreadDispatcher() {
    ChunkSnapshot snapshot = TestProxy.create(ChunkSnapshot.class, (proxy, method, args) -> {
      throw new AssertionError("Snapshot should only be returned");
    });
    Chunk chunk = TestProxy.create(Chunk.class, (proxy, method, args) -> {
      assertEquals("getChunkSnapshot", method.getName());
      assertEquals(List.of(true, true, false), List.of(args));
      return snapshot;
    });
    World world = TestProxy.create(World.class,
        (proxy, method, args) -> CompletableFuture.completedFuture(chunk));
    List<Runnable> mainThread = new ArrayList<>();
    ChunkSnapshotProvider provider = new ChunkSnapshotProvider(
        world, MissingChunkPolicy.UNKNOWN, mainThread::add);
    CompletableFuture<ChunkSnapshot> result = provider.snapshotAt(0, 0);
    assertFalse(result.isDone());
    mainThread.getFirst().run();
    assertSame(snapshot, result.join());
  }

  @Test
  void propagatesRealLoadingAndDispatcherFailuresAsErrors() {
    RuntimeException failure = new IllegalStateException("disk read failed");
    World world = TestProxy.create(World.class,
        (proxy, method, args) -> CompletableFuture.failedFuture(failure));
    ChunkSnapshotProvider provider = new ChunkSnapshotProvider(
        world, MissingChunkPolicy.UNKNOWN, Runnable::run);
    CompletionException thrown = assertThrows(CompletionException.class,
        () -> provider.snapshotAt(0, 0).join());
    assertSame(failure, thrown.getCause());

    World throwingWorld = TestProxy.create(World.class, (proxy, method, args) -> {
      throw failure;
    });
    assertTrue(new ChunkSnapshotProvider(throwingWorld, MissingChunkPolicy.ERROR, Runnable::run)
        .snapshotAt(0, 0).isCompletedExceptionally());
  }

  @Test
  void snapshotAndDispatchExceptionsCannotLeaveTheFuturePending() {
    RuntimeException failure = new IllegalStateException("snapshot unavailable");
    Chunk chunk = TestProxy.create(Chunk.class, (proxy, method, args) -> {
      throw failure;
    });
    World world = TestProxy.create(World.class,
        (proxy, method, args) -> CompletableFuture.completedFuture(chunk));
    assertTrue(new ChunkSnapshotProvider(world, MissingChunkPolicy.UNKNOWN, Runnable::run)
        .snapshotAt(0, 0).isCompletedExceptionally());
    assertTrue(new ChunkSnapshotProvider(world, MissingChunkPolicy.UNKNOWN, action -> {
      throw new IllegalStateException("scheduler unavailable");
    }).snapshotAt(0, 0).isCompletedExceptionally());
  }
}
