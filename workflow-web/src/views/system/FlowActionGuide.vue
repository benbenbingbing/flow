<template>
  <div class="dev-guide-page">
    <div class="page-header">
      <div>
        <h2>流程动作配置与开发指南</h2>
        <p>定制开发 / 流程配置 / 流程动作</p>
      </div>
    </div>

    <el-alert
      title="流程动作是由流程引擎自动触发的后端扩展点。请先确定配置位置和触发时机，再选择事务方式、失败策略与处理器。"
      type="info"
      :closable="false"
      show-icon
      class="page-alert"
    />

    <div class="guide-layout">
      <aside class="toc-panel">
        <div class="toc-title">目录</div>
        <nav class="toc-list" aria-label="流程动作指南目录">
          <a
            v-for="item in tocItems"
            :key="item.id"
            :href="`#${item.id}`"
            class="toc-link"
            :class="{ active: activeSection === item.id }"
            @click.prevent="scrollToSection(item.id)"
          >
            <span class="toc-index">{{ item.index }}</span>
            <span>{{ item.label }}</span>
          </a>
        </nav>
      </aside>

      <main class="detail-sections">
      <section id="scope" class="doc-section">
        <div class="section-title">一、配置位置与作用域</div>
        <div class="section-content">
          <p class="section-intro">
            流程动作不再只配置在箭头上。平台按作用域将动作绑定到整个流程、BPMN 节点或顺序流，并根据元素类型过滤可选时机。
          </p>
          <el-table :data="scopeList" border>
            <el-table-column prop="position" label="配置入口" width="150" />
            <el-table-column prop="scopeType" label="作用域编码" width="150">
              <template #default="{ row }"><code>{{ row.scopeType }}</code></template>
            </el-table-column>
            <el-table-column prop="elements" label="适用元素" min-width="190" />
            <el-table-column prop="timings" label="可选触发时机" min-width="300" />
            <el-table-column prop="description" label="绑定语义" min-width="240" />
          </el-table>
        </div>
      </section>

      <section id="fields" class="doc-section">
        <div class="section-title">二、配置界面字段字典</div>
        <div class="section-content">
          <p class="section-intro">
            下表覆盖动作弹窗和动作列表中的全部业务字段。字段组合会在保存和发布时由后端再次校验，不能仅依赖前端选项限制。
          </p>
          <el-table :data="fieldList" border>
            <el-table-column prop="label" label="界面字段" width="120" />
            <el-table-column prop="field" label="存储字段" width="180">
              <template #default="{ row }"><code>{{ row.field }}</code></template>
            </el-table-column>
            <el-table-column prop="type" label="字段类型" width="180" />
            <el-table-column prop="required" label="必填条件" width="130" />
            <el-table-column prop="meaning" label="含义与运行效果" min-width="330" />
            <el-table-column prop="notes" label="配置注意事项" min-width="280" />
          </el-table>

          <el-collapse class="storage-collapse">
            <el-collapse-item title="查看平台自动维护的绑定字段">
              <el-table :data="storageFieldList" border>
                <el-table-column prop="field" label="字段" width="180">
                  <template #default="{ row }"><code>{{ row.field }}</code></template>
                </el-table-column>
                <el-table-column prop="type" label="类型" width="180" />
                <el-table-column prop="meaning" label="含义" />
                <el-table-column prop="notes" label="兼容说明" min-width="280" />
              </el-table>
            </el-collapse-item>
          </el-collapse>
        </div>
      </section>

      <section id="timings" class="doc-section">
        <div class="section-title">三、十个标准触发时机详解</div>
        <div class="section-content">
          <p class="section-intro">
            “界面名称”是业务语义，“时机编码”用于处理器能力声明和执行日志。展开每一行可查看准确触发点、上下文与风险。
          </p>
          <el-table :data="timingDetailList" border row-key="code">
            <el-table-column type="expand">
              <template #default="{ row }">
                <div class="timing-detail">
                  <div><strong>准确触发点：</strong>{{ row.phase }}</div>
                  <div><strong>可用上下文：</strong>{{ row.context }}</div>
                  <div><strong>适合场景：</strong>{{ row.use }}</div>
                  <div><strong>注意事项：</strong>{{ row.caution }}</div>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="label" label="界面名称" width="190" />
            <el-table-column prop="code" label="时机编码" width="210">
              <template #default="{ row }"><code>{{ row.code }}</code></template>
            </el-table-column>
            <el-table-column prop="scope" label="配置位置" width="130" />
            <el-table-column prop="elementType" label="元素限制" width="150" />
            <el-table-column label="默认执行组合" min-width="220">
              <template #default="{ row }">
                <el-tag :type="row.mode === 'AFTER_COMMIT' ? 'warning' : 'success'" size="small">
                  {{ executionModeLabel(row.mode) }}
                </el-tag>
                <el-tag class="policy-tag" size="small" type="info">{{ failurePolicyLabel(row.policy) }}</el-tag>
              </template>
            </el-table-column>
          </el-table>

          <el-alert
            v-if="customTimingOptions.length"
            :title="`当前系统另注册了 ${customTimingOptions.length} 个自定义时机，仍会复用版本隔离、事务策略、Outbox 和执行日志。`"
            type="success"
            :closable="false"
            show-icon
            class="inline-alert"
          />
        </div>
      </section>

      <section id="execution" class="doc-section">
        <div class="section-title">四、执行方式与失败策略</div>
        <div class="section-content">
          <el-table :data="executionPolicyList" border>
            <el-table-column prop="modeLabel" label="执行方式" width="150" />
            <el-table-column prop="mode" label="方式编码" width="170">
              <template #default="{ row }"><code>{{ row.mode }}</code></template>
            </el-table-column>
            <el-table-column prop="policyLabel" label="失败策略" width="160" />
            <el-table-column prop="policy" label="策略编码" width="130">
              <template #default="{ row }"><code>{{ row.policy }}</code></template>
            </el-table-column>
            <el-table-column prop="behavior" label="失败后的实际行为" min-width="330" />
            <el-table-column prop="scene" label="推荐场景" min-width="260" />
          </el-table>
          <div class="tip-grid">
            <el-alert
              title="事务内动作与流程保存、表单提交、任务流转处于同一事务，适合必须成功的校验和核心写入。"
              type="warning"
              :closable="false"
              show-icon
            />
            <el-alert
              title="提交后动作先写入 PENDING 执行记录，主事务提交后再异步执行；外部系统必须使用 idempotencyKey 去重。"
              type="success"
              :closable="false"
              show-icon
            />
          </div>
        </div>
      </section>

      <section id="params" class="doc-section">
        <div class="section-title">五、动作参数字段类型</div>
        <div class="section-content">
          <p class="section-intro">
            参数配置最终保存为 <code>paramsJson</code>。平台在动作触发时解析变量和表达式，再通过 <code>ctx.getCustomParams()</code> 传给处理器。
          </p>
          <el-table :data="paramTypeList" border>
            <el-table-column prop="label" label="界面类型" width="130" />
            <el-table-column prop="value" label="类型编码" width="130">
              <template #default="{ row }"><code>{{ row.value }}</code></template>
            </el-table-column>
            <el-table-column prop="input" label="配置值示例" width="220">
              <template #default="{ row }"><code>{{ row.input }}</code></template>
            </el-table-column>
            <el-table-column prop="runtimeType" label="Handler 接收类型" width="190" />
            <el-table-column prop="behavior" label="解析规则" min-width="320" />
            <el-table-column prop="notes" label="注意事项" min-width="280" />
          </el-table>

          <el-card shadow="never" class="code-block-card">
            <template #header>
              <div class="code-header">
                <el-tag size="small" type="success">JSON</el-tag>
                <span class="code-title">参数保存与运行时解析示例</span>
              </div>
            </template>
            <pre class="code-block" v-pre><code>{
  "templateCode": "NOTIFY_MANAGER",
  "retryLevel": 3,
  "urgent": true,
  "starter": "${startUserId}",
  "doubleAmount": "${#amount * 2}"
}</code></pre>
          </el-card>
        </div>
      </section>

      <section id="context" class="doc-section">
        <div class="section-title">六、FlowActionContext 字段与方法</div>
        <div class="section-content">
          <p class="section-intro">
            上下文中的字段是否有值取决于触发时机。结束类动作中运行时流程和当前任务通常已不存在，平台会自动回退历史变量。
          </p>
          <el-table :data="contextList" border>
            <el-table-column prop="method" label="方法 / 字段" width="240">
              <template #default="{ row }"><code>{{ row.method }}</code></template>
            </el-table-column>
            <el-table-column prop="returnType" label="返回类型" width="210" />
            <el-table-column prop="availability" label="主要可用时机" width="220" />
            <el-table-column prop="desc" label="含义与空值规则" min-width="360" />
          </el-table>
        </div>
      </section>

      <section id="handlers" class="doc-section">
        <div class="section-title">七、处理器开发与能力声明</div>
        <div class="section-content">
          <ol class="step-list">
            <li>实现 <code>FlowActionHandler</code>，并注册为 Spring Bean。</li>
            <li>声明处理器支持的时机和执行方式，避免在配置界面中被误选。</li>
            <li>需要结构化参数时实现 <code>TypedFlowActionHandler&lt;T&gt;</code>。</li>
            <li>外部接口动作使用 <code>ctx.getIdempotencyKey()</code> 作为幂等键。</li>
            <li>发布流程后动作才进入运行态；草稿动作不会影响已发布版本。</li>
          </ol>

          <el-card shadow="never" class="code-block-card">
            <template #header>
              <div class="code-header">
                <el-tag size="small" type="danger">Java</el-tag>
                <span class="code-title">带能力声明的 Handler</span>
              </div>
            </template>
            <pre class="code-block" v-pre><code>@Component("sendNotificationHandler")
public class SendNotificationHandler implements FlowActionHandler {

    @Override
    public Set&lt;String&gt; supportedTriggerTimings() {
        return Set.of("TASK_CREATED", "PROCESS_COMPLETED");
    }

    @Override
    public Set&lt;String&gt; supportedExecutionModes() {
        return Set.of("AFTER_COMMIT");
    }

    @Override
    public String recommendedExecutionMode() {
        return "AFTER_COMMIT";
    }

    @Override
    public void execute(FlowActionContext ctx) {
        String idempotencyKey = ctx.getIdempotencyKey();
        String taskId = ctx.getTaskId();
        Map&lt;String, Object&gt; params = ctx.getCustomParams();
        // 调用通知服务或外部接口
    }
}</code></pre>
          </el-card>

          <div class="subsection-title">当前已注册处理器</div>
          <el-table :data="handlers" border v-loading="handlersLoading">
            <el-table-column prop="beanName" label="Bean 名称" width="210" />
            <el-table-column prop="className" label="实现类" min-width="260" />
            <el-table-column label="参数类型" min-width="220">
              <template #default="{ row }">
                {{ row.typed ? (row.paramType || '类型化参数') : 'Map<String, Object>' }}
              </template>
            </el-table-column>
            <el-table-column label="支持时机" min-width="260">
              <template #default="{ row }">{{ formatCodes(row.supportedTriggerTimings, '全部时机') }}</template>
            </el-table-column>
            <el-table-column label="支持方式" width="190">
              <template #default="{ row }">{{ formatCodes(row.supportedExecutionModes, '两种方式') }}</template>
            </el-table-column>
            <el-table-column label="推荐方式" width="130">
              <template #default="{ row }">{{ executionModeLabel(row.recommendedExecutionMode) }}</template>
            </el-table-column>
          </el-table>
        </div>
      </section>

      <section id="scenes" class="doc-section">
        <div class="section-title">八、场景选择建议与常见问题</div>
        <div class="section-content">
          <el-table :data="sceneGuideList" border>
            <el-table-column prop="scene" label="业务场景" width="200" />
            <el-table-column prop="timing" label="推荐时机" width="210">
              <template #default="{ row }"><code>{{ row.timing }}</code></template>
            </el-table-column>
            <el-table-column prop="mode" label="推荐执行组合" width="230" />
            <el-table-column prop="reason" label="选择原因" />
          </el-table>

          <el-collapse class="faq-collapse">
            <el-collapse-item title="通知下一办理人为什么不能配置在源节点完成后？">
              源节点完成时目标任务可能尚未创建，无法获得真实任务 ID 和最终办理人。应配置在目标用户任务的
              <code>TASK_CREATED</code>，并使用 <code>AFTER_COMMIT + RETRY</code>。
            </el-collapse-item>
            <el-collapse-item title="NODE_COMPLETED 和 TASK_COMPLETING 有什么区别？">
              <code>TASK_COMPLETING</code> 只适用于用户任务，能获取审批动作、审批人和触发任务；
              <code>NODE_COMPLETED</code> 适用于所有节点，主要用于节点结束后的变量计算和路由准备。
            </el-collapse-item>
            <el-collapse-item title="结束类动作为什么不能依赖 currentTask？">
              正常完成、撤回或终止后，活动任务通常已经删除。请使用 <code>getHistoricProcessInstance()</code>、
              <code>getVariables()</code>、<code>getEntityData()</code> 和结束原因。
            </el-collapse-item>
            <el-collapse-item title="会签、循环节点会触发几次？">
              按实际执行实例触发：每个任务实例分别触发任务动作，每次令牌经过分别触发节点和连线动作，
              流程结束动作每个流程实例只触发一次。
            </el-collapse-item>
            <el-collapse-item title="表达式参数可以直接使用用户输入吗？">
              不可以。表达式由受信任的流程管理员配置，不应拼接终端用户输入；复杂业务判断应放在受控的 Handler 中实现。
            </el-collapse-item>
          </el-collapse>
        </div>
      </section>
      </main>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { processActionApi } from '@/api/processAction'

const handlers = ref([])
const handlersLoading = ref(false)
const runtimeTimingOptions = ref([])
const activeSection = ref('scope')
let sectionObserver = null

const tocItems = [
  { id: 'scope', index: '01', label: '配置位置与作用域' },
  { id: 'fields', index: '02', label: '配置界面字段字典' },
  { id: 'timings', index: '03', label: '标准触发时机详解' },
  { id: 'execution', index: '04', label: '执行方式与失败策略' },
  { id: 'params', index: '05', label: '动作参数字段类型' },
  { id: 'context', index: '06', label: '上下文字段与方法' },
  { id: 'handlers', index: '07', label: '处理器开发与声明' },
  { id: 'scenes', index: '08', label: '场景建议与常见问题' }
]

const scopeList = [
  {
    position: '设计器“全局动作”',
    scopeType: 'PROCESS',
    elements: '整个流程实例',
    timings: 'PROCESS_STARTED / PROCESS_COMPLETED / PROCESS_WITHDRAWN / PROCESS_TERMINATED',
    description: '每个流程实例绑定一次，不依赖具体节点或连线。'
  },
  {
    position: '普通节点“流程动作”',
    scopeType: 'NODE',
    elements: '开始、结束、网关、服务任务、脚本任务等 BPMN 节点',
    timings: 'NODE_ENTERED / NODE_COMPLETED',
    description: '按实际执行令牌进入或完成该节点时触发。'
  },
  {
    position: '用户任务“流程动作”',
    scopeType: 'NODE',
    elements: 'UserTask 用户任务',
    timings: 'NODE_ENTERED / NODE_COMPLETED / TASK_CREATED / TASK_ASSIGNED / TASK_COMPLETING',
    description: '同时具备节点生命周期和人工任务生命周期。'
  },
  {
    position: '连线“流程动作”',
    scopeType: 'SEQUENCE_FLOW',
    elements: 'SequenceFlow 顺序流',
    timings: 'TRANSITION_TAKEN',
    description: '只在该分支被实际选中、令牌经过该连线时触发。'
  }
]

const fieldList = [
  {
    label: '快捷模板',
    field: '仅界面辅助',
    type: 'Enum<String>',
    required: '否',
    meaning: '一次性带出常见场景的动作名称、时机、执行方式和失败策略。',
    notes: '模板不会作为独立字段保存，应用后仍可逐项修改。'
  },
  {
    label: '动作名称',
    field: 'actionName',
    type: 'String',
    required: '是',
    meaning: '动作的业务名称，显示在配置列表、发布版本和执行日志中。',
    notes: '应描述业务目的，例如“发送待办通知”，不要只写 Handler 名。'
  },
  {
    label: '执行时机',
    field: 'triggerTiming',
    type: 'Enum<String>',
    required: '是',
    meaning: '决定平台在哪个流程生命周期事件中触发动作。',
    notes: '必须与作用域和 BPMN 元素类型兼容；用户任务专属时机不能配置到普通节点。'
  },
  {
    label: '执行方式',
    field: 'executionMode',
    type: 'Enum<IN_TRANSACTION | AFTER_COMMIT>',
    required: '是',
    meaning: '决定动作与主流程是否共用事务，以及能否阻断流程流转。',
    notes: '核心校验选择事务内；通知、HTTP 和外部同步优先选择提交后。'
  },
  {
    label: '失败策略',
    field: 'failurePolicy',
    type: 'Enum<ROLLBACK | CONTINUE | RETRY | IGNORE>',
    required: '是',
    meaning: '决定处理器抛出异常后的回滚、继续、重试或忽略行为。',
    notes: 'ROLLBACK/CONTINUE 仅适用于事务内；RETRY/IGNORE 仅适用于提交后。'
  },
  {
    label: '最大重试',
    field: 'retryConfig.maxRetries',
    type: 'Integer',
    required: 'RETRY 时',
    meaning: '提交后动作失败后最多自动重试的次数，范围 0～20。',
    notes: '采用指数退避，超过次数进入 DEAD 死信状态，可在执行记录中手工重试。'
  },
  {
    label: '处理器',
    field: 'interfaceName',
    type: 'Spring Bean Name / String',
    required: '是',
    meaning: '实际执行逻辑对应的 FlowActionHandler Bean 名称。',
    notes: '保存和发布时校验 Bean、接口类型、支持时机及支持执行方式。'
  },
  {
    label: '描述',
    field: 'description',
    type: 'Text',
    required: '否',
    meaning: '记录动作目的、前置条件、外部系统和运维说明。',
    notes: '建议说明失败影响和外部接口幂等要求。'
  },
  {
    label: '参数配置',
    field: 'paramsJson',
    type: 'Map<String, Object> / JSON',
    required: '否',
    meaning: '传递给 Handler 的业务参数集合，支持静态值、流程变量和表达式。',
    notes: '运行时解析后通过 ctx.getCustomParams() 获取；类型化 Handler 会自动映射为参数类。'
  },
  {
    label: '参数名',
    field: 'paramsJson 的 key',
    type: 'String',
    required: '每行必填',
    meaning: 'Handler 读取参数时使用的键名。',
    notes: '同一动作内必须唯一，并应与类型化参数类字段名一致。'
  },
  {
    label: '参数类型',
    field: '界面类型标记',
    type: 'Enum<string | number | boolean | variable | expression>',
    required: '每行必填',
    meaning: '控制参数值写入 JSON 的类型及运行时解析方式。',
    notes: '详细规则见“动作参数字段类型”。'
  },
  {
    label: '是否启用',
    field: 'enabled',
    type: 'Boolean',
    required: '是',
    meaning: '禁用后配置仍保留，但发布版本运行时不会执行。',
    notes: '修改草稿后必须重新发布，才会影响新的流程实例。'
  },
  {
    label: '执行顺序',
    field: 'sortOrder',
    type: 'Integer',
    required: '平台维护',
    meaning: '同一元素、同一时机存在多个动作时按升序执行。',
    notes: '事务内前序动作失败并回滚后，后续动作不会执行。'
  }
]

const storageFieldList = [
  { field: 'processConfigId', type: 'String', meaning: '所属流程配置 ID。', notes: '发布时复制到对应版本，确保版本隔离。' },
  { field: 'scopeType', type: 'Enum<PROCESS | NODE | SEQUENCE_FLOW>', meaning: '动作绑定作用域。', notes: '由全局、节点或连线配置入口自动带入。' },
  { field: 'elementId', type: 'String / null', meaning: '绑定的 BPMN 节点或顺序流 ID。', notes: 'PROCESS 作用域为空；NODE 和 SEQUENCE_FLOW 必填。' },
  { field: 'sequenceFlowId', type: 'String', meaning: '历史连线动作兼容字段。', notes: '新代码以 scopeType + elementId 为准，流程级使用兼容占位值。' },
  { field: 'methodName', type: 'String', meaning: '历史方法名字段，当前固定为 execute。', notes: '运行时统一调用 FlowActionHandler.execute，不支持任意反射方法。' },
  { field: 'retryConfig', type: 'JSON String', meaning: '提交后动作的重试参数。', notes: '当前支持 maxRetries，后续可扩展退避策略。' },
  { field: 'versionId', type: 'String', meaning: '发布版本 ID。', notes: '草稿为空；发布复制后只读取当前流程定义对应版本。' },
  { field: 'status', type: 'String', meaning: 'DRAFT 或 PUBLISHED。', notes: '设计器编辑草稿，运行时只执行已发布动作。' }
]

const timingDetailList = [
  {
    code: 'PROCESS_STARTED',
    label: '流程启动时',
    scope: '全局流程',
    elementType: '整个流程',
    mode: 'IN_TRANSACTION',
    policy: 'ROLLBACK',
    phase: '流程实例创建并发出 PROCESS_STARTED 事件时，仍处于流程启动主事务内。',
    context: '流程实例、流程定义、执行实例、启动变量、实体编码和实体数据 ID；任务字段通常为空。',
    use: '初始化流程变量、创建业务关联、启动后必须同步完成的核心写入。',
    caution: '失败会导致流程启动整体回滚；不要在此同步调用不稳定的外部网络服务。'
  },
  {
    code: 'PROCESS_COMPLETED',
    label: '流程正常完成后',
    scope: '全局流程',
    elementType: '整个流程',
    mode: 'AFTER_COMMIT',
    policy: 'RETRY',
    phase: '流程通过普通结束事件正常完成并发出 PROCESS_COMPLETED 事件时。',
    context: '历史变量、实体数据、流程实例标识；当前任务为空。',
    use: '归档、完成通知、报表同步、外部系统完成状态回写。',
    caution: '不要用于撤回或异常终止；需要区分结束类型时分别使用 WITHDRAWN 和 TERMINATED。'
  },
  {
    code: 'PROCESS_WITHDRAWN',
    label: '流程撤回后',
    scope: '全局流程',
    elementType: '整个流程',
    mode: 'AFTER_COMMIT',
    policy: 'RETRY',
    phase: '流程被取消且取消原因被平台识别为“撤回”时。',
    context: '历史变量、实体数据、撤回原因、操作人；当前任务通常已删除。',
    use: '撤回通知、资源释放、业务状态恢复、撤回后的外部同步。',
    caution: '只处理平台可识别的撤回；普通取消或异常结束会进入 PROCESS_TERMINATED。'
  },
  {
    code: 'PROCESS_TERMINATED',
    label: '流程终止后',
    scope: '全局流程',
    elementType: '整个流程',
    mode: 'AFTER_COMMIT',
    policy: 'RETRY',
    phase: '主动终止、非撤回取消，或终止/错误/升级结束事件导致流程结束时。',
    context: '历史变量、实体数据、终止原因和操作人；当前任务为空。',
    use: '异常清理、终止通知、补偿资源、终止状态同步。',
    caution: '不要与正常完成共用同一业务语义；处理器应允许 endReason 为空或为引擎事件编码。'
  },
  {
    code: 'NODE_ENTERED',
    label: '进入节点时',
    scope: '节点',
    elementType: '所有非连线元素',
    mode: 'IN_TRANSACTION',
    policy: 'ROLLBACK',
    phase: 'Flowable 发出 ACTIVITY_STARTED 事件，执行令牌实际进入该 BPMN 节点时。',
    context: '节点 ID、名称、类型、执行实例和流程变量；用户任务此时可能尚未创建。',
    use: '初始化节点变量、准备节点数据、记录进入节点的业务轨迹。',
    caution: '会签、循环和多令牌场景会按执行实例多次触发，处理器必须考虑幂等。'
  },
  {
    code: 'NODE_COMPLETED',
    label: '节点完成、路由计算前',
    scope: '节点',
    elementType: '所有非连线元素',
    mode: 'IN_TRANSACTION',
    policy: 'ROLLBACK',
    phase: 'Flowable 发出 ACTIVITY_COMPLETED 事件，节点执行结束且流程继续路由仍处于同一事务时。',
    context: '节点、执行实例和最新流程变量；用户任务专属审批字段不保证完整。',
    use: '计算后续条件变量、写入节点结果、准备网关或顺序流判断数据。',
    caution: '修改变量可能改变后续分支；会签或循环节点可能多次触发。'
  },
  {
    code: 'TASK_CREATED',
    label: '待办创建后',
    scope: '用户任务',
    elementType: 'UserTask',
    mode: 'AFTER_COMMIT',
    policy: 'RETRY',
    phase: '用户任务实体创建并发出 TASK_CREATED 事件后，目标待办已经具有真实任务 ID。',
    context: '任务 ID、任务名称、初始办理人、节点信息和流程变量。',
    use: '通知下一办理人、写入待办扩展属性、创建任务关联记录。',
    caution: '通知下一办理人应选此时机，而不是源节点完成；办理人后续变更还会触发 TASK_ASSIGNED。'
  },
  {
    code: 'TASK_ASSIGNED',
    label: '办理人分配或变更后',
    scope: '用户任务',
    elementType: 'UserTask',
    mode: 'AFTER_COMMIT',
    policy: 'RETRY',
    phase: '任务首次分配、认领、转办或重新指定办理人并发出 TASK_ASSIGNED 事件时。',
    context: '任务 ID、任务名称、最新办理人、节点和流程变量。',
    use: '认领通知、转办通知、同步最新责任人。',
    caution: '同一任务可能触发多次，外部通知必须使用幂等键或业务去重规则。'
  },
  {
    code: 'TASK_COMPLETING',
    label: '任务提交、流程流转前',
    scope: '用户任务',
    elementType: 'UserTask',
    mode: 'IN_TRANSACTION',
    policy: 'ROLLBACK',
    phase: '用户提交完成命令并产生 TASK_COMPLETED 事件时，提交变量已合并，流程继续流转仍在同一事务中。',
    context: '触发任务、审批动作、审批人、审批意见相关变量、实体数据和完整流程变量快照。',
    use: '审批前后置校验、核心业务写入、必须与任务完成原子提交的操作。',
    caution: '失败会回滚任务完成和流程流转；外部 HTTP、短信和邮件不应放在此时机同步执行。'
  },
  {
    code: 'TRANSITION_TAKEN',
    label: '分支选中、进入目标节点前',
    scope: '连线',
    elementType: 'SequenceFlow',
    mode: 'IN_TRANSACTION',
    policy: 'ROLLBACK',
    phase: '顺序流被实际选中并发出 SEQUENCEFLOW_TAKEN 事件时，每个经过该连线的令牌触发一次。',
    context: '顺序流 ID、来源节点、目标节点、执行实例和流程变量。',
    use: '审批结果处理、分支状态更新、只针对实际命中分支的业务写入。',
    caution: '排他网关只触发命中的分支；并行、循环和回退场景可能多次经过同一连线。'
  }
]

const executionPolicyList = [
  {
    mode: 'IN_TRANSACTION',
    modeLabel: '事务内执行',
    policy: 'ROLLBACK',
    policyLabel: '失败回滚流程',
    behavior: '处理器抛出异常后主事务回滚，任务、表单数据、流程变量和后续流转均不提交。',
    scene: '审批校验、余额扣减、核心状态写入。'
  },
  {
    mode: 'IN_TRANSACTION',
    modeLabel: '事务内执行',
    policy: 'CONTINUE',
    policyLabel: '记录失败后继续',
    behavior: '记录错误日志但不抛出阻断异常，流程继续提交。',
    scene: '非关键审计或允许降级的本地逻辑。'
  },
  {
    mode: 'AFTER_COMMIT',
    modeLabel: '提交后执行',
    policy: 'RETRY',
    policyLabel: '失败自动重试',
    behavior: '主事务内写入 PENDING Outbox，提交后异步执行；失败指数退避，超过次数进入 DEAD。',
    scene: '通知、HTTP、消息队列、外部系统同步。'
  },
  {
    mode: 'AFTER_COMMIT',
    modeLabel: '提交后执行',
    policy: 'IGNORE',
    policyLabel: '记录失败后忽略',
    behavior: '提交后执行一次，失败记录为最终失败，不自动重试，也不影响已完成的流程事务。',
    scene: '允许丢弃的低价值日志或可由其他任务补偿的数据。'
  }
]

const paramTypeList = [
  {
    label: '静态文本',
    value: 'string',
    input: 'NOTIFY_MANAGER',
    runtimeType: 'String',
    behavior: '按 JSON 字符串原样传入，不读取流程变量。',
    notes: '适合模板编码、固定状态、接口路径等常量。'
  },
  {
    label: '数字',
    value: 'number',
    input: '100 / 12.5',
    runtimeType: 'Number',
    behavior: '保存为 JSON 数字，类型化参数可映射为 Integer、Long、Double、BigDecimal 等。',
    notes: '不要在数字中携带单位或千分位分隔符。'
  },
  {
    label: '布尔',
    value: 'boolean',
    input: 'true / false',
    runtimeType: 'Boolean',
    behavior: '保存为 JSON true 或 false。',
    notes: '用于开关和条件标记，不要使用字符串“是/否”。'
  },
  {
    label: '流程变量',
    value: 'variable',
    input: 'startUserId',
    runtimeType: '变量的实际类型',
    behavior: '界面自动保存为 ${startUserId}；运行时优先按完整变量名读取。',
    notes: '变量不存在会继续按表达式解析并可能失败，关键变量应在 Handler 中校验空值。'
  },
  {
    label: '表达式',
    value: 'expression',
    input: '#amount * 2',
    runtimeType: 'SpEL 计算结果类型',
    behavior: '界面自动包裹为 ${#amount * 2}，运行时使用当前流程变量作为 SpEL 变量计算。',
    notes: '仅允许受信任管理员配置；复杂逻辑和用户输入应放入受控 Handler。'
  }
]

const contextList = [
  { method: 'getActionId()', returnType: 'String', availability: '全部时机', desc: '当前已发布 flow_action 记录 ID。' },
  { method: 'getActionName()', returnType: 'String', availability: '全部时机', desc: '动作业务名称。' },
  { method: 'getTriggerTiming()', returnType: 'String', availability: '全部时机', desc: '本次触发的业务时机编码。' },
  { method: 'getScopeType()', returnType: 'String', availability: '全部时机', desc: 'PROCESS、NODE 或 SEQUENCE_FLOW。' },
  { method: 'getElementId()', returnType: 'String', availability: '节点/连线', desc: '绑定元素 ID；流程级动作为空。' },
  { method: 'getElementName()', returnType: 'String', availability: '节点/任务', desc: '节点或任务名称；连线通常为空。' },
  { method: 'getElementType()', returnType: 'String', availability: '节点/任务/连线', desc: 'userTask、sequenceFlow 等 BPMN 类型。' },
  { method: 'getProcessInstanceId()', returnType: 'String', availability: '全部时机', desc: '流程实例 ID，提交后动作仍可使用。' },
  { method: 'getProcessDefinitionId()', returnType: 'String', availability: '全部时机', desc: 'Flowable 流程定义 ID，用于解析发布版本。' },
  { method: 'getExecutionId()', returnType: 'String', availability: '流程运行期间', desc: '触发事件的执行实例 ID；结束类动作可能只用于日志。' },
  { method: 'getEntityCode()', returnType: 'String', availability: '绑定实体的流程', desc: '流程变量 entityCode 的快照。' },
  { method: 'getEntityDataId()', returnType: 'String', availability: '绑定实体的流程', desc: '流程变量 entityDataId 的快照。' },
  { method: 'getTaskId()', returnType: 'String', availability: 'TASK_*', desc: '触发任务 ID；非任务时机为空。' },
  { method: 'getTaskName()', returnType: 'String', availability: 'TASK_*', desc: '触发任务名称。' },
  { method: 'getTaskAssignee()', returnType: 'String', availability: 'TASK_*', desc: '事件发生时的办理人；候选人模式下可能为空。' },
  { method: 'getOperatorId()', returnType: 'String', availability: '用户触发事件', desc: '当前用户、审批人或发起人的兜底标识。' },
  { method: 'getApprovalAction()', returnType: 'String', availability: 'TASK_COMPLETING', desc: '审批动作变量 action 或 approved，例如 approve、reject。' },
  { method: 'getSequenceFlowId()', returnType: 'String', availability: '连线动作', desc: '兼容字段；新动作通常与 elementId 相同。' },
  { method: 'getSourceNodeId()/Name()', returnType: 'String', availability: 'TRANSITION_TAKEN', desc: '本次实际经过连线的来源节点。' },
  { method: 'getTargetNodeId()/Name()', returnType: 'String', availability: 'TRANSITION_TAKEN', desc: '本次实际经过连线的目标节点。' },
  { method: 'getEndReason()', returnType: 'String', availability: '撤回/终止', desc: '撤回原因、取消原因或终止事件编码；正常完成为空。' },
  { method: 'getIdempotencyKey()', returnType: 'String', availability: '全部时机', desc: '稳定执行幂等键，提交后调用外部系统时必须传递。' },
  { method: 'getCustomParams()', returnType: 'Map<String, Object>', availability: '全部时机', desc: 'paramsJson 解析变量和表达式后的业务参数。' },
  { method: 'getVariables()', returnType: 'Map<String, Object>', availability: '全部时机', desc: '事件变量与流程变量合并后的快照；流程结束时自动回退历史变量。' },
  { method: 'getVariable(name)', returnType: 'Object', availability: '全部时机', desc: '读取单个流程变量，优先使用事件快照。' },
  { method: 'getTriggerTask()', returnType: 'Task', availability: 'TASK_*', desc: '按 taskId 查询触发任务；任务删除后可能为空。' },
  { method: 'getCurrentTask()', returnType: 'Task', availability: '流程运行期间', desc: '查询当前活动任务；并行任务可能无法唯一确定，结束类动作为空。' },
  { method: 'getProcessInstance()', returnType: 'ProcessInstance', availability: '流程运行期间', desc: '运行时流程实例；流程结束后为空。' },
  { method: 'getHistoricProcessInstance()', returnType: 'HistoricProcessInstance', availability: '全部时机', desc: '历史流程实例，结束类动作应优先使用。' },
  { method: 'getEntityData()', returnType: 'EntityDataDTO', availability: '绑定实体的流程', desc: '按 entityCode 和 entityDataId 查询最新实体数据。' }
]

const sceneGuideList = [
  { scene: '流程启动初始化', timing: 'PROCESS_STARTED', mode: 'IN_TRANSACTION + ROLLBACK', reason: '初始化失败时流程不应成功启动。' },
  { scene: '审批前业务校验', timing: 'TASK_COMPLETING', mode: 'IN_TRANSACTION + ROLLBACK', reason: '校验、任务完成和流程流转保持原子性。' },
  { scene: '计算后续分支变量', timing: 'NODE_COMPLETED', mode: 'IN_TRANSACTION + ROLLBACK', reason: '变量可参与后续网关或连线条件计算。' },
  { scene: '通知下一办理人', timing: 'TASK_CREATED', mode: 'AFTER_COMMIT + RETRY', reason: '此时已获得真实任务 ID 和最终目标任务。' },
  { scene: '转办或认领通知', timing: 'TASK_ASSIGNED', mode: 'AFTER_COMMIT + RETRY', reason: '每次办理人变化都能获得最新人员。' },
  { scene: '审批分支状态同步', timing: 'TRANSITION_TAKEN', mode: 'IN_TRANSACTION + ROLLBACK', reason: '只处理实际命中的顺序流。' },
  { scene: '流程完成通知', timing: 'PROCESS_COMPLETED', mode: 'AFTER_COMMIT + RETRY', reason: '不阻塞流程完成，并可可靠重试。' },
  { scene: '撤回资源释放', timing: 'PROCESS_WITHDRAWN', mode: 'AFTER_COMMIT + RETRY', reason: '区分撤回语义，并在主事务提交后执行清理。' }
]

const customTimingOptions = computed(() => runtimeTimingOptions.value.filter(option => option.custom))

async function loadRuntimeMetadata() {
  handlersLoading.value = true
  try {
    const [handlerList, timingOptions] = await Promise.all([
      processActionApi.listHandlers(),
      processActionApi.timingOptions()
    ])
    handlers.value = handlerList || []
    runtimeTimingOptions.value = timingOptions || []
  } catch (error) {
    console.error('加载流程动作运行时元数据失败:', error)
    handlers.value = []
    runtimeTimingOptions.value = []
  } finally {
    handlersLoading.value = false
  }
}

function formatCodes(values, emptyText) {
  if (!values) return emptyText
  const list = Array.isArray(values) ? values : [...values]
  return list.length ? list.join('、') : emptyText
}

function executionModeLabel(value) {
  if (value === 'IN_TRANSACTION') return '事务内执行'
  if (value === 'AFTER_COMMIT') return '提交后执行'
  return value || '未声明'
}

function failurePolicyLabel(value) {
  const labels = {
    ROLLBACK: '失败回滚',
    CONTINUE: '失败继续',
    RETRY: '自动重试',
    IGNORE: '失败忽略'
  }
  return labels[value] || value
}

function scrollToSection(id) {
  const target = document.getElementById(id)
  if (!target) return
  activeSection.value = id
  target.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

function setupSectionObserver() {
  const sections = tocItems
    .map(item => document.getElementById(item.id))
    .filter(Boolean)
  if (!sections.length) return
  sectionObserver = new IntersectionObserver(
    entries => {
      const current = entries
        .filter(entry => entry.isIntersecting)
        .sort((left, right) => left.boundingClientRect.top - right.boundingClientRect.top)[0]
      if (current?.target?.id) activeSection.value = current.target.id
    },
    { rootMargin: '-12% 0px -72% 0px', threshold: [0, 0.01] }
  )
  sections.forEach(section => sectionObserver.observe(section))
}

onMounted(async () => {
  loadRuntimeMetadata()
  await nextTick()
  setupSectionObserver()
})

onBeforeUnmount(() => {
  sectionObserver?.disconnect()
})
</script>

<style scoped>
.dev-guide-page {
  padding: 20px;
}

.page-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  margin-bottom: 16px;
}

.page-header h2 {
  margin: 0;
  font-size: 22px;
  font-weight: 600;
}

.page-header p {
  margin: 6px 0 0;
  color: #909399;
  font-size: 13px;
}

.page-alert {
  margin-bottom: 20px;
}

.guide-layout {
  display: grid;
  grid-template-columns: 220px minmax(0, 1fr);
  gap: 20px;
  align-items: start;
}

.toc-panel {
  position: sticky;
  top: 10px;
  max-height: calc(100vh - 100px);
  overflow-y: auto;
  padding: 16px 12px;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  background: #fff;
}

.toc-title {
  margin-bottom: 10px;
  padding: 0 8px;
  color: #303133;
  font-size: 15px;
  font-weight: 600;
}

.toc-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.toc-link {
  display: flex;
  gap: 9px;
  align-items: center;
  padding: 9px 8px;
  border-radius: 6px;
  color: #606266;
  font-size: 13px;
  line-height: 1.4;
  text-decoration: none;
  transition: color 0.15s ease, background-color 0.15s ease;
}

.toc-link:hover {
  background: #f5f7fa;
  color: #409eff;
}

.toc-link.active {
  background: #ecf5ff;
  color: #409eff;
  font-weight: 600;
}

.toc-index {
  color: #a8abb2;
  font-family: Menlo, Monaco, Consolas, monospace;
  font-size: 11px;
}

.toc-link.active .toc-index {
  color: #409eff;
}

.detail-sections {
  display: flex;
  flex-direction: column;
  gap: 20px;
  min-width: 0;
}

.doc-section {
  scroll-margin-top: 14px;
  padding: 20px;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  background: #fff;
}

.section-title {
  margin-bottom: 14px;
  padding-left: 10px;
  border-left: 4px solid #409eff;
  font-size: 17px;
  font-weight: 600;
}

.section-intro {
  margin: 0 0 14px;
  color: #606266;
  line-height: 1.8;
}

.storage-collapse,
.faq-collapse {
  margin-top: 16px;
}

.timing-detail {
  display: grid;
  gap: 10px;
  padding: 8px 20px 14px 58px;
  color: #606266;
  line-height: 1.7;
}

.timing-detail strong {
  color: #303133;
}

.policy-tag {
  margin-left: 6px;
}

.inline-alert,
.code-block-card,
.subsection-title {
  margin-top: 16px;
}

.tip-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin-top: 16px;
}

.step-list {
  margin: 0 0 16px;
  padding-left: 22px;
  color: #606266;
  line-height: 2;
}

code {
  padding: 2px 5px;
  border-radius: 4px;
  background: #f4f4f5;
  color: #7c3aed;
  font-family: Menlo, Monaco, Consolas, monospace;
  font-size: 12px;
}

.code-header {
  display: flex;
  align-items: center;
  gap: 10px;
}

.code-title,
.subsection-title {
  color: #303133;
  font-weight: 600;
}

.code-block {
  overflow-x: auto;
  margin: 0;
  padding: 16px;
  border-radius: 4px;
  background: #f5f7fa;
  font-family: Menlo, Monaco, Consolas, monospace;
  font-size: 13px;
  line-height: 1.7;
}

@media (max-width: 1100px) {
  .guide-layout {
    grid-template-columns: 1fr;
  }

  .toc-panel {
    position: static;
    max-height: none;
  }

  .toc-list {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .tip-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 680px) {
  .toc-list {
    grid-template-columns: 1fr;
  }
}
</style>
