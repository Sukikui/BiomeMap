package fr.sukikui.biomemap.export;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import org.bukkit.ChunkSnapshot;
import org.bukkit.World;

/** Loads snapshots with an explicit generation policy; the export bounds concurrent requests. */
@SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "The live world is shared by design")
public final class ChunkSnapshotProvider {

  private final World world;
  private final boolean generate;
  private final Consumer<Runnable> mainThread;
  private final AtomicLong chunkRequests = new AtomicLong();

  /** Creates a provider with a main-thread dispatcher for safe snapshot capture. */
  public ChunkSnapshotProvider(
      World world, MissingChunkPolicy policy, Consumer<Runnable> mainThread) {
    this.world = world;
    this.generate = policy == MissingChunkPolicy.GENERATE;
    this.mainThread = mainThread;
  }

  /** Returns null for absent terrain when generation is disabled; failures remain exceptional. */
  public CompletableFuture<ChunkSnapshot> snapshotAt(int chunkX, int chunkZ) {
    chunkRequests.incrementAndGet();
    CompletableFuture<ChunkSnapshot> result = new CompletableFuture<>();
    try {
      world.getChunkAtAsync(chunkX, chunkZ, generate).whenComplete((chunk, error) -> {
        if (error != null) {
          result.completeExceptionally(error);
        } else if (chunk == null) {
          if (generate) {
            result.completeExceptionally(
                new IllegalStateException("Chunk generation returned null"));
          } else {
            result.complete(null);
          }
        } else {
          try {
            mainThread.accept(() -> {
              try {
                result.complete(chunk.getChunkSnapshot(true, true, false));
              } catch (RuntimeException snapshotError) {
                result.completeExceptionally(snapshotError);
              }
            });
          } catch (RuntimeException schedulingError) {
            result.completeExceptionally(schedulingError);
          }
        }
      });
    } catch (RuntimeException loadError) {
      result.completeExceptionally(loadError);
    }
    return result;
  }

  /** Number of load requests, including missing chunks when generation is disabled. */
  public long getRequestedSnapshots() {
    return chunkRequests.get();
  }
}
