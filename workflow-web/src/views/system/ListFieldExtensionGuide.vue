<template>
  <div class="guide-page">
    <div class="guide-header">
      <div>
        <h2>列表字段扩展 2</h2>
        <p>一列数据怎么来、格子怎么画。先看总流程，再按需求往下做。</p>
      </div>
      <el-tag type="success">先总后分</el-tag>
    </div>

    <div class="guide-layout">
      <main class="guide-content">
        <section id="overview" class="guide-section">
          <h3>1. 先看全貌</h3>
          <p>列表每一列只做两件事：<strong>出数</strong>和<strong>显示</strong>。扩展时先想清楚你改哪一头，还是两头都改。</p>
          <el-table :data="overviewRows" border size="small">
            <el-table-column prop="part" label="这一头" width="100" />
            <el-table-column prop="what" label="要做什么" min-width="220" />
            <el-table-column prop="how" label="怎么做" min-width="280" />
          </el-table>
          <p>运行时顺序也是这两步：后端先查出当前页，再按列的数据源补 <code>extData</code>；前端按 <code>renderComponent</code> 画格子，<code>value</code> 的取值顺序是 <code>extData &gt; data &gt; 行顶层</code>。</p>
          <p>按需求选路径：</p>
          <ol class="check-list">
            <li>字段已经有值，只想换样子 → 第 2 节，只写一个单元格组件。</li>
            <li>要把已有字段拼成一列展示文本 → 第 3 节，设计器里配模板，不用写代码。</li>
            <li>这一列的值库里没有 → 第 4 节，使用统一扩展接口计算列值。</li>
            <li>自己出数，还要画进度条 → 第 5 节，把 2 和 4 叠在同一列；查询使用实体字段。</li>
            <li>设计器每一项都填了、组件里要把 props 用全 → 第 6 节，看一次请求里实际长什么样。</li>
          </ol>
        </section>

        <section id="cell" class="guide-section">
          <h3>2. 换显示：写一个单元格组件</h3>
          <p>平台已经把这一格的值和行数据传给你。组件就是普通 Vue 组件，内部怎么写 computed、怎么拆子组件、怎么用 Element Plus，都是你的事。平台只约定五个入参名字，别改。</p>

          <h4>2.1 要做什么</h4>
          <ol class="check-list">
            <li>写一个 Vue 组件，接收 <code>value / row / field / config / context</code>。</li>
            <li>新增 <code>LIST_CELL</code> 类型的 <code>*.extension.json</code>，声明 name、implementation、metadata。name 是设计器选项值，发布后保持稳定。</li>
            <li>打开实体列表设计 → 字段配置 → 该列「设置」→「数据与显示」→「渲染组件」选中它。</li>
            <li>元数据里的 <code>configSchema</code> 会自动变成参数表单，填完写进这一列的 <code>renderConfig</code>。</li>
            <li>点「保存当前列」。提示是「当前列已保存，尚未发布」。再发布列表，运行时才看得到。</li>
          </ol>

          <h4>2.2 平台传进来的五个 props</h4>
          <el-table :data="cellProps" border size="small">
            <el-table-column prop="name" label="prop" width="110" />
            <el-table-column prop="what" label="是什么" min-width="240" />
            <el-table-column prop="use" label="你怎么用" min-width="260" />
          </el-table>
          <p><code>value</code> 够用就只用 <code>value</code>。要拿同一行别的字段、原始 ID、点一下刷新列表，再去翻 <code>row</code> 和 <code>context</code>。组件里可以继续加自己的 computed、方法、样式，不需要向平台再注册一遍。</p>

          <h4>2.3 从最小组件抄起</h4>
          <p>仓库里能跑的例子是 <code>src/extensions/examples/demo/list-fields/DemoRiskProgressCell.vue</code>。开发环境启动后，渲染组件下拉里会有「Demo·风险进度」。自己做业务组件时，复制一份改名字即可。</p>
          <CodeCard title="单元格组件（带注释，可直接改）" language="Vue">
            <pre v-pre><code>&lt;template&gt;
  &lt;!-- 平台只负责把组件挂到格子里，模板怎么排是组件自己的事 --&gt;
  &lt;div class="risk-cell"&gt;
    &lt;el-progress
      :percentage="percentage"
      :status="progressStatus"
      :stroke-width="10"
      :show-text="config.showText !== false"
    /&gt;
    &lt;el-tag v-if="config.showLevel !== false" :type="tagType" size="small"&gt;
      {{ levelText }}
    &lt;/el-tag&gt;
  &lt;/div&gt;
&lt;/template&gt;

&lt;script setup&gt;
import { computed } from 'vue'

// 这五个名字是 ListCellRenderer 传下来的，缺省也能跑，但不要改名
const props = defineProps({
  value: { type: [String, Number], default: 0 },   // 这一格的展示值
  row: { type: Object, default: () => ({}) },      // 整行，需要旁路字段时再用
  field: { type: Object, default: () => ({}) },    // 列配置
  config: { type: Object, default: () => ({}) },   // 设计器里填的 renderConfig
  context: { type: Object, default: () => ({}) }   // entityCode、refresh 等运行时上下文
})

// 显示逻辑全部放组件内部。阈值来自 config，管理员改设计器即可，不用改代码
const percentage = computed(() => {
  const value = Number(props.value)
  if (Number.isNaN(value)) return 0
  return Math.min(100, Math.max(0, value))
})
const warningAt = computed(() => Number(props.config.warningAt ?? 40))
const dangerAt = computed(() => Number(props.config.dangerAt ?? 70))
&lt;/script&gt;</code></pre>
          </CodeCard>

          <h4>2.4 注册：让设计器能选到</h4>
          <p>实现与注册声明分开：组件放在所属模块，JSON 放在 <code>extensions/manifests/</code>。平台构建自动发现并注册，新增组件不修改入口。examples 清单仅在开发环境或 <code>VITE_ENABLE_DEMO_EXTENSIONS=true</code> 时启用。</p>
          <CodeCard title="LIST_CELL JSON 清单" language="JSON">
            <pre v-pre><code>{
  "schemaVersion": 1,
  "type": "LIST_CELL",
  "name": "RiskProgressCell",
  "label": "风险进度",
  "version": 1,
  "implementation": {
    "path": "src/extensions/common/list-cells/RiskProgressCell.vue",
    "export": "default",
    "kind": "COMPONENT"
  },
  "metadata": {
    "supportedEntityCodes": [
      "expense"
    ],
    "supportedFieldTypes": [
      "INTEGER",
      "LONG",
      "DECIMAL",
      "DOUBLE"
    ],
    "configSchema": [
      {
        "key": "warningAt",
        "label": "关注阈值",
        "type": "number",
        "min": 0,
        "max": 100,
        "defaultValue": 40
      },
      {
        "key": "dangerAt",
        "label": "高危阈值",
        "type": "number",
        "min": 0,
        "max": 100,
        "defaultValue": 70
      },
      {
        "key": "showText",
        "label": "显示百分比",
        "type": "boolean",
        "defaultValue": true
      },
      {
        "key": "showLevel",
        "label": "显示风险等级",
        "type": "boolean",
        "defaultValue": true
      }
    ]
  }
}</code></pre>
          </CodeCard>
          <p><code>configSchema</code> 的 <code>type</code> 设计器认识这些：<code>text</code>、<code>textarea</code>、<code>number</code>、<code>boolean</code>、<code>select</code>、<code>json</code>。组件里用 <code>props.config.xxx</code> 读对应 <code>key</code>。显示参数写 <code>renderConfig</code>，不要塞进数据源配置；运行时优先读 <code>field.renderConfig</code>，没有才回退 <code>dataSourceConfig</code>。</p>
          <p>组件没注册时，格子会静默走内置 <code>DefaultText</code>，页面不会报红。自己测的时候先看下拉里有没有你的显示名。</p>

          <h4 id="scope">2.5 限定用在哪个实体</h4>
          <p>扩展多了，每个实体的「渲染组件」下拉都会很长。注册时用 <code>supportedEntityCodes</code> 收窄范围：</p>
          <el-table :data="entityScopeRows" border size="small">
            <el-table-column prop="write" label="怎么写" min-width="220" />
            <el-table-column prop="effect" label="设计器里怎样" min-width="280" />
          </el-table>
          <p>这只过滤下拉。已经保存到列上的渲染组件，换实体后仍会渲染；打开该列时，当前选中项即使不在范围内也会留在下拉里，避免配置丢了。</p>
          <CodeCard title="三种范围写法" language="JSON">
            <pre v-pre><code>{
  "schemaVersion": 1,
  "type": "LIST_CELL",
  "name": "PlainTextCell",
  "label": "普通文本",
  "version": 1,
  "implementation": {
    "path": "src/extensions/common/list-cells/PlainTextCell.vue",
    "export": "default",
    "kind": "COMPONENT"
  },
  "metadata": {}
}

{
  "schemaVersion": 1,
  "type": "LIST_CELL",
  "name": "ExpenseRiskCell",
  "label": "报销风险",
  "version": 1,
  "implementation": {
    "path": "src/extensions/common/list-cells/ExpenseRiskCell.vue",
    "export": "default",
    "kind": "COMPONENT"
  },
  "metadata": {
    "supportedEntityCodes": [
      "expense"
    ]
  }
}

{
  "schemaVersion": 1,
  "type": "LIST_CELL",
  "name": "AmountBarCell",
  "label": "金额条",
  "version": 1,
  "implementation": {
    "path": "src/extensions/common/list-cells/AmountBarCell.vue",
    "export": "default",
    "kind": "COMPONENT"
  },
  "metadata": {
    "supportedEntityCodes": [
      "expense",
      "contract",
      "order"
    ]
  }
}</code></pre>
          </CodeCard>
        </section>

        <section id="template" class="guide-section">
          <h3>3. 拼一列：不用写代码</h3>
          <p>只要把当前行已有字段拼成一段文本，用内置数据源 <code>FIELD_TEMPLATE</code>（设计器里叫「字段组合」）。</p>
          <ol class="check-list">
            <li>实体 → 列表设计 → 字段配置 → <strong>添加虚拟列</strong>。</li>
            <li>改名称，比如「摘要」。编码预填 <code>virtual_时间戳</code>，可改成 <code>summary</code>。</li>
            <li>「设置」→「数据与显示」→ 取值方式选「字段组合」。</li>
            <li>组合模板填 <code v-pre>${code} - ${name}</code>。占位符是 <code v-pre>${字段编码}</code>，字段编码以字母开头，只做替换，不跑脚本。</li>
            <li>空值文本配在单元格 <code>renderConfig.emptyText</code>，默认 <code>-</code>。</li>
            <li>保存当前列，发布列表。</li>
          </ol>
          <p>后端 <code>TemplateListFieldDataProvider</code> 渲染后写入 <code>record.extData[字段编码]</code>。占位符取值顺序：<code>extData</code> → <code>data</code> → 系统字段（<code>id / code / name / status / submitterName</code>）。</p>
          <p>这个数据源只用于展示，不支持查询或排序。数据库完成过滤、排序和分页后，平台只为当前页计算模板值。</p>
        </section>

        <section id="provider" class="guide-section">
          <h3>4. 自己出数：统一扩展接口</h3>
          <p>关联查询、汇总或业务计算统一通过 <code>UiDataSourceProvider</code> 接入。在扩展管理中创建接口，列配置只选「取值方式：扩展接口」并绑定接口，不再单独注册列数据源类型。</p>
          <p>仓库对照：<code>ProjectCustomListUiDataSourceProvider</code>，Provider 编码 <code>PROJECT_CUSTOM_UI_LIST</code>；其 <code>LIST_COLUMN</code> 分支使用 <code>columnPrefix</code> 参数，为当前页返回记录 ID 到列值的 Map。</p>

          <h4>4.1 要做什么</h4>
          <ol class="check-list">
            <li>实现 <code>UiDataSourceProvider</code> 并加 <code>@Component</code>，用 <code>getCode()</code> 声明稳定且唯一的编码。</li>
            <li>在「扩展管理」创建扩展接口，后端实现选「已注册 Provider」，选择该编码；上下文为列表（<code>LIST</code>），接口类型为只读（<code>READ</code>）。</li>
            <li>实现参数在接口中维护。例如选择 <code>PROJECT_CUSTOM_UI_LIST</code> 后填写 <code>{"columnPrefix":"项目状态"}</code>，列上不重复填写参数。</li>
            <li>接口启用后，列表设计添加虚拟列，取值方式选「扩展接口」，选择这个接口，再保存当前列并发布。</li>
            <li>平台以 <code>LIST_COLUMN</code> 调用，每次传入当前页 <code>records</code> 和当前列 <code>field</code>；返回 <code>recordId → 值</code> 的 Map，由平台合并进 <code>extData[字段编码]</code>。</li>
          </ol>
          <p>业务实体的实体字段和虚拟列都可选择扩展接口；系统实体仅支持实体字段取值。接口列只负责展示，不参与数据库查询或排序，不写回记录原值。</p>

          <h4>4.2 带注释的最小实现</h4>
          <CodeCard title="UiDataSourceProvider：按记录 ID 返回展示值" language="Java">
            <pre v-pre><code>@Component
@RequiredArgsConstructor
public class CustomerLevelProvider implements UiDataSourceProvider {
    private final ObjectMapper objectMapper;

    @Override
    public String getCode() { return "CUSTOMER_LEVEL"; }

    @Override
    public String getDisplayName() { return "客户等级"; }

    // 参数保存在扩展接口中，不保存在每列 dataSourceConfig 中。
    @Override
    public Map&lt;String, Object&gt; configurationSchema() {
        return Map.of("type", "object", "properties", Map.of(
            "columnPrefix", Map.of("type", "string", "title", "展示前缀")));
    }

    /** 当前示例只生成展示文本；业务查询须批量处理，并执行 dataScopePlan 权限约束。 */
    @Override
    public Object execute(UiInvocationContext context,
                          DataScopePlan dataScopePlan,
                          Map&lt;String, Object&gt; configuration,
                          Map&lt;String, Object&gt; input) {
        if (!(context instanceof ListInvocationContext)
                || !"LIST_COLUMN".equals(context.usage())) {
            throw new IllegalArgumentException("仅支持列表扩展列");
        }
        // 同时接受运行时 DTO 和调试界面的 JSON 对象，避免依赖宿主实体 DTO 类型。
        List&lt;Map&lt;String, Object&gt;&gt; records = objectMapper.convertValue(
            input.get("records"), new TypeReference&lt;List&lt;Map&lt;String, Object&gt;&gt;&gt;() {});
        String prefix = String.valueOf(configuration.getOrDefault("columnPrefix", "客户"));
        Map&lt;String, Object&gt; values = new LinkedHashMap&lt;&gt;();
        for (Map&lt;String, Object&gt; record : records) {
            // 返回键必须是记录 ID，平台按 ID 定位行，不能使用列表下标。
            values.put(String.valueOf(record.get("id")), prefix + " VIP");
        }
        return values;
    }
}</code></pre>
          </CodeCard>
          <p>示例依赖 <code>com.workflow.contracts.entity.ui.spi.UiDataSourceProvider</code>、<code>entity.ui.context.UiInvocationContext / ListInvocationContext</code> 和 <code>entity.list.model.DataScopePlan</code>；JSON 转换使用 Jackson 的 <code>ObjectMapper / TypeReference</code>。</p>

          <h4>4.3 输入与可信上下文</h4>
          <p>用户身份、实体和列表配置由服务端生成；不要从 input 中的同名字段推断登录身份。需要查询关联数据时，同样遵守 dataScopePlan。</p>
          <el-table :data="contextRows" border size="small">
            <el-table-column prop="key" label="参数" width="220" />
            <el-table-column prop="meaning" label="含义" />
          </el-table>
          <p><code>GET /api/entity-list-config/extension-options</code> 只提供三种固定取值方式和字段组合参数；具体接口来自扩展接口目录。接口未出现在下拉时，检查是否启用、是否为 LIST / READ。</p>
        </section>

        <section id="combine" class="guide-section">
          <h3>5. 同一列：出数 + 显示</h3>
          <p>出数和显示是同一列上的两个配置，不是两条规则。列的取值方式选「扩展接口」并绑定接口，渲染组件选你的单元格；阈值进 <code>renderConfig</code>，接口实现参数在扩展管理中维护。</p>
          <p>查询和默认排序仅允许 <code>ENTITY_FIELD</code> 实体字段。字段组合和扩展接口仅用于展示。</p>
          <p>固定查询条件、页面查询参数和默认排序也只能引用实体字段。</p>
        </section>

        <section id="deep" class="guide-section">
          <h3>6. 深一层：每个属性都填上之后</h3>
          <p>前面几节可以只碰 <code>value</code> 和两三个配置项。管理员把列配满、扩展接口结果合并到 <code>extData</code> 后，五个 props 会同时带齐。下面按一次真实渲染拆开。</p>

          <h4>6.1 格子实际收到的一份快照</h4>
          <p>假设虚拟列编码 <code>riskScore</code>，数据源补了 82，渲染组件选了风险进度，展示阈值已配置。组件入参就是这样（JSON 里的函数写成了说明）：</p>
          <CodeCard title="一次渲染时的 props（值来自真实字段，不是示意乱编）" language="JSON">
            <pre v-pre><code>{
  "value": 82,
  "config": {
    "warningAt": 40,
    "dangerAt": 70,
    "showText": true,
    "showLevel": true
  },
  "field": {
    "id": "col_risk_001",
    "listConfigId": "lst_expense_default",
    "fieldId": "virtual_1710000000000",
    "fieldCode": "riskScore",
    "fieldName": "风险",
    "showInList": true,
    "isQuery": false,
    "queryType": "GE",
    "width": 180,
    "align": "left",
    "dataSourceType": "INTERFACE",
    "interfaceExtensionId": "interface_risk_score",
    "dataSourceConfig": "",
    "renderComponent": "RiskProgressCell",
    "renderConfig": "{\"warningAt\":40,\"dangerAt\":70,\"showText\":true,\"showLevel\":true}",
    "queryConfig": "{\"componentType\":\"number\",\"placeholder\":\"最低风险\",\"defaultValue\":\"\"}",
    "columnConfig": "{\"fixed\":\"\",\"minWidth\":180,\"showOverflowTooltip\":true,\"quickCopy\":true}",
    "revision": 3
  },
  "row": {
    "id": "2038628006255251457",
    "entityCode": "expense",
    "code": "BX-001",
    "name": "差旅报销",
    "status": "PENDING",
    "data": { "amount": 1200, "customerId": "c1" },
    "extData": { "riskScore": 82, "summary": "BX-001 - 差旅报销" },
    "submitterId": "u1",
    "submitterName": "张三",
    "deptId": "dept-1",
    "deptName": "销售部",
    "create_by": "zhangsan",
    "actionCapabilities": {
      "edit": { "visible": true, "enabled": true, "reason": "" }
    }
  },
  "context": {
    "entityCode": "expense",
    "entityDefinition": { "entityCode": "expense", "entityName": "报销单" },
    "entityStatusMap": { "PENDING": "审批中", "APPROVED": "已通过" },
    "getStatusText": "function(status)",
    "refresh": "function() 重新拉当前列表",
    "refEntityNameMap": { "CUSTOM:customer:c1": "华东客户" }
  }
}</code></pre>
          </CodeCard>
          <p>对照关系：</p>
          <ul class="check-list">
            <li><code>config</code> 是 <code>field.renderConfig</code> 解析后的对象。组件读 <code>config.warningAt</code>，不要自己再 <code>JSON.parse(field.renderConfig)</code>。</li>
            <li><code>value</code> 不是你在组件里算的，是 <code>ListCellRenderer</code> 先跑 <code>formatListFieldValue(row, field)</code> 再传进来。取值顺序 <code>extData &gt; data &gt; 行顶层</code>；字段编码是下划线时还会试驼峰，比如 <code>customer_name</code> 能读到 <code>data.customerName</code>。</li>
            <li>如果 <code>extData</code> 里同时有 <code>riskScore_display</code>，<code>value</code> 会变成那串展示文本，不再是 82。进度条这类组件不要写 <code>riskScore_display</code>，数字会丢。</li>
            <li>引用、多选的选项名在 <code>row.extData.字段Options</code> 或 <code>context.refEntityNameMap</code>。默认文本组件会用它们；你自己画格子时也可以直接读。</li>
            <li><code>field.dataSourceConfig</code> 仍是字符串。它只用于 FIELD_TEMPLATE 的组合模板；接口实现参数由扩展管理维护，单元格一般不用动它。</li>
          </ul>

          <h4>6.2 五个 props 用全的组件</h4>
          <CodeCard title="把 row / field / context 都用上" language="Vue">
            <pre v-pre><code>&lt;script setup&gt;
import { computed } from 'vue'

// 五个名字仍然不能改。值都填齐时，按需取，不必五个都用。
const props = defineProps({
  value: { type: [String, Number], default: 0 }, // 已经被 formatListFieldValue 处理过
  row: { type: Object, default: () => ({}) },    // data / extData / 系统字段
  field: { type: Object, default: () => ({}) },  // 列配置，含 fieldCode
  config: { type: Object, default: () => ({}) }, // 解析后的 renderConfig
  context: { type: Object, default: () => ({}) } // refresh、refEntityNameMap
})

// value 已经被 formatListFieldValue 处理过；要原始数字时回 extData
const score = computed(() => {
  const raw = props.row?.extData?.[props.field.fieldCode]
  const n = Number(raw ?? props.value)
  return Number.isNaN(n) ? 0 : n
})

// 同一行旁路字段：自定义字段在 row.data，系统字段在行顶层
const customerId = computed(() => props.row?.data?.customerId)
const code = computed(() => props.row?.code)

// 引用显示名：key 是 类型:实体ID:记录ID
const customerName = computed(() => {
  const map = props.context?.refEntityNameMap || {}
  return map[`CUSTOM:customer:${customerId.value}`] || customerId.value || '-'
})

function reloadList() {
  // 改完关联数据后刷新当前列表，平台传入的就是这个函数
  props.context?.refresh?.()
}
&lt;/script&gt;</code></pre>
          </CodeCard>

          <h4>6.3 注册元数据全开</h4>
          <p><code>normalizeExtensionDescriptor</code> 会收下这些字段。没写的给默认值，多写的不会进运行时。</p>
          <CodeCard title="LIST_CELL JSON 完整参数" language="JSON">
            <pre v-pre><code>{
  "schemaVersion": 1,
  "type": "LIST_CELL",
  "name": "RiskProgressCell",
  "label": "风险进度",
  "version": 1,
  "implementation": {
    "path": "src/extensions/common/list-cells/RiskProgressCell.vue",
    "export": "default",
    "kind": "COMPONENT"
  },
  "metadata": {
    "snapshotVersion": 1,
    "supportedEntityCodes": [
      "expense"
    ],
    "supportedFieldTypes": [
      "INTEGER",
      "LONG",
      "DECIMAL",
      "DOUBLE"
    ],
    "supportedModes": [],
    "capabilities": {},
    "configSchema": [
      {
        "key": "warningAt",
        "label": "关注阈值",
        "type": "number",
        "required": true,
        "min": 0,
        "max": 100,
        "step": 1,
        "defaultValue": 40,
        "group": "common",
        "order": 10,
        "description": "达到该值显示关注"
      },
      {
        "key": "dangerAt",
        "label": "高危阈值",
        "type": "number",
        "min": 0,
        "max": 100,
        "defaultValue": 70,
        "group": "common",
        "order": 20
      },
      {
        "key": "showText",
        "label": "显示百分比",
        "type": "boolean",
        "defaultValue": true,
        "group": "common",
        "order": 30
      },
      {
        "key": "showLevel",
        "label": "显示风险等级",
        "type": "boolean",
        "defaultValue": true,
        "group": "common",
        "order": 40
      },
      {
        "key": "dangerText",
        "label": "高危文案",
        "type": "text",
        "defaultValue": "高风险",
        "placeholder": "例如 高风险",
        "group": "advanced",
        "advanced": true,
        "order": 100,
        "visibleWhen": {
          "field": "showLevel",
          "equals": true
        }
      }
    ]
  }
}</code></pre>
          </CodeCard>
          <el-table :data="schemaItemRows" border size="small">
            <el-table-column prop="key" label="schema 项" width="140" />
            <el-table-column prop="note" label="设计器怎么用" />
          </el-table>
          <p><code>visibleWhen</code> 还认 <code>notEquals</code>、<code>in</code>、<code>notIn</code>、<code>includes</code>、<code>exists</code>、<code>truthy</code> / <code>falsy</code>，以及 <code>all</code>/<code>and</code>、<code>any</code>/<code>or</code>、<code>not</code>。服务端下发的 schema 不要带可执行函数。</p>

          <h4>6.4 列上四份 JSON 各自管什么</h4>
          <el-table :data="jsonBagRows" border size="small">
            <el-table-column prop="bag" label="字段" width="170" />
            <el-table-column prop="who" label="谁填" width="140" />
            <el-table-column prop="shape" label="配满时的内容" min-width="320" />
          </el-table>
          <CodeCard title="列配置示例（queryConfig 仅用于实体字段）" language="JSON">
            <pre v-pre><code>{
  "dataSourceType": "INTERFACE",
  "interfaceExtensionId": "interface_risk_score",
  "dataSourceConfig": {},
  "renderConfig": { "warningAt": 40, "dangerAt": 70, "showText": true, "showLevel": true, "dangerText": "高风险" },
  "queryConfig": { "componentType": "number", "placeholder": "最低风险", "defaultValue": "" },
  "columnConfig": { "fixed": "left", "minWidth": 180, "showOverflowTooltip": true, "quickCopy": true }
}</code></pre>
          </CodeCard>
          <p>查询方式本身不在 <code>queryConfig</code> 里，在列字段 <code>queryType</code>。设计器可选：<code>EQ NE LIKE NOT_LIKE GT GE LT LE BETWEEN IN NOT_IN EMPTY NOT_EMPTY</code>。这些条件仅用于实体字段，条件值通过参数绑定交给数据库执行。请求中的 <code>{fieldCode}_op</code> 指定操作符，<code>{fieldCode}_start</code> / <code>{fieldCode}_end</code> 指定范围。</p>

          <h4>6.5 Provider 方法全覆盖</h4>
          <p>接口里每个方法都重写时，行为如下。</p>
          <el-table :data="providerFullRows" border size="small">
            <el-table-column prop="method" label="方法" width="240" />
            <el-table-column prop="whenSet" label="你写了之后" />
          </el-table>
          <p>模板列配满时，<code>dataSourceType=FIELD_TEMPLATE</code>，<code>dataSourceConfig</code> 只有 <code>template</code>，例如 <code v-pre>{"template":"${code} - ${name}"}</code>；空值在 <code>renderConfig.emptyText</code>。占位符取值同样是 <code>extData &gt; data &gt; 系统字段</code>。</p>
        </section>

        <section id="designer" class="guide-section">
          <h3>7. 设计器字段落在哪</h3>
          <el-table :data="designerRows" border size="small">
            <el-table-column prop="place" label="你在界面上点的" width="200" />
            <el-table-column prop="field" label="存到哪" width="200" />
            <el-table-column prop="note" label="保存后怎样" min-width="260" />
          </el-table>
        </section>

        <section id="api" class="guide-section">
          <h3>8. 扩展时会用到的接口</h3>
          <h4>前端</h4>
          <el-table :data="frontApis" border size="small">
            <el-table-column prop="api" label="接口" width="280" />
            <el-table-column prop="file" label="在哪" min-width="240" />
            <el-table-column prop="note" label="做什么" min-width="240" />
          </el-table>
          <h4>后端</h4>
          <el-table :data="backApis" border size="small">
            <el-table-column prop="api" label="接口" width="280" />
            <el-table-column prop="file" label="在哪" min-width="240" />
            <el-table-column prop="note" label="做什么" min-width="240" />
          </el-table>
          <h4>UiDataSourceProvider</h4>
          <el-table :data="providerMethods" border size="small">
            <el-table-column prop="method" label="方法" width="260" />
            <el-table-column prop="note" label="默认和约束" />
          </el-table>
        </section>

        <section id="check" class="guide-section">
          <h3>9. 做完自己过一遍</h3>
          <ul class="check-list">
            <li>只保存列、不发布，运行时还是旧快照。</li>
            <li>接口返回记录 ID 到列值的 Map，平台写入 <code>extData</code>；不要改 <code>record.data</code>。</li>
            <li><code>execute</code> 里先收齐 ID 再查，不要按行打远程。</li>
            <li>显示参数进 <code>renderConfig</code>，组合模板进 <code>dataSourceConfig</code>，接口参数在扩展管理中维护。</li>
            <li>业务注册跟应用启动走，不要只挂在 Demo 开关后面。</li>
            <li>组件名、扩展接口 ID 保存后会被列配置引用，不要轻易改。</li>
            <li>进度、计算类组件不要写 <code>字段_display</code>，否则 <code>value</code> 会变成文案。</li>
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
  { id: 'overview', label: '先看全貌' },
  { id: 'cell', label: '换显示' },
  { id: 'scope', label: '适用实体' },
  { id: 'template', label: '模板拼列' },
  { id: 'provider', label: '自己出数' },
  { id: 'combine', label: '叠在同一列' },
  { id: 'deep', label: '属性都填上' },
  { id: 'designer', label: '设计器落点' },
  { id: 'api', label: '接口' },
  { id: 'check', label: '做完检查' }
]

const overviewRows = [
  { part: '出数', what: '这一列每个格子的值从哪来', how: '实体字段直接读；字段组合拼接；扩展接口返回列值' },
  { part: '显示', what: '这个值在表格里长什么样', how: 'LIST_CELL JSON 清单声明 Vue 组件，列上选渲染组件' }
]

const entityScopeRows = [
  { write: '不写 / [] / [\'*\']', effect: '每个实体的列表设计器都能选' },
  { write: '[\'expense\']', effect: '只有实体编码是 expense 的列表能选' },
  { write: '[\'expense\', \'contract\']', effect: '这两个实体能选，其它实体下拉里没有' }
]

const cellProps = [
  { name: 'value', what: '这一格已经算好的展示值', use: '进度、标签、格式化，优先只用它' },
  { name: 'row', what: '当前行 DTO，含 data、extData、id', use: '要旁路字段或原始 ID 时再读' },
  { name: 'field', what: '这一列的配置', use: '看 fieldCode、renderConfig 等' },
  { name: 'config', what: '设计器为这个组件填的参数', use: '阈值、开关、文案；优先来自 renderConfig' },
  { name: 'context', what: '列表运行时上下文', use: 'entityCode、entityDefinition、entityStatusMap、getStatusText、refresh、refEntityNameMap' }
]

const contextRows = [
  { key: 'context.entityCode()', meaning: '当前实体编码' },
  { key: 'context.listKey() / configId()', meaning: '当前列表编码和配置 ID' },
  { key: 'context.userId() / userName()', meaning: '服务端认证的用户身份' },
  { key: 'context.usage()', meaning: '当前调用用途，此处为 LIST_COLUMN' },
  { key: 'input.records / input.field', meaning: '当前页记录与当前列配置，用于计算列值' },
  { key: 'configuration', meaning: '扩展管理中保存的接口实现参数' },
  { key: 'dataScopePlan', meaning: '不可绕过的数据权限计划，关联查询也须遵守' }
]

const designerRows = [
  { place: '添加虚拟列', field: 'fieldId 以 virtual_ 开头', note: '无实体字段的展示列；使用字段组合或扩展接口' },
  { place: '用途：列表 / 查询', field: 'showInList / isQuery', note: '仅 ENTITY_FIELD 可查询；虚拟列仅展示' },
  { place: '取值方式 + 组合模板', field: 'dataSourceType / dataSourceConfig', note: '固定三种方式；仅 FIELD_TEMPLATE 配置模板参数' },
  { place: '扩展接口', field: 'interfaceExtensionId', note: '仅 INTERFACE 显示且必选，接口参数在扩展管理维护' },
  { place: '渲染组件 + 参数表单', field: 'renderComponent / renderConfig', note: '选项来自 getCellComponentOptions()' },
  { place: '保存当前列', field: 'PATCH .../fields/{id}/patch', note: '带 expectedRevision；还要再发布列表' }
]

const frontApis = [
  { api: 'LIST_CELL 清单', file: 'src/extensions/core/registries/listCellRegistry.js', note: '注册单元格。meta 含 label、configSchema、supportedEntityCodes、supportedFieldTypes' },
  { api: 'getCellComponent / hasCellComponent', file: 'src/extensions/core/registries/listCellRegistry.js', note: '运行时按 renderComponent 取组件' },
  { api: 'filterOptionsByEntity(options, entityCode, current)', file: 'src/shared/extension-entity-scope.js', note: '按实体收窄下拉，已选值始终保留' },
  { api: 'getCellComponentOptions()', file: 'src/extensions/core/registries/listCellRegistry.js', note: '全部单元格；设计器再按实体过滤' },
  { api: 'getCellValue(row, field)', file: 'src/shared/list-runtime/index.js', note: 'extData > data > row' },
  { api: 'formatListFieldValue(...)', file: 'src/shared/list-runtime/index.js', note: 'ListCellRenderer 用来算 value' },
  { api: 'entityListConfigApi.getExtensionOptions()', file: 'src/api/entityListConfig.js', note: 'GET /entity-list-config/extension-options' }
]

const backApis = [
  { api: 'UiDataSourceProvider', file: 'workflow-spi/.../entity/ui/spi/UiDataSourceProvider.java', note: '统一扩展接口 SPI，列以 LIST_COLUMN 调用' },
  { api: 'ProjectCustomListUiDataSourceProvider', file: 'biz-project/.../project/custom/ProjectCustomListUiDataSourceProvider.java', note: '项目示例：PROJECT_CUSTOM_UI_LIST，配置 columnPrefix' },
  { api: 'TemplateListFieldDataProvider', file: 'list/extension/TemplateListFieldDataProvider.java', note: '内置 FIELD_TEMPLATE 实现，不用于业务扩展注册' },
  { api: 'EntityListQueryPolicy', file: 'list/application/EntityListQueryPolicy.java', note: '只允许实体字段参与查询与排序' },
  { api: 'GET /api/entity-list-config/extension-options', file: 'EntityListConfigController', note: '三种固定取值方式及字段组合参数' }
]

const schemaItemRows = [
  { key: 'key / label / type', note: '必填语义。type：text、textarea、number、boolean、select、json' },
  { key: 'required / defaultValue', note: '保存时校验必填；打开设计器时用 defaultValue 补空' },
  { key: 'min / max / step', note: '仅 number' },
  { key: 'options / multiple / placeholder / rows', note: 'select 的选项和多选；textarea/json 的行数和占位' },
  { key: 'description / helpKey', note: '项下方说明；列参数问号优先使用 helpKey，未设置时使用 description' },
  { key: 'group / advanced / order / priority', note: 'common 首屏，advanced 默认折叠；order 管组内顺序' },
  { key: 'visibleWhen', note: '按当前配置显隐，例如 { field: \'showLevel\', equals: true }' }
]

const jsonBagRows = [
  { bag: 'dataSourceConfig', who: '字段组合表单', shape: '仅字段组合使用，如 { template: ${code}-${name} }；扩展接口参数在扩展管理中维护' },
  { bag: 'renderConfig', who: '单元格参数表单', shape: '组件的 configSchema。如 { warningAt, dangerAt, showText, showLevel }' },
  { bag: 'queryConfig', who: '查询项', shape: '{ componentType, placeholder, defaultValue }。查询方式在 queryType' },
  { bag: 'columnConfig', who: '列展示与高级列布局', shape: '{ fixed: left|right|空, minWidth, showOverflowTooltip, quickCopy }。列宽/对齐在 width、align' }
]

const providerFullRows = [
  { method: 'getCode() / getDisplayName()', whenSet: '扩展管理中的 Provider 编码及展示名称' },
  { method: 'getVersion() / getArtifactDigest()', whenSet: '发布时固定具体实现；逻辑变更须提升版本' },
  { method: 'configurationSchema()', whenSet: '声明扩展接口实现参数的 JSON Schema' },
  { method: 'execute(context, dataScopePlan, configuration, input)', whenSet: '只读计算当前页的列值，返回记录 ID 到值的 Map' }
]

const providerMethods = [
  { method: 'getCode()', note: '必填且全局唯一，供扩展管理选择实现' },
  { method: 'getDisplayName()', note: '必填，Provider 的可读名称' },
  { method: 'getVersion()', note: '默认 1；已发布实现发生变化时提升版本' },
  { method: 'getArtifactDigest()', note: '默认对类字节码求摘要；有额外依赖时覆盖完整受审制品' },
  { method: 'configurationSchema()', note: '默认空；使用 JSON Schema 声明接口实现参数' },
  { method: 'execute(...)', note: '读取可信 context 和 dataScopePlan，批量计算，不修改实体数据' }
]
</script>

<style scoped src="./dev-guide-shared.scss"></style>
<style scoped>
.guide-section h4 {
  margin: 16px 0 10px;
  font-size: 14px;
  color: #303133;
}
</style>
