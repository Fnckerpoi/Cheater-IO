import fs from "node:fs";
import path from "node:path";
import crypto from "node:crypto";

export const ADAPTERS = Object.freeze({
  "rayneo-1.0.5-201": Object.freeze({
    sha256: "770ba0793d31609aa1e4477db2f8a7aec2c8acc4d9c6ab43d57b0dfc720d3ab3",
    version: "1.0.5",
    code: 201,
    baselineSha256:
      "839b8f584ff45ab3a532d2a199a4c36216ea4c7131f6b3d4838f4f0d68bc8568",
  }),
  "rayneo-1.0.7-230": Object.freeze({
    sha256: "30239f65d910cf7008cfdf6cfacd5c068c5877068b4a637027786377a9ce4f91",
    version: "1.0.7",
    code: 230,
    baselineSha256:
      "a2961c06a9109a982386921044551cc982afa935fc67299f1216991dd1a2675c",
  }),
});

const contractSha = (baseline) =>
  crypto.createHash("sha256").update(JSON.stringify(baseline)).digest("hex");

export function validateBaseline(baseline, adapterId) {
  const sha = contractSha(baseline);
  if (adapterId && ADAPTERS[adapterId]) {
    if (sha !== ADAPTERS[adapterId].baselineSha256)
      throw new Error("Static adapter contract digest mismatch");
  } else {
    const valid = Object.values(ADAPTERS).some(a => a.baselineSha256 === sha);
    if (!valid) throw new Error("Static adapter contract digest mismatch");
  }
  return baseline;
}

function freeze(value) {
  if (value && typeof value === "object") {
    Object.values(value).forEach(freeze);
    Object.freeze(value);
  }
  return value;
}

export function validateProfileDocument(doc) {
  const keys = (o, allowed) =>
    o &&
    typeof o === "object" &&
    !Array.isArray(o) &&
    Object.keys(o).every((k) => allowed.includes(k)) &&
    allowed.every((k) => Object.hasOwn(o, k));
  if (
    !keys(doc, ["schemaVersion", "profiles"]) ||
    doc.schemaVersion !== 1 ||
    !Array.isArray(doc.profiles) ||
    !doc.profiles.length
  )
    throw new Error("Invalid registry schema");
  const ids = new Set(),
    shas = new Set();
  for (const p of doc.profiles) {
    if (
      !keys(p, [
        "id",
        "sha256",
        "version",
        "code",
        "directory",
        "status",
        "adapterId",
        "baseline",
      ]) ||
      typeof p.id !== "string" ||
      !/^[a-z0-9][a-z0-9.-]+$/.test(p.id) ||
      typeof p.sha256 !== "string" ||
      !/^[a-f0-9]{64}$/.test(p.sha256) ||
      typeof p.version !== "string" ||
      !/^\d+\.\d+\.\d+$/.test(p.version) ||
      !Number.isSafeInteger(p.code) ||
      p.code < 1 ||
      typeof p.directory !== "string" ||
      !/^[a-z0-9-]+$/.test(p.directory) ||
      !["draft", "approved", "revoked"].includes(p.status) ||
      !(p.adapterId === null || typeof p.adapterId === "string") ||
      (p.baseline !== "static-1.0.5" && p.baseline !== "static-1.0.7")
    )
      throw new Error("Invalid profile schema");
    if (ids.has(p.id) || shas.has(p.sha256))
      throw new Error("Duplicate profile ID or SHA");
    ids.add(p.id);
    shas.add(p.sha256);
  }
  return doc;
}

export function loadProfiles(
  file = new URL("./profiles/registry.json", import.meta.url),
) {
  const doc = validateProfileDocument(
      JSON.parse(fs.readFileSync(file, "utf8")),
    ),
    result = Object.create(null);

  const baselineCache = new Map();
  const getBaseline = (name, adapterId) => {
    if (!baselineCache.has(name)) {
      const baseline = JSON.parse(
        fs.readFileSync(new URL(`./profiles/${name}.json`, import.meta.url), "utf8"),
      );
      if (
        !Array.isArray(baseline) ||
        !baseline.length ||
        baseline.some(
          (row) =>
            !Array.isArray(row) ||
            row.length !== 2 ||
            typeof row[0] !== "string" ||
            !/^L[A-Za-z0-9_$/]+;$/.test(row[0]) ||
            typeof row[1] !== "string" ||
            !(/^(?:\.method |\.field |")/.test(row[1])),
        ) ||
        new Set(baseline.map(JSON.stringify)).size !== baseline.length
      )
        throw new Error("Invalid static baseline schema");
      validateBaseline(baseline, adapterId);
      baselineCache.set(name, baseline);
    }
    return baselineCache.get(name);
  };

  for (const p of doc.profiles) {
    const baseline = getBaseline(p.baseline, p.adapterId);
    result[p.sha256] = freeze({ ...p, baseline });
  }
  return freeze(result);
}

export const PROFILE_REGISTRY = loadProfiles();
export const PROFILES = PROFILE_REGISTRY;

export function profileFor(sha) {
  if (typeof sha !== "string" || !/^[a-f0-9]{64}$/.test(sha))
    throw new Error("Invalid APK SHA-256");
  const p = PROFILE_REGISTRY[sha];
  if (!p)
    throw new Error("Unsupported APK SHA256; no host mutations performed");
  return p;
}

export function assertBuildProfile(profile) {
  if (!profile || profile.status !== "approved")
    throw new Error("Build profile is not approved");
  const allowedVersions = [
    { version: "1.0.5", code: 201 },
    { version: "1.0.7", code: 230 },
  ];
  if (!allowedVersions.some(v => v.version === profile.version && v.code === profile.code))
    throw new Error("Historical host profiles are not buildable");
  const adapter = ADAPTERS[profile.adapterId];
  if (
    !adapter ||
    adapter.version !== profile.version ||
    adapter.code !== profile.code ||
    adapter.sha256 !== profile.sha256
  )
    throw new Error("Unknown or mismatched host adapter");
  validateBaseline(profile.baseline, profile.adapterId);
  if (PROFILE_REGISTRY[profile.sha256] !== profile)
    throw new Error("Profile is not a registered immutable profile");
  return true;
}

function dexDirs(root) {
  return fs
    .readdirSync(root)
    .filter((n) => /^smali(?:_classes\d+)?$/.test(n))
    .sort()
    .map((n) => path.join(root, n));
}

function descriptorFiles(root) {
  const files = [];
  for (const dex of dexDirs(root)) {
    const walk = (d) => {
      for (const ent of fs
        .readdirSync(d, { withFileTypes: true })
        .sort((a, b) => a.name.localeCompare(b.name))) {
        const f = path.join(d, ent.name);
        if (ent.isDirectory()) walk(f);
        else if (ent.isFile() && ent.name.endsWith(".smali")) files.push(f);
      }
    };
    walk(dex);
  }
  return files;
}

export function classIndex(root) {
  const index = new Map();
  for (const file of descriptorFiles(root)) {
    const text = fs.readFileSync(file, "utf8");
    for (const line of text.split(/\r?\n/)) {
      const m = line.match(/^\.class\s+.*?\s+(L[^;]+;)\s*$/);
      if (m) {
        const a = index.get(m[1]) || [];
        a.push(file);
        index.set(m[1], a);
      }
    }
  }
  return index;
}

export function classFile(root, descriptor) {
  if (typeof descriptor !== "string" || !/^L[A-Za-z0-9_$/]+;$/.test(descriptor))
    throw new Error("Invalid descriptor");
  const matches = classIndex(root).get(descriptor) || [];
  if (matches.length !== 1)
    throw new Error(
      (matches.length ? "Ambiguous" : "Missing") +
        " exact class: " +
        descriptor,
    );
  return matches[0];
}

export function verifyHost(root, profile) {
  assertBuildProfile(profile);
  const index = classIndex(root);
  const report = auditDirectory(root, profile, {
    inputSha256: profile.sha256,
    index,
  });
  if (!report.staticBaselineMatch)
    throw new Error(
      "Host ABI mismatch: missing/changed/ambiguous symbols, duplicate descriptors or turboioOriginal residue",
    );
  const file = (d) => {
    const f = index.get(d);
    if (!f || f.length !== 1) throw new Error("Missing or ambiguous exact class: " + d);
    return f[0];
  };

  if (profile.version === "1.0.7") {
    return {
      listener: file("Lq8/c$c;"),
      activity: file("Lcom/rayneo/venus/MainActivity;"),
      event: file("Lcom/rayneo/rayneo_venus_sdk_plugin/j;"),
      ota: { queue: file("LY3/V;"), sender: file("LY3/u;") },
      version: profile.version,
      code: profile.code,
    };
  }

  return {
    listener: file("LH7/c$c;"),
    activity: file("Lcom/rayneo/venus/MainActivity;"),
    event: file("Lcom/rayneo/rayneo_venus_sdk_plugin/j;"),
    ota: { queue: file("LE3/V;"), sender: file("LE3/u;") },
    version: profile.version,
    code: profile.code,
  };
}

export function auditDirectory(
  root,
  profile,
  { inputSha256 = null, index = classIndex(root) } = {},
) {
  validateBaseline(profile.baseline, profile.adapterId);
  const files = descriptorFiles(root),
    rows = [];
  for (const descriptor of [...new Set(profile.baseline.map((x) => x[0]))]) {
    const candidates = index.get(descriptor) || [];
    rows.push({
      kind: "class",
      descriptor,
      symbol: descriptor,
      status:
        candidates.length === 1
          ? "match"
          : candidates.length
            ? "ambiguous"
            : "missing",
      files: candidates.map((f) =>
        path.relative(root, f).split(path.sep).join("/"),
      ),
    });
  }
  for (const [descriptor, symbol] of profile.baseline) {
    const candidates = index.get(descriptor) || [],
      kind = symbol.startsWith(".method")
        ? "method"
        : symbol.startsWith(".field")
          ? "field"
          : "constant";
    let status = "not-checked";
    if (!candidates.length) status = "missing";
    else if (candidates.length > 1) status = "ambiguous";
    else {
      const text = fs.readFileSync(candidates[0], "utf8"),
        lines = text.split(/\r?\n/).map((x) => x.trim());
      if (kind === "constant") {
        const matchesConstant = lines.some((l) => {
          if (l.startsWith("#")) return false;
          if (l.startsWith(".field") && l.includes(" = " + symbol)) return true;
          if (
            /^const-string(?:\/jumbo)?\s+[^,]+,\s*".*"$/.test(l) &&
            l.endsWith(symbol)
          )
            return true;
          return false;
        });
        status = matchesConstant ? "match" : "missing";
      } else {
        const prefix = kind === ".method" || kind === "method" ? ".method " : ".field ",
          signature = symbol.split(" ").at(-1),
          name = signature.split(/[(:]/)[0],
          decls = lines.filter((l) => l.startsWith(prefix)),
          exact = decls.filter((l) => l.split(" ").at(-1) === signature);
        status =
          exact.length > 1
            ? "ambiguous"
            : exact.length === 1
              ? exact[0] === symbol
                ? "match"
                : "changed"
              : decls.some((l) => l.split(" ").at(-1).split(/[(:]/)[0] === name)
                ? "changed"
                : "missing";
      }
    }
    rows.push({
      kind,
      descriptor,
      symbol,
      status,
      files: candidates.map((f) =>
        path.relative(root, f).split(path.sep).join("/"),
      ),
    });
  }
  const duplicates = [...index.entries()]
    .filter(([, f]) => f.length > 1)
    .map(([descriptor, files]) => ({
      descriptor,
      files: files.map((f) => path.relative(root, f).split(path.sep).join("/")),
    }));
  const residues = [];
  for (const f of files) {
    const text = fs.readFileSync(f, "utf8");
    if (text.includes("turboioOriginal"))
      residues.push(path.relative(root, f).split(path.sep).join("/"));
  }
  const dynamicItems = [
    {
      id: "private-java-contract",
      status: "not-checked",
      description:
        "NavReflect message fields c/b/e, NativeTransfer and other private Java runtime symbols are not exhaustively covered by this static baseline.",
    },
    {
      id: "native-source-pin",
      status: "not-checked",
      description:
        "official-ota-source.mjs separately validates libapp.so SHA-256 and byte offsets; this audit does not replace that validation.",
    },
    {
      id: "runtime-reflection",
      status: "not-checked",
      description:
        "Runtime reflective method lookup requires device execution evidence.",
    },
    {
      id: "native-ota-dependencies",
      status: "not-checked",
      description:
        "Native OTA and external runtime dependencies require separate acceptance.",
    },
  ];
  const summary = {
    match: rows.filter((x) => x.status === "match").length,
    missing: rows.filter((x) => x.status === "missing").length,
    changed: rows.filter((x) => x.status === "changed").length,
    ambiguous: rows.filter((x) => x.status === "ambiguous").length,
    notChecked:
      rows.filter((x) => x.status === "not-checked").length +
      dynamicItems.length,
  };
  return {
    schemaVersion: 1,
    profile: {
      id: profile.id,
      version: profile.version,
      code: profile.code,
      status: profile.status,
      adapterId: profile.adapterId,
      sha256: profile.sha256,
    },
    summary,
    symbols: rows,
    duplicateDescriptors: duplicates,
    residualOriginalNames: residues,
    dynamicItems,
    staticBaselineMatch:
      summary.missing === 0 &&
      summary.changed === 0 &&
      summary.ambiguous === 0 &&
      duplicates.length === 0 &&
      residues.length === 0,
    input: { sha256: inputSha256 },
    approvalMeaning:
      "Existing static build baseline; not new human or device acceptance",
    baselineSummary: {
      id: profile.baseline,
      sha256: crypto
        .createHash("sha256")
        .update(JSON.stringify(profile.baseline))
        .digest("hex"),
      scope:
        "Existing static build baseline; not exhaustive ABI or runtime acceptance",
    },
    tools: {
      node: process.version,
      apktool: "not used (directory fixture)",
    },
    packagingAllowed:
      PROFILE_REGISTRY[profile.sha256] === profile &&
      inputSha256 === profile.sha256 &&
      profile.status === "approved" &&
      ADAPTERS[profile.adapterId]?.sha256 === profile.sha256 &&
      summary.missing === 0 &&
      summary.changed === 0 &&
      summary.ambiguous === 0 &&
      duplicates.length === 0 &&
      residues.length === 0,
  };
}
