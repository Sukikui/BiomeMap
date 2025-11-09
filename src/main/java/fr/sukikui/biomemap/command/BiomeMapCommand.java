package fr.sukikui.biomemap.command;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import fr.sukikui.biomemap.export.BiomeExporter;
import fr.sukikui.biomemap.export.BiomeExporter.ExportResult;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

/**
 * Handles the /biomemap command registration, parsing, and tab completion.
 */
@SuppressFBWarnings("EI_EXPOSE_REP2")
public final class BiomeMapCommand implements CommandExecutor, TabCompleter {

  private static final String DEFAULT_WORLD = "world";

  private final BiomeExporter exporter;
  private final Logger logger;
  private final int defaultCellSize;

  /**
   * Creates a handler bound to a {@link BiomeExporter}.
   *
   * @param exporter exporter that performs the heavy lifting
   * @param logger plugin logger used for console output
   * @param defaultCellSize fallback cell size when the user omits it
   */
  public BiomeMapCommand(BiomeExporter exporter, Logger logger, int defaultCellSize) {
    this.exporter = exporter;
    this.logger = logger;
    this.defaultCellSize = defaultCellSize;
  }

  /**
   * Parses `/biomemap` arguments and triggers the export.
   */
  @Override
  public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    String worldName = args.length >= 1 && !args[0].isBlank() ? args[0] : DEFAULT_WORLD;
    int cellSize = defaultCellSize;
    if (args.length >= 2) {
      try {
        cellSize = Integer.parseInt(args[1]);
      } catch (NumberFormatException ex) {
        sender.sendMessage("§cCell size must be a positive integer.");
        return true;
      }
    }

    if (cellSize <= 0) {
      sender.sendMessage("§cCell size must be greater than 0.");
      return true;
    }

    World world = Bukkit.getWorld(worldName);
    if (world == null) {
      sender.sendMessage("§cWorld '" + worldName + "' not found.");
      return true;
    }

    logger.info(
        String.format(
            "[BiomeMap] Exporting biomes for world '%s' (cellSize=%d)...", world.getName(),
            cellSize));
    sender.sendMessage(
        String.format(
            "§a[BiomeMap] Exporting biomes for world '%s' (cellSize=%d)...", world.getName(),
            cellSize));

    ExportResult result;
    try {
      result = exporter.exportWorld(world, cellSize);
    } catch (IOException ex) {
      logger.log(Level.SEVERE, "Failed to write biome export", ex);
      sender.sendMessage("§cFailed to write biome export: " + ex.getMessage());
      return true;
    }

    logger.info(
        String.format(
            "[BiomeMap] Export complete: %d cells saved to biome-map.json (%.2fs).",
            result.cellCount(), result.durationMs() / 1000.0));
    sender.sendMessage(
        String.format(
            "§a[BiomeMap] Export complete: %d cells saved to biome-map.json.",
            result.cellCount()));
    sender.sendMessage("§7Location: " + result.outputFile().getAbsolutePath());
    return true;
  }

  /**
   * Provides suggestions for world names and cell sizes.
   */
  @Override
  public List<String> onTabComplete(
      CommandSender sender, Command command, String alias, String[] args) {
    if (args.length == 1) {
      String prefix = args[0].toLowerCase(Locale.ROOT);
      List<String> suggestions = new ArrayList<>();
      for (World world : Bukkit.getWorlds()) {
        String name = world.getName();
        if (name.toLowerCase(Locale.ROOT).startsWith(prefix)) {
          suggestions.add(name);
        }
      }
      return suggestions;
    } else if (args.length == 2) {
      return List.of("32", "48", "64", "128");
    }
    return Collections.emptyList();
  }
}
