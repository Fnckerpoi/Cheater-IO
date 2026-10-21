import fs from "node:fs";
import path from "node:path";
import os from "node:os";
import crypto from "node:crypto";
import { spawnSync } from "node:child_process";
import { fileURLToPath } from "node:url";
import { PROFILES, auditDirectory } from "./host-profiles.mjs";
export { validateProfileDocument } from "./host-profiles.mjs";
export function sha256File(file) {
  return crypto
    .createHash("sha256")
    .update(fs.readFileSync(file))
    .digest("hex");
}
function command(tool, args) {
  const r = spawnSync(tool, args, { encoding: "utf8", windowsHide: true });
  if (r.error || r.status !== 0)
    throw new Error(
      "apktool failed: " + (r.error?.message || r.stderr || r.status),
    );
  return r.stdout.trim();
}
export function auditApk(
  input,
  {
    apktool = "apktool",
    java = "java",
    runTool = command,
    profileId = null,
    baseline = null,
  } = {},
) {
  const sourceSha = sha256File(input);
  if (!baseline) {
    if (profileId) {
      baseline = Object.values(PROFILES).find((p) => p.id === profileId);
      if (!baseline) throw new Error("Unknown profile ID: " + profileId);
    } else {
      baseline = PROFILES[sourceSha] || Object.values(PROFILES).find((p) => p.version === "1.0.5");
    }
  }
  const work = fs.mkdtempSync(path.join(os.tmpdir(), "turbo-host-audit-"));
  const invoke = (args) =>
    runTool(
      apktool.endsWith(".jar") ? java : apktool,
      apktool.endsWith(".jar") ? ["-jar", apktool, ...args] : args,
    );
  try {
    const snapshot = path.join(work, "input.apk");
    fs.copyFileSync(input, snapshot, fs.constants.COPYFILE_EXCL);
    const sha = sha256File(snapshot);
    if (sha !== sourceSha)
      throw new Error("APK changed while creating audit snapshot");
    const size = fs.statSync(snapshot).size;
    const version = invoke(["--version"]);
    invoke(["d", "--no-res", "--output", path.join(work, "decoded"), snapshot]);
    if (sha256File(snapshot) !== sha || fs.statSync(snapshot).size !== size)
      throw new Error("Audit snapshot changed during decode");
    const report = auditDirectory(path.join(work, "decoded"), baseline, {
      inputSha256: sha,
    });
    report.input = { sha256: sha, size };
    report.tools = { node: process.version, apktool: version };
    report.cliExitMeaning =
      "0 = static baseline match only; build eligibility is packagingAllowed";
    return report;
  } finally {
    fs.rmSync(work, { recursive: true, force: true });
  }
}
export function markdownReport(r) {
  const lines = [
    "# Android host audit",
    "",
    "Baseline: " +
      r.profile.id +
      " / " +
      r.profile.version +
      " (" +
      r.profile.code +
      ")",
    "Baseline APK SHA-256: " + r.profile.sha256,
    "Baseline contract: " + r.baselineSummary.id,
    "Baseline contract SHA-256: " + r.baselineSummary.sha256,
    "Input SHA-256: " + (r.input?.sha256 || "not supplied"),
    "Static baseline match: " + r.staticBaselineMatch,
    "Build eligible: " + r.packagingAllowed,
    "",
    "Approved means existing static build baseline; no new human or device acceptance.",
    "CLI exit 0 means static baseline match only; build eligibility is packagingAllowed.",
    "",
    "| Kind | Descriptor | Expected | Status |",
    "|---|---|---|---|",
  ];
  for (const x of r.symbols)
    lines.push(
      "| " +
        x.kind +
        " | " +
        x.descriptor +
        " | " +
        x.symbol +
        " | " +
        x.status +
        " |",
    );
  lines.push(
    "",
    "## Structural blockers",
    "",
    JSON.stringify({
      duplicates: r.duplicateDescriptors,
      residue: r.residualOriginalNames,
    }),
    "",
    "## Manual items",
    "",
    ...r.dynamicItems.map(
      (x) => "- " + x.id + ": " + x.status + " — " + x.description,
    ),
    "",
    "## Tools",
    "",
    JSON.stringify(
      r.tools || {
        node: process.version,
        apktool: "not used (directory fixture)",
      },
    ),
    "",
  );
  return lines.join("\n");
}
export function writeReports(r, { jsonPath, markdownPath }) {
  const outputs = [
    [jsonPath, JSON.stringify(r, null, 2) + "\n"],
    [markdownPath, markdownReport(r)],
  ]
    .filter(([p]) => p)
    .map(([p, content]) => ({ path: path.resolve(p), content }));
  const names = outputs.map((x) =>
    process.platform === "win32" ? x.path.toLowerCase() : x.path,
  );
  if (new Set(names).size !== names.length)
    throw new Error("Report output paths must be distinct");
  for (const o of outputs)
    if (fs.existsSync(o.path))
      throw new Error("Refusing to overwrite existing report path: " + o.path);
  const created = [];
  try {
    for (const o of outputs) {
      const fd = fs.openSync(o.path, "wx");
      created.push(o.path);
      try {
        fs.writeFileSync(fd, o.content);
      } finally {
        fs.closeSync(fd);
      }
    }
  } catch (e) {
    for (const p of created) fs.unlinkSync(p);
    throw e;
  }
}

if (
  process.argv[1] &&
  path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)
) {
  try {
    const args = process.argv.slice(2);
    if (!args[0] || args[0] === "--help")
      throw new Error(
        "Usage: node audit-host.mjs INPUT.apk [--apktool PATH_OR_JAR] [--java PATH] [--profile ID] [--json FILE] [--markdown FILE]",
      );
    const input = args.shift(),
      options = {},
      outputs = {};
    while (args.length) {
      const key = args.shift(),
        value = args.shift();
      if (!value) throw new Error("Missing option value");
      if (key === "--apktool") options.apktool = value;
      else if (key === "--java") options.java = value;
      else if (key === "--profile") options.profileId = value;
      else if (key === "--json") outputs.jsonPath = value;
      else if (key === "--markdown") outputs.markdownPath = value;
      else throw new Error("Unknown option " + key);
    }
    const report = auditApk(input, options);
    writeReports(report, outputs);
    if (!outputs.jsonPath) console.log(JSON.stringify(report, null, 2));
    if (!report.staticBaselineMatch) process.exitCode = 1;
  } catch (e) {
    console.error(e.message);
    process.exitCode = 1;
  }
}
