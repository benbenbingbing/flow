<template>
  <div class="guide-page">
    <div class="guide-header">
      <div>
        <h2>表单与列表配置扩展</h2>
        <p>稳定节点、单项保存、统一数据源、发布快照和前后端扩展 SPI 的完整契约。</p>
      </div>
      <el-tag type="success">配置优先，代码兜底</el-tag>
    </div>

    <div class="guide-layout">
      <main class="guide-content">
        <el-alert
          title="优先使用递归节点、统一数据源和节点级组件。整页自定义只用于平台布局与运行时契约确实无法表达的场景。"
          type="info"
          :closable="false"
          show-icon
        />

        <section id="levels" class="guide-section">
          <h3>1. 扩展层级怎么选</h3>
          <el-table :data="extensionLevels" border size="small">
            <el-table-column prop="level" label="层级" width="150" />
            <el-table-column prop="useCase" label="适用场景" min-width="240" />
            <el-table-column prop="implementation" label="实现方式" min-width="260" />
          </el-table>
        </section>

        <section id="config-contract" class="guide-section">
          <h3>2. 稳定 ID、单项保存与乐观锁</h3>
          <ul class="check-list">
            <li>表单节点、列表列和按钮都以稳定 `id` 定位；禁止使用数组下标、显示名称或排序值充当业务键。</li>
            <li>属性面板只 PATCH 当前项目，拖拽单独保存 `orderKey`；不同项目可并行保存，不再全删全插。</li>
            <li>表单节点拖拽必须走平台递归拖拽容器与 reorder 接口：目标父容器按 nodeType、循环引用和最大 8 层校验，成功后重新读取节点 revision。自定义节点组件不得自行改 parentId 或绕过服务端校验。</li>
            <li>所有修改和删除请求必须携带 `expectedRevision`；成功后使用响应中的新 `revision` 更新 Store。</li>
            <li>同一项目发生并发冲突时返回 HTTP `409`，服务器当前对象位于响应 `data`；客户端以 `data.revision` 作为 serverRevision、以 `data` 作为 currentData，保留本地值并展示差异。</li>
            <li>兼容整包更新调用 `/nodes/update?expectedRevision=...`，先校验表单草稿 revision，再按稳定 ID 差异 upsert；HTTP 请求仍执行节点锁定和 CAS。只有启动迁移/配置导入等可信系统命令可进入兼容迁移通道。</li>
          </ul>
          <CodeCard title="PATCH 单个表单节点" language="HTTP">
            <pre v-pre><code>PATCH /api/entity-forms/frm_order/nodes/node_amount
Content-Type: application/json

{
  "expectedRevision": 7,
  "label": "含税金额",
  "props": {
    "precision": 2,
    "readonly": true
  }
}

200 OK
{
  "id": "node_amount",
  "revision": 8,
  "updatedAt": "2026-07-18T10:30:00+08:00"
}</code></pre>
          </CodeCard>
          <CodeCard title="409 冲突响应" language="HTTP">
            <pre v-pre><code>409 Conflict
{
  "code": 409,
  "errorCode": "CONFIG_REVISION_CONFLICT",
  "message": "节点已被其他管理员修改",
  "data": {
    "id": "node_amount",
    "label": "订单金额",
    "revision": 9
  }
}</code></pre>
          </CodeCard>
        </section>

        <section id="form-node-spi" class="guide-section">
          <h3>3. 递归表单节点与组件注册协议</h3>
          <el-table :data="nodeTypes" border size="small">
            <el-table-column prop="type" label="nodeType" width="220" />
            <el-table-column prop="capability" label="能力与限制" />
          </el-table>
          <p>表单树最大嵌套深度为 `8`。发布校验必须检查父子类型、孤儿节点、循环引用、跨表单 SUB_FORM 循环和引用的已发布表单版本。</p>
          <CodeCard title="form-node-extension.ts" language="JSON">
            <pre v-pre><code>{
  "schemaVersion": 1,
  "type": "NODE",
  "name": "risk-matrix",
  "label": "risk-matrix",
  "version": 3,
  "implementation": {
    "path": "src/extensions/common/nodes/RiskMatrixNode.vue",
    "export": "default",
    "kind": "COMPONENT"
  },
  "metadata": {
    "nodeTypes": [
      "FIELD"
    ],
    "supportedBindings": [
      "ENTITY_FIELD",
      "COMPUTED"
    ],
    "configSchema": [
      {
        "key": "levels",
        "label": "等级数",
        "type": "number",
        "required": true,
        "group": "common",
        "order": 10,
        "priority": "high"
      },
      {
        "key": "showLegend",
        "label": "显示图例",
        "type": "boolean",
        "group": "common",
        "order": 20
      },
      {
        "key": "legendPosition",
        "label": "图例位置",
        "type": "select",
        "group": "advanced",
        "advanced": true,
        "order": 100,
        "visibleWhen": {
          "field": "showLegend",
          "equals": true
        }
      }
    ],
    "snapshotVersion": 1
  },
  "$comment": "配置迁移函数放入 JS 文件，通过 hooks.migrateConfig 的 path/export 引用。"
}</code></pre>
          </CodeCard>
          <ul class="check-list">
            <li>节点组件接收 `node / modelValue / readonly / mode / context / dataSourceRuntime`，并通过标准事件更新值。</li>
            <li>`snapshotVersion` 和 `migrateConfig` 用于读取历史发布快照；废弃参数至少保留一个兼容周期。</li>
            <li>同时通过 `/api/ui-extensions` 注册 `NODE` manifest；实现版本和快照版本必须与前端描述符一致。</li>
            <li>配置迁移会携带 manifest 和递归节点结构，但不会携带组件可执行代码；部署流水线必须先部署代码再激活快照。</li>
            <li>未知历史 `componentProps` 迁移到 `legacyProps`，扩展不得静默丢弃或直接执行其中脚本。</li>
            <li>设计器父容器候选必须复用平台父子类型规则，排除自身、后代和移动整棵子树后超过 8 层的目标；服务端 PATCH/reorder 必须重新计算并校验，不能只信前端过滤。</li>
          </ul>
        </section>

        <section id="node-property-schema" class="guide-section">
          <h3>4. 节点属性 Schema、绑定锁定与预览契约</h3>
          <el-table :data="nodePropertyRows" border size="small">
            <el-table-column prop="types" label="节点类型" width="210" />
            <el-table-column prop="editable" label="设计器可编辑属性" min-width="280" />
            <el-table-column prop="locked" label="不可编辑或不适用属性" min-width="280" />
          </el-table>
          <ul class="check-list">
            <li>属性抽屉默认关闭；选中画布节点后才从右侧打开。节点 ID、nodeKey、revision、orderKey、发布快照版本、bindingType 与 bindingRef 只能作为只读摘要展示。</li>
            <li>扩展 `configSchema` 的每个可编辑项应标注 `group: 'common' | 'advanced'`；也可使用自定义 group 对象。`order` 控制组内稳定顺序，`priority` 在未指定 order 时决定优先级，`advanced: true` 让低频或高风险组默认折叠，`visibleWhen` 使用结构化条件按当前配置显隐。未分组的历史 Schema 自动归入常用区。</li>
            <li>平台内置与扩展属性面板统一复用 `SettingsSection` 语义：常用区使用 `primary` 且不可折叠，高级区使用 `defaultExpanded=false`；不要为每个字段再创建一层折叠，也不要自行实现不同的展开状态和视觉规范。</li>
            <li>`visibleWhen` 只能描述字段路径、equals/notEquals、in/notIn、includes、exists、truthy/falsy 以及 all/any/not 等结构化条件；来自服务端或迁移包的 Schema 不得携带可执行脚本。生产数据源必须引用 Provider，实体引用的筛选范围通过已发布的选择列表配置。</li>
            <li>父容器是受限结构属性：TAB 只能选择 TAB_SET；TAB_SET 只直接接受 TAB；其他节点可位于根节点或 SECTION、GRID、TAB、COLLAPSE、SUB_FORM、REPEATER，不能直接放入 TAB_SET。</li>
            <li>创建与 PATCH 必须读取同一份 nodeType Schema 和服务端白名单；新增、编辑、整包 diff/upsert 不得出现不同的属性能力或绕过路径。</li>
            <li>历史节点编辑保存时按当前 Schema 归一化：不兼容的活动 props、rules、组件参数和数据源绑定必须清除，必要原值仅进入 `legacyProps` 等非活动兼容区，运行时和发布快照不得继续消费。</li>
            <li>实体字段或实体关系已经绑定时，`nodeType`、fieldId、fieldCode、关系与子实体绑定必须锁定。需要改变数据语义时创建新节点，再显式迁移可复用显示配置。</li>
            <li>RELATION 子表节点的 childEntityId、relationType、childRefFieldCode 和 relationCode 必须由实体关系定义反向计算；请求值只能匹配，不能补写或覆盖。引用 release 所属表单的 entityId 必须等于关系子实体。</li>
            <li>绑定迁移采用“新建目标节点 → 绑定新字段/关系 → 迁移标签、布局、兼容组件和允许的数据源 → 预览与发布 → 删除旧节点”，禁止原地改 bindingType/bindingRef。</li>
            <li>扩展 manifest 必须声明适用 `nodeTypes`、supportedBindings 与 configSchema。设计器只渲染该类型允许的参数；后端 PATCH 仍须按节点类型白名单拒绝未知、不兼容或已锁定字段。</li>
            <li>组件切换只能发生在兼容的实体字段类型集合内；切换后应清除不兼容的组件参数、校验和数据源绑定，不能静默保留无效配置。</li>
            <li>设计画布、草稿预览和激活 release 使用同一递归节点布局：新表单统一 grid 并读取 gridSpan，历史垂直 / 水平仍按 24 / 12 栅格兼容，显式 GRID 容器优先；标签位置与宽度由 viewConfig.labelPosition / labelWidth 独立控制；容器节点不能在预览中退回扁平字段列表。</li>
          </ul>
          <CodeCard title="扩展属性自动分组" language="Vue">
            <pre v-pre><code>&lt;ConfigSchemaEditor
  v-model="nodeConfig"
  :schema="descriptor.configSchema"
/&gt;

// ConfigSchemaEditor 根据 group / advanced 自动复用 SettingsSection：
// common 首屏展开；advanced 默认折叠；visibleWhen 不满足时不渲染。</code></pre>
          </CodeCard>
        </section>

        <section id="field-config" class="guide-section">
          <h3>5. 动态列配置模型</h3>
          <el-descriptions :column="1" border>
            <el-descriptions-item label="fieldCode">运行时唯一键；实体字段使用原字段编码，虚拟列使用自定义安全编码。</el-descriptions-item>
            <el-descriptions-item label="dataSourceType">固定三种取值方式：`ENTITY_FIELD`、`FIELD_TEMPLATE`、`INTERFACE`。</el-descriptions-item>
            <el-descriptions-item label="dataSourceConfig">仅字段组合使用的模板参数，例如 template；接口实现参数在扩展管理中维护。</el-descriptions-item>
            <el-descriptions-item label="interfaceExtensionId">取值方式为 INTERFACE 时必填；绑定已启用的 LIST / READ 扩展接口。</el-descriptions-item>
            <el-descriptions-item label="renderComponent">单元格组件注册名；留空时自动选择默认文本或字段类型渲染。</el-descriptions-item>
            <el-descriptions-item label="renderConfig">单元格组件参数，不再与数据源参数混用。</el-descriptions-item>
            <el-descriptions-item label="queryType">支持 EQ、NE、LIKE、NOT_LIKE、GT、GE、LT、LE、BETWEEN、IN、NOT_IN、EMPTY、NOT_EMPTY。</el-descriptions-item>
            <el-descriptions-item label="queryConfig">查询组件、占位提示和默认值。</el-descriptions-item>
            <el-descriptions-item label="columnConfig">快捷复制、固定位置、最小宽度、溢出提示等列展示配置。</el-descriptions-item>
          </el-descriptions>
          <div class="tips">
            <p>实体查询字段不要求同时显示在列表中。虚拟列只负责展示，不允许筛选或排序；扩展值仅在数据库分页后补充。</p>
          </div>
        </section>

        <section id="provider" class="guide-section">
          <h3>6. 列表列统一扩展接口</h3>
          <p>复杂关联、聚合和业务计算实现 <code>UiDataSourceProvider</code> 并注册 Spring Bean。在扩展管理选择已注册 Provider，创建上下文为 LIST、类型为 READ 的接口；列表列选择「取值方式：扩展接口」后绑定该接口。</p>
          <p>可运行示例为 <code>ProjectCustomListUiDataSourceProvider</code>，编码 <code>PROJECT_CUSTOM_UI_LIST</code>。配置 <code>{"columnPrefix":"项目状态"}</code> 后，其 LIST_COLUMN 分支为当前页生成展示值。配置参数集中保存在扩展接口，列仅保存 <code>interfaceExtensionId</code>。</p>
          <CodeCard title="LIST_COLUMN 输入输出约定" language="Java">
            <pre v-pre><code>// UiDataSourceProvider.execute 的四个参数：
// context：服务端生成的 ListInvocationContext，context.usage() == "LIST_COLUMN"
// dataScopePlan：不可绕过的数据权限约束
// configuration：扩展管理保存的实现参数，如 columnPrefix
// input：records（当前页记录）及 field（当前列配置）

// 返回记录 ID → 展示值，平台合并到 extData[当前列编码]。
// 键必须是 records 中的记录 ID，不使用数组下标，也不写回原始 data。
return Map.of(
    "record-001", "项目状态：审批中",
    "record-002", "项目状态：已通过"
);</code></pre>
          </CodeCard>
          <p>扩展列在数据库分页后计算，不支持查询或排序。字段组合继续使用内置 FIELD_TEMPLATE；业务自定义统一走扩展接口，不再注册独立列数据源类型。</p>
        </section>

        <section id="provider-context" class="guide-section">
          <h3>7. 提供者上下文与约束</h3>
          <el-table :data="providerContext" border size="small">
            <el-table-column prop="key" label="上下文键" width="180" />
            <el-table-column prop="meaning" label="含义" />
          </el-table>
          <ul class="check-list">
            <li>使用批量查询，禁止在每条记录中单独访问数据库或远程接口。</li>
            <li>列接口返回记录 ID 到值的 Map，由平台合并到 `extData`，不要覆盖实体原始 `data`。</li>
            <li>外部调用必须设置超时、限流和降级；敏感信息不能写入配置 JSON。</li>
            <li>自定义查询仍受实体数据权限约束，提供者只能处理已授权记录。</li>
            <li>需要接管整页查询时实现 `EntityListDataProvider`，平台会传入不可绕过的 `DataScopePlan`。</li>
            <li>来源记录联动使用 `EntityListContextResolverProvider`，不要信任前端直接传入的客户、部门、项目或组织 ID。</li>
          </ul>
        </section>

        <section id="unified-data-source" class="guide-section">
          <h3>8. 统一扩展接口目录</h3>
          <el-table :data="dataSourceTypes" border size="small">
            <el-table-column prop="type" label="sourceType" width="230" />
            <el-table-column prop="capability" label="能力与安全限制" />
          </el-table>
          <el-table :data="dataSourceBindings" border size="small" style="margin-top: 12px">
            <el-table-column prop="binding" label="绑定位置" width="230" />
            <el-table-column prop="meaning" label="用途" />
          </el-table>
          <ul class="check-list">
            <li>禁止任意 SQL、JavaScript、Groovy、SpEL、动态类名和外网 URL；外部调用只能封装在已注册 Provider 中，凭据由服务端安全管理。</li>
            <li>`FORM_INIT` 是表单级绑定；FIELD 仅允许 `FIELD_OPTIONS / FIELD_DEFAULT / FIELD_COMPUTE / AFTER_LOAD / BEFORE_SUBMIT`。</li>
            <li>SUB_FORM 与 REPEATER 仅允许 `SUBFORM_ROWS / AFTER_LOAD / BEFORE_SUBMIT`，不能借用 FIELD Usage 配置选项、默认值或字段计算。</li>
            <li>一个 `INTERFACE` 扩展记录对应一个完整可调用接口；页面只保存 `extensionId`，不再选择“服务 + 操作”。</li>
            <li>配置 Schema、输入映射、输出映射、分页、超时、缓存和失败策略在预览与发布时统一校验。</li>
            <li>所有实体查询、LIST_QUERY 和 LIST_COLUMN 都接收不可绕过的 `DataScopePlan`；缓存键必须包含用户、权限版本和发布版本。</li>
            <li>`AFTER_LOAD` 不得重新拼回未授权字段，`BEFORE_SUBMIT` 的关键校验失败策略必须为 FAIL。</li>
            <li>运行时组件调用 `POST /api/ui-runtime/extensions/execute`，只提交已发布的 `extensionId` 和业务输入；管理员调试使用 `/api/ui-extensions/{id}/preview`。嵌套 `SUB_FORM/REPEATER` 必须为每条行记录传入独立业务数据，表单和实体身份由服务端解析。</li>
          </ul>
        </section>

        <section id="data-source-spi" class="guide-section">
          <h3>9. Provider SPI</h3>
          <CodeCard title="UiDataSourceProvider.java" language="Java">
            <pre v-pre><code>public interface UiDataSourceProvider {
    String getCode();
    String getDisplayName();
    default int getVersion() { return 1; }
    default String getArtifactDigest() {
        return UiProviderArtifactIdentity.defaultDigest(
            getClass(), getVersion());
    }
    default Map&lt;String, Object&gt; configurationSchema() {
        return Map.of();
    }

    Object execute(
        UiInvocationContext context,
        DataScopePlan dataScopePlan,
        Map&lt;String, Object&gt; configuration,
        Map&lt;String, Object&gt; input
    );
}</code></pre>
          </CodeCard>
          <ul class="check-list">
            <li>自定义实现加 `@Component`（或显式声明 `@Bean`）；Provider 的 `getCode()` 必须全局唯一。</li>
            <li>同一 `getCode() + getVersion()` 对应的实现必须保持不可变；逻辑或依赖变化时提升版本。默认摘要覆盖实现类字节码，依赖额外模型、脚本或资源时必须覆盖 `getArtifactDigest()`，返回整个受审制品的稳定 SHA-256。</li>
            <li>表单事件 Provider 可参考 `ProjectCustomFormUiDataSourceProvider`。已有多入口 Provider 可在扩展管理高级配置中填写内部实现入口；该值只供后端路由，不暴露给设计器。</li>
            <li>`UiInvocationContext` 按 FORM、LIST、ENTITY 分型，只暴露服务端解析的用户、实体、页面、发布版本和绑定位置等可信元数据。</li>
            <li>列表列业务扩展统一使用 `UiDataSourceProvider` 的 `LIST_COLUMN` 调用；内置字段组合在平台内部执行。</li>
            <li>Provider 必须支持超时、批量、取消、可观测 traceId 和结构化错误，不得按行发起 N+1 远程请求。</li>
          </ul>
        </section>

        <section id="release-api" class="guide-section">
          <h3>10. 草稿与发布接口</h3>
          <el-descriptions :column="1" border>
            <el-descriptions-item label="/draft">返回可编辑草稿、稳定项目 ID、revision 和未发布状态。</el-descriptions-item>
            <el-descriptions-item label="/diff">按稳定 ID 比较草稿与当前发布，changedItems[] 描述新增、修改、移动、删除。</el-descriptions-item>
            <el-descriptions-item label="/publish-preview">校验完整配置、权限、引用和参数契约，返回 draftHash 和 activeReleaseId 并发前置条件。</el-descriptions-item>
            <el-descriptions-item label="/publish">表单覆盖唯一当前配置，发布后立即全局生效；列表按自己的版本机制发布。</el-descriptions-item>
            <el-descriptions-item label="/runtime">表单按稳定 ID 读取当前配置。configHash 用于检测配置变化，不是表单业务版本。</el-descriptions-item>
            <el-descriptions-item label="/releases、/release-summaries">仅列表提供发布历史查询。</el-descriptions-item>
            <el-descriptions-item label="/activate、/restore-draft">仅列表提供带原因的历史激活和恢复历史到草稿。</el-descriptions-item>
          </el-descriptions>
          <ul class="check-list">
            <li>表单草稿保存不生效，成功发布后新建、任务、历史详情、子表单、列表表单按钮与 Embed 全部采用当前配置。</li>
            <li>旧页面提交返回 409 FORM_CONFIGURATION_CHANGED，提示保留填写内容并重新加载核对。</li>
            <li>configHash 没有签名、有效期和续期，只用于检测当前页面依赖的配置变化。业务来源通过 runtimeContext 传递，由服务端实时鉴权。</li>
            <li>列表仍可由服务端根据父表单引用或 Embed 会话读取固定列表版本，表单配置不再固定到流程版本。</li>
          </ul>
        </section>

        <section id="migration-compatibility" class="guide-section">
          <h3>11. 迁移、废弃与运行时回退</h3>
          <ul class="check-list">
            <li>迁移必须幂等：旧表单字段转一级 FIELD 节点，列表项目保留已有 ID，缺失 ID 只生成一次。</li>
            <li>历史子表、引用、事件和选项迁入显式属性，未知内容进入 `legacyProps` 并写入迁移报告。</li>
            <li>历史节点首次按新 Schema 编辑保存时，不兼容配置从活动文档移除并保留为非活动兼容数据；后续 diff、发布和运行时不得再次把它提升为有效属性。</li>
            <li>列表生成初始不可变 release；表单仅保留唯一当前发布配置。报告节点数、未知属性和快照哈希。启动迁移必须按单个配置的 `REQUIRES_NEW` 事务执行并输出结构化失败报告，禁止一个坏配置回滚已成功项或阻塞应用启动。</li>
            <li>V112/V113 建立当前表单发布表，校验合法 ACTIVE 内容及稳定引用后复制；旧 FORM 记录只读归档。</li>
            <li>存量 `process_version_history.node_forms_snapshot` 由启动回填器分页 200 条幂等解析；配置 `workflow.ui-hotfix.binding-backfill-enabled=false` 可暂时关闭，缺省为开启。</li>
            <li>生产升级应核对回填日志中的 `histories`、`inserted`、`updated`、`missingRelease`、`invalidSnapshot`、`skippedExisting`；关闭回填只适合受控迁移窗口，完成核对后应重新开启。</li>
            <li>运行时只读取已发布配置，不存在时拒绝运行；不会临时回退旧配置或启用草稿。</li>
            <li>扩展废弃必须保留快照读取和配置迁移路径，不得因为 Provider 或组件升级导致历史 release 无法渲染。</li>
          </ul>
        </section>

        <section id="cell" class="guide-section">
          <h3>12. 前端单元格组件</h3>
          <p>组件注册时同时声明名称、说明和参数 Schema，设计器会自动生成参数表单，不需要管理员手写 JSON。</p>
          <CodeCard title="extension.ts" language="JSON">
            <pre v-pre><code>{
  "schemaVersion": 1,
  "type": "LIST_CELL",
  "name": "RiskBadgeCell",
  "label": "风险等级",
  "version": 1,
  "implementation": {
    "path": "src/extensions/common/list-cells/RiskBadgeCell.vue",
    "export": "default",
    "kind": "COMPONENT"
  },
  "metadata": {
    "configSchema": [
      {
        "key": "warningAt",
        "label": "预警阈值",
        "type": "number",
        "required": true
      },
      {
        "key": "dangerAt",
        "label": "高危阈值",
        "type": "number",
        "required": true
      }
    ]
  }
}</code></pre>
          </CodeCard>
          <CodeCard title="RiskBadgeCell.vue" language="Vue">
            <pre v-pre><code>&lt;script setup&gt;
const props = defineProps({
  value: [String, Number, Boolean, Object, Array],
  row: Object,
  field: Object,
  config: Object,
  context: Object
})
&lt;/script&gt;</code></pre>
          </CodeCard>
        </section>

        <section id="built-in" class="guide-section">
          <h3>13. 内置能力</h3>
          <el-table :data="builtIns" border size="small">
            <el-table-column prop="type" label="类型" width="180" />
            <el-table-column prop="capability" label="能力" />
          </el-table>
          <p class="muted">`FIELD_TEMPLATE` 仅替换 `${fieldCode}` 占位符，不执行 JavaScript、Groovy 或 SQL，可用于编号+名称、人员+部门等组合列。</p>
        </section>

        <section id="demo" class="guide-section">
          <h3>14. 可运行 Demo</h3>
          <p>仓库已经提供真实注册示例，不需要从文档片段重新拼装：</p>
          <ul class="check-list">
            <li>`src/extensions/examples/demo/list-fields/DemoRiskProgressCell.vue`：读取 `value / row / field / config / context`，展示风险进度和等级。</li>
            <li>`extensions/manifests/examples/demo/`：以 `DemoRiskProgressCell` 注册组件，并声明数值字段类型与四项可视化参数。</li>
            <li>`scripts/real-dynamic-extension-demo.mjs`：创建实体、动态列、定制列表、定制表单和流程，验证配置真实生效。</li>
            <li>执行 `npm run test:demo:real`；最近一次结果保存在 `docs/dynamic-extension-demo/latest.json`。</li>
          </ul>
          <el-alert
            title="单元格展示参数请保存到 renderConfig；dataSourceConfig 只描述数据如何产生。运行时仅为历史配置兼容才回退读取 dataSourceConfig。"
            type="warning"
            :closable="false"
            show-icon
          />
        </section>

        <section id="acceptance" class="guide-section">
          <h3>15. 验收清单</h3>
          <ul class="check-list">
            <li>虚拟列能显示、排序位置正确，空值有明确占位。</li>
            <li>隐藏的实体查询项仍可查询；虚拟筛选和排序在保存、发布及请求时显式拒绝。</li>
            <li>未注册数据源不能保存；历史未注册展示字段不会绕过查询过滤。</li>
            <li>自定义组件不存在时回退默认文本，不影响列表基本访问。</li>
            <li>数据权限、功能权限和行操作能力仍由后端统一计算。</li>
            <li>单项修改不改变其他项目的 ID、revision、更新时间和内容。</li>
            <li>草稿不影响线上，发布原子生效，历史 release 可 activate 回滚。</li>
            <li>迁移重复执行结果一致，旧配置临时回退和初始 release 哈希可核对。</li>
          </ul>
        </section>
      </main>

      <aside class="guide-toc">
        <div class="toc-title">目录</div>
        <a v-for="item in toc" :key="item.id" :href="`#${item.id}`">{{ item.label }}</a>
      </aside>
    </div>
  </div>
</template>

<script setup>
import CodeCard from '@/components/dev-guide/CodeCard.vue'

const toc = [
  { id: 'levels', label: '扩展层级' },
  { id: 'config-contract', label: '单项与并发' },
  { id: 'form-node-spi', label: '表单节点 SPI' },
  { id: 'node-property-schema', label: '属性与预览约束' },
  { id: 'field-config', label: '动态列配置' },
  { id: 'provider', label: '后端提供者' },
  { id: 'provider-context', label: '上下文与约束' },
  { id: 'unified-data-source', label: '统一数据源' },
  { id: 'data-source-spi', label: 'Provider SPI' },
  { id: 'release-api', label: '草稿与发布' },
  { id: 'migration-compatibility', label: '迁移兼容' },
  { id: 'cell', label: '单元格组件' },
  { id: 'built-in', label: '内置能力' },
  { id: 'demo', label: '可运行 Demo' },
  { id: 'acceptance', label: '验收清单' }
]

const extensionLevels = [
  { level: '声明式节点/列', useCase: '常规布局、字段、查询、选项和动作', implementation: '使用稳定 ID 项目和统一数据源配置' },
  { level: '节点级组件', useCase: '评分、签名、复杂单元格和局部交互', implementation: '注册表单节点组件或单元格组件，平台继续管理布局、权限和发布' },
  { level: '声明式虚拟列', useCase: '字段拼接、轻量派生值', implementation: '添加虚拟列，取值方式选择 FIELD_TEMPLATE 字段组合' },
  { level: '扩展接口', useCase: '关联、聚合、远程业务数据', implementation: '实现 UiDataSourceProvider，创建 LIST / READ 接口并绑定列' },
  { level: '自定义单元格', useCase: '进度、标签、图片、复杂交互展示', implementation: 'LIST_CELL JSON 清单声明 Vue 组件' },
  { level: '整页自定义', useCase: '平台节点树或表格运行时无法表达的整体交互', implementation: '使用整表单/整列表组件，但仍复用发布快照、数据源与权限运行时' }
]

const nodeTypes = [
  { type: 'SECTION / GRID', capability: '业务区块和栅格容器，可递归包含布局或内容节点' },
  { type: 'TAB_SET / TAB', capability: 'TAB_SET 只直接包含 TAB；TAB 可递归承载字段、布局、文本和其他合法内容节点' },
  { type: 'COLLAPSE / TEXT', capability: '折叠容器与无数据说明节点；TEXT 不执行脚本' },
  { type: 'FIELD', capability: '绑定实体字段、实体关系、计算字段或上下文字段' },
  { type: 'SUB_FORM / REPEATER', capability: '引用已发布子表单版本或一对多明细；检查跨表单循环' },
  { type: 'ACTION_SLOT', capability: '受控动作插槽；权限和操作校验仍由平台负责' }
]

const nodePropertyRows = [
  { types: 'SECTION', editable: '显示标签（区块标题）、合法父容器；用于组织业务区块。', locked: '不显示组件、默认值、实体绑定、字段规则或数据源。' },
  { types: 'GRID', editable: '合法父容器、列间距 gutter、默认子项跨度 defaultSpan；用于控制栅格布局。', locked: '子项 gridSpan 属于子节点；GRID 不配置字段规则、组件或数据源。' },
  { types: 'TAB_SET', editable: '合法父容器、页签位置 tabPosition；用于组织 Tab 页集合。', locked: '直接子项只能是 TAB；当前不提供默认激活页配置。' },
  { types: 'TAB', editable: '页签标题、所属 TAB_SET；用于承载页签内递归内容。', locked: '不能位于根节点，父级只能是有效 TAB_SET；无字段规则和数据源。' },
  { types: 'COLLAPSE', editable: '标题、合法父容器、默认展开 defaultExpanded、手风琴 accordion；用于折叠内容组。', locked: '不显示字段组件、默认值、校验或字段数据源。' },
  { types: 'TEXT', editable: '合法父容器、受限说明内容 text；用于提示和静态文本。', locked: '禁止脚本、任意 HTML、事件、实体绑定、字段规则和数据源。' },
  { types: 'FIELD', editable: '显示标签、父容器、兼容组件、必填/只读/隐藏、默认值、占位、组件参数、类型兼容校验、模式权限、gridSpan、事件、节点扩展和受控数据源。', locked: 'Usage 仅 FIELD_OPTIONS、FIELD_DEFAULT、FIELD_COMPUTE、AFTER_LOAD、BEFORE_SUBMIT；长度/格式仅 STRING、TEXT，范围仅数值类型；绑定身份不可改。' },
  { types: 'SUB_FORM', editable: '显示标签、父容器、子表布局、已发布子表单版本、gridSpan、节点扩展和受控行数据源。', locked: '展示位置由父容器决定；Usage 仅 SUBFORM_ROWS、AFTER_LOAD、BEFORE_SUBMIT，子实体、关系与外键绑定不可改。' },
  { types: 'REPEATER', editable: '显示标签、父容器、明细布局、已发布子表单版本、gridSpan、节点扩展和受控行数据源。', locked: '展示位置由父容器决定；Usage 仅 SUBFORM_ROWS、AFTER_LOAD、BEFORE_SUBMIT，不支持 FIELD 默认值、普通组件、校验、模式权限或事件。' },
  { types: 'ACTION_SLOT', editable: '仅合法父容器；用于放置稳定的运行时动作插槽。', locked: '插槽标识只读；当前不开放动作、权限、位置、字段规则或数据源编辑。' }
]

const dataSourceTypes = [
  { type: 'DICTIONARY', capability: '平台字典，输出稳定 label/value' },
  { type: 'STATIC_OPTIONS', capability: '少量固定选项或对象，不存储敏感信息' },
  { type: 'REGISTERED_PROVIDER', capability: '部署时注册的 Provider，声明 Schema 与支持位置' },
  { type: 'RUNTIME_CONTEXT', capability: '读取白名单运行上下文，客户端值不能作为授权事实' },
  { type: 'STRUCTURED_COMPUTE', capability: '白名单运算符和路径，不执行任意脚本或 SQL' }
]

const dataSourceBindings = [
  { binding: 'FORM_INIT', meaning: '表单级初始化业务对象，不属于节点级 Usage' },
  { binding: 'FIELD_OPTIONS / FIELD_DEFAULT', meaning: '仅 FIELD：字段选项与默认值' },
  { binding: 'FIELD_COMPUTE', meaning: '仅 FIELD：结构化字段计算' },
  { binding: 'SUBFORM_ROWS', meaning: '仅 SUB_FORM / REPEATER：子表或明细行加载' },
  { binding: 'LIST_QUERY / LIST_COLUMN', meaning: '整表查询与单列扩展' },
  { binding: 'AFTER_LOAD / BEFORE_SUBMIT', meaning: 'FIELD、SUB_FORM、REPEATER：加载后转换与提交前校验/映射' }
]



const providerContext = [
  { key: 'entityCode', meaning: '当前实体编码' },
  { key: 'listKey', meaning: '当前列表标识' },
  { key: 'scene', meaning: 'MENU、PAGE、DIALOG、DRAWER、EMBEDDED、FORM_PICKER 或 SUB_TABLE' },
  { key: 'DataScopePlan', meaning: '后端已计算的数据范围 SQL、命中方案、说明和发布版本；自定义查询必须执行' },
  { key: 'userId / userName', meaning: '当前登录用户，用于业务上下文；不能替代数据权限校验' }
]

const builtIns = [
  { type: 'ENTITY_FIELD', capability: '读取实体系统字段或自定义字段，支持数据库查询条件' },
  { type: 'FIELD_TEMPLATE', capability: '安全字段占位符组合，虚拟列只用于展示' },
  { type: 'DefaultText', capability: '默认文本与空值占位' },
  { type: 'StatusBadge', capability: '状态文本和颜色映射' },
  { type: 'DateFormatter', capability: '安全日期模板格式化' }
]
</script>

<style scoped src="./dev-guide-shared.scss"></style>
