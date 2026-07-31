import crypto from "node:crypto";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { execFileSync } from "node:child_process";
import { fileURLToPath } from "node:url";

const moduleRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const resourcesRoot = path.join(moduleRoot, "src/main/resources");
const output = path.join(
  resourcesRoot,
  "project-config/packages/project-f01-f07-v3.wfpack"
);
const signingKey = process.env.CONFIG_MIGRATION_SIGNING_KEY
  || "workflow-config-migration-development-key";
const stage = fs.mkdtempSync(path.join(os.tmpdir(), "project-wfpack-"));

const discoverAssets = (assetType, relativeDirectory) => {
  const directory = path.join(resourcesRoot, relativeDirectory);
  return fs.readdirSync(directory)
    .filter((name) => name.endsWith(".json"))
    .sort()
    .map((name) => {
      const relativeSource = path.posix.join(relativeDirectory, name);
      const source = JSON.parse(
        fs.readFileSync(path.join(resourcesRoot, relativeSource), "utf8")
      );
      return [
        assetType,
        source.businessKey,
        source.assetName,
        relativeSource
      ];
    });
};

const assetSources = [
  ...discoverAssets("ENTITY", "project-config/assets/entities"),
  ...discoverAssets("PROCESS", "project-config/assets/processes")
];

const entries = new Map();
const manifestAssets = [];
const packageDependencies = new Map();

const compact = (value) => Buffer.from(JSON.stringify(value));
const sha256 = (value) => crypto.createHash("sha256").update(value).digest("hex");
const addEntry = (entryPath, value) => {
  entries.set(entryPath, Buffer.isBuffer(value) ? value : Buffer.from(value));
};
const parseComponentProps = (value) => {
  if (!value) return {};
  if (typeof value === "object") return { ...value };
  try {
    return JSON.parse(value);
  } catch {
    return {};
  }
};
const enrichEntityFormFields = (source) => {
  const entityFields = new Map(
    (source.fields ?? []).map((field) => [field.fieldCode, field])
  );
  for (const form of source.forms ?? []) {
    form.fields = (form.fields ?? []).map((formField) => {
      const entityField = entityFields.get(formField.fieldCode);
      if (!entityField) {
        return formField;
      }
      const enriched = { ...formField };
      if (entityField.optionsJson != null) {
        const componentProps = parseComponentProps(enriched.componentProps);
        if (!Array.isArray(componentProps.options) || componentProps.options.length === 0) {
          componentProps.options = JSON.parse(entityField.optionsJson);
        }
        enriched.componentProps = JSON.stringify(componentProps);
      }
      if (enriched.defaultValue == null && entityField.defaultValue != null) {
        enriched.defaultValue = entityField.defaultValue;
      }
      if (enriched.validationRules == null && entityField.validateRules != null) {
        enriched.validationRules = entityField.validateRules;
      }
      return enriched;
    });
  }
  return source;
};

for (const [assetType, businessKey, assetName, relativeSource] of assetSources) {
  let source = JSON.parse(
    fs.readFileSync(path.join(resourcesRoot, relativeSource), "utf8")
  );
  if (assetType === "PROCESS") {
    source.bpmnXml = fs.readFileSync(
      path.join(resourcesRoot, source.bpmnFile),
      "utf8"
    );
    delete source.bpmnFile;
  } else {
    source = enrichEntityFormFields(source);
  }

  const assetPath = assetType === "ENTITY"
    ? `assets/entities/${businessKey}-v1.json`
    : `assets/processes/${businessKey}-v1.json`;
  const snapshot = compact(source);
  addEntry(assetPath, snapshot);

  if (assetType === "PROCESS") {
    addEntry(`assets/processes/${businessKey}.bpmn`, source.bpmnXml);
  } else {
    for (const form of source.forms ?? []) {
      addEntry(
        `assets/forms/${businessKey}/${form.formKey}.json`,
        compact(form)
      );
    }
    for (const list of source.lists ?? []) {
      addEntry(
        `assets/lists/${businessKey}/${list.listKey}.json`,
        compact(list)
      );
    }
  }

  for (const dependency of source.dependencies ?? []) {
    packageDependencies.set(`${dependency.type}:${dependency.key}`, dependency);
  }

  const contentHash = sha256(snapshot);
  manifestAssets.push({
    assetType,
    businessKey,
    assetName,
    sourceVersion: 1,
    sourceHash: contentHash,
    fullSourceHash: contentHash,
    snapshotSchemaVersion: 1,
    path: assetPath,
    selection: null
  });
}

addEntry("dependencies.json", compact([...packageDependencies.values()]));
addEntry("manifest.json", compact({
  formatVersion: 1,
  packageNo: "WFP-PROJECT-F01-F07-V3-20260730-R14",
  migrationTag: "PROJECT-F01-F07-V3",
  sourceEnvironment: "local",
  createdAt: new Date().toISOString(),
  assets: manifestAssets
}));

const checksums = {};
for (const [entryPath, value] of entries) {
  checksums[entryPath] = sha256(value);
}
const checksumBytes = compact(checksums);
addEntry("checksums.json", checksumBytes);
addEntry(
  "signature.sig",
  crypto.createHmac("sha256", signingKey).update(checksumBytes).digest("hex")
);

for (const [entryPath, value] of entries) {
  const destination = path.join(stage, entryPath);
  fs.mkdirSync(path.dirname(destination), { recursive: true });
  fs.writeFileSync(destination, value);
}

fs.rmSync(output, { force: true });
execFileSync("zip", ["-q", "-r", output, "."], { cwd: stage });
fs.rmSync(stage, { recursive: true, force: true });
console.log(output);
