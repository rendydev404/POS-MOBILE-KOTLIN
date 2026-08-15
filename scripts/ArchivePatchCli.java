// Build-time CLI for Google File-by-File v1 patches.
// Compile/run via scripts/create-archive-patch.ps1; Android uses the exact same
// applier library, while APK SHA-256 verification remains in AppUpdateManager.
import com.google.archivepatcher.applier.FileByFileV1DeltaApplier;
import com.google.archivepatcher.generator.FileByFileV1DeltaGenerator;
import com.google.archivepatcher.shared.DefaultDeflater;
import com.google.archivepatcher.shared.IDeflater;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.function.BiFunction;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

public final class ArchivePatchCli {
  private static final int BUFFER_SIZE = 32 * 1024;
  private static final BiFunction<Integer, Boolean, IDeflater> DEFLATER_FACTORY =
      (level, nowrap) -> new DefaultDeflater(level, nowrap);

  public static void main(String[] args) throws Exception {
    if (args.length != 4 || !(args[0].equals("generate") || args[0].equals("apply"))) {
      throw new IllegalArgumentException(
          "usage: ArchivePatchCli generate OLD.apk NEW.apk PATCH | apply OLD.apk PATCH NEW.apk");
    }
    if (args[0].equals("generate")) {
      generate(new File(args[1]), new File(args[2]), new File(args[3]));
    } else {
      apply(new File(args[1]), new File(args[2]), new File(args[3]));
    }
  }

  private static void generate(File oldFile, File newFile, File patchFile) throws Exception {
    Deflater compressor = new Deflater(9, true);
    try (FileOutputStream rawOut = new FileOutputStream(patchFile);
         DeflaterOutputStream compressedOut =
             new DeflaterOutputStream(rawOut, compressor, BUFFER_SIZE)) {
      new FileByFileV1DeltaGenerator(DEFLATER_FACTORY)
          .generateDelta(oldFile, newFile, compressedOut);
      compressedOut.finish();
    } finally {
      compressor.end();
    }
  }

  private static void apply(File oldFile, File patchFile, File newFile) throws Exception {
    Inflater inflater = new Inflater(true);
    File tempDir = newFile.getAbsoluteFile().getParentFile();
    try (BufferedInputStream rawIn =
             new BufferedInputStream(new FileInputStream(patchFile), BUFFER_SIZE);
         InflaterInputStream patchIn = new InflaterInputStream(rawIn, inflater, BUFFER_SIZE);
         BufferedOutputStream newOut =
             new BufferedOutputStream(new FileOutputStream(newFile), BUFFER_SIZE)) {
      new FileByFileV1DeltaApplier(tempDir, DEFLATER_FACTORY)
          .applyDelta(oldFile, patchIn, newOut);
    } finally {
      inflater.end();
    }
  }
}
