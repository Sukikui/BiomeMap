package fr.sukikui.biomemap.export;

import fr.sukikui.biomemap.export.BiomeExporter.BiomeCell;
import fr.sukikui.biomemap.export.BiomeExporter.BiomeMapExport;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

/**
 * Renders a 1px-per-cell biome preview image.
 */
public final class BiomePreviewRenderer {

  /**
   * Writes the preview file as a PNG.
   */
  public void writePreview(BiomeMapExport export, File outputFile) throws IOException {
    if (export.width() <= 0 || export.height() <= 0) {
      throw new IOException("Unable to render preview for an empty export");
    }

    BufferedImage image =
        new BufferedImage(export.width(), export.height(), BufferedImage.TYPE_INT_RGB);
    int unknownRgb = BiomeColorPalette.colorFor("minecraft:unknown").getRGB();
    fillBackground(image, unknownRgb);

    for (BiomeCell cell : export.cells()) {
      if (cell == null) {
        continue;
      }
      int pixelX = cell.i();
      int pixelY = cell.j();
      if (pixelX < 0 || pixelX >= export.width() || pixelY < 0 || pixelY >= export.height()) {
        continue;
      }
      int rgb = BiomeColorPalette.colorFor(cell.biome()).getRGB();
      image.setRGB(pixelX, pixelY, rgb);
    }

    File parent = outputFile.getParentFile();
    if (parent != null && !parent.exists() && !parent.mkdirs()) {
      throw new IOException("Unable to create directory " + parent);
    }
    if (!ImageIO.write(image, "png", outputFile)) {
      throw new IOException("No PNG image writer available");
    }
  }

  private void fillBackground(BufferedImage image, int rgb) {
    for (int y = 0; y < image.getHeight(); y++) {
      for (int x = 0; x < image.getWidth(); x++) {
        image.setRGB(x, y, rgb);
      }
    }
  }
}
