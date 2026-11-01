package fr.sukikui.biomemap.command;

import static fr.sukikui.biomemap.command.BiomeMapArguments.alignCellSize;
import static fr.sukikui.biomemap.command.BiomeMapArguments.parseInteger;
import static fr.sukikui.biomemap.command.BiomeMapArguments.parsePositiveInteger;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import fr.sukikui.biomemap.export.AsyncBiomeExportTask;
import fr.sukikui.biomemap.export.BiomeExporter;
import fr.sukikui.biomemap.util.GridMath;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.RemoteConsoleCommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Handles the /biomemap command registration, parsing, and tab completion.
 */
@SuppressFBWarnings("EI_EXPOSE_REP2")
public final class BiomeMapCommand implements CommandExecutor, TabCompleter {

  static final String ADMIN_PERMISSION = "biomemap.admin";
  private static final String CHAT_PREFIX = "§8[§b§lBiomeMap§8] §r";

  private final JavaPlugin plugin;
  private final BiomeExporter exporter;
  private final Logger logger;
  private final int defaultCellSize;
  private final int chunksPerTick;
  private final int maxInFlight;
  private final int maxConcurrentChunks;
  private final Map<String, AsyncBiomeExportTask> runningExports = new HashMap<>();

  /**
   * Creates a command handler.
   *
   * @param plugin owning plugin
   * @param exporter exporter providing biome sampling utilities
   * @param logger shared plugin logger
   * @param defaultCellSize fallback cell size if not provided
   */
  public BiomeMapCommand(
      JavaPlugin plugin,
      BiomeExporter exporter,
      Logger logger,
      int defaultCellSize,
      int chunksPerTick,
      int maxInFlight,
      int maxConcurrentChunks) {
    this.plugin = plugin;
    this.exporter = exporter;
    this.logger = logger;
    this.defaultCellSize = defaultCellSize;
    this.chunksPerTick = chunksPerTick;
    this.maxInFlight = maxInFlight;
    this.maxConcurrentChunks = maxConcurrentChunks;
  }

  /**
   * Parses `/biomemap` arguments and kicks off the asynchronous export.
   */
  @Override
  public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    if (!sender.hasPermission(ADMIN_PERMISSION)) {
      sendError(sender, "You do not have permission to use BiomeMap.");
      return true;
    }
    if (args.length >= 1 && "status".equalsIgnoreCase(args[0])) {
      return handleStatusCommand(sender, args);
    }
    if (args.length >= 1 && "stop".equalsIgnoreCase(args[0])) {
      return handleStopCommand(sender, args);
    }
    if (args.length < 5) {
      sendWarning(
          sender,
          "Usage: /biomemap <world> <x1> <z1> <x2> <z2> [cellSize] [preview]");
      sendWarning(sender, "Stop: /biomemap stop");
      sendWarning(sender, "Status: /biomemap status [map]");
      return true;
    }

    String worldName = args[0];

    World world = Bukkit.getWorld(worldName);
    if (world == null) {
      sendError(sender, "World '" + worldName + "' not found.");
      return true;
    }

    Integer x1 = parseInteger(args[1]);
    Integer z1 = parseInteger(args[2]);
    Integer x2 = parseInteger(args[3]);
    Integer z2 = parseInteger(args[4]);
    if (x1 == null || z1 == null || x2 == null || z2 == null) {
      sendError(sender, "Coordinates must be integers.");
      return true;
    }

    int cellSize = defaultCellSize;
    boolean previewEnabled = false;
    boolean hasCustomCellSize = false;
    for (int index = 5; index < args.length; index++) {
      String option = args[index];
      if ("preview".equalsIgnoreCase(option) || "--preview".equalsIgnoreCase(option)) {
        previewEnabled = true;
        continue;
      }
      Integer parsedCellSize = parsePositiveInteger(option);
      if (!hasCustomCellSize && parsedCellSize != null) {
        cellSize = parsedCellSize;
        hasCustomCellSize = true;
        continue;
      }
      sendError(sender, "Invalid option '" + option + "'. Use a cell size and/or preview.");
      sendWarning(
          sender,
          "Usage: /biomemap <world> <x1> <z1> <x2> <z2> [cellSize] [preview]");
      return true;
    }

    int alignedCellSize = alignCellSize(cellSize);
    if (alignedCellSize != cellSize) {
      sendWarning(sender, String.format(
          Locale.ROOT,
          "Cell size adjusted to %d to stay aligned with the grid.",
          alignedCellSize));
    }
    cellSize = alignedCellSize;

    if (!runningExports.isEmpty()) {
      String activeWorld = runningExports.keySet().iterator().next();
      sendError(sender, "An export is already running for world '" + activeWorld + "'.");
      sendWarning(sender, "Use /biomemap stop before starting a new export.");
      return true;
    }
    int selectionMinX = Math.min(x1, x2);
    int selectionMinZ = Math.min(z1, z2);
    int selectionMaxX = Math.max(x1, x2);
    int selectionMaxZ = Math.max(z1, z2);

    int chunkAlignedSize = cellSize;
    int chunkMinX = Math.floorDiv(selectionMinX, chunkAlignedSize);
    int chunkMaxX = Math.floorDiv(selectionMaxX, chunkAlignedSize);
    int chunkMinZ = Math.floorDiv(selectionMinZ, chunkAlignedSize);
    int chunkMaxZ = Math.floorDiv(selectionMaxZ, chunkAlignedSize);

    int width = chunkMaxX - chunkMinX + 1;
    int height = chunkMaxZ - chunkMinZ + 1;
    if (width <= 0 || height <= 0) {
      sendError(sender, "Invalid selection: zero-area bounding box.");
      return true;
    }

    final int originX = chunkMinX * cellSize;
    final int originZ = chunkMinZ * cellSize;

    final File selectionOutputFile = exporter.resolveSelectionOutput(
        world.getName(), cellSize);

    long totalCells = (long) width * height;
    long totalChunks;
    try {
      totalChunks = GridMath.countChunksForGrid(originX, originZ, width, height, cellSize);
    } catch (IllegalArgumentException ex) {
      sendError(sender, "Selection is too large to export.");
      return true;
    }
    notifyInfo(
        sender,
        String.format(
            Locale.ROOT,
            "Selection: §f§l%d×%d§7 cells (§f%d§7 total, ~§f%d§7 chunks).",
            width,
            height,
            totalCells,
            totalChunks),
        String.format(
            Locale.ROOT,
            "Selection: grid=%dx%d cells=%d chunks~%d",
            width,
            height,
            totalCells,
            totalChunks));
    String previewMode = previewEnabled ? "on" : "off";
    notifyInfo(
        sender,
        String.format(
            Locale.ROOT,
            "§a§lExport started§7 for '§f%s§7' (cell=§f%d§7, preview=§f%s§7).",
            world.getName(),
            cellSize,
            previewMode),
        String.format(
            Locale.ROOT,
            "Export started: world=%s area=[%d,%d -> %d,%d] cell=%d grid=%dx%d "
                + "cells=%d chunks~%d preview=%s output=%s",
            world.getName(),
            selectionMinX,
            selectionMinZ,
            selectionMaxX,
            selectionMaxZ,
            cellSize,
            width,
            height,
            totalCells,
            totalChunks,
            previewMode,
            toLogPath(selectionOutputFile)));

    String worldKey = world.getName().toLowerCase(Locale.ROOT);
    Runnable completion = () -> runningExports.remove(worldKey);
    UUID senderPlayerId = sender instanceof Player player ? player.getUniqueId() : null;
    AsyncBiomeExportTask task =
        new AsyncBiomeExportTask(plugin, exporter, world, cellSize, width, height,
            originX, originZ, selectionMinX, selectionMinZ, selectionMaxX, selectionMaxZ,
            selectionOutputFile, previewEnabled, sender, senderPlayerId, logger, completion,
            chunksPerTick,
            maxInFlight,
            maxConcurrentChunks);
    runningExports.put(worldKey, task);
    task.runTaskTimer(plugin, 1L, 1L);
    return true;
  }

  /**
   * Provides suggestions for world names and a few common cell sizes.
   */
  @Override
  public List<String> onTabComplete(
      CommandSender sender, Command command, String alias, String[] args) {
    if (!sender.hasPermission(ADMIN_PERMISSION)) {
      return Collections.emptyList();
    }
    return BiomeMapSuggestions.complete(args);
  }

  /**
   * Cancels and forgets every running export. Invoked when the plugin disables.
   */
  public void cancelAllExports() {
    for (AsyncBiomeExportTask task : new ArrayList<>(runningExports.values())) {
      task.cancelAndCleanup();
    }
    runningExports.clear();
  }

  private boolean handleStopCommand(CommandSender sender, String[] args) {
    if (args.length != 1) {
      sendWarning(sender, "Usage: /biomemap stop");
      return true;
    }
    if (runningExports.isEmpty()) {
      sendWarning(sender, "No biome export is currently running.");
      return true;
    }
    for (AsyncBiomeExportTask task : new ArrayList<>(runningExports.values())) {
      task.cancelAndCleanup();
    }
    sendWarning(sender, "Stopped running export. Temporary output files removed.");
    return true;
  }

  private boolean handleStatusCommand(CommandSender sender, String[] args) {
    boolean showMap = false;
    if (args.length == 2) {
      String option = args[1];
      if ("map".equalsIgnoreCase(option) || "--map".equalsIgnoreCase(option)) {
        showMap = true;
      } else {
        sendWarning(sender, "Usage: /biomemap status [map]");
        return true;
      }
    } else if (args.length != 1) {
      sendWarning(sender, "Usage: /biomemap status [map]");
      return true;
    }
    if (runningExports.isEmpty()) {
      sendWarning(sender, "No biome export is currently running.");
      return true;
    }

    AsyncBiomeExportTask task = runningExports.values().iterator().next();
    BiomeMapStatusDisplay.show(task, showMap, isConsoleSender(sender),
        message -> sendInfo(sender, message));
    return true;
  }

  private void notifyInfo(CommandSender sender, String senderMessage, String logMessage) {
    if (isConsoleSender(sender)) {
      sender.sendMessage(CHAT_PREFIX + senderMessage);
      return;
    }
    sender.sendMessage(CHAT_PREFIX + senderMessage);
    logger.info(logMessage);
  }

  private boolean isConsoleSender(CommandSender sender) {
    return sender instanceof ConsoleCommandSender || sender instanceof RemoteConsoleCommandSender;
  }

  private void sendWarning(CommandSender sender, String message) {
    sender.sendMessage(CHAT_PREFIX + "§6" + message);
  }

  private void sendInfo(CommandSender sender, String message) {
    sender.sendMessage(CHAT_PREFIX + "§7" + message);
  }

  private void sendError(CommandSender sender, String message) {
    sender.sendMessage(CHAT_PREFIX + "§c§lError: §c" + message);
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
}
