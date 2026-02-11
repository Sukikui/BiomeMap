package fr.sukikui.biomemap.export;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Biome;

/**
 * Generates the JSON export for the biome map.
 */
public final class BiomeExporter {

  private static final String EXPORTS_FOLDER = "exports";
  private static final Gson GSON =
      new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

  private final File pluginFolder;
  private final BiomePreviewRenderer previewRenderer = new BiomePreviewRenderer();

  /**
   * Creates a new exporter tied to the plugin data folder.
   *
   * @param pluginFolder location where exports will be saved
   */
  public BiomeExporter(File pluginFolder) {
    this.pluginFolder = pluginFolder;
  }

  /**
   * Resolves an output path using world + cell size + incremented index.
   */
  public File resolveSelectionOutput(String worldName, int cellSize) {
    File exportsDir = new File(pluginFolder, EXPORTS_FOLDER);
    String baseName = String.format(
        Locale.ROOT,
        "%s_%d",
        sanitizeComponent(worldName),
        cellSize);
    Path basePath = exportsDir.toPath().toAbsolutePath().normalize();
    for (int index = 1; index < Integer.MAX_VALUE; index++) {
      String filename = String.format(Locale.ROOT, "%s_%d.json", baseName, index);
      Path candidate = basePath.resolve(filename).normalize();
      if (!candidate.startsWith(basePath)) {
        throw new IllegalArgumentException("Selection output escaped exports directory");
      }
      if (!candidate.toFile().exists()) {
        return candidate.toFile();
      }
    }
    throw new IllegalStateException("Unable to resolve available output filename");
  }

  /**
   * Samples the biome for a single cell.
   */
  public BiomeCell sampleCell(
      World world, int cellSize, int cellMinX, int cellMinZ, int i, int j) {
    int cellMaxX = cellMinX + cellSize - 1;
    int cellMaxZ = cellMinZ + cellSize - 1;
    String dominantBiome = determineDominantBiome(world, cellMinX, cellMinZ, cellSize);
    BiomeCell.Bounds bounds =
        new BiomeCell.Bounds(new Point(cellMinX, cellMinZ), new Point(cellMaxX, cellMaxZ));
    return new BiomeCell(i, j, bounds, dominantBiome);
  }

  /**
   * Writes the export to disk.
   */
  public void writeExport(BiomeMapExport export, File outputFile) throws IOException {
    File parent = outputFile.getParentFile();
    if (parent != null && !parent.exists() && !parent.mkdirs()) {
      throw new IOException("Unable to create directory " + parent);
    }

    try (Writer writer = Files.newBufferedWriter(outputFile.toPath(), StandardCharsets.UTF_8)) {
      GSON.toJson(export, writer);
    }
  }

  /**
   * Resolves the preview PNG path matching the provided JSON filename.
   */
  public File resolvePreviewOutput(File jsonOutputFile) {
    Path jsonPath = jsonOutputFile.toPath().toAbsolutePath().normalize();
    Path parent = jsonPath.getParent();
    if (parent == null) {
      throw new IllegalArgumentException("JSON output path has no parent directory");
    }

    Path fileName = jsonPath.getFileName();
    if (fileName == null) {
      throw new IllegalArgumentException("JSON output path has no filename");
    }
    String name = fileName.toString();
    int extensionIndex = name.lastIndexOf('.');
    String baseName = extensionIndex > 0 ? name.substring(0, extensionIndex) : name;
    String safeBaseName = sanitizeComponent(baseName);
    Path previewPath = parent.resolve(safeBaseName + ".png").normalize();
    if (!previewPath.startsWith(parent)) {
      throw new IllegalArgumentException("Preview output escaped target directory");
    }
    return previewPath.toFile();
  }

  /**
   * Writes a PNG preview where each pixel represents one exported cell.
   */
  public void writePreview(BiomeMapExport export, File outputFile) throws IOException {
    previewRenderer.writePreview(export, outputFile);
  }

  /**
   * Determines the dominant biome for a cell by sampling five points.
   */
  public String determineDominantBiome(
      World world, int cellOriginX, int cellOriginZ, int cellSize) {
    Map<String, Integer> counts = new HashMap<>();
    int[][] offsets = new int[][] {
        {cellSize / 2, cellSize / 2},
        {0, 0},
        {cellSize - 1, 0},
        {0, cellSize - 1},
        {cellSize - 1, cellSize - 1},
    };

    for (int[] offset : offsets) {
      int sampleX = cellOriginX + offset[0];
      int sampleZ = cellOriginZ + offset[1];
      int y = world.getHighestBlockYAt(sampleX, sampleZ);
      Biome biome = world.getBiome(sampleX, y, sampleZ);
      String biomeKey = biomeKey(biome);
      counts.merge(biomeKey, 1, Integer::sum);
    }

    return counts.entrySet().stream()
        .max(Comparator.comparingInt(Map.Entry<String, Integer>::getValue)
            .thenComparing(Map.Entry::getKey))
        .map(Map.Entry::getKey)
        .orElse("minecraft:unknown");
  }

  /**
   * Formats a biome object into its minecraft:namespace identifier, falling back to unknown.
   */
  public static String biomeKey(Biome biome) {
    if (biome == null) {
      return "minecraft:unknown";
    }
    NamespacedKey key = biome.getKey();
    return key.getNamespace() + ":" + key.getKey();
  }

  private static String sanitizeComponent(String raw) {
    if (raw == null || raw.isBlank()) {
      return "unknown";
    }
    String lower = raw.toLowerCase(Locale.ROOT);
    String sanitized = lower.replaceAll("[^a-z0-9_-]", "_");
    sanitized = sanitized.replaceAll("_+", "_");
    return sanitized.isBlank() ? "unknown" : sanitized;
  }

  /** Simple DTO describing a cell coordinate and its biome id. */
  public record BiomeCell(int i, int j, Bounds bounds, String biome) {

    /** Bounding box of the cell in world coordinates. */
    public record Bounds(Point min, Point max) {
    }
  }

  /** Bundles metadata and sampled cells for JSON export. */
  @SuppressFBWarnings("EI_EXPOSE_REP")
  public record BiomeMapExport(
      int cellSize,
      Point selectionMin,
      Point selectionMax,
      Point gridOrigin,
      int width,
      int height,
      List<BiomeCell> cells) {
  }

  /** Simple x/z coordinate pair. */
  public record Point(int x, int z) {
  }
}
