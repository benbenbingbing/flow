import crypto from "node:crypto";
import fs from "node:fs";
import path from "node:path";
import { execFileSync } from "node:child_process";
import { fileURLToPath } from "node:url";

const moduleRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const resourcesRoot = path.join(moduleRoot, "src/main/resources");
const entityDir = path.join(resourcesRoot, "project-config/assets/entities");
const processDir = path.join(resourcesRoot, "project-config/assets/processes");
const packageFile = path.join(
  resourcesRoot,
  "project-config/packages/project-f01-f06-v2.wfpack"
);
const signingKey = process.env.CONFIG_MIGRATION_SIGNING_KEY
  || "workflow-config-migration-development-key";

const systemFields = new Set([
  "id", "code", "name", "status", "createdBy", "createdAt", "updatedBy",
  "updatedAt", "submitterId", "submitterName", "submitTime",
  "processInstanceId", "processDefinitionId", "processStatus",
  "currentTaskId", "currentTaskName", "currentAssignee"
]);

const readJson = (file) => JSON.parse(fs.readFileSync(file, "utf8"));
const sha256 = (value) => crypto.createHash("sha256").update(value).digest("hex");
const escapeRegex = (value) => value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
const assert = (condition, message) => {
  if (!condition) {
    throw new Error(message);
  }
};
const parseComponentProps = (field) => {
  if (!field?.componentProps) return {};
  if (typeof field.componentProps === "object") return field.componentProps;
  try {
    return JSON.parse(field.componentProps);
  } catch {
    return {};
  }
};
const hasStaticOptions = (field) => {
  const componentProps = parseComponentProps(field);
  return Array.isArray(componentProps.options) && componentProps.options.length > 0;
};

const entityFiles = fs.readdirSync(entityDir)
  .filter((name) => name.endsWith(".json"))
  .map((name) => path.join(entityDir, name));
const processFiles = fs.readdirSync(processDir)
  .filter((name) => name.endsWith(".json"))
  .map((name) => path.join(processDir, name));
const entities = entityFiles.map(readJson);
const processes = processFiles.map(readJson);
const entityByCode = new Map(entities.map((entity) => [entity.businessKey, entity]));
const processByKey = new Map(processes.map((process) => [process.businessKey, process]));

for (const entity of entities) {
  assert(entity.assetType === "ENTITY", `${entity.businessKey}: assetType 必须为 ENTITY`);
  assert(
    entity.definition?.entityCode === entity.businessKey,
    `${entity.businessKey}: definition.entityCode 不一致`
  );

  const fieldCodes = new Set(entity.fields.map((field) => field.fieldCode));
  const formKeys = new Set(entity.forms.map((form) => form.formKey));
  const listKeys = new Set(entity.lists.map((list) => list.listKey));
  const policyKeys = new Set(entity.scopePolicies.map((policy) => policy.policyKey));

  for (const field of entity.fields) {
    if (field.refEntityType === "CUSTOM") {
      assert(
        entityByCode.has(field.refEntityCode),
        `${entity.businessKey}.${field.fieldCode}: 引用实体 ${field.refEntityCode} 不存在`
      );
    }
  }

  for (const relation of entity.relations) {
    assert(
      fieldCodes.has(relation.parentFieldCode),
      `${entity.businessKey}: 关系父字段 ${relation.parentFieldCode} 不存在`
    );
    const child = entityByCode.get(relation.childEntityCode);
    assert(child, `${entity.businessKey}: 子实体 ${relation.childEntityCode} 不存在`);
    assert(
      child.fields.some((field) => field.fieldCode === relation.childRefFieldCode),
      `${entity.businessKey}: 子实体字段 ${relation.childRefFieldCode} 不存在`
    );
  }

  for (const form of entity.forms) {
    for (const field of form.fields) {
      assert(
        fieldCodes.has(field.fieldCode) || systemFields.has(field.fieldCode),
        `${entity.businessKey}/${form.formKey}: 表单字段 ${field.fieldCode} 不存在`
      );
    }
  }

  for (const binding of entity.scopeBindings) {
    assert(
      policyKeys.has(binding.policyKey),
      `${entity.businessKey}: 数据范围策略 ${binding.policyKey} 不存在`
    );
    assert(
      binding.listKey == null || listKeys.has(binding.listKey),
      `${entity.businessKey}: 数据范围列表 ${binding.listKey} 不存在`
    );
  }

  assert(entity.forms.length > 0, `${entity.businessKey}: 至少需要一张表单`);
  assert(entity.lists.length > 0, `${entity.businessKey}: 至少需要一张列表`);
  assert(
    entity.scopeBindings.some((binding) => binding.listKey == null),
    `${entity.businessKey}: 至少需要一个实体级 ALLOW 数据范围`
  );

  for (const dependency of entity.dependencies ?? []) {
    if (dependency.type === "ENTITY") {
      assert(
        entityByCode.has(dependency.key),
        `${entity.businessKey}: 实体依赖 ${dependency.key} 不存在`
      );
    }
    if (dependency.type === "PROCESS") {
      assert(
        processByKey.has(dependency.key),
        `${entity.businessKey}: 流程依赖 ${dependency.key} 不存在`
      );
    }
  }

  assert(formKeys.size === entity.forms.length, `${entity.businessKey}: formKey 重复`);
  assert(listKeys.size === entity.lists.length, `${entity.businessKey}: listKey 重复`);
}

for (const process of processes) {
  assert(process.assetType === "PROCESS", `${process.businessKey}: assetType 必须为 PROCESS`);
  assert(
    process.definition?.processKey === process.businessKey,
    `${process.businessKey}: definition.processKey 不一致`
  );
  assert(
    !process.bpmnFile.startsWith("processes/"),
    `${process.businessKey}: BPMN 源文件不得放入 Flowable 自动部署目录 processes/`
  );
  const bpmnPath = path.join(resourcesRoot, process.bpmnFile);
  assert(fs.existsSync(bpmnPath), `${process.businessKey}: BPMN 文件不存在`);
  const bpmn = fs.readFileSync(bpmnPath, "utf8");
  const nodeIds = new Set(process.nodes.map((node) => node.nodeId));
  const formNodeIds = new Set(process.nodeForms.map((node) => node.nodeId));
  const approvalNodeIds = new Set(process.nodeApprovals.map((node) => node.nodeId));
  const statusMappingRoutes = new Set();
  const sequenceFlowIds = new Set(
    [...bpmn.matchAll(
      /<(?:[A-Za-z_][\w.-]*:)?sequenceFlow\b[^>]*\bid="([^"]+)"/gi
    )].map(match => match[1])
  );
  const edgeRefs = new Set(
    [...bpmn.matchAll(
      /<(?:[A-Za-z_][\w.-]*:)?BPMNEdge\b[^>]*\bbpmnElement="([^"]+)"/gi
    )].map(match => match[1])
  );
  const shapeRefs = new Set(
    [...bpmn.matchAll(
      /<(?:[A-Za-z_][\w.-]*:)?BPMNShape\b[^>]*\bbpmnElement="([^"]+)"/gi
    )].map(match => match[1])
  );

  assert(sequenceFlowIds.size > 0, `${process.businessKey}: BPMN 未定义 sequenceFlow`);
  for (const sequenceFlowId of sequenceFlowIds) {
    assert(
      edgeRefs.has(sequenceFlowId),
      `${process.businessKey}: sequenceFlow ${sequenceFlowId} 缺少 BPMNEdge 连线`
    );
  }

  for (const nodeId of nodeIds) {
    assert(bpmn.includes(`id="${nodeId}"`), `${process.businessKey}: BPMN 缺少节点 ${nodeId}`);
    assert(formNodeIds.has(nodeId), `${process.businessKey}: 节点 ${nodeId} 缺少表单`);
    assert(approvalNodeIds.has(nodeId), `${process.businessKey}: 节点 ${nodeId} 缺少审批配置`);
  }
  for (const mapping of process.statusMappings) {
    assert(
      typeof mapping.sourceNodeId === "string" && mapping.sourceNodeId.length > 0,
      `${process.businessKey}: 状态映射 ${mapping.sequenceFlowId} 缺少 sourceNodeId`
    );
    assert(
      typeof mapping.targetNodeId === "string" && mapping.targetNodeId.length > 0,
      `${process.businessKey}: 状态映射 ${mapping.sequenceFlowId} 缺少 targetNodeId`
    );
    assert(
      bpmn.includes(`id="${mapping.sequenceFlowId}"`),
      `${process.businessKey}: BPMN 缺少状态映射连线 ${mapping.sequenceFlowId}`
    );
    const sequenceFlowPattern = new RegExp(
      `<(?:[A-Za-z_][\\w.-]*:)?sequenceFlow\\s+id="${escapeRegex(mapping.sequenceFlowId)}"\\s+`
      + `sourceRef="${escapeRegex(mapping.sourceNodeId)}"\\s+`
      + `targetRef="${escapeRegex(mapping.targetNodeId)}"`
    );
    assert(
      sequenceFlowPattern.test(bpmn),
      `${process.businessKey}: 状态映射 ${mapping.sequenceFlowId} 的源/目标节点与 BPMN 不一致`
    );
    assert(
      shapeRefs.has(mapping.sourceNodeId) && shapeRefs.has(mapping.targetNodeId),
      `${process.businessKey}: 状态映射 ${mapping.sequenceFlowId} 的源/目标节点缺少 BPMNShape`
    );
    const routeKey = `${mapping.sourceNodeId}->${mapping.targetNodeId}`;
    assert(
      !statusMappingRoutes.has(routeKey),
      `${process.businessKey}: 状态映射源/目标节点重复 ${routeKey}`
    );
    statusMappingRoutes.add(routeKey);
    assert(
      entityByCode.has(mapping.entityCode),
      `${process.businessKey}: 状态映射实体 ${mapping.entityCode} 不存在`
    );
  }
}

const unzip = (entry) => execFileSync(
  "unzip",
  ["-p", packageFile, entry],
  { encoding: null }
);
const manifest = JSON.parse(unzip("manifest.json").toString("utf8"));
const checksumsBytes = unzip("checksums.json");
const checksums = JSON.parse(checksumsBytes.toString("utf8"));
const signature = unzip("signature.sig").toString("utf8").trim();
const expectedSignature = crypto.createHmac("sha256", signingKey)
  .update(checksumsBytes)
  .digest("hex");

assert(signature === expectedSignature, "发布包 HMAC 签名不一致");
for (const [entry, expected] of Object.entries(checksums)) {
  assert(sha256(unzip(entry)) === expected, `发布包文件校验失败: ${entry}`);
}

const expectedAssets = new Set([
  ...entities.map((entity) => `ENTITY:${entity.businessKey}`),
  ...processes.map((process) => `PROCESS:${process.businessKey}`)
]);
const packagedAssets = new Set(
  manifest.assets.map((asset) => `${asset.assetType}:${asset.businessKey}`)
);
assert(
  expectedAssets.size === packagedAssets.size
    && [...expectedAssets].every((asset) => packagedAssets.has(asset)),
  "发布包资产与配置源不一致"
);

for (const asset of manifest.assets.filter((item) => item.assetType === "ENTITY")) {
  const packagedEntity = JSON.parse(unzip(asset.path).toString("utf8"));
  const packagedFields = new Map(
    (packagedEntity.fields ?? []).map((field) => [field.fieldCode, field])
  );
  for (const form of packagedEntity.forms ?? []) {
    for (const formField of form.fields ?? []) {
      const entityField = packagedFields.get(formField.fieldCode);
      if (entityField?.optionsJson) {
        assert(
          hasStaticOptions(formField),
          `${asset.businessKey}/${form.formKey}.${formField.fieldCode}: 发布包表单字段缺少可持久化静态选项`
        );
      }
    }
  }
}

console.log(JSON.stringify({
  entities: entities.length,
  processes: processes.length,
  forms: entities.reduce((sum, entity) => sum + entity.forms.length, 0),
  lists: entities.reduce((sum, entity) => sum + entity.lists.length, 0),
  fields: entities.reduce((sum, entity) => sum + entity.fields.length, 0),
  scopePolicies: entities.reduce(
    (sum, entity) => sum + entity.scopePolicies.length,
    0
  ),
  userTasks: processes.reduce((sum, process) => sum + process.nodes.length, 0),
  flowActions: processes.reduce(
    (sum, process) => sum + process.flowActions.length,
    0
  ),
  packageAssets: manifest.assets.length
}, null, 2));
