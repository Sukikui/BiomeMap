package fr.sukikui.biomemap.export;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;

/** Publishes a fully written file without replacing an existing directory entry. */
final class StagedExportFile {

  private final Path target;
  private final Path temporary;
  private final Publisher publisher;
  private boolean published;

  StagedExportFile(Path target, Publisher publisher) throws IOException {
    this.target = target.toAbsolutePath().normalize();
    this.publisher = publisher;
    Path parent = this.target.getParent();
    if (parent == null) {
      throw new IOException("Export output path has no parent directory: " + target);
    }
    Files.createDirectories(parent);
    temporary = Files.createTempFile(parent, ".biomemap-", ".tmp");
  }

  Path temporary() {
    return temporary;
  }

  void publish() throws IOException {
    // The writer has closed; flush content before making the complete file visible.
    try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
      channel.force(true);
    }
    try {
      publisher.publish(temporary, target);
      published = true;
    } catch (UnsupportedOperationException ex) {
      throw new IOException("The export filesystem does not support atomic hard-link publication.",
          ex);
    }
  }

  /** Uses link creation instead of ATOMIC_MOVE, which may silently replace an existing target. */
  static void publishLink(Path temporary, Path target) throws IOException {
    Files.createLink(target, temporary);
  }

  void discard() throws IOException {
    if (published && stillOwnsTarget()) {
      Files.delete(target);
    }
    releaseTemporary();
  }

  void releaseTemporary() throws IOException {
    Files.deleteIfExists(temporary);
  }

  private boolean stillOwnsTarget() throws IOException {
    try {
      BasicFileAttributes attributes = Files.readAttributes(
          target, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
      return attributes.isRegularFile() && Files.isSameFile(temporary, target);
    } catch (NoSuchFileException ex) {
      return false;
    }
  }

  /** Implementations must publish atomically and fail rather than replace an occupied target. */
  @FunctionalInterface
  interface Publisher {
    void publish(Path temporary, Path target) throws IOException;
  }
}
