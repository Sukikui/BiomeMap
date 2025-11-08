package fr.sukikui.biomemap.export;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Biome;

/**
 * Generates the JSON export for the biome map.
 */
public final class BiomeExporter {

  private static final String EXPORT_RELATIVE_PATH = "data/biome-map.json";
  private static final Gson GSON =
      new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

  private final File pluginFolder;
  private final int scanRadius;

  public BiomeExporter(File pluginFolder, int scanRadius) {
    this.pluginFolder = pluginFolder;
    this.scanRadius = scanRadius;
  }

  public ExportResult exportWorld(World world, int cellSize) throws IOException {
    long start = System.currentTimeMillis();
    Location spawn = world.getSpawnLocation();
    int originX = spawn.getBlockX() - scanRadius;
    int originZ = spawn.getBlockZ() - scanRadius;
    int diameter = scanRadius * 2;
    int width = Math.max(1, diameter / cellSize);
    int height = width;

    List<BiomeCell> cells = new ArrayList<>(width * height);
    for (int j = 0; j < height; j++) {
      int cellOriginZ = originZ + (j * cellSize);
      for (int i = 0; i < width; i++) {
        int cellOriginX = originX + (i * cellSize);
        String dominantBiome = determineDominantBiome(world, cellOriginX, cellOriginZ, cellSize);
        cells.add(new BiomeCell(i, j, dominantBiome));
      }
    }

    BiomeMapExport export = new BiomeMapExport(cellSize, new Origin(originX, originZ), width, height, cells);
    File outputFile = new File(pluginFolder, EXPORT_RELATIVE_PATH);
    writeExport(export, outputFile);
    long duration = System.currentTimeMillis() - start;
    return new ExportResult(cells.size(), outputFile, duration);
  }

  private void writeExport(BiomeMapExport export, File outputFile) throws IOException {
    File parent = outputFile.getParentFile();
    if (parent != null && !parent.exists() && !parent.mkdirs()) {
      throw new IOException("Unable to create directory " + parent);
    }

    try (Writer writer = Files.newBufferedWriter(outputFile.toPath(), StandardCharsets.UTF_8)) {
      GSON.toJson(export, writer);
    }
  }

  private String determineDominantBiome(World world, int cellOriginX, int cellOriginZ, int cellSize) {
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

  private String biomeKey(Biome biome) {
    if (biome == null) return "minecraft:unknown";
    NamespacedKey key = biome.getKey();
    return key.asString();
  }

  public record ExportResult(int cellCount, File outputFile, long durationMs) {
  }

  private record BiomeCell(int i, int j, String biome) {
  }

  private record BiomeMapExport(int cellSize, Origin origin, int width, int height,
      List<BiomeCell> cells) {
  }

  private record Origin(int x, int z) {
  }
}
