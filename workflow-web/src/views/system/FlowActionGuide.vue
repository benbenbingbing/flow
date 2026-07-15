<template>
  <div class="dev-guide-page">
    <div class="page-header">
      <h2>流程动作开发指南</h2>
    </div>

    <div class="content-layout">
      <div class="main-content">
        <el-alert
          title="流程动作用于在顺序流被执行时，调用后端自定义业务逻辑。开发者只需实现 FlowActionHandler 接口并注册为 Spring Bean，即可在流程设计器中配置使用。"
          type="info"
          :closable="false"
          show-icon
          style="margin-bottom: 16px"
        />

        <div class="detail-sections">
          <section id="section0" class="doc-section">
            <div class="section-title">一、概述</div>
            <div class="section-content">
              <p class="section-intro">
                流程动作（Flow Action）配置在 BPMN 顺序流（SequenceFlow）上。当流程实例经过该顺序流时，平台会依次执行顺序流上启用的动作。
              </p>
              <p class="section-intro">
                与审批操作不同：审批操作是用户在任务办理界面点击的按钮；流程动作是后台自动执行的接口调用，常用于发送通知、同步外部系统、记录日志等场景。
              </p>
            </div>
          </section>

          <section id="section1" class="doc-section">
            <div class="section-title">二、开发步骤</div>
            <div class="section-content">
              <ol class="step-list">
                <li>创建一个 Spring Bean，实现 <code>FlowActionHandler</code> 接口。</li>
                <li>在 <code>execute(FlowActionContext ctx)</code> 中编写业务逻辑。</li>
                <li>如需类型化参数，实现 <code>TypedFlowActionHandler&lt;T&gt;</code> 并定义参数类。</li>
                <li>在流程设计器的顺序流“流程动作”中，选择处理器并配置业务参数。</li>
                <li>发布流程后，流程经过该顺序流时会自动触发动作。</li>
              </ol>
            </div>
          </section>

          <section id="section2" class="doc-section">
            <div class="section-title">三、示例代码</div>
            <div class="section-content">
              <div class="subsection">
                <div class="subsection-title">1. 普通 Handler</div>
                <el-card shadow="never" class="code-block-card">
                  <template #header>
                    <div class="code-header">
                      <el-tag size="small" type="danger">Java</el-tag>
                      <span class="code-title">SendNotificationHandler.java</span>
                    </div>
                  </template>
                  <pre class="code-block" v-pre><code>package com.workflow.process.action.handler;

import com.workflow.process.action.FlowActionContext;
import com.workflow.process.action.FlowActionHandler;
import org.springframework.stereotype.Component;

@Component("sendNotificationHandler")
public class SendNotificationHandler implements FlowActionHandler {

    @Override
    public void execute(FlowActionContext ctx) {
        String templateCode = (String) ctx.getCustomParams().get("templateCode");
        String receiver = (String) ctx.getCustomParams().get("receiverExpr");

        Object startUserId = ctx.getVariable("startUserId");
        Object entityData = ctx.getEntityData();

        System.out.println("发送通知: " + templateCode
                + ", 流程实例: " + ctx.getProcessInstanceId()
                + ", 实体: " + ctx.getEntityCode() + "/" + ctx.getEntityDataId()
                + ", 接收人: " + receiver);
    }
}</code></pre>
                </el-card>
              </div>

              <div class="subsection">
                <div class="subsection-title">2. 带类型化参数的 Handler</div>
                <el-card shadow="never" class="code-block-card">
                  <template #header>
                    <div class="code-header">
                      <el-tag size="small" type="danger">Java</el-tag>
                      <span class="code-title">TypedNotificationHandler.java</span>
                    </div>
                  </template>
                  <pre class="code-block" v-pre><code>package com.workflow.process.action.handler;

import com.workflow.process.action.FlowActionContext;
import com.workflow.process.action.TypedFlowActionHandler;
import lombok.Data;
import org.springframework.stereotype.Component;

@Component("typedNotificationHandler")
public class TypedNotificationHandler implements TypedFlowActionHandler&lt;TypedNotificationHandler.Param&gt; {

    @Data
    public static class Param {
        private String templateCode;
        private String notifyType;
        private String receiverExpr;
        private Boolean skipOnHoliday;
    }

    @Override
    public Class&lt;Param&gt; getParamType() {
        return Param.class;
    }

    @Override
    public void execute(FlowActionContext ctx, Param param) {
        Object entityData = ctx.getEntityData();
        System.out.println("发送通知: " + param.getTemplateCode()
                + ", 接收人: " + param.getReceiverExpr()
                + ", 流程实例: " + ctx.getProcessInstanceId());
    }
}</code></pre>
                </el-card>
              </div>

              <div class="subsection">
                <div class="subsection-title">3. 流程设计器中参数配置示例</div>
                <el-card shadow="never" class="code-block-card">
                  <template #header>
                    <div class="code-header">
                      <el-tag size="small" type="success">JSON</el-tag>
                      <span class="code-title">paramsJson</span>
                    </div>
                  </template>
                  <pre class="code-block" v-pre><code>{
  "templateCode": "NOTIFY_MANAGER",
  "notifyType": "sms",
  "level": "high",
  "receiverExpr": "${startUserId}",
  "skipOnHoliday": false
}</code></pre>
                </el-card>
              </div>
            </div>
          </section>

          <section id="section3" class="doc-section">
            <div class="section-title">四、上下文 API</div>
            <div class="section-content">
              <p class="section-intro">平台自动注入的固化参数与便捷查询方法如下：</p>
              <el-table :data="apiList" border style="width: 100%">
                <el-table-column prop="method" label="方法 / 字段" width="220" />
                <el-table-column prop="returnType" label="返回值" width="200" />
                <el-table-column prop="desc" label="说明" />
              </el-table>
            </div>
          </section>

          <section id="section4" class="doc-section">
            <div class="section-title">五、参数值类型</div>
            <div class="section-content">
              <el-table :data="paramTypeList" border style="width: 100%">
                <el-table-column prop="type" label="类型" width="120" />
                <el-table-column prop="example" label="示例" width="200" />
                <el-table-column prop="desc" label="说明" />
              </el-table>
            </div>
          </section>

          <section id="section5" class="doc-section">
            <div class="section-title">六、已注册处理器</div>
            <div class="section-content">
              <p class="section-intro">以下为当前系统中已注册的 FlowActionHandler Bean，可直接在流程设计器中选择使用。</p>
              <el-table :data="handlers" border style="width: 100%" v-loading="handlersLoading">
                <el-table-column prop="beanName" label="Bean 名称" width="200" />
                <el-table-column prop="className" label="类名" />
                <el-table-column prop="typed" label="是否类型化" width="120">
                  <template #default="{ row }">
                    <el-tag :type="row.typed ? 'success' : 'info'" size="small">{{ row.typed ? '是' : '否' }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="paramType" label="参数类型" width="200" />
              </el-table>
            </div>
          </section>

          <section id="section6" class="doc-section">
            <div class="section-title">七、常见问题</div>
            <div class="section-content">
              <el-collapse>
                <el-collapse-item title="发布流程时提示 Bean 未实现 FlowActionHandler">
                  请确认：<br />
                  1. 类上添加了 <code>@Component</code> 或其他 Spring 注解；<br />
                  2. 类实现了 <code>FlowActionHandler</code> 接口；<br />
                  3. 类位于 <code>com.workflow</code> 包或其子包下，能被组件扫描覆盖。
                </el-collapse-item>
                <el-collapse-item title="如何获取发起人信息">
                  通过 <code>ctx.getVariable("startUserId")</code> 获取发起人 ID，再通过用户服务查询详细信息。
                </el-collapse-item>
                <el-collapse-item title="如何获取审批结果">
                  审批结果会作为流程变量保存，变量名为 <code>approved</code>，取值为动作选项的 value，如 <code>approve</code>、<code>reject</code> 或自定义值。
                </el-collapse-item>
                <el-collapse-item title="动作执行异常会影响流程流转吗">
                  会。动作执行失败会抛出异常，导致当前顺序流执行中断。如需降级处理，请在 Handler 内部捕获异常并记录日志。
                </el-collapse-item>
              </el-collapse>
            </div>
          </section>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { flowActionApi } from '@/api/flowAction'

const handlers = ref([])
const handlersLoading = ref(false)

const apiList = [
  { method: 'getActionId()', returnType: 'String', desc: '当前 flow_action 记录 ID' },
  { method: 'getActionName()', returnType: 'String', desc: '当前 flow_action 动作名称' },
  { method: 'getProcessInstanceId()', returnType: 'String', desc: '流程实例 ID' },
  { method: 'getEntityCode()', returnType: 'String', desc: '实体编码' },
  { method: 'getEntityDataId()', returnType: 'String', desc: '实体数据 ID' },
  { method: 'getSequenceFlowId()', returnType: 'String', desc: '触发该动作的顺序流元素 ID' },
  { method: 'getSourceNodeId()', returnType: 'String', desc: '顺序流源节点 ID' },
  { method: 'getSourceNodeName()', returnType: 'String', desc: '顺序流源节点名称' },
  { method: 'getTargetNodeId()', returnType: 'String', desc: '顺序流目标节点 ID' },
  { method: 'getTargetNodeName()', returnType: 'String', desc: '顺序流目标节点名称' },
  { method: 'getCustomParams()', returnType: 'Map<String, Object>', desc: '前端 paramsJson 解析后的业务参数' },
  { method: 'getVariables()', returnType: 'Map<String, Object>', desc: '当前流程变量快照' },
  { method: 'getVariable(name)', returnType: 'Object', desc: '获取单个流程变量' },
  { method: 'getProcessInstance()', returnType: 'ProcessInstance', desc: '流程实例对象' },
  { method: 'getHistoricProcessInstance()', returnType: 'HistoricProcessInstance', desc: '历史流程实例对象' },
  { method: 'getCurrentTask()', returnType: 'Task', desc: '当前活动任务' },
  { method: 'getEntityData()', returnType: 'EntityDataDTO', desc: '完整实体数据' }
]

const paramTypeList = [
  { type: '静态文本', example: 'NOTIFY_MANAGER', desc: '字符串，直接传入 Handler' },
  { type: '数字', example: '100', desc: '整数或小数' },
  { type: '布尔', example: 'true / false', desc: 'true 或 false' },
  { type: '流程变量', example: '${startUserId}', desc: '解析为流程变量值后传入' },
  { type: '表达式', example: '${#vars[\'price\'] * 2}', desc: '支持 SpEL 复杂表达式' }
]

async function loadHandlers() {
  handlersLoading.value = true
  try {
    const res = await flowActionApi.listHandlers()
    handlers.value = res || []
  } catch (e) {
    console.error('加载处理器列表失败:', e)
    handlers.value = []
  } finally {
    handlersLoading.value = false
  }
}

onMounted(() => {
  loadHandlers()
})
</script>

<style scoped>
.dev-guide-page { padding: 20px; }
.page-header { margin-bottom: 20px; }
.page-header h2 { margin: 0; font-size: 20px; font-weight: 600; }
.content-layout { display: flex; gap: 20px; }
.main-content { flex: 1; min-width: 0; }
.detail-sections { display: flex; flex-direction: column; gap: 24px; }
.doc-section { background: #fff; border-radius: 8px; padding: 20px; border: 1px solid #e4e7ed; }
.section-title { font-size: 16px; font-weight: 600; margin-bottom: 12px; padding-left: 10px; border-left: 4px solid #409eff; }
.section-intro { color: #606266; line-height: 1.8; margin: 0 0 12px; }
.step-list { padding-left: 20px; color: #606266; line-height: 2; }
.step-list code { background: #f4f4f5; padding: 2px 6px; border-radius: 4px; }
.subsection { margin-top: 16px; }
.subsection-title { font-weight: 500; margin-bottom: 8px; color: #303133; }
.code-block-card { margin-bottom: 16px; }
.code-header { display: flex; align-items: center; gap: 10px; }
.code-title { font-weight: 500; color: #303133; }
.code-block { margin: 0; padding: 16px; background: #f5f7fa; border-radius: 4px; overflow-x: auto; font-family: monospace; font-size: 13px; line-height: 1.6; }
</style>
