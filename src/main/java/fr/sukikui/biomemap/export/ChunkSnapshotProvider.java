package fr.sukikui.biomemap.export;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

/**
 * Throttles asynchronous chunk snapshot requests to avoid overwhelming the server.
 */
@SuppressFBWarnings("EI_EXPOSE_REP2")
public final class ChunkSnapshotProvider {

  private final Plugin plugin;
  private final World world;
  private final AtomicLong chunkRequests = new AtomicLong();
  private final int maxConcurrentChunks;
  private final AtomicInteger availableChunkPermits;

  /**
   * Creates a new snapshot provider bound to a world.
   *
   * @param plugin owning plugin (used for scheduling main-thread work)
   * @param world world to load chunks from
   * @param maxConcurrentChunks max number of simultaneous chunk loads
   */
  public ChunkSnapshotProvider(Plugin plugin, World world, int maxConcurrentChunks) {
    this.plugin = plugin;
    this.world = world;
    this.maxConcurrentChunks = Math.max(1, maxConcurrentChunks);
    this.availableChunkPermits = new AtomicInteger(this.maxConcurrentChunks);
  }

  /**
   * Returns a snapshot future for the requested chunk coordinates.
   */
  public CompletableFuture<ChunkSnapshot> snapshotAt(int chunkX, int chunkZ) {
    return loadSnapshot(chunkX, chunkZ);
  }

  /**
   * Number of snapshot requests that have been queued.
   */
  public long getRequestedSnapshots() {
    return chunkRequests.get();
  }

  /**
   * Clears cached futures. Pending loads will still complete, but future lookups will reload.
   */
  public void reset() {
    availableChunkPermits.set(maxConcurrentChunks);
    chunkRequests.set(0);
  }

  /**
   * Attempts to reserve a chunk load permit. Returns false if exhausted.
   */
  public boolean tryReserveChunkPermit() {
    while (true) {
      int current = availableChunkPermits.get();
      if (current <= 0) {
        return false;
      }
      if (availableChunkPermits.compareAndSet(current, current - 1)) {
        return true;
      }
    }
  }

  /**
   * Releases a previously reserved chunk permit.
   */
  public void releaseChunkPermit() {
    int updated = availableChunkPermits.incrementAndGet();
    if (updated > maxConcurrentChunks) {
      availableChunkPermits.set(maxConcurrentChunks);
    }
  }

  private CompletableFuture<ChunkSnapshot> loadSnapshot(int chunkX, int chunkZ) {
    chunkRequests.incrementAndGet();
    CompletableFuture<ChunkSnapshot> result = new CompletableFuture<>();
    world.getChunkAtAsync(chunkX, chunkZ, true)
        .whenComplete((chunk, throwable) -> {
          if (throwable != null) {
            result.completeExceptionally(throwable);
            return;
          }
          Chunk loadedChunk = chunk;
          if (loadedChunk == null) {
            result.completeExceptionally(
                new IllegalStateException("Chunk load returned null for " + chunkX + "," + chunkZ));
            return;
          }
          Bukkit.getScheduler().runTask(
              plugin,
              () -> {
                try {
                  result.complete(loadedChunk.getChunkSnapshot(true, true, false));
                } catch (Throwable snapshotError) {
                  result.completeExceptionally(snapshotError);
                }
              });
        });
    return result;
  }
}
