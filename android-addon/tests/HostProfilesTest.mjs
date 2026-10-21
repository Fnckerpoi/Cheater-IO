import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import os from "node:os";
import {
  profileFor,
  classFile,
  verifyHost,
  assertBuildProfile,
  PROFILES,
  validateProfileDocument,
  validateBaseline,
  auditDirectory,
} from "../host-profiles.mjs";
import { markdownReport, auditApk, writeReports } from "../audit-host.mjs";
const sha = "770ba0793d31609aa1e4477db2f8a7aec2c8acc4d9c6ab43d57b0dfc720d3ab3",
  profile = profileFor(sha);
assert.equal(profile.code, 201);
assert.throws(() => profileFor("unknown"));
assert.throws(() => profileFor("a".repeat(64)));
assert.equal(assertBuildProfile(profile), true);
for (const patch of [
  { status: "draft" },
  { status: "revoked" },
  { adapterId: "unknown" },
  { version: "1.0.4" },
  { sha256: "a".repeat(64) },
])
  assert.throws(() => assertBuildProfile({ ...profile, ...patch }));
assert.throws(() =>
  assertBuildProfile(
    Object.values(PROFILES).find((p) => p.version === "1.0.4"),
  ),
);
const doc = JSON.parse(
  fs.readFileSync(new URL("../profiles/registry.json", import.meta.url)),
);
assert.equal(validateProfileDocument(doc), doc);
for (const change of [
  (d) => d.profiles.push({ ...d.profiles[0], id: "other" }),
  (d) => d.profiles.push({ ...d.profiles[0], sha256: "a".repeat(64) }),
  (d) => (d.profiles[0].extra = "bad"),
  (d) => delete d.profiles[0].status,
  (d) => (d.extra = true),
  (d) => (d.profiles[0].code = "201"),
  (d) => (d.profiles[0].directory = "../escape"),
  (d) => (d.profiles[0].status = "accepted"),
]) {
  const bad = structuredClone(doc);
  change(bad);
  assert.throws(() => validateProfileDocument(bad));
}
const dir = fs.mkdtempSync(path.join(os.tmpdir(), "turbo-host-test-"));
function fixture() {
  for (const descriptor of new Set(profile.baseline.map((x) => x[0]))) {
    const file = path.join(dir, "smali", descriptor.slice(1, -1) + ".smali");
    fs.mkdirSync(path.dirname(file), { recursive: true });
    const symbols = profile.baseline
      .filter((x) => x[0] === descriptor)
      .map((x) => (x[1].startsWith('"') ? "const-string v0, " + x[1] : x[1]));
    fs.writeFileSync(
      file,
      ".class public " + descriptor + "\n" + symbols.join("\n") + "\n",
    );
  }
}
try {
  fixture();
  const sender = path.join(dir, "smali/E3/u.smali");
  fs.renameSync(sender, path.join(dir, "smali/E3/u.1.smali"));
  fs.writeFileSync(
    path.join(dir, "smali/E3/U.smali"),
    ".class public LE3/U;\n",
  );
  assert.equal(path.basename(classFile(dir, "LE3/u;")), "u.1.smali");
  assert.equal(path.basename(classFile(dir, "LE3/U;")), "U.smali");
  assert.throws(() => classFile(dir, "../../secret"));
  const good = auditDirectory(dir, profile);
  assert.equal(good.staticBaselineMatch, true);
  assert.equal(good.packagingAllowed, false);
  assert.equal(good.input.sha256, null);
  assert.equal(
    auditDirectory(dir, profile, { inputSha256: sha }).packagingAllowed,
    true,
  );
  assert.equal(verifyHost(dir, profile).version, "1.0.5");
  assert.ok(good.dynamicItems.every((x) => x.status === "not-checked"));
  assert.deepEqual(auditDirectory(dir, profile), good);
  assert.equal(
    markdownReport(good),
    markdownReport(auditDirectory(dir, profile)),
  );
  assert.equal(
    auditDirectory(dir, profile, { inputSha256: "a".repeat(64) })
      .packagingAllowed,
    false,
  );
  assert.throws(
    () => validateBaseline(profile.baseline.filter((x) => x[0] !== "LP3/h;")),
    /digest/,
  );
  assert.throws(
    () =>
      auditDirectory(dir, { ...profile, baseline: profile.baseline.slice(1) }),
    /digest/,
  );
  const constantFile = path.join(dir, "smali/P3/h.smali"),
    constantText = fs.readFileSync(constantFile, "utf8");
  fs.writeFileSync(
    constantFile,
    constantText.replaceAll("const-string", "# const-string"),
  );
  assert.ok(auditDirectory(dir, profile).summary.missing > 0);
  assert.throws(() => verifyHost(dir, profile));
  fs.writeFileSync(constantFile, constantText);
  const out = path.join(dir, "report.json"),
    md = path.join(dir, "report.md");
  assert.throws(
    () => writeReports(good, { jsonPath: out, markdownPath: out }),
    /distinct/,
  );
  assert.equal(fs.existsSync(out), false);
  fs.writeFileSync(md, "keep");
  assert.throws(
    () => writeReports(good, { jsonPath: out, markdownPath: md }),
    /overwrite/,
  );
  assert.equal(fs.readFileSync(md, "utf8"), "keep");
  assert.equal(fs.existsSync(out), false);
  assert.throws(() =>
    writeReports(good, {
      jsonPath: out,
      markdownPath: path.join(dir, "absent/report.md"),
    }),
  );
  assert.equal(fs.existsSync(out), false);
  writeReports(good, { jsonPath: out });
  assert.throws(() => writeReports(good, { jsonPath: out }), /overwrite/);
  const file = path.join(dir, "smali/E3/u.1.smali"),
    original = fs.readFileSync(file, "utf8");
  fs.writeFileSync(
    file,
    original.replace(
      ".method public static g()Ljava/util/List;",
      ".method public static g(I)Ljava/util/List;",
    ),
  );
  assert.ok(auditDirectory(dir, profile).summary.changed > 0);
  assert.throws(() => verifyHost(dir, profile));
  fs.writeFileSync(
    file,
    original.replace(".field public static volatile h:LE3/u;", ""),
  );
  assert.ok(auditDirectory(dir, profile).summary.missing > 0);
  fs.writeFileSync(
    file,
    original + "\n.method public static g()Ljava/util/List;\n",
  );
  assert.ok(auditDirectory(dir, profile).summary.ambiguous > 0);
  fs.writeFileSync(file, original + "\n# turboioOriginal_o\n");
  assert.equal(auditDirectory(dir, profile).residualOriginalNames.length, 1);
  assert.throws(() => verifyHost(dir, profile));
  fs.writeFileSync(file, original);
  fs.mkdirSync(path.join(dir, "smali_classes2"), { recursive: true });
  fs.writeFileSync(path.join(dir, "smali_classes2/Other.smali"), original);
  assert.throws(() => classFile(dir, "LE3/u;"));
  assert.ok(auditDirectory(dir, profile).summary.ambiguous > 0);
  assert.throws(() => verifyHost(dir, profile));
  const apk = path.join(dir, "unknown.apk");
  fs.writeFileSync(apk, "unknown apk fixture");
  let decoded;
  const report = auditApk(apk, {
    apktool: "C:\tools with spaces\apktool.jar",
    java: "C:\java\java.exe",
    runTool: (tool, args) => {
      assert.equal(tool, "C:\java\java.exe");
      assert.equal(args[0], "-jar");
      if (args.includes("--version")) return "2.fixture";
      decoded = args[args.indexOf("--output") + 1];
      assert.notEqual(decoded, dir);
      const snapshot = args.at(-1);
      assert.notEqual(snapshot, apk);
      fs.writeFileSync(apk, "source replaced after snapshot");
      assert.equal(fs.readFileSync(snapshot, "utf8"), "unknown apk fixture");
      fs.mkdirSync(path.join(decoded, "smali"), { recursive: true });
      return "";
    },
  });
  assert.equal(report.packagingAllowed, false);
  assert.equal(report.tools.apktool, "2.fixture");
  assert.equal(report.input.size, 19);
  assert.ok(!fs.existsSync(decoded));
  assert.throws(() => auditApk(path.join(dir, "absent.apk")));
  assert.throws(
    () =>
      auditApk(apk, {
        runTool: (_tool, args) => {
          if (args.includes("--version")) return "fixture";
          fs.writeFileSync(args.at(-1), "snapshot tampered");
          return "";
        },
      }),
    /snapshot changed/,
  );
} finally {
  fs.rmSync(dir, { recursive: true, force: true });
}

// 1.0.7 profile and baseline verification
const sha107 = "30239f65d910cf7008cfdf6cfacd5c068c5877068b4a637027786377a9ce4f91";
const profile107 = profileFor(sha107);
assert.equal(profile107.version, "1.0.7");
assert.equal(profile107.code, 230);
assert.equal(assertBuildProfile(profile107), true);

const dir107 = fs.mkdtempSync(path.join(os.tmpdir(), "turbo-host-107-test-"));
try {
  for (const descriptor of new Set(profile107.baseline.map((x) => x[0]))) {
    const file = path.join(dir107, "smali", descriptor.slice(1, -1) + ".smali");
    fs.mkdirSync(path.dirname(file), { recursive: true });
    const symbols = profile107.baseline
      .filter((x) => x[0] === descriptor)
      .map((x) => (x[1].startsWith('"') ? "const-string v0, " + x[1] : x[1]));
    fs.writeFileSync(file, ".class public " + descriptor + "\n" + symbols.join("\n") + "\n");
  }
  const good107 = auditDirectory(dir107, profile107, { inputSha256: sha107 });
  assert.equal(good107.staticBaselineMatch, true);
  assert.equal(good107.packagingAllowed, true);
  const host107 = verifyHost(dir107, profile107);
  assert.equal(host107.version, "1.0.7");
  assert.match(host107.listener, /q8[\\/]c\$c\.smali$/);
  assert.match(host107.ota.queue, /Y3[\\/]V\.smali$/);
  assert.match(host107.ota.sender, /Y3[\\/]u\.smali$/);
} finally {
  fs.rmSync(dir107, { recursive: true, force: true });
}

console.log("Host profile and audit fixtures passed (including 1.0.5 and 1.0.7).");
