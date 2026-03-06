<template>
  <div class="node-config-panel">
    <!-- 节点类型标识 -->
    <div class="node-type-header">
      <el-tag :type="getNodeTypeTag(element?.type)" size="large">
        {{ nodeTypeText }}
      </el-tag>
      <span class="node-id">{{ element?.id }}</span>
    </div>
    
    <div v-if="!element" class="no-selection">
      <el-empty description="请点击流程节点进行配置" />
    </div>
    
    <el-tabs v-else v-model="activeTab" class="config-tabs">
      <!-- ========== 基本信息（所有节点都有） ========== -->
      <el-tab-pane label="基本信息" name="basic">
        <el-form :model="basicForm" label-width="100px" size="small">
          <el-form-item label="节点名称">
            <el-input 
              v-model="basicForm.name" 
              :placeholder="getNamePlaceholder()"
              @blur="updateProperty('name', basicForm.name)"
            />
          </el-form-item>
          
          <el-form-item label="节点ID">
            <el-input v-model="basicForm.id" disabled />
          </el-form-item>
          
          <!-- 节点类型说明 -->
          <el-alert type="info" :closable="false" class="node-type-alert">
            <template #title>
              <div class="node-type-info">
                <div class="info-title">{{ nodeTypeDesc.title }}</div>
                <div class="info-desc">{{ nodeTypeDesc.desc }}</div>
                <div class="info-scene">
                  <el-tag size="small" type="warning">场景</el-tag>
                  {{ nodeTypeDesc.scene }}
                </div>
              </div>
            </template>
          </el-alert>
          
          <el-form-item label="说明文档" class="doc-item">
            <el-input 
              v-model="basicForm.documentation" 
              type="textarea"
              :rows="3"
              placeholder="输入节点说明..."
              @blur="updateDocumentation"
            />
          </el-form-item>
        </el-form>
      </el-tab-pane>
      
      <!-- ========== 执行人配置（仅用户任务） ========== -->
      <el-tab-pane v-if="isUserTask" label="执行人" name="assignee">
        <el-form :model="assigneeForm" label-width="100px" size="small">
          <el-form-item label="执行人">
            <el-input 
              v-model="assigneeForm.assignee" 
              placeholder="如：zhangsan 或 ${userId}"
              @blur="updateProperty('assignee', assigneeForm.assignee)"
            />
            <div class="form-tip">直接指定一个用户，支持表达式</div>
          </el-form-item>
          
          <el-form-item label="候选人">
            <el-input 
              v-model="assigneeForm.candidateUsers" 
              type="textarea"
              :rows="2"
              placeholder="多个用户用逗号分隔，如：zhangsan,lisi"
              @blur="updateProperty('candidateUsers', assigneeForm.candidateUsers)"
            />
            <div class="form-tip">多个候选人，任务可被其中任意一人认领</div>
          </el-form-item>
          
          <el-form-item label="候选组">
            <el-input 
              v-model="assigneeForm.candidateGroups" 
              type="textarea"
              :rows="2"
              placeholder="多个组用逗号分隔，如：manager,hr"
              @blur="updateProperty('candidateGroups', assigneeForm.candidateGroups)"
            />
            <div class="form-tip">组成员都可处理任务</div>
          </el-form-item>
          
          <el-divider>多实例配置（会签/或签）</el-divider>
          
          <el-form-item label="启用多实例">
            <el-switch v-model="assigneeForm.isMultiInstance" @change="onMultiInstanceChange" />
          </el-form-item>
          
          <template v-if="assigneeForm.isMultiInstance">
            <el-form-item label="执行方式">
              <el-radio-group v-model="assigneeForm.multiInstanceType">
                <el-radio-button label="parallel">并行（会签）</el-radio-button>
                <el-radio-button label="sequential">串行（或签）</el-radio-button>
              </el-radio-group>
              <div class="form-tip">并行：多人同时审批；串行：按顺序审批</div>
            </el-form-item>
            
            <el-form-item label="集合变量">
              <el-input 
                v-model="assigneeForm.collection" 
                placeholder="如：${assigneeList}"
                @blur="updateMultiInstance"
              />
            </el-form-item>
            
            <el-form-item label="元素变量">
              <el-input 
                v-model="assigneeForm.elementVariable" 
                placeholder="如：assignee"
                @blur="updateMultiInstance"
              />
            </el-form-item>
            
            <el-form-item label="完成条件">
              <el-input 
                v-model="assigneeForm.completionCondition" 
                placeholder="如：${nrOfCompletedInstances >= 2}"
                @blur="updateMultiInstance"
              />
              <div class="form-tip">满足此条件时任务完成</div>
            </el-form-item>
          </template>
        </el-form>
      </el-tab-pane>
      
      <!-- ========== 服务配置（服务任务） ========== -->
      <el-tab-pane v-if="isServiceTask" label="服务" name="service">
        <el-form :model="serviceForm" label-width="100px" size="small">
          <el-alert type="info" :closable="false" class="section-alert">
            自动执行Java代码、调用外部服务或发送HTTP请求，无需人工干预
          </el-alert>
          
          <el-form-item label="实现类型">
            <el-radio-group v-model="serviceForm.implementationType" @change="onServiceTypeChange">
              <el-radio-button label="class">Java类</el-radio-button>
              <el-radio-button label="expression">表达式</el-radio-button>
              <el-radio-button label="delegateExpression">Spring Bean</el-radio-button>
              <el-radio-button label="rest">REST接口</el-radio-button>
            </el-radio-group>
          </el-form-item>
          
          <!-- Java类/表达式/Spring Bean 配置 -->
          <template v-if="serviceForm.implementationType !== 'rest'">
            <el-form-item label="实现">
              <el-input 
                v-model="serviceForm.implementation" 
                :placeholder="servicePlaceholder"
                @blur="updateServiceImplementation"
              />
            </el-form-item>
          </template>
          
          <!-- REST接口配置 -->
          <template v-else>
            <el-form-item label="请求方式">
              <el-radio-group v-model="restForm.method">
                <el-radio-button label="GET">GET</el-radio-button>
                <el-radio-button label="POST">POST</el-radio-button>
                <el-radio-button label="PUT">PUT</el-radio-button>
                <el-radio-button label="DELETE">DELETE</el-radio-button>
              </el-radio-group>
            </el-form-item>
            
            <el-form-item label="请求URL">
              <el-input 
                v-model="restForm.url" 
                placeholder="如：https://api.example.com/users/${userId}"
                @blur="updateRestConfig"
              />
              <div class="form-tip">支持流程变量表达式，如：${userId}</div>
            </el-form-item>
            
            <el-form-item label="Content-Type">
              <el-select v-model="restForm.contentType" @change="updateRestConfig">
                <el-option label="application/json" value="application/json" />
                <el-option label="application/x-www-form-urlencoded" value="application/x-www-form-urlencoded" />
                <el-option label="multipart/form-data" value="multipart/form-data" />
                <el-option label="text/xml" value="text/xml" />
              </el-select>
            </el-form-item>
            
            <el-form-item label="请求头(Headers)">
              <el-input 
                v-model="restForm.headers" 
                type="textarea"
                :rows="3"
                placeholder='{"Authorization": "Bearer ${token}", "X-Request-Id": "${requestId}"}'
                @blur="updateRestConfig"
              />
              <div class="form-tip">JSON格式，支持流程变量表达式</div>
            </el-form-item>
            
            <el-form-item label="请求体(Body)" v-if="restForm.method !== 'GET'">
              <el-input 
                v-model="restForm.body" 
                type="textarea"
                :rows="5"
                :placeholder="getRestBodyPlaceholder()"
                @blur="updateRestConfig"
                class="code-input"
              />
              <div class="form-tip">JSON格式或表单格式，支持流程变量表达式如：${variable}</div>
            </el-form-item>
            
            <el-form-item label="查询参数">
              <el-input 
                v-model="restForm.queryParams" 
                type="textarea"
                :rows="2"
                placeholder='{"page": "${page}", "size": "10"}'
                @blur="updateRestConfig"
              />
              <div class="form-tip">URL查询参数，JSON格式，支持流程变量</div>
            </el-form-item>
            
            <el-divider>高级配置</el-divider>
            
            <el-row :gutter="10">
              <el-col :span="12">
                <el-form-item label="超时时间(秒)">
                  <el-input-number v-model="restForm.timeout" :min="1" :max="300" style="width: 100%" @change="updateRestConfig" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="重试次数">
                  <el-input-number v-model="restForm.retryCount" :min="0" :max="5" style="width: 100%" @change="updateRestConfig" />
                </el-form-item>
              </el-col>
            </el-row>
            
            <el-form-item label="错误处理">
              <el-radio-group v-model="restForm.errorHandling" @change="updateRestConfig">
                <el-radio label="throw">抛出异常终止流程</el-radio>
                <el-radio label="continue">记录错误继续流程</el-radio>
                <el-radio label="ignore">忽略错误</el-radio>
              </el-radio-group>
            </el-form-item>
            
            <el-form-item label="结果映射">
              <el-input 
                v-model="restForm.resultMapping" 
                type="textarea"
                :rows="3"
                placeholder='{"data.id": "userId", "data.status": "status", "code": "resultCode"}'
                @blur="updateRestConfig"
              />
              <div class="form-tip">将响应结果映射到流程变量，JSON格式：响应路径 -> 变量名</div>
            </el-form-item>
          </template>
          
          <el-form-item label="结果变量" v-if="serviceForm.implementationType !== 'rest'">
            <el-input 
              v-model="serviceForm.resultVariable" 
              placeholder="存储结果到变量"
              @blur="updateProperty('resultVariable', serviceForm.resultVariable)"
            />
          </el-form-item>
        </el-form>
      </el-tab-pane>
      
      <!-- ========== 发送配置（发送任务） ========== -->
      <el-tab-pane v-if="isSendTask" label="发送" name="send">
        <el-form :model="sendForm" label-width="100px" size="small">
          <el-alert type="info" :closable="false" class="section-alert">
            自动发送消息（邮件、短信、站内信）给指定人员
          </el-alert>
          
          <el-form-item label="发送渠道">
            <el-checkbox-group v-model="sendForm.channels">
              <el-checkbox label="email">邮件</el-checkbox>
              <el-checkbox label="sms">短信</el-checkbox>
              <el-checkbox label="message">站内信</el-checkbox>
            </el-checkbox-group>
          </el-form-item>
          
          <el-form-item label="接收人">
            <el-input 
              v-model="sendForm.to" 
              placeholder="如：${approverEmail} 或具体邮箱"
            />
          </el-form-item>
          
          <el-form-item label="消息标题">
            <el-input 
              v-model="sendForm.subject" 
              placeholder="消息标题模板"
            />
          </el-form-item>
          
          <el-form-item label="消息内容">
            <el-input 
              v-model="sendForm.content" 
              type="textarea"
              :rows="4"
              placeholder="支持变量如：${processName} 已提交，请审批"
            />
          </el-form-item>
          
          <el-form-item label="消息模板">
            <el-select v-model="sendForm.templateKey" placeholder="选择消息模板" clearable>
              <el-option label="流程提交通知" value="PROCESS_SUBMIT" />
              <el-option label="审批通过通知" value="APPROVE_PASS" />
              <el-option label="审批拒绝通知" value="APPROVE_REJECT" />
            </el-select>
          </el-form-item>
        </el-form>
      </el-tab-pane>
      
      <!-- ========== 接收配置（接收任务） ========== -->
      <el-tab-pane v-if="isReceiveTask" label="接收" name="receive">
        <el-form :model="receiveForm" label-width="100px" size="small">
          <el-alert type="warning" :closable="false" class="section-alert">
            流程将暂停执行，等待外部系统或事件触发后才继续
          </el-alert>
          
          <el-form-item label="消息名称">
            <el-input 
              v-model="receiveForm.messageRef" 
              placeholder="如：paymentCallback"
            />
            <div class="form-tip">外部系统需要发送此名称的消息来触发流程继续</div>
          </el-form-item>
          
          <el-form-item label="超时设置">
            <el-switch v-model="receiveForm.hasTimeout" />
          </el-form-item>
          
          <template v-if="receiveForm.hasTimeout">
            <el-form-item label="超时时间">
              <el-input-number v-model="receiveForm.timeout" :min="1" style="width: 120px" />
              <el-select v-model="receiveForm.timeoutUnit" style="width: 100px; margin-left: 8px">
                <el-option label="分钟" value="MINUTE" />
                <el-option label="小时" value="HOUR" />
                <el-option label="天" value="DAY" />
              </el-select>
            </el-form-item>
            
            <el-form-item label="超时处理">
              <el-radio-group v-model="receiveForm.timeoutAction">
                <el-radio label="error">抛出异常</el-radio>
                <el-radio label="continue">继续执行</el-radio>
              </el-radio-group>
            </el-form-item>
          </template>
        </el-form>
      </el-tab-pane>
      
      <!-- ========== 手动任务配置（手动任务） ========== -->
      <el-tab-pane v-if="isManualTask" label="手动" name="manual">
        <el-form :model="manualForm" label-width="100px" size="small">
          <el-alert type="info" :closable="false" class="section-alert">
            标记需要在流程系统外完成的工作，仅作记录，不生成待办
          </el-alert>
          
          <el-form-item label="任务描述">
            <el-input 
              v-model="manualForm.description" 
              type="textarea"
              :rows="3"
              placeholder="描述需要完成的线下工作..."
            />
          </el-form-item>
          
          <el-form-item label="完成条件">
            <el-input 
              v-model="manualForm.completionCriteria" 
              type="textarea"
              :rows="2"
              placeholder="说明任务完成的判断标准..."
            />
          </el-form-item>
          
          <el-form-item label="负责人">
            <el-input 
              v-model="manualForm.responsible" 
              placeholder="负责完成此任务的人员"
            />
            <div class="form-tip">仅作记录，不发送待办</div>
          </el-form-item>
          
          <el-form-item label="预计工时">
            <el-input-number v-model="manualForm.estimatedHours" :min="0" :precision="1" />
            <span class="unit">小时</span>
          </el-form-item>
        </el-form>
      </el-tab-pane>
      
      <!-- ========== 业务规则配置（业务规则任务） ========== -->
      <el-tab-pane v-if="isBusinessRuleTask" label="规则" name="rule">
        <el-form :model="ruleForm" label-width="100px" size="small">
          <el-alert type="info" :closable="false" class="section-alert">
            执行DMN决策表，根据规则自动决策流程走向
          </el-alert>
          
          <el-form-item label="决策表Key">
            <el-input 
              v-model="ruleForm.decisionRef" 
              placeholder="如：approvalLevelDecision"
            />
            <div class="form-tip">关联的DMN决策表定义Key</div>
          </el-form-item>
          
          <el-form-item label="输入变量">
            <el-input 
              v-model="ruleForm.inputVariables" 
              type="textarea"
              :rows="3"
              placeholder='{"amount": "${amount}", "dept": "${department}"}'
            />
            <div class="form-tip">传递给决策表的输入变量映射</div>
          </el-form-item>
          
          <el-form-item label="结果变量">
            <el-input 
              v-model="ruleForm.resultVariable" 
              placeholder="如：decisionResult"
            />
            <div class="form-tip">存储决策结果的变量名</div>
          </el-form-item>
          
          <el-form-item label="映射结果">
            <el-switch v-model="ruleForm.mapDecisionResult" />
            <div class="form-tip">是否将决策结果映射到流程变量</div>
          </el-form-item>
        </el-form>
      </el-tab-pane>
      
      <!-- ========== 脚本配置（脚本任务） ========== -->
      <el-tab-pane v-if="isScriptTask" label="脚本" name="script">
        <el-form :model="scriptForm" label-width="100px" size="small">
          <el-alert type="info" :closable="false" class="section-alert">
            执行脚本代码，用于轻量级数据处理
          </el-alert>
          
          <el-form-item label="脚本类型">
            <el-radio-group v-model="scriptForm.scriptFormat">
              <el-radio-button label="javascript">JavaScript</el-radio-button>
              <el-radio-button label="groovy">Groovy</el-radio-button>
              <el-radio-button label="python">Python</el-radio-button>
            </el-radio-group>
          </el-form-item>
          
          <el-form-item label="脚本内容">
            <el-input 
              v-model="scriptForm.script" 
              type="textarea"
              :rows="10"
              placeholder="// 脚本代码示例：&#10;execution.setVariable('result', amount * 0.1);"
              class="code-input"
            />
          </el-form-item>
          
          <el-form-item label="结果变量">
            <el-input 
              v-model="scriptForm.resultVariable" 
              placeholder="存储脚本执行结果"
            />
          </el-form-item>
          
          <el-form-item label="自动存储">
            <el-switch v-model="scriptForm.autoStoreVariables" />
            <div class="form-tip">自动将脚本变量存储到流程上下文</div>
          </el-form-item>
        </el-form>
      </el-tab-pane>
      
      <!-- ========== 调用活动配置（调用活动） ========== -->
      <el-tab-pane v-if="isCallActivity" label="子流程" name="call">
        <el-form :model="callForm" label-width="100px" size="small">
          <el-alert type="info" :closable="false" class="section-alert">
            调用另一个独立的子流程，实现流程模块化复用
          </el-alert>
          
          <el-form-item label="子流程Key">
            <el-select 
              v-model="callForm.calledElement" 
              placeholder="选择要调用的子流程"
              filterable
              style="width: 100%"
            >
              <el-option 
                v-for="process in subProcesses" 
                :key="process.key" 
                :label="process.name" 
                :value="process.key" 
              />
            </el-select>
            <div class="form-tip">被调用的子流程定义Key</div>
          </el-form-item>
          
          <el-form-item label="调用方式">
            <el-radio-group v-model="callForm.callActivityType">
              <el-radio label="bpmn">BPMN子流程</el-radio>
              <el-radio label="cmmn">CMMN案例</el-radio>
            </el-radio-group>
          </el-form-item>
          
          <el-divider>参数传递</el-divider>
          
          <el-form-item label="输入参数">
            <el-input 
              v-model="callForm.inputParameters" 
              type="textarea"
              :rows="3"
              placeholder='{"subProcessVar": "${parentVar}"}'
            />
            <div class="form-tip">传递给子流程的变量映射</div>
          </el-form-item>
          
          <el-form-item label="输出参数">
            <el-input 
              v-model="callForm.outputParameters" 
              type="textarea"
              :rows="3"
              placeholder='{"parentResult": "${subProcessResult}"}'
            />
            <div class="form-tip">子流程返回后映射到主流程的变量</div>
          </el-form-item>
          
          <el-form-item label="业务Key">
            <el-input 
              v-model="callForm.businessKey" 
              placeholder="子流程的业务Key"
            />
          </el-form-item>
        </el-form>
      </el-tab-pane>
      
      <!-- ========== 条件配置（顺序流） ========== -->
      <el-tab-pane v-if="isSequenceFlow" label="条件" name="condition">
        <el-form :model="conditionForm" label-width="100px" size="small">
          <el-form-item label="条件类型">
            <el-radio-group v-model="conditionForm.type" @change="onConditionTypeChange">
              <el-radio-button label="">无条件</el-radio-button>
              <el-radio-button label="expression">表达式</el-radio-button>
              <el-radio-button label="default">默认流</el-radio-button>
            </el-radio-group>
          </el-form-item>
          
          <el-form-item v-if="conditionForm.type === 'expression'" label="表达式">
            <el-input 
              v-model="conditionForm.expression" 
              type="textarea"
              :rows="3"
              placeholder="如：${amount > 1000 && approved == true}"
              @blur="updateCondition"
            />
            <div class="form-tip">返回true时执行此分支</div>
          </el-form-item>
          
          <el-alert 
            v-if="conditionForm.type === 'default'" 
            type="warning" 
            :closable="false"
          >
            默认流：当其他条件都不满足时执行
          </el-alert>
        </el-form>
      </el-tab-pane>
      
      <!-- ========== 流程动作（顺序流） ========== -->
      <el-tab-pane v-if="isSequenceFlow" label="流程动作" name="actions">
        <div class="actions-section">
          <div class="actions-header">
            <span>接口动作列表</span>
            <el-button type="primary" size="small" @click="showActionDialog()">
              <el-icon><Plus /></el-icon>添加动作
            </el-button>
          </div>
          
          <el-alert type="info" :closable="false" class="action-alert">
            <template #title>
              <div class="alert-content">
                <span>流程发布后动作才生效</span>
                <el-tag v-if="hasDraftChanges" type="warning" size="small">有未发布变更</el-tag>
              </div>
            </template>
          </el-alert>
          
          <div class="actions-list">
            <div 
              v-for="(action, index) in sortedActions" 
              :key="action.id"
              class="action-item"
              :class="{ disabled: !action.enabled }"
            >
              <div class="action-sort">
                <el-button link size="small" :disabled="index === 0" @click="moveAction(index, -1)">
                  <el-icon><ArrowUp /></el-icon>
                </el-button>
                <span class="sort-number">{{ index + 1 }}</span>
                <el-button link size="small" :disabled="index === sortedActions.length - 1" @click="moveAction(index, 1)">
                  <el-icon><ArrowDown /></el-icon>
                </el-button>
              </div>
              
              <div class="action-content">
                <div class="action-name">{{ action.actionName }}</div>
                <div class="action-detail">
                  <el-tag size="small" :type="action.enabled ? 'success' : 'info'">
                    {{ action.enabled ? '启用' : '禁用' }}
                  </el-tag>
                  <span class="interface-info">{{ action.interfaceName }}</span>
                </div>
              </div>
              
              <div class="action-ops">
                <el-button link type="primary" size="small" @click="showActionDialog(action)">
                  编辑
                </el-button>
                <el-button link type="warning" size="small" @click="toggleActionEnabled(action)">
                  {{ action.enabled ? '禁用' : '启用' }}
                </el-button>
                <el-button link type="danger" size="small" @click="deleteAction(action)">
                  删除
                </el-button>
              </div>
            </div>
            
            <el-empty v-if="sortedActions.length === 0" description="暂无流程动作" />
          </div>
        </div>
        
        <!-- 动作编辑对话框 -->
        <el-dialog
          v-model="actionDialogVisible"
          :title="editingAction.id ? '编辑动作' : '添加动作'"
          width="500px"
          append-to-body
        >
          <el-form :model="editingAction" label-width="100px" size="small">
            <el-form-item label="动作名称" required>
              <el-input v-model="editingAction.actionName" placeholder="如：发送通知" />
            </el-form-item>
            
            <el-form-item label="描述">
              <el-input 
                v-model="editingAction.description" 
                type="textarea"
                :rows="2"
                placeholder="动作描述..."
              />
            </el-form-item>
            
            <el-form-item label="接口名称" required>
              <el-input 
                v-model="editingAction.interfaceName" 
                placeholder="Spring Bean名称或类名"
              />
              <div class="form-tip">如：notificationService 或 com.example.Service</div>
            </el-form-item>
            
            <el-form-item label="方法名">
              <el-input 
                v-model="editingAction.methodName" 
                placeholder="execute"
              />
              <div class="form-tip">默认为 execute</div>
            </el-form-item>
            
            <el-form-item label="参数JSON">
              <el-input 
                v-model="editingAction.paramsJson" 
                type="textarea"
                :rows="4"
                placeholder='{"key": "value"}'
              />
            </el-form-item>
            
            <el-form-item label="是否启用">
              <el-switch v-model="editingAction.enabled" />
            </el-form-item>
          </el-form>
          
          <template #footer>
            <el-button @click="actionDialogVisible = false">取消</el-button>
            <el-button type="primary" @click="saveAction">确定</el-button>
          </template>
        </el-dialog>
      </el-tab-pane>
      
      <!-- ========== 表单配置（任务/开始事件） ========== -->
      <el-tab-pane v-if="isTask || isStartEvent" label="表单" name="form">
        <el-form :model="formConfig" label-width="100px" size="small">
          <el-form-item label="表单Key">
            <el-input 
              v-model="formConfig.formKey" 
              placeholder="如：leave_apply_form"
              @blur="updateProperty('formKey', formConfig.formKey)"
            />
            <div class="form-tip">关联外部表单，留空表示无表单</div>
          </el-form-item>
        </el-form>
      </el-tab-pane>
      
      <!-- ========== 高级配置 ========== -->
      <el-tab-pane v-if="isTask || isGateway" label="高级" name="advanced">
        <el-form :model="advancedForm" label-width="120px" size="small">
          <el-form-item label="异步执行">
            <el-switch v-model="advancedForm.async" @change="onAsyncChange" />
          </el-form-item>
          
          <template v-if="advancedForm.async">
            <el-form-item label="异步前">
              <el-switch v-model="advancedForm.asyncBefore" @change="updateAsync" />
            </el-form-item>
            <el-form-item label="异步后">
              <el-switch v-model="advancedForm.asyncAfter" @change="updateAsync" />
            </el-form-item>
          </template>
          
          <el-form-item label="跳过表达式">
            <el-input 
              v-model="advancedForm.skipExpression" 
              placeholder="如：${skip}"
              @blur="updateSkipExpression"
            />
          </el-form-item>
        </el-form>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { Plus, ArrowUp, ArrowDown } from '@element-plus/icons-vue'
import { flowActionApi } from '@/api/flowAction'
import { ElMessage, ElMessageBox } from 'element-plus'

const props = defineProps({
  element: { type: Object, required: true },
  processId: { type: String, default: '' }
})

const emit = defineEmits(['save'])
const activeTab = ref('basic')

// ========== 节点类型判断 ==========
const isUserTask = computed(() => props.element?.type === 'bpmn:UserTask')
const isServiceTask = computed(() => props.element?.type === 'bpmn:ServiceTask')
const isSendTask = computed(() => props.element?.type === 'bpmn:SendTask')
const isReceiveTask = computed(() => props.element?.type === 'bpmn:ReceiveTask')
const isManualTask = computed(() => props.element?.type === 'bpmn:ManualTask')
const isBusinessRuleTask = computed(() => props.element?.type === 'bpmn:BusinessRuleTask')
const isScriptTask = computed(() => props.element?.type === 'bpmn:ScriptTask')
const isCallActivity = computed(() => props.element?.type === 'bpmn:CallActivity')
const isSubProcess = computed(() => props.element?.type === 'bpmn:SubProcess')
const isTask = computed(() => props.element?.type?.includes('Task') || props.element?.type?.includes('Activity'))
const isStartEvent = computed(() => props.element?.type === 'bpmn:StartEvent')
const isSequenceFlow = computed(() => props.element?.type === 'bpmn:SequenceFlow')
const isGateway = computed(() => props.element?.type?.includes('Gateway'))

// ========== 节点类型说明 ==========
const nodeTypeDesc = computed(() => {
  const descs = {
    'bpmn:UserTask': {
      title: '用户任务 - 人工审批',
      desc: '需要人工参与处理的任务，生成待办事项，支持审批、会签、或签等操作',
      scene: '请假审批、报销审核、合同签署等需要人工决策的场景'
    },
    'bpmn:ServiceTask': {
      title: '服务任务 - 自动执行',
      desc: '自动执行Java代码或调用外部服务，无需人工干预，用于数据处理、状态更新等',
      scene: '审批通过后自动更新业务表状态、发送系统通知、调用第三方接口等'
    },
    'bpmn:SendTask': {
      title: '发送任务 - 消息通知',
      desc: '向外部系统或用户发送消息（邮件、短信、站内信），流程执行时自动触发',
      scene: '节点完成后自动发送邮件通知、向消息队列发送事件等'
    },
    'bpmn:ReceiveTask': {
      title: '接收任务 - 等待触发',
      desc: '暂停流程执行，等待外部系统或事件触发后才继续，实现异步等待',
      scene: '等待第三方系统回调结果、等待用户在外部系统完成操作等'
    },
    'bpmn:ManualTask': {
      title: '手动任务 - 线下记录',
      desc: '标记需要在流程系统外完成的工作，仅作记录，不生成待办任务',
      scene: '打印纸质文件、现场设备调试等非系统操作'
    },
    'bpmn:BusinessRuleTask': {
      title: '业务规则任务 - 自动决策',
      desc: '执行DMN决策表，根据预定义规则自动决策流程走向',
      scene: '根据金额、部门等条件自动判断审批层级、风险等级等'
    },
    'bpmn:ScriptTask': {
      title: '脚本任务 - 轻量处理',
      desc: '执行脚本代码（JavaScript/Groovy），用于轻量级的数据处理和转换',
      scene: '简单的数据计算、格式转换、变量赋值等'
    },
    'bpmn:CallActivity': {
      title: '调用活动 - 子流程复用',
      desc: '调用另一个独立的子流程，实现流程模块化和复用',
      scene: '合同审批中调用盖章子流程，子流程可在多个主流程中复用'
    },
    'bpmn:SubProcess': {
      title: '子流程 - 逻辑封装',
      desc: '将一组相关的任务封装在父节点内，可折叠/展开，简化主流程视图',
      scene: '将多级审批等复杂逻辑封装，使主流程更清晰'
    },
    'bpmn:SequenceFlow': {
      title: '顺序流 - 流程连线',
      desc: '连接各个节点的箭头，可配置流转条件和执行动作',
      scene: '控制流程走向，设置分支条件和后续处理动作'
    },
    'bpmn:StartEvent': {
      title: '开始事件 - 流程起点',
      desc: '流程的起始节点，触发流程实例的创建',
      scene: '流程的入口'
    },
    'bpmn:EndEvent': {
      title: '结束事件 - 流程终点',
      desc: '流程的终止节点，标志着流程实例的完成',
      scene: '流程的出口'
    }
  }
  return descs[props.element?.type] || { title: '未知节点', desc: '', scene: '' }
})

const nodeTypeText = computed(() => {
  const types = {
    'bpmn:StartEvent': '开始事件',
    'bpmn:EndEvent': '结束事件',
    'bpmn:UserTask': '用户任务',
    'bpmn:ServiceTask': '服务任务',
    'bpmn:ManualTask': '手动任务',
    'bpmn:ScriptTask': '脚本任务',
    'bpmn:BusinessRuleTask': '业务规则任务',
    'bpmn:SendTask': '发送任务',
    'bpmn:ReceiveTask': '接收任务',
    'bpmn:CallActivity': '调用活动',
    'bpmn:SubProcess': '子流程',
    'bpmn:ExclusiveGateway': '排他网关',
    'bpmn:ParallelGateway': '并行网关',
    'bpmn:InclusiveGateway': '包容网关',
    'bpmn:SequenceFlow': '顺序流'
  }
  return types[props.element?.type] || props.element?.type || '未知'
})

function getNodeTypeTag(type) {
  if (type?.includes('StartEvent')) return 'success'
  if (type?.includes('EndEvent')) return 'danger'
  if (type?.includes('UserTask')) return 'primary'
  if (type?.includes('ServiceTask') || type?.includes('Script') || type?.includes('BusinessRule')) return 'warning'
  if (type?.includes('SendTask') || type?.includes('ReceiveTask')) return 'info'
  if (type?.includes('Gateway')) return 'warning'
  return ''
}

function getNamePlaceholder() {
  if (isUserTask.value) return '如：经理审批'
  if (isServiceTask.value) return '如：自动审核'
  if (isSendTask.value) return '如：发送通知'
  if (isReceiveTask.value) return '如：等待回调'
  if (isManualTask.value) return '如：打印文件'
  if (isBusinessRuleTask.value) return '如：风险评级'
  if (isScriptTask.value) return '如：数据计算'
  if (isCallActivity.value) return '如：调用盖章流程'
  return '请输入节点名称'
}

const servicePlaceholder = computed(() => {
  const map = { class: 'com.example.MyServiceTask', expression: '${myService.execute()}', delegateExpression: '${myDelegate}' }
  return map[serviceForm.value.implementationType] || ''
})

// ========== 表单数据 ==========
const basicForm = ref({ id: '', name: '', documentation: '' })
const assigneeForm = ref({ assignee: '', candidateUsers: '', candidateGroups: '', isMultiInstance: false, multiInstanceType: 'parallel', collection: '', elementVariable: 'assignee', completionCondition: '' })
const serviceForm = ref({ implementationType: 'class', implementation: '', resultVariable: '' })

// REST接口配置
const restForm = ref({
  method: 'POST',
  url: '',
  contentType: 'application/json',
  headers: '',
  body: '',
  queryParams: '',
  timeout: 30,
  retryCount: 0,
  errorHandling: 'throw',
  resultMapping: ''
})
const sendForm = ref({ channels: ['email'], to: '', subject: '', content: '', templateKey: '' })
const receiveForm = ref({ messageRef: '', hasTimeout: false, timeout: 30, timeoutUnit: 'MINUTE', timeoutAction: 'error' })
const manualForm = ref({ description: '', completionCriteria: '', responsible: '', estimatedHours: 0 })
const ruleForm = ref({ decisionRef: '', inputVariables: '', resultVariable: '', mapDecisionResult: true })
const scriptForm = ref({ scriptFormat: 'javascript', script: '', resultVariable: '', autoStoreVariables: false })
const callForm = ref({ calledElement: '', callActivityType: 'bpmn', inputParameters: '', outputParameters: '', businessKey: '' })
const conditionForm = ref({ type: '', expression: '' })
const formConfig = ref({ formKey: '' })
const advancedForm = ref({ async: false, asyncBefore: false, asyncAfter: false, skipExpression: '' })

// 子流程列表（模拟）
const subProcesses = ref([
  { key: 'seal_process', name: '盖章流程' },
  { key: 'payment_process', name: '付款流程' },
  { key: 'contract_subprocess', name: '合同子流程' }
])

// ========== 流程动作 ==========
const actions = ref([])
const actionDialogVisible = ref(false)
const editingAction = ref({ id: null, actionName: '', description: '', interfaceName: '', methodName: 'execute', paramsJson: '', enabled: true })
const hasDraftChanges = ref(false)
const sortedActions = computed(() => [...actions.value].sort((a, b) => (a.sortOrder || 0) - (b.sortOrder || 0)))

async function loadActions() {
  if (!isSequenceFlow.value || !props.processId || !props.element?.id) return
  try {
    const res = await flowActionApi.findDraftActionsBySequenceFlow(props.processId, props.element.id)
    actions.value = res || []
    hasDraftChanges.value = false
  } catch (e) { console.error('加载流程动作失败:', e) }
}

function showActionDialog(action = null) {
  editingAction.value = action ? { ...action } : { id: null, actionName: '', description: '', interfaceName: '', methodName: 'execute', paramsJson: '', enabled: true }
  actionDialogVisible.value = true
}

async function saveAction() {
  if (!editingAction.value.actionName || !editingAction.value.interfaceName) {
    ElMessage.warning('请填写必填项')
    return
  }
  try {
    const data = { ...editingAction.value, processConfigId: props.processId, sequenceFlowId: props.element.id, sortOrder: editingAction.value.id ? editingAction.value.sortOrder : actions.value.length }
    await flowActionApi.saveAction(data)
    ElMessage.success('保存成功')
    actionDialogVisible.value = false
    hasDraftChanges.value = true
    await loadActions()
  } catch (e) { ElMessage.error('保存失败') }
}

async function deleteAction(action) {
  try {
    await ElMessageBox.confirm('确定删除该动作吗？', '提示', { type: 'warning' })
    await flowActionApi.deleteAction(action.id)
    ElMessage.success('删除成功')
    hasDraftChanges.value = true
    await loadActions()
  } catch (e) { if (e !== 'cancel') ElMessage.error('删除失败') }
}

async function toggleActionEnabled(action) {
  try {
    await flowActionApi.toggleEnabled(action.id)
    ElMessage.success('操作成功')
    hasDraftChanges.value = true
    await loadActions()
  } catch (e) { ElMessage.error('操作失败') }
}

async function moveAction(index, direction) {
  const newIndex = index + direction
  if (newIndex < 0 || newIndex >= sortedActions.value.length) return
  const list = [...sortedActions.value]
  const temp = list[index]; list[index] = list[newIndex]; list[newIndex] = temp
  try {
    await flowActionApi.updateSortOrder(list.map(a => a.id))
    await loadActions()
  } catch (e) { ElMessage.error('排序失败') }
}

// ========== 监听和初始化 ==========
watch(() => props.element, (newElement) => {
  if (newElement?.type === 'bpmn:SequenceFlow') loadActions()
}, { immediate: true })

watch(() => props.element, (newElement) => {
  if (newElement?.businessObject) {
    const bo = newElement.businessObject
    basicForm.value = { id: bo.id || '', name: bo.name || '', documentation: bo.documentation?.[0]?.text || '' }
    
    if (isUserTask.value) {
      const loop = bo.loopCharacteristics
      assigneeForm.value = { assignee: bo.assignee || '', candidateUsers: bo.candidateUsers || '', candidateGroups: bo.candidateGroups || '', isMultiInstance: !!loop, multiInstanceType: loop?.isSequential ? 'sequential' : 'parallel', collection: loop?.collection || '', elementVariable: loop?.elementVariable || 'assignee', completionCondition: loop?.completionCondition?.body || '' }
    }
    if (isServiceTask.value) {
      // 检查是否是REST配置
      const extensionProps = bo.extensionProperties
      if (extensionProps?.restConfig) {
        serviceForm.value = { implementationType: 'rest', implementation: '', resultVariable: bo.resultVariable || '' }
        restForm.value = { ...restForm.value, ...extensionProps.restConfig }
      } else {
        serviceForm.value = { implementationType: bo.class ? 'class' : bo.expression ? 'expression' : bo.delegateExpression ? 'delegateExpression' : 'class', implementation: bo.class || bo.expression || bo.delegateExpression || '', resultVariable: bo.resultVariable || '' }
      }
    }
    if (isSequenceFlow.value) {
      conditionForm.value = { type: bo.conditionExpression ? 'expression' : bo.sourceRef?.default === bo ? 'default' : '', expression: bo.conditionExpression?.body || '' }
    }
    if (isTask.value || isStartEvent.value) {
      formConfig.value = { formKey: bo.formKey || '' }
    }
    if (isTask.value || isGateway.value) {
      advancedForm.value = { async: bo.async || bo.asyncBefore || bo.asyncAfter, asyncBefore: bo.asyncBefore || false, asyncAfter: bo.asyncAfter || false, skipExpression: bo.skipExpression?.body || '' }
    }
  }
}, { immediate: true })

// ========== 更新方法 ==========
function getModeling() { return props.element?._modeler?.get('modeling') }
function getModdle() { return props.element?._modeler?.get('moddle') }

function updateProperty(prop, value) {
  const modeling = getModeling()
  if (!modeling) return
  const updates = {}; updates[prop] = value || undefined
  modeling.updateProperties(props.element, updates)
  emit('save')
}

function updateDocumentation() {
  const modeling = getModeling(), moddle = getModdle()
  if (!modeling || !moddle) return
  const docs = basicForm.value.documentation ? [moddle.create('bpmn:Documentation', { text: basicForm.value.documentation })] : []
  modeling.updateProperties(props.element, { documentation: docs })
  emit('save')
}

function onMultiInstanceChange(enabled) {
  const modeling = getModeling(), moddle = getModdle()
  if (!modeling || !moddle) return
  if (enabled) {
    const loop = moddle.create('bpmn:MultiInstanceLoopCharacteristics', { isSequential: assigneeForm.value.multiInstanceType === 'sequential' })
    modeling.updateProperties(props.element, { loopCharacteristics: loop })
  } else {
    modeling.updateProperties(props.element, { loopCharacteristics: undefined })
  }
  emit('save')
}

function updateMultiInstance() {
  if (!assigneeForm.value.isMultiInstance) return
  const modeling = getModeling(), moddle = getModdle()
  if (!modeling || !moddle) return
  const loop = moddle.create('bpmn:MultiInstanceLoopCharacteristics', { isSequential: assigneeForm.value.multiInstanceType === 'sequential', collection: assigneeForm.value.collection || undefined, elementVariable: assigneeForm.value.elementVariable || 'assignee' })
  if (assigneeForm.value.completionCondition) loop.completionCondition = moddle.create('bpmn:FormalExpression', { body: assigneeForm.value.completionCondition })
  modeling.updateProperties(props.element, { loopCharacteristics: loop })
  emit('save')
}

function onServiceTypeChange() { 
  if (serviceForm.value.implementationType === 'rest') {
    // REST类型，清除其他实现配置
    updateRestConfig()
  } else {
    serviceForm.value.implementation = ''
    updateServiceImplementation()
  }
}

function updateServiceImplementation() {
  const modeling = getModeling()
  if (!modeling) return
  const updates = { class: undefined, expression: undefined, delegateExpression: undefined }
  if (serviceForm.value.implementation) updates[serviceForm.value.implementationType] = serviceForm.value.implementation
  modeling.updateProperties(props.element, updates)
  emit('save')
}

function onConditionTypeChange(type) {
  const modeling = getModeling()
  if (!modeling) return
  if (type === 'expression') updateCondition()
  else if (type === 'default') {
    modeling.updateProperties(props.element, { conditionExpression: undefined })
    const source = props.element.businessObject.sourceRef
    if (source) modeling.updateProperties(source.$parent, { default: props.element.businessObject })
  } else modeling.updateProperties(props.element, { conditionExpression: undefined })
  emit('save')
}

function updateCondition() {
  if (conditionForm.value.type !== 'expression') return
  const modeling = getModeling(), moddle = getModdle()
  if (!modeling || !moddle) return
  const condition = moddle.create('bpmn:FormalExpression', { body: conditionForm.value.expression })
  modeling.updateProperties(props.element, { conditionExpression: condition })
  emit('save')
}

function onAsyncChange() {
  if (!advancedForm.value.async) { advancedForm.value.asyncBefore = false; advancedForm.value.asyncAfter = false; updateAsync() }
}

function updateAsync() {
  const modeling = getModeling()
  if (!modeling) return
  modeling.updateProperties(props.element, { async: advancedForm.value.async, asyncBefore: advancedForm.value.asyncBefore, asyncAfter: advancedForm.value.asyncAfter })
  emit('save')
}

function updateSkipExpression() {
  const modeling = getModeling(), moddle = getModdle()
  if (!modeling || !moddle) return
  if (advancedForm.value.skipExpression) {
    const expr = moddle.create('bpmn:FormalExpression', { body: advancedForm.value.skipExpression })
    modeling.updateProperties(props.element, { skipExpression: expr })
  } else modeling.updateProperties(props.element, { skipExpression: undefined })
  emit('save')
}

// REST接口配置更新
function updateRestConfig() {
  const modeling = getModeling()
  if (!modeling) return
  // 将REST配置存储到扩展属性中
  const restConfig = { ...restForm.value }
  modeling.updateProperties(props.element, { 
    class: undefined,
    expression: undefined,
    delegateExpression: undefined,
    extensionProperties: { restConfig }
  })
  emit('save')
}

function getRestBodyPlaceholder() {
  const contentType = restForm.value.contentType
  if (contentType === 'application/json') {
    return '{\n  "userId": "${userId}",\n  "status": "approved",\n  "remark": "${comment}"\n}'
  } else if (contentType === 'application/x-www-form-urlencoded') {
    return 'userId=${userId}&status=approved&remark=${comment}'
  }
  return '请求体内容'
}
</script>

<style scoped>
.node-config-panel { height: 100%; display: flex; flex-direction: column; }
.node-type-header { display: flex; align-items: center; gap: 10px; padding: 10px 15px; border-bottom: 1px solid #e4e7ed; background-color: #f5f7fa; }
.node-id { font-size: 12px; color: #909399; font-family: monospace; }
.no-selection { flex: 1; display: flex; align-items: center; justify-content: center; }
.config-tabs { flex: 1; }
.config-tabs :deep(.el-tabs__content) { padding: 15px; height: calc(100% - 40px); overflow-y: auto; }
.form-tip { font-size: 12px; color: #909399; margin-top: 5px; }
:deep(.el-divider__text) { font-size: 12px; color: #909399; }
.unit { margin-left: 8px; color: #606266; }
.code-input :deep(textarea) { font-family: monospace; }

.node-type-alert { margin-bottom: 15px; }
.node-type-info { line-height: 1.6; }
.info-title { font-weight: bold; margin-bottom: 5px; }
.info-desc { color: #606266; margin-bottom: 8px; }
.info-scene { display: flex; align-items: center; gap: 8px; }
.section-alert { margin-bottom: 15px; }

.actions-section { display: flex; flex-direction: column; gap: 10px; }
.actions-header { display: flex; justify-content: space-between; align-items: center; }
.action-alert { margin-bottom: 10px; }
.alert-content { display: flex; align-items: center; gap: 10px; }
.actions-list { display: flex; flex-direction: column; gap: 8px; max-height: 400px; overflow-y: auto; }
.action-item { display: flex; align-items: center; gap: 10px; padding: 10px; border: 1px solid #e4e7ed; border-radius: 4px; background-color: #fafafa; }
.action-item.disabled { opacity: 0.6; background-color: #f5f5f5; }
.action-sort { display: flex; flex-direction: column; align-items: center; gap: 2px; }
.sort-number { font-size: 12px; font-weight: bold; color: #606266; }
.action-content { flex: 1; min-width: 0; }
.action-name { font-weight: 500; font-size: 14px; margin-bottom: 5px; }
.action-detail { display: flex; align-items: center; gap: 8px; }
.interface-info { font-size: 12px; color: #909399; font-family: monospace; }
.action-ops { display: flex; gap: 5px; }
</style>
