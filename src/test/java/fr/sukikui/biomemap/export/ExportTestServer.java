package fr.sukikui.biomemap.export;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import org.bukkit.Server;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;

/** Minimal scheduler boundary for lifecycle tests; every operation is explicitly accounted for. */
final class ExportTestServer {

  private final AtomicInteger ids = new AtomicInteger();
  private final Map<Integer, Runnable> timers = new LinkedHashMap<>();
  private final Queue<Runnable> pending = new ConcurrentLinkedQueue<>();
  private final BukkitScheduler scheduler = TestProxy.create(
      BukkitScheduler.class, (proxy, method, args) -> switch (method.getName()) {
        case "runTaskTimer" -> schedule((Plugin) args[0], (Runnable) args[1], true);
        case "runTask" -> schedule((Plugin) args[0], (Runnable) args[1], false);
        case "cancelTask" -> {
          timers.remove((Integer) args[0]);
          yield null;
        }
        default -> throw new AssertionError("Unexpected scheduler call: " + method.getName());
      });
  private final PluginManager manager = TestProxy.create(
      PluginManager.class, (proxy, method, args) -> switch (method.getName()) {
        case "getPermissionSubscriptions", "getDefaultPermSubscriptions" -> Set.of();
        case "recalculatePermissionDefaults" -> null;
        default -> throw new AssertionError("Unexpected permissions call: " + method.getName());
      });
  private final Server server = TestProxy.create(
      Server.class, (proxy, method, args) -> switch (method.getName()) {
        case "getName", "getVersion", "getBukkitVersion" -> "BiomeMapTest";
        case "getLogger" -> Logger.getLogger("BiomeMapTestServer");
        case "getScheduler" -> scheduler;
        case "getPluginManager" -> manager;
        case "getWorldContainer" -> new java.io.File(".");
        default -> throw new AssertionError("Unexpected server call: " + method.getName());
      });

  Server server() {
    return server;
  }

  void tick() {
    new ArrayList<>(timers.values()).forEach(Runnable::run);
    int count = pending.size();
    for (int index = 0; index < count; index++) {
      Runnable action = pending.poll();
      if (action != null) {
        action.run();
      }
    }
  }

  void reset() {
    timers.clear();
    pending.clear();
  }

  private BukkitTask schedule(Plugin plugin, Runnable action, boolean repeat) {
    int id = ids.incrementAndGet();
    if (repeat) {
      timers.put(id, action);
    } else {
      pending.add(action);
    }
    return TestProxy.create(BukkitTask.class, (proxy, method, args) -> switch (method.getName()) {
      case "getTaskId" -> id;
      case "getOwner" -> plugin;
      case "isSync" -> true;
      case "isCancelled" -> !timers.containsKey(id);
      default -> throw new AssertionError("Unexpected task call: " + method.getName());
    });
  }
}
