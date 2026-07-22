<template>
  <div class="guide-page">
    <div class="guide-header">
      <div>
        <h2>表单与列表配置扩展</h2>
        <p>稳定节点、单项保存、统一数据源、发布快照、模板升级和前后端扩展 SPI 的完整契约。</p>
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
            <li>表单节点、列表列、按钮和场景都以稳定 `id` 定位；禁止使用数组下标、显示名称或排序值充当业务键。</li>
            <li>属性面板只 PATCH 当前项目，拖拽单独保存 `orderKey`；不同项目可并行保存，不再全删全插。</li>
            <li>所有修改和删除请求必须携带 `expectedRevision`；成功后使用响应中的新 `revision` 更新 Store。</li>
            <li>同一项目发生并发冲突时返回 HTTP `409`，服务器当前对象位于响应 `data`；客户端以 `data.revision` 作为 serverRevision、以 `data` 作为 currentData，保留本地值并展示差异。</li>
            <li>兼容整包导入时，后端仍按稳定 ID 计算差异并 upsert，不得退回“删除全部再插入”。</li>
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
          <CodeCard title="form-node-extension.ts" language="TypeScript">
            <pre v-pre><code>registerFormNodeComponent('risk-matrix', RiskMatrixNode, {
  version: 3,
  nodeTypes: ['FIELD'],
  supportedBindings: ['ENTITY_FIELD', 'COMPUTED'],
  configSchema: [
    { key: 'levels', label: '等级数', type: 'number', required: true }
  ],
  snapshotVersion: 1,
  migrateConfig({ fromVersion, config }) {
    return fromVersion === 1 ? config : { ...config, levels: 5 }
  }
})</code></pre>
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
            <li>父容器是受限结构属性：TAB 只能选择 TAB_SET；TAB_SET 只直接接受 TAB；其他节点可位于根节点或 SECTION、GRID、TAB、COLLAPSE、SUB_FORM、REPEATER，不能直接放入 TAB_SET。</li>
            <li>实体字段或实体关系已经绑定时，`nodeType`、fieldId、fieldCode、关系与子实体绑定必须锁定。需要改变数据语义时创建新节点，再显式迁移可复用显示配置。</li>
            <li>扩展 manifest 必须声明适用 `nodeTypes`、supportedBindings 与 configSchema。设计器只渲染该类型允许的参数；后端 PATCH 仍须按节点类型白名单拒绝未知、不兼容或已锁定字段。</li>
            <li>组件切换只能发生在兼容的实体字段类型集合内；切换后应清除不兼容的组件参数、校验和数据源绑定，不能静默保留无效配置。</li>
            <li>设计画布、草稿预览和激活 release 使用同一递归节点布局：垂直默认 24 栅格、水平默认 12 栅格、网格读取 gridSpan，显式 GRID 容器优先；容器节点不能在预览中退回扁平字段列表。</li>
          </ul>
        </section>

        <section id="field-config" class="guide-section">
          <h3>5. 动态列配置模型</h3>
          <el-descriptions :column="1" border>
            <el-descriptions-item label="fieldCode">运行时唯一键；实体字段使用原字段编码，虚拟列使用自定义安全编码。</el-descriptions-item>
            <el-descriptions-item label="dataSourceType">`ENTITY_FIELD` 或已注册的 `ListFieldDataProvider` 编码。</el-descriptions-item>
            <el-descriptions-item label="dataSourceConfig">数据提供者参数，按提供者声明的 `configSchema` 可视化编辑。</el-descriptions-item>
            <el-descriptions-item label="renderComponent">单元格组件注册名；留空时自动选择默认文本或字段类型渲染。</el-descriptions-item>
            <el-descriptions-item label="renderConfig">单元格组件参数，不再与数据源参数混用。</el-descriptions-item>
            <el-descriptions-item label="queryType">支持 EQ、NE、LIKE、NOT_LIKE、GT、GE、LT、LE、BETWEEN、IN、NOT_IN、EMPTY、NOT_EMPTY。</el-descriptions-item>
            <el-descriptions-item label="queryConfig">查询组件、占位提示和默认值。</el-descriptions-item>
            <el-descriptions-item label="columnConfig">固定位置、最小宽度、溢出提示等列展示配置。</el-descriptions-item>
          </el-descriptions>
          <div class="tips">
            <p>查询字段不要求同时显示在列表中。虚拟查询字段会先完成扩展值计算，再由后端结构化条件过滤，不会把虚拟字段拼进实体 SQL。</p>
          </div>
        </section>

        <section id="provider" class="guide-section">
          <h3>6. 既有列表数据提供者</h3>
          <p>复杂关联、聚合或业务计算通过 Spring Bean 注册。编码必须稳定且唯一，配置保存时后端会校验数据源是否存在、参数是否完整、是否支持虚拟列和查询。</p>
          <CodeCard title="CustomerLevelProvider.java" language="Java">
            <pre v-pre><code>@Component
public class CustomerLevelProvider implements ListFieldDataProvider {
    @Override
    public String getDataSourceType() {
        return "CUSTOMER_LEVEL";
    }

    @Override
    public String getDisplayName() {
        return "客户等级";
    }

    @Override
    public String getDescription() {
        return "根据客户ID批量查询等级，避免逐行请求。";
    }

    @Override
    public boolean supportsQuery() {
        return true;
    }

    @Override
    public List&lt;Map&lt;String, Object&gt;&gt; getConfigSchema() {
        return List.of(Map.of(
            "key", "customerField",
            "label", "客户字段",
            "type", "text",
            "required", true
        ));
    }

    @Override
    public void enrich(List&lt;EntityDataDTO&gt; records,
                       List&lt;EntityListField&gt; fields,
                       Map&lt;String, Object&gt; context) {
        // 1. 汇总当前页/当前结果集中的客户ID
        // 2. 一次批量查询客户等级
        // 3. 写入 record.extData[field.getFieldCode()]
    }
}</code></pre>
          </CodeCard>
          <el-alert
            title="查询条件命中未注册的数据源时后端拒绝请求，避免扩展查询失效后返回未过滤数据。"
            type="warning"
            :closable="false"
            show-icon
          />
        </section>

        <section id="provider-context" class="guide-section">
          <h3>7. 提供者上下文与约束</h3>
          <el-table :data="providerContext" border size="small">
            <el-table-column prop="key" label="上下文键" width="180" />
            <el-table-column prop="meaning" label="含义" />
          </el-table>
          <ul class="check-list">
            <li>使用批量查询，禁止在每条记录中单独访问数据库或远程接口。</li>
            <li>扩展值写入 `extData`，不要覆盖实体原始 `data`。</li>
            <li>外部调用必须设置超时、限流和降级；敏感信息不能写入配置 JSON。</li>
            <li>自定义查询仍受实体数据权限约束，提供者只能处理已授权记录。</li>
            <li>需要接管整页查询时实现 `EntityListDataProvider`，平台会传入不可绕过的 `DataScopePlan`。</li>
            <li>来源记录联动使用 `EntityListContextResolver`，不要信任前端直接传入的客户、部门、项目或组织 ID。</li>
          </ul>
        </section>

        <section id="unified-data-source" class="guide-section">
          <h3>8. 统一数据源目录</h3>
          <el-table :data="dataSourceTypes" border size="small">
            <el-table-column prop="type" label="sourceType" width="230" />
            <el-table-column prop="capability" label="能力与安全限制" />
          </el-table>
          <el-table :data="dataSourceBindings" border size="small" style="margin-top: 12px">
            <el-table-column prop="binding" label="绑定位置" width="230" />
            <el-table-column prop="meaning" label="用途" />
          </el-table>
          <ul class="check-list">
            <li>禁止任意 SQL、JavaScript、Groovy、SpEL、动态类名和外网 URL；外部调用只能引用已注册 Connector 与平台凭据。</li>
            <li>配置 Schema、输入映射、输出映射、分页、超时、缓存和失败策略在预览与发布时统一校验。</li>
            <li>所有实体查询、LIST_QUERY 和 LIST_COLUMN 都接收不可绕过的 `DataScopePlan`；缓存键必须包含用户、权限版本和发布版本。</li>
            <li>`AFTER_LOAD` 不得重新拼回未授权字段，`BEFORE_SUBMIT` 的关键校验失败策略必须为 FAIL。</li>
            <li>运行时组件调用 `POST /api/ui-data-sources/{id}/execute`；管理员调试使用 `/preview`。禁止让普通运行时复用可查看配置的管理接口。嵌套 `SUB_FORM/REPEATER` 必须为每条行记录传入独立 `record`、`recordId`、child `formId` 和 `entityId`，不能复用父记录上下文。</li>
          </ul>
        </section>

        <section id="data-source-spi" class="guide-section">
          <h3>9. Provider 与 Connector SPI</h3>
          <CodeCard title="UiDataSourceProvider.java" language="Java">
            <pre v-pre><code>public interface UiDataSourceProvider {
    String code();
    Set&lt;UiDataSourceBinding&gt; supportedBindings();
    JsonNode configSchema();
    JsonNode inputSchema();
    JsonNode outputSchema();

    UiDataSourceResult execute(
        UiDataSourceDefinition definition,
        UiDataSourceRequest request,
        UiDataSourceContext context,
        DataScopePlan dataScopePlan
    );
}</code></pre>
          </CodeCard>
          <CodeCard title="IntegrationConnector.java" language="Java">
            <pre v-pre><code>public interface IntegrationConnector {
    String code();
    ConnectorResult invoke(
        String operation,
        JsonNode input,
        ConnectorExecutionPolicy policy,
        CredentialReference credential
    );
}</code></pre>
          </CodeCard>
          <ul class="check-list">
            <li>`UiDataSourceContext` 只暴露当前用户、实体、记录、表单/列表 release、场景和可信来源关系等白名单上下文。</li>
            <li>Connector 配置只保存 `connectorCode + operation + credentialRef`；URL、令牌和密钥由平台连接器中心管理。</li>
            <li>现有 `EntityListDataProvider` 和 `ListFieldDataProvider` 通过适配器接入 `LIST_QUERY`、`LIST_COLUMN`，保持已有扩展兼容。</li>
            <li>Provider 必须支持超时、批量、取消、可观测 traceId 和结构化错误，不得按行发起 N+1 远程请求。</li>
          </ul>
        </section>

        <section id="release-api" class="guide-section">
          <h3>10. 草稿、发布、回滚与模板升级</h3>
          <el-descriptions :column="1" border>
            <el-descriptions-item label="/draft">返回可编辑草稿、稳定项目 ID、revision 和未发布状态。</el-descriptions-item>
            <el-descriptions-item label="/diff">按稳定 ID 比较草稿与激活快照，并返回树、数据源、权限、模板和兼容校验；`changedItems[]` 使用 section、id、label、changeType、changedFields 标识新增、修改、移动、删除，`changedSections` 仅保留兼容用途。</el-descriptions-item>
            <el-descriptions-item label="/publish">原子创建不可变 release，保存内容哈希、发布人、发布时间并激活；失败时旧版本继续服务。</el-descriptions-item>
            <el-descriptions-item label="/releases">列出历史版本、哈希和激活状态。</el-descriptions-item>
            <el-descriptions-item label="/activate">重新校验并激活指定历史版本，不修改历史快照。</el-descriptions-item>
          </el-descriptions>
          <CodeCard title="发布请求与响应" language="HTTP">
            <pre v-pre><code>POST /api/entity-forms/frm_order/publish
Content-Type: application/json

{
  "description": "订单表单金额区块升级"
}

200 OK
{
  "code": 200,
  "message": "success",
  "data": {
    "id": "fr_20260718_001",
    "version": 4,
    "contentHash": "8f1c...",
    "status": "ACTIVE",
    "publishedAt": "2026-07-18T10:45:00"
  }
}</code></pre>
          </CodeCard>
          <ul class="check-list">
            <li>模板实例固定 `templateId + templateVersion + localOverrides`，模板发布新版本不会自动级联。</li>
            <li>显式升级使用“旧模板、目标模板、本地覆盖”三方合并；冲突逐项确认后只写入草稿，再预览和发布。</li>
            <li>“复制后独立”不保留模板关系，适合无需后续升级的局部配置。</li>
          </ul>
        </section>

        <section id="migration-compatibility" class="guide-section">
          <h3>11. 迁移、废弃与运行时回退</h3>
          <ul class="check-list">
            <li>迁移必须幂等：旧表单字段转一级 FIELD 节点，列表项目保留已有 ID，缺失 ID 只生成一次。</li>
            <li>历史子表、引用、事件和选项迁入显式属性，未知内容进入 `legacyProps` 并写入迁移报告。</li>
            <li>已有表单和列表生成初始不可变 release；报告节点数、未知属性、快照哈希和版本。启动迁移必须按单个配置的 `REQUIRES_NEW` 事务执行并输出结构化失败报告，禁止一个坏配置回滚已成功项或阻塞应用启动。</li>
            <li>新运行时优先读取激活 release；不存在时仅临时回退旧配置并记录告警，生成初始 release 后停止依赖回退。</li>
            <li>扩展废弃必须保留快照读取和配置迁移路径，不得因为 Provider、Connector 或组件升级导致历史 release 无法渲染。</li>
          </ul>
        </section>

        <section id="cell" class="guide-section">
          <h3>12. 前端单元格组件</h3>
          <p>组件注册时同时声明名称、说明和参数 Schema，设计器会自动生成参数表单，不需要管理员手写 JSON。</p>
          <CodeCard title="extension.ts" language="TypeScript">
            <pre v-pre><code>import { registerCellComponent } from '@/utils/listCellRegistry'
import RiskBadgeCell from './RiskBadgeCell.vue'

registerCellComponent('RiskBadgeCell', RiskBadgeCell, {
  label: '风险等级',
  description: '按阈值显示风险标签',
  configSchema: [
    { key: 'warningAt', label: '预警阈值', type: 'number', required: true },
    { key: 'dangerAt', label: '高危阈值', type: 'number', required: true }
  ]
})</code></pre>
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
            <li>`src/demo/list-fields/DemoRiskProgressCell.vue`：读取 `value / row / field / config / context`，展示风险进度和等级。</li>
            <li>`src/demo/index.js`：以 `DemoRiskProgressCell` 注册组件，并声明数值字段类型与四项可视化参数。</li>
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
            <li>隐藏查询项仍可查询，虚拟查询不会触发未知数据库列错误。</li>
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
  { id: 'data-source-spi', label: 'Provider / Connector' },
  { id: 'release-api', label: '发布与模板' },
  { id: 'migration-compatibility', label: '迁移兼容' },
  { id: 'cell', label: '单元格组件' },
  { id: 'built-in', label: '内置能力' },
  { id: 'demo', label: '可运行 Demo' },
  { id: 'acceptance', label: '验收清单' }
]

const extensionLevels = [
  { level: '声明式节点/列', useCase: '常规布局、字段、查询、选项和动作', implementation: '使用稳定 ID 项目和统一数据源配置' },
  { level: '节点级组件', useCase: '评分、签名、复杂单元格和局部交互', implementation: '注册表单节点组件或单元格组件，平台继续管理布局、权限和发布' },
  { level: '声明式虚拟列', useCase: '字段拼接、轻量派生值', implementation: '添加虚拟列，选择 FIELD_TEMPLATE 等可配置数据源' },
  { level: '自定义数据提供者', useCase: '关联、聚合、远程业务数据', implementation: '实现 ListFieldDataProvider 并声明配置 Schema' },
  { level: '自定义单元格', useCase: '进度、标签、图片、复杂交互展示', implementation: 'registerCellComponent 注册 Vue 组件' },
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
  { types: 'SECTION / GRID', editable: '合法父容器、标题、显示标签和容器样式；GRID 还可配置列间距和默认跨度。', locked: '不显示字段组件、默认值、实体绑定、字段校验或字段数据源。' },
  { types: 'TAB_SET / TAB / COLLAPSE', editable: '合法父容器；TAB_SET 的页签位置；TAB 的页签标题和所属 Tab 集合；COLLAPSE 的标题、默认展开与手风琴模式。', locked: 'TAB 只能位于 TAB_SET；TAB_SET 的直接子节点只能是 TAB。' },
  { types: 'TEXT / ACTION_SLOT', editable: '合法父容器；TEXT 的受限说明内容；ACTION_SLOT 仅展示稳定插槽标识。', locked: 'TEXT 禁止脚本和实体绑定；ACTION_SLOT 暂不开放动作、权限或位置编辑。' },
  { types: 'FIELD', editable: '合法父容器、显示标签、兼容组件、必填、只读、隐藏、占位、默认值、校验、受控数据源、事件和模式权限。', locked: '不能直接放入 TAB_SET；已绑定时 nodeType、fieldId、fieldCode、bindingType 与 bindingRef 不可改。' },
  { types: 'SUB_FORM / REPEATER', editable: '合法父容器、展示模式、子表布局、已发布子表单版本与受控行数据源。', locked: '子实体、关系与外键不可由表单配置覆盖；其内嵌节点必须递归渲染。' }
]

const dataSourceTypes = [
  { type: 'ENTITY_QUERY', capability: '受控实体查询，强制执行 DataScopePlan' },
  { type: 'DICTIONARY', capability: '平台字典，输出稳定 label/value' },
  { type: 'STATIC_OPTIONS', capability: '少量固定选项或对象，不存储敏感信息' },
  { type: 'REGISTERED_PROVIDER', capability: '部署时注册的 Provider，声明 Schema 与支持位置' },
  { type: 'INTEGRATION_CONNECTOR', capability: '引用受控连接器、操作和凭据引用，不接受自由 URL' },
  { type: 'RUNTIME_CONTEXT', capability: '读取白名单运行上下文，客户端值不能作为授权事实' },
  { type: 'STRUCTURED_COMPUTE', capability: '白名单运算符和路径，不执行任意脚本或 SQL' }
]

const dataSourceBindings = [
  { binding: 'FORM_INIT', meaning: '初始化表单业务对象' },
  { binding: 'FIELD_OPTIONS / FIELD_DEFAULT', meaning: '字段选项与默认值' },
  { binding: 'FIELD_COMPUTE', meaning: '结构化字段计算' },
  { binding: 'SUBFORM_ROWS', meaning: '子表或明细行加载' },
  { binding: 'LIST_QUERY / LIST_COLUMN', meaning: '整表查询与单列扩展' },
  { binding: 'AFTER_LOAD / BEFORE_SUBMIT', meaning: '加载后转换与提交前校验/映射' }
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
  { type: 'FIELD_TEMPLATE', capability: '安全字段占位符组合，支持虚拟列和结构化查询' },
  { type: 'DefaultText', capability: '默认文本与空值占位' },
  { type: 'StatusBadge', capability: '状态文本和颜色映射' },
  { type: 'DateFormatter', capability: '安全日期模板格式化' }
]
</script>

<style scoped src="./dev-guide-shared.scss"></style>
