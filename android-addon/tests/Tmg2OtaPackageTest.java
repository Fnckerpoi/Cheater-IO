package com.turboio.addon;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

public final class Tmg2OtaPackageTest {
 interface Work { void run() throws Exception; }
 static int checks;
 static void ok(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
 static void rejects(Work work, Class<? extends Exception> type) throws Exception {
  try { work.run(); } catch (Exception e) { if (type.isInstance(e)) { checks++; return; } throw e; }
  throw new AssertionError("expected " + type.getSimpleName());
 }
 static void extract(byte[] zip, Path dir) throws IOException {
  Files.createDirectories(dir);
  try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
   ZipEntry entry;
   while ((entry = in.getNextEntry()) != null) {
    if (entry.isDirectory() || !entry.getName().matches("[A-Za-z0-9_.]+")) throw new IOException("unexpected ZIP entry");
    Files.copy(in, dir.resolve(entry.getName()));
   }
  }
 }
 static void removeTree(Path root) throws IOException {
  try (java.util.stream.Stream<Path> paths = Files.walk(root)) {
   Path[] ordered = paths.sorted(Comparator.reverseOrder()).toArray(Path[]::new);
   for (Path path : ordered) Files.delete(path);
  }
 }
 public static void main(String[] args) throws Exception {
  if (args.length != 1) throw new IllegalArgumentException("supply the actual TMG2 ZIP path");
  byte[] zip = Files.readAllBytes(Paths.get(args[0]));
  OtaPackage verified = OtaPackage.verify(zip, OtaProfile.TMG2);
  ok(verified.profile == OtaProfile.TMG2, "profile identity");
  ok(verified.size("nuttx_ap.bin") == 7408584, "AP size");
  ok(BoundedJson.list(verified.manifest()).size() == 15, "manifest row count");
  byte[] corrupted = zip.clone(); corrupted[corrupted.length / 2] ^= 1;
  rejects(() -> OtaPackage.verify(corrupted, OtaProfile.TMG2), IOException.class);
  rejects(() -> OtaPackage.verify(Arrays.copyOf(zip, zip.length - 1), OtaProfile.TMG2), IOException.class);
  rejects(() -> OtaPackage.verify(zip, OtaProfile.TAP1), IOException.class);
  rejects(() -> OtaPackage.verify(zip, OtaProfile.TMIG1), IOException.class);
  rejects(() -> OtaPackage.verify(zip), IOException.class);
  rejects(() -> new OtaSession(verified), IllegalArgumentException.class);
  Path root = Files.createTempDirectory("tmg2-package-");
  try {
   for (String prefix : new String[]{"files/ota/", "app_flutter/ota/"}) {
    Path relative = Paths.get(prefix + OtaProfile.TMG2.directory), dir = root.resolve(relative);
    extract(zip, dir);
    OtaPackage frozen = OtaPackage.freezePreparedDirectory(root, relative, OtaProfile.TMG2);
    ok(frozen.profile == OtaProfile.TMG2, "prepared profile");
    byte[] before = verified.slice("ota_installer_progress.bin", 0, 100);
    ok(Arrays.equals(before, frozen.slice("ota_installer_progress.bin", 0, 100)), "prepared bytes");
    Path file = dir.resolve("ota_installer_progress.bin");
    byte[] original = Files.readAllBytes(file), changed = original.clone(); changed[0] ^= 1;
    Files.write(file, changed);
    rejects(() -> OtaPackage.freezePreparedDirectory(root, relative, OtaProfile.TMG2), IOException.class);
    ok(Arrays.equals(before, frozen.slice("ota_installer_progress.bin", 0, 100)), "frozen bytes after mutation");
    Files.write(file, original);
    Files.delete(file);
    rejects(() -> OtaPackage.freezePreparedDirectory(root, relative, OtaProfile.TMG2), IOException.class);
    Files.write(file, original);
    Path extra = dir.resolve("extra.bin"); Files.write(extra, new byte[]{1});
    rejects(() -> OtaPackage.freezePreparedDirectory(root, relative, OtaProfile.TMG2), IOException.class);
    Files.delete(extra);
    rejects(() -> OtaPackage.freezePreparedDirectory(root, relative, OtaProfile.TMIG1), IOException.class);
    rejects(() -> OtaPackage.freezePreparedDirectory(root, relative, OtaProfile.TAP1), IOException.class);
   }
  } finally { removeTree(root); }
  System.out.println("TMG2 package: " + checks + " checks passed; simulated filesystem and identity only.");
 }
}
