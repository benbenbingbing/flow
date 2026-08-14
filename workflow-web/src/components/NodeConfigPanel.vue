<template>
  <div class="node-config-panel">
    <div v-if="!element" class="no-selection">
      <el-empty description="请点击流程节点进行配置" />
    </div>

    <div v-if="element" class="config-tabs">
      <div class="config-tab-nav" role="tablist" aria-label="节点配置分类">
        <button
          type="button"
          role="tab"
          class="config-tab-button"
          :class="{ active: activeTab === 'basic' }"
          :aria-selected="activeTab === 'basic'"
          @click="activeTab = 'basic'"
        >
          常用
        </button>
        <button
          v-if="isCcConfigurable"
          type="button"
          role="tab"
          class="config-tab-button"
          :class="{ active: activeTab === 'collaboration' }"
          :aria-selected="activeTab === 'collaboration'"
          @click="activeTab = 'collaboration'"
        >
          协同
        </button>
        <button
          v-if="hasAdvancedConfig"
          type="button"
          role="tab"
          class="config-tab-button"
          :class="{ active: activeTab === 'advanced' }"
          :aria-selected="activeTab === 'advanced'"
          @click="activeTab = 'advanced'"
        >
          高级
        </button>
        <button
          v-if="isActionConfigurable"
          type="button"
          role="tab"
          class="config-tab-button"
          :class="{ active: activeTab === 'actions' }"
          :aria-selected="activeTab === 'actions'"
          @click="activeTab = 'actions'"
        >
          流程动作
        </button>
      </div>

      <div class="config-tab-content">
      <!-- ========== 基本信息（所有节点都有） ========== -->
      <section v-show="activeTab === 'basic'" class="config-section">
        <el-form :model="basicForm" label-width="100px" size="small">
          <el-form-item label="节点名称">
            <el-input 
              v-model="basicForm.name" 
              :placeholder="getNamePlaceholder()"
            />
          </el-form-item>
          
          <SettingsSection
            v-if="!hasAdvancedConfig"
            title="标识与备注"
            description="节点标识与设计备注，通常无需频繁修改"
          >
            <el-form-item label="节点ID">
              <el-input v-model="basicForm.id" disabled />
            </el-form-item>

            <el-form-item label="设计备注" class="doc-item">
              <el-input
                v-model="basicForm.documentation"
                type="textarea"
                :rows="3"
                placeholder="记录节点设计说明..."
              />
            </el-form-item>
          </SettingsSection>
        </el-form>
      </section>
      
      <!-- ========== 状态配置（仅连线） ========== -->
      <section v-if="isSequenceFlow && activeTab === 'basic'" class="config-section">
        <SettingsSection
          title="实体状态"
          description="流程经过此连线时更新绑定实体的业务状态"
          :default-expanded="!!statusForm.entityStatusCode"
        >
          <template #summary>
            <el-tag size="small" :type="statusForm.entityStatusCode ? 'success' : 'info'">
              {{ selectedStatusName || '未配置' }}
            </el-tag>
          </template>
          <el-form :model="statusForm" label-width="100px" size="small">
          <el-form-item label="来源节点">
            <el-input v-model="statusForm.sourceNodeName" disabled />
          </el-form-item>
          
          <el-form-item label="目标节点">
            <el-input v-model="statusForm.targetNodeName" disabled />
          </el-form-item>
          
          <el-form-item label="实体状态">
            <el-select
              v-model="statusForm.entityStatusCode"
              placeholder="请选择实体状态"
              style="width: 100%"
              filterable
              clearable
            >
              <el-option-group label="📋 新建流程状态">
                <el-option 
                  v-for="status in entityStatusList.filter(s => s.statusCategory === 'NEW')" 
                  :key="status.id" 
                  :label="status.statusName" 
                  :value="status.statusCode"
                />
              </el-option-group>
              <el-option-group label="⏳ 审批中流程状态">
                <el-option 
                  v-for="status in entityStatusList.filter(s => s.statusCategory === 'PROCESSING')" 
                  :key="status.id" 
                  :label="status.statusName" 
                  :value="status.statusCode"
                />
              </el-option-group>
              <el-option-group label="✅ 已完成流程状态">
                <el-option 
                  v-for="status in entityStatusList.filter(s => s.statusCategory === 'COMPLETED')" 
                  :key="status.id" 
                  :label="status.statusName" 
                  :value="status.statusCode"
                />
              </el-option-group>
              <el-option-group label="❌ 终止流程状态">
                <el-option 
                  v-for="status in entityStatusList.filter(s => s.statusCategory === 'TERMINATED')" 
                  :key="status.id" 
                  :label="status.statusName" 
                  :value="status.statusCode"
                />
              </el-option-group>
            </el-select>
            <div class="form-tip">从实体预定义的状态中选择</div>
          </el-form-item>
          
          <el-form-item label="状态名称" v-if="selectedStatusName">
            <el-input v-model="selectedStatusName" disabled />
          </el-form-item>
          
          <el-form-item label="条件表达式" v-if="hasCondition">
            <el-input v-model="statusForm.conditionExpression" type="textarea" :rows="2" disabled />
            <div class="form-tip">网关连线的判断条件</div>
          </el-form-item>
          
          <el-form-item label="说明">
            <el-input v-model="statusForm.description" type="textarea" :rows="2" placeholder="状态变更说明..." />
          </el-form-item>
          </el-form>
        </SettingsSection>
      </section>
      
      <!-- ========== 执行人配置（仅用户任务） ========== -->
      <section v-if="isUserTask && activeTab === 'basic'" class="config-section">
        <SettingsSection
          title="执行人与多人办理"
          description="配置固定人员、组织角色、动态人员接口以及会签或签"
          :collapsible="false"
          primary
        >
          <el-form :model="assigneeForm" label-width="100px" size="small">
          <!-- 多实例启用时，常规执行人配置不再使用，改为下方的会签人员配置 -->
          <template v-if="!assigneeForm.isMultiInstance">
          <!-- 执行人选择类型 -->
          <el-form-item label="指定方式">
            <el-select v-model="assigneeForm.assigneeType" @change="onAssigneeTypeChange" style="width: 100%">
              <el-option label="固定人员" value="user" />
              <el-option label="用户组" value="group" />
              <el-option label="角色" value="role" />
              <el-option label="表达式" value="expression" />
              <el-option label="接口动态" value="interface" />
            </el-select>
          </el-form-item>
          
          <!-- 固定人员选择 -->
          <template v-if="assigneeForm.assigneeType === 'user'">
            <el-form-item label="执行人">
              <UserSelector
                v-model="assigneeForm.assignee"
                value-key="code"
                placeholder="请选择执行人"
                title="选择执行人"
                @change="updateAssignee"
              />
              <div class="form-tip">指定一个固定用户处理此任务</div>
            </el-form-item>

            <el-form-item label="候选人">
              <UserSelector
                v-model="assigneeForm.candidateUserIds"
                multiple
                value-key="code"
                placeholder="请选择候选人"
                title="选择候选人"
                @change="updateCandidateUsers"
              />
              <div class="form-tip">任务可被其中任意一人认领</div>
            </el-form-item>
          </template>
          
          <!-- 用户组选择 -->
          <template v-if="assigneeForm.assigneeType === 'group'">
            <el-form-item label="用户组">
              <el-select-v2
                v-model="assigneeForm.candidateGroupIds"
                :options="groupOptions"
                placeholder="选择用户组"
                multiple
                filterable
                clearable
                style="width: 100%"
                @change="updateCandidateGroups"
              >
                <template #default="{ item }">
                  <span>{{ item.label }}</span>
                  <span style="color: #909399; margin-left: 8px; font-size: 12px">({{ item.code }})</span>
                </template>
              </el-select-v2>
              <div class="form-tip">组内所有成员都可处理任务</div>
            </el-form-item>
          </template>
          
          <!-- 角色选择 -->
          <template v-if="assigneeForm.assigneeType === 'role'">
            <el-form-item label="角色">
              <el-select-v2
                v-model="assigneeForm.candidateRoleIds"
                :options="roleOptions"
                placeholder="选择角色"
                multiple
                filterable
                clearable
                style="width: 100%"
                @change="updateCandidateRoles"
              >
                <template #default="{ item }">
                  <span>{{ item.label }}</span>
                  <span style="color: #909399; margin-left: 8px; font-size: 12px">({{ item.code }})</span>
                </template>
              </el-select-v2>
              <div class="form-tip">拥有该角色的用户都可处理任务</div>
            </el-form-item>
          </template>
          
          <!-- 表达式 -->
          <template v-if="assigneeForm.assigneeType === 'expression'">
            <el-form-item label="执行人表达式">
              <el-input 
                v-model="assigneeForm.assignee" 
                placeholder="如：${submitUser} 或 ${initiator}"
              />
              <div class="form-tip">使用流程变量动态指定执行人</div>
            </el-form-item>
            
            <el-form-item label="候选人表达式">
              <el-input 
                v-model="assigneeForm.candidateUsers" 
                type="textarea"
                :rows="2"
                placeholder="如：${deptManagers}"
              />
              <div class="form-tip">返回用户ID列表的表达式</div>
            </el-form-item>
            
            <el-form-item label="候选组表达式">
              <el-input 
                v-model="assigneeForm.candidateGroups" 
                type="textarea"
                :rows="2"
                placeholder="如：${deptCode}_manager"
              />
              <div class="form-tip">返回组编码的表达式</div>
            </el-form-item>
          </template>
          
          <!-- 接口动态 -->
          <template v-if="assigneeForm.assigneeType === 'interface'">
            <el-form-item label="人员接口" required>
              <ExtensionCapabilityPicker
                v-model="assigneeForm.resolverCode"
                capability-type="PERSON_RESOLVER"
                placeholder="输入名称或编码搜索办理人接口"
                :context-params="assigneeResolverContext"
                :current-option="assigneeResolverCurrentOption"
                @selected="onAssigneeResolverSelected"
              />
              <div class="form-tip">平台固定传入流程、节点、实体和操作人上下文</div>
            </el-form-item>

            <el-form-item>
              <template #label>
                <JsonConfigLabel
                  label="extraParams"
                  help-key="process.assigneeExtraParams"
                />
              </template>
              <el-input
                v-model="assigneeForm.extraParamsText"
                type="textarea"
                :rows="4"
                placeholder='JSON 对象，例如 {"level": 2}'
              />
              <div class="form-tip">仅填写此人员接口声明的扩展参数</div>
            </el-form-item>
          </template>
          </template>
          
          <SettingsSection
            title="多人办理（会签/或签）"
            description="仅在多人同时或顺序办理时配置"
            :default-expanded="assigneeForm.isMultiInstance"
          >
            <template #summary>
              <el-tag :type="assigneeForm.isMultiInstance ? 'success' : 'info'" size="small">
                {{ assigneeForm.isMultiInstance ? '已启用' : '未启用' }}
              </el-tag>
            </template>

            <el-form-item label="启用多实例">
              <el-switch v-model="assigneeForm.isMultiInstance" @change="onMultiInstanceChange" />
            </el-form-item>

            <template v-if="assigneeForm.isMultiInstance">
              <SettingsSection
                title="办理方式"
                description="设置多人任务并行或串行执行，以及参与人员来源"
                :collapsible="false"
                primary
              >
                <el-form-item label="执行方式">
                  <template #label>
                    <ConfigHelpLabel
                      label="执行方式"
                      help-key="process.multiInstanceType"
                    />
                  </template>
                  <el-radio-group v-model="assigneeForm.multiInstanceType">
                    <el-radio-button value="parallel">并行多实例</el-radio-button>
                    <el-radio-button value="sequential">串行多实例</el-radio-button>
                  </el-radio-group>
                  <div class="form-tip">并行：多人同时审批；串行：按顺序审批</div>
                </el-form-item>

                <el-form-item label="人员来源">
                  <template #label>
                    <ConfigHelpLabel
                      label="人员来源"
                      help-key="process.multiInstanceSource"
                    />
                  </template>
                  <el-radio-group v-model="assigneeForm.collectionSource" @change="onCollectionSourceChange">
                    <el-radio-button value="variable">直接选择</el-radio-button>
                    <el-radio-button value="interface">人员接口</el-radio-button>
                  </el-radio-group>
                </el-form-item>
              </SettingsSection>

              <SettingsSection
                title="参与人员"
                description="选择生成多人任务的用户、用户组或角色"
                :collapsible="false"
              >
                <template #summary>
                  <el-tag size="small" type="info">
                    {{ assigneeForm.collectionSource === 'interface' ? '动态获取' : '直接选择' }}
                  </el-tag>
                </template>

                <template v-if="assigneeForm.collectionSource === 'variable'">
                  <el-form-item label="会签人员">
                    <UserSelector
                      v-model="assigneeForm.multiInstanceUserIds"
                      multiple
                      value-key="code"
                      placeholder="请选择会签用户"
                      title="选择会签用户"
                      @change="updateMultiInstanceUsers"
                    />
                    <div class="form-tip">所选用户每人都会生成一个会签任务</div>
                  </el-form-item>

                  <el-form-item label="会签用户组">
                    <el-select-v2
                      v-model="assigneeForm.multiInstanceGroupIds"
                      :options="groupOptions"
                      placeholder="选择会签用户组"
                      multiple
                      filterable
                      clearable
                      style="width: 100%"
                      @change="updateMultiInstanceUsers"
                    >
                      <template #default="{ item }">
                        <span>{{ item.label }}</span>
                        <span style="color: #909399; margin-left: 8px; font-size: 12px">({{ item.code }})</span>
                      </template>
                    </el-select-v2>
                    <div class="form-tip">组内所有成员都会生成会签任务</div>
                  </el-form-item>

                  <el-form-item label="会签角色">
                    <el-select-v2
                      v-model="assigneeForm.multiInstanceRoleIds"
                      :options="roleOptions"
                      placeholder="选择会签角色"
                      multiple
                      filterable
                      clearable
                      style="width: 100%"
                      @change="updateMultiInstanceUsers"
                    >
                      <template #default="{ item }">
                        <span>{{ item.label }}</span>
                        <span style="color: #909399; margin-left: 8px; font-size: 12px">({{ item.code }})</span>
                      </template>
                    </el-select-v2>
                    <div class="form-tip">拥有该角色的所有成员都会生成会签任务</div>
                  </el-form-item>
                </template>

                <template v-else>
                  <el-form-item label="人员接口" required>
                    <ExtensionCapabilityPicker
                      v-model="assigneeForm.collectionResolverCode"
                      capability-type="PERSON_RESOLVER"
                      placeholder="输入名称或编码搜索会签人员接口"
                      :context-params="multiInstanceResolverContext"
                      :current-option="collectionResolverCurrentOption"
                      @selected="onCollectionResolverSelected"
                    />
                  </el-form-item>
                  <el-form-item label="extraParams">
                    <template #label>
                      <JsonConfigLabel
                        label="extraParams"
                        help-key="process.multiInstanceExtraParams"
                      />
                    </template>
                    <el-input
                      v-model="assigneeForm.collectionExtraParamsText"
                      type="textarea"
                      :rows="3"
                      placeholder='JSON 对象，例如 {"departmentLevel": 2}'
                    />
                  </el-form-item>
                </template>
              </SettingsSection>

              <SettingsSection
                title="完成规则"
                description="默认等待全部实例完成；仅特殊会签规则需要修改"
                :default-expanded="!!assigneeForm.completionCondition"
              >
                <template #summary>
                  <el-tag size="small" :type="assigneeForm.completionCondition ? 'warning' : 'info'">
                    {{ assigneeForm.completionCondition ? '自定义条件' : '全部完成' }}
                  </el-tag>
                </template>

                <el-form-item label="完成条件">
                  <el-input
                    v-model="assigneeForm.completionCondition"
                    placeholder="如：${nrOfCompletedInstances >= nrOfInstances * 0.5}"
                  />
                  <div class="form-tip">满足此条件时任务完成，留空表示全部实例完成</div>
                </el-form-item>
              </SettingsSection>

              <SettingsSection
                title="技术参数"
                description="BPMN 多实例集合与单个办理人的变量名，通常保持默认"
              >
                <template #summary>
                  <el-tag size="small" type="info">
                    {{ assigneeForm.elementVariable || 'assignee' }}
                  </el-tag>
                </template>

                <el-form-item v-if="assigneeForm.collectionSource === 'variable'" label="集合变量">
                  <el-input
                    v-model="assigneeForm.collection"
                    placeholder="系统自动生成当前节点唯一集合变量"
                    disabled
                  />
                  <div class="form-tip">系统生成的用户ID集合变量，只读展示</div>
                </el-form-item>

                <el-form-item label="元素变量">
                  <el-input
                    v-model="assigneeForm.elementVariable"
                    placeholder="如：approver"
                  />
                  <div class="form-tip">集合中单个用户ID在任务内使用的变量名</div>
                </el-form-item>
              </SettingsSection>
            </template>
          </SettingsSection>
          <NextApproverConfigEditor
            ref="nextApproverConfigEditorRef"
            v-model="assigneeForm.nextApproverSelection"
            :role-options="roleOptions"
            :group-options="groupOptions"
            :organization-options="organizationOptions"
          />
          </el-form>
        </SettingsSection>
      </section>
      
      <!-- ========== 服务配置（服务任务） ========== -->
      <section v-if="isServiceTask && activeTab === 'basic'" class="config-section">
        <SettingsSection
          title="服务调用"
          description="执行 Java、Spring Bean、表达式或 REST 请求"
          :collapsible="false"
          primary
        >
          <el-form :model="serviceForm" label-width="100px" size="small">
          <el-form-item label="实现类型">
            <template #label>
              <ConfigHelpLabel
                label="实现类型"
                help-key="process.serviceImplementationType"
              />
            </template>
            <el-radio-group v-model="serviceForm.implementationType" @change="onServiceTypeChange">
              <el-radio-button value="class">Java类</el-radio-button>
              <el-radio-button value="expression">表达式</el-radio-button>
              <el-radio-button value="delegateExpression">Spring Bean</el-radio-button>
              <el-radio-button value="rest">REST接口</el-radio-button>
            </el-radio-group>
          </el-form-item>
          
          <!-- Java类/表达式/Spring Bean 配置 -->
          <template v-if="serviceForm.implementationType !== 'rest'">
            <el-form-item label="实现">
              <el-input 
                v-model="serviceForm.implementation" 
                :placeholder="servicePlaceholder"
              />
            </el-form-item>
          </template>
          
          <!-- REST接口配置 -->
          <template v-else>
            <el-form-item label="请求方式">
              <el-radio-group v-model="restForm.method">
                <el-radio-button value="GET">GET</el-radio-button>
                <el-radio-button value="POST">POST</el-radio-button>
                <el-radio-button value="PUT">PUT</el-radio-button>
                <el-radio-button value="DELETE">DELETE</el-radio-button>
              </el-radio-group>
            </el-form-item>
            
            <el-form-item label="请求URL">
              <el-input 
                v-model="restForm.url" 
                placeholder="如：https://api.example.com/users/${userId}"
              />
              <div class="form-tip">支持流程变量表达式，如：${userId}</div>
            </el-form-item>
            
            <el-form-item label="Content-Type">
              <el-select v-model="restForm.contentType">
                <el-option label="application/json" value="application/json" />
              </el-select>
              <div class="form-tip">当前运行时仅支持 application/json</div>
            </el-form-item>
            
            <el-form-item>
              <template #label>
                <JsonConfigLabel
                  label="请求头(Headers)"
                  help-key="process.restHeaders"
                />
              </template>
              <el-input 
                v-model="restForm.headers" 
                type="textarea"
                :rows="3"
                placeholder='{"X-Business-Ref":"${businessRef}","X-Client-Type":"workflow"}'
              />
              <div class="form-tip">JSON 对象，支持精确的 ${流程变量} 模板</div>
            </el-form-item>
            
            <el-form-item v-if="restForm.method !== 'GET'">
              <template #label>
                <JsonConfigLabel
                  label="请求体(Body)"
                  help-key="process.restBody"
                />
              </template>
              <el-input 
                v-model="restForm.body" 
                type="textarea"
                :rows="5"
                :placeholder="getRestBodyPlaceholder()"
                class="code-input"
              />
              <div class="form-tip">JSON 对象或数组，支持精确的 ${流程变量} 模板</div>
            </el-form-item>
            
            <el-form-item>
              <template #label>
                <JsonConfigLabel
                  label="查询参数"
                  help-key="process.restQueryParams"
                />
              </template>
              <el-input 
                v-model="restForm.queryParams" 
                type="textarea"
                :rows="2"
                placeholder='{"page": "${page}", "size": "10"}'
              />
              <div class="form-tip">URL查询参数，JSON格式，支持流程变量</div>
            </el-form-item>
            
            <SettingsSection
              title="可靠性与结果"
              description="超时、重试、错误策略和响应变量映射"
              :default-expanded="restForm.retryCount > 0 || restForm.errorHandling !== 'throw' || !!restForm.resultMapping"
            >
              <template #summary>
                <el-tag size="small" type="info">
                  {{ restForm.timeout }} 秒 / {{ restForm.retryCount }} 次重试
                </el-tag>
              </template>

              <el-row :gutter="10">
                <el-col :span="12">
                  <el-form-item label="超时时间(秒)">
                    <el-input-number v-model="restForm.timeout" :min="1" :max="300" style="width: 100%" />
                  </el-form-item>
                </el-col>
                <el-col :span="12">
                  <el-form-item label="重试次数">
                    <el-input-number v-model="restForm.retryCount" :min="0" :max="5" style="width: 100%" />
                  </el-form-item>
                </el-col>
              </el-row>

              <el-form-item label="错误处理">
                <el-radio-group v-model="restForm.errorHandling">
                  <el-radio value="throw">抛出异常终止流程</el-radio>
                  <el-radio value="continue">记录错误继续流程</el-radio>
                  <el-radio value="ignore">忽略错误</el-radio>
                </el-radio-group>
              </el-form-item>

              <el-form-item>
                <template #label>
                  <JsonConfigLabel
                    label="结果映射"
                    help-key="process.restResultMapping"
                  />
                </template>
                <el-input
                  v-model="restForm.resultMapping"
                  type="textarea"
                  :rows="3"
                  placeholder='{"data.id": "userId", "data.status": "status", "code": "resultCode"}'
                />
                <div class="form-tip">将响应结果映射到流程变量，JSON格式：响应路径 -> 变量名</div>
              </el-form-item>
            </SettingsSection>
          </template>
          
          <el-form-item label="结果变量" v-if="serviceForm.implementationType !== 'rest'">
            <el-input 
              v-model="serviceForm.resultVariable" 
              placeholder="存储结果到变量"
            />
          </el-form-item>
          </el-form>
        </SettingsSection>
      </section>
      
      <!-- ========== 发送配置（发送任务） ========== -->
      <section v-if="isSendTask && activeTab === 'basic'" class="config-section">
        <SettingsSection
          title="发送消息"
          description="配置消息渠道、接收人和内容模板"
          :collapsible="false"
          primary
        >
          <el-form :model="sendForm" label-width="100px" size="small">
          <el-form-item label="发送渠道">
            <el-checkbox-group v-model="sendForm.channels">
              <el-checkbox label="message">站内信</el-checkbox>
            </el-checkbox-group>
            <div class="form-tip">当前运行时仅支持站内信</div>
          </el-form-item>
          
          <el-form-item label="接收人">
            <el-input 
              v-model="sendForm.to" 
              placeholder="用户名、用户ID或变量，如：${approverUsername}"
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
        </SettingsSection>
      </section>
      
      <!-- ========== 接收配置（接收任务） ========== -->
      <section v-if="isReceiveTask && activeTab === 'basic'" class="config-section">
        <SettingsSection
          title="接收消息"
          description="等待外部系统或事件触发后继续流程"
          :collapsible="false"
          primary
        >
          <el-form :model="receiveForm" label-width="100px" size="small">
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
                <el-radio value="error">抛出异常</el-radio>
                <el-radio value="continue">继续执行</el-radio>
              </el-radio-group>
            </el-form-item>
          </template>
          </el-form>
        </SettingsSection>
      </section>
      
      <!-- ========== 手动任务配置（手动任务） ========== -->
      <section v-if="isManualTask && activeTab === 'basic'" class="config-section">
        <SettingsSection
          title="线下任务"
          description="记录需要在流程系统外完成的工作"
          :collapsible="false"
          primary
        >
          <el-form :model="manualForm" label-width="100px" size="small">
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
        </SettingsSection>
      </section>
      
      <!-- ========== 业务规则配置（业务规则任务） ========== -->
      <section v-if="isBusinessRuleTask && activeTab === 'basic'" class="config-section">
        <SettingsSection
          title="业务规则"
          description="执行 DMN 决策并映射输入输出变量"
          :collapsible="false"
          primary
        >
          <el-form :model="ruleForm" label-width="100px" size="small">
          <el-form-item label="决策表Key">
            <el-input 
              v-model="ruleForm.decisionRef" 
              placeholder="如：approvalLevelDecision"
            />
            <div class="form-tip">关联的DMN决策表定义Key</div>
          </el-form-item>
          
          <el-form-item>
            <template #label>
              <JsonConfigLabel
                label="输入变量"
                help-key="process.dmnInputVariables"
              />
            </template>
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
        </SettingsSection>
      </section>
      
      <!-- Historical script nodes are read-only and cannot be published. -->
      <section v-if="isScriptTask && activeTab === 'basic'" class="config-section">
        <el-alert
          type="error"
          :closable="false"
          title="脚本任务已禁用"
          description="生产运行时不会执行脚本。请将该节点替换为已注册的服务任务或流程动作后再发布。"
          show-icon
        />
      </section>
      
      <!-- ========== 调用活动配置（调用活动） ========== -->
      <section v-if="isCallActivity && activeTab === 'basic'" class="config-section">
        <SettingsSection
          title="调用流程"
          description="调用另一个独立流程并配置参数映射"
          :collapsible="false"
          primary
        >
          <el-form :model="callForm" label-width="100px" size="small">
          <el-form-item label="子流程Key">
            <el-select 
              v-model="callForm.calledElement" 
              placeholder="选择要调用的子流程"
              filterable
              allow-create
              default-first-option
              :loading="subProcessesLoading"
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
              <el-radio value="bpmn">BPMN子流程</el-radio>
              <el-radio value="cmmn">CMMN案例</el-radio>
            </el-radio-group>
          </el-form-item>
          
          <SettingsSection
            title="参数传递"
            description="父流程与子流程之间的变量和业务键映射"
            :default-expanded="!!callForm.inputParameters || !!callForm.outputParameters || !!callForm.businessKey"
          >
            <template #summary>
              <el-tag size="small" type="info">
                {{ callForm.inputParameters || callForm.outputParameters ? '已配置映射' : '未配置映射' }}
              </el-tag>
            </template>

            <el-form-item>
              <template #label>
                <JsonConfigLabel
                  label="输入参数"
                  help-key="process.callInputParameters"
                />
              </template>
              <el-input
                v-model="callForm.inputParameters"
                type="textarea"
                :rows="3"
                placeholder='{"subProcessVar": "${parentVar}"}'
              />
              <div class="form-tip">传递给子流程的变量映射</div>
            </el-form-item>

            <el-form-item>
              <template #label>
                <JsonConfigLabel
                  label="输出参数"
                  help-key="process.callOutputParameters"
                />
              </template>
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
          </SettingsSection>
          </el-form>
        </SettingsSection>
      </section>
      
      <!-- ========== 条件配置（顺序流） ========== -->
      <section v-if="isSequenceFlow && activeTab === 'basic'" class="config-section">
        <SettingsSection
          title="流转条件"
          description="设置无条件、条件表达式或默认连线"
          :collapsible="false"
          primary
        >
          <el-form :model="conditionForm" label-width="100px" size="small">
          <el-form-item label="条件类型">
            <template #label>
              <ConfigHelpLabel
                label="条件类型"
                help-key="process.sequenceConditionType"
              />
            </template>
            <el-radio-group v-model="conditionForm.type" @change="onConditionTypeChange">
              <el-radio-button value="">无条件</el-radio-button>
              <el-radio-button value="expression">表达式</el-radio-button>
              <el-radio-button value="default">默认流</el-radio-button>
            </el-radio-group>
          </el-form-item>
          
          <!-- 表达式编辑器 -->
          <template v-if="conditionForm.type === 'expression'">
            <el-alert
              v-if="conditionParseWarning"
              type="warning"
              :closable="false"
              show-icon
              class="condition-parse-warning"
            >
              <template #title>当前表达式暂时无法转换为可视化条件组</template>
              <div>{{ conditionParseWarning }}</div>
              <el-button type="warning" link @click="resetConditionGroups">清空并改用条件组</el-button>
            </el-alert>

            <div v-else class="condition-group-editor">
              <el-alert
                title="每个条件组可选择“全部满足”或“任一满足”，组内还可以继续添加子条件组。"
                type="info"
                :closable="false"
                show-icon
                class="condition-group-tip"
              />
              <FlowConditionGroupEditor
                :group="conditionRoot"
                :entity-fields="entityFields"
                :approval-options="sourceNodeApprovalOptions"
                @change="updateCondition"
              />
            </div>
            
            <!-- 表达式预览 -->
            <el-form-item label="完整表达式" class="expression-preview">
              <el-input 
                :model-value="getFullExpression()" 
                disabled
                type="textarea"
                :rows="2"
              />
            </el-form-item>
          </template>
          
          <el-alert 
            v-if="conditionForm.type === 'default'" 
            type="warning" 
            :closable="false"
          >
            <template #title>
              <div>
                <strong>默认流</strong>：当其他条件都不满足时执行
              </div>
            </template>
            <div class="default-flow-tip">
              <p>⚠️ 一个排他网关只能有一个默认流</p>
              <p>💡 建议在其他分支都设置条件表达式，最后一个分支设为默认流</p>
            </div>
          </el-alert>
          
          <el-alert 
            v-if="conditionForm.type === ''" 
            type="info" 
            :closable="false"
          >
            无条件：此连线在任何情况下都会执行
          </el-alert>
          </el-form>
        </SettingsSection>
      </section>
      
      <!-- ========== 表单配置（仅用户任务/开始事件） ========== -->
      <section v-if="(isUserTask || isStartEvent) && activeTab === 'basic'" class="config-section">
        <SettingsSection
          title="办理表单"
          description="绑定实体表单、自定义表单或设置无表单"
          :collapsible="false"
          primary
        >
          <el-form :model="formConfig" label-width="100px" size="small">
          <!-- 显示绑定的实体信息 -->
          <el-form-item label="所属实体">
            <el-tag v-if="boundEntity" type="success" size="large">
              {{ boundEntity.entityName }} ({{ boundEntity.entityCode }})
            </el-tag>
            <el-tag v-else type="warning" size="large">该流程未绑定实体</el-tag>
          </el-form-item>

          <el-form-item label="表单来源">
            <el-select v-model="formConfig.formSource" @change="onFormSourceChange" style="width: 100%">
              <el-option label="实体表单" value="entity" />
              <el-option label="自定义表单" value="custom" />
              <el-option label="无表单" value="none" />
            </el-select>
          </el-form-item>

          <!-- 实体表单选择 -->
          <template v-if="formConfig.formSource === 'entity'">
            <el-form-item label="选择表单">
              <el-select
                v-model="formConfig.entityFormId"
                placeholder="请选择实体表单"
                style="width: 100%"
                filterable
                @change="onEntityFormChange"
              >
                <el-option
                  v-for="form in entityFormOptions"
                  :key="form.id"
                  :label="form.formName"
                  :value="form.id"
                >
                  <div class="form-option">
                    <span class="form-name">{{ form.formName }}</span>
                    <span class="form-key">({{ form.formKey }})</span>
                    <el-tag size="small" type="info" v-if="form.fields">{{ form.fields?.length }}个字段</el-tag>
                  </div>
                </el-option>
              </el-select>
              <div class="form-tip" v-if="boundEntity && entityFormOptions.length === 0">
                暂无可用表单，请先
                <el-button type="primary" link size="small" @click="goToFormDesign">创建表单</el-button>
              </div>
              <div class="form-tip" v-if="!boundEntity">
                当前流程未绑定实体，无法选择实体表单
              </div>
            </el-form-item>
            
            <el-form-item label="强制整表只读">
              <el-switch v-model="formConfig.isReadonly" />
              <div class="form-tip">
                开启后，本节点所有办理表单均不可编辑，并覆盖表单字段的“审批可编辑”配置。
              </div>
            </el-form-item>
          </template>
          
          <!-- 自定义表单 -->
          <template v-if="formConfig.formSource === 'custom'">
            <el-form-item label="表单Key">
              <el-input 
                v-model="formConfig.formKey" 
                placeholder="如：leave_apply_form"
              />
              <div class="form-tip">关联外部表单标识</div>
            </el-form-item>
          </template>
          </el-form>
        </SettingsSection>
      </section>
      
      <!-- ========== 审批配置（仅用户任务） ========== -->
      <section v-if="isUserTask && activeTab === 'basic'" class="config-section">
        <SettingsSection
          title="审批设置"
          description="配置审批意见和用户可选择的审批操作"
          :collapsible="false"
          primary
        >
          <el-form :model="approvalForm" label-width="120px" size="small">
          <el-form-item label="启用审批意见">
            <el-switch v-model="approvalForm.enabled" />
          </el-form-item>
          
          <template v-if="approvalForm.enabled">
            <el-form-item label="审批意见名称">
              <el-input v-model="approvalForm.commentLabel" placeholder="如：审批意见、审批备注" />
            </el-form-item>
            
            <el-divider>审批选项</el-divider>
            
            <div class="approval-options-list">
              <div v-for="(option, index) in approvalForm.options" :key="index" class="approval-option-item">
                <el-row :gutter="8" align="middle">
                  <el-col :span="6">
                    <el-input v-model="option.label" placeholder="选项名称" size="small" />
                  </el-col>
                  <el-col :span="6">
                    <el-input v-model="option.value" placeholder="选项值" size="small" />
                  </el-col>
                  <el-col :span="6">
                    <el-select v-model="option.type" placeholder="样式" size="small">
                      <el-option label="主要" value="primary" />
                      <el-option label="成功" value="success" />
                      <el-option label="警告" value="warning" />
                      <el-option label="危险" value="danger" />
                    </el-select>
                  </el-col>
                  <el-col :span="6" class="approval-option-actions">
                    <el-tooltip content="显示备注" placement="top">
                      <el-button
                        :type="option.showComment ? 'primary' : ''"
                        link
                        size="small" aria-label="切换备注显示" title="切换备注显示"
                        @click="option.showComment = !option.showComment"
                      >
                        <el-icon><View /></el-icon>
                      </el-button>
                    </el-tooltip>
                    <el-tooltip v-if="option.showComment" content="备注必填" placement="top">
                      <el-button
                        :type="option.remarkRequired ? 'danger' : ''"
                        link
                        size="small" aria-label="切换备注必填" title="切换备注必填"
                        @click="option.remarkRequired = !option.remarkRequired"
                      >
                        <el-icon><WarningFilled /></el-icon>
                      </el-button>
                    </el-tooltip>
                    <el-tooltip content="删除" placement="top">
                      <el-button
                        type="danger"
                        link
                        size="small" aria-label="删除审批选项" title="删除审批选项"
                        @click="removeApprovalOption(index)"
                        :disabled="approvalForm.options.length <= 1"
                      >
                        <el-icon><Delete /></el-icon>
                      </el-button>
                    </el-tooltip>
                  </el-col>
                </el-row>
              </div>
              <el-button type="primary" link size="small" @click="addApprovalOption">
                <el-icon><Plus /></el-icon> 添加选项
              </el-button>
            </div>
          </template>
          </el-form>
        </SettingsSection>
      </section>
      
      <!-- ========== 知会配置 ========== -->
      <section v-if="isCcConfigurable && activeTab === 'collaboration'" class="config-section">
        <SettingsSection
          title="知会配置"
          description="按任务时机向指定人员发送协同通知"
          :collapsible="false"
          primary
        >
          <el-form :model="ccForm" label-width="120px" size="small">
          <el-form-item label="启用知会">
            <el-switch v-model="ccForm.enabled" />
          </el-form-item>
          <template v-if="ccForm.enabled">
            <el-form-item label="触发时机">
              <el-select v-model="ccForm.timings" multiple style="width:100%">
                <el-option v-if="isUserTask" label="任务创建时" value="TASK_CREATE" />
                <el-option v-if="isUserTask" label="任务完成时" value="TASK_COMPLETE" />
                <el-option v-if="isServiceTask || isSendTask" label="执行到知会节点" value="EXPLICIT" />
              </el-select>
            </el-form-item>
            <el-form-item label="通知渠道">
              <el-checkbox-group v-model="ccForm.channels">
                <el-checkbox label="IN_APP">站内知会</el-checkbox>
              </el-checkbox-group>
              <div class="form-tip">邮件、短信通过后端注册通知渠道扩展，不在流程事务内直接发送</div>
            </el-form-item>
            <el-form-item label="包含当前办理人">
              <el-switch v-model="ccForm.includeOperator" />
            </el-form-item>
            <el-form-item v-if="isUserTask" label="允许手工知会">
              <template #label>
                <ConfigHelpLabel
                  label="允许手工知会"
                  help-key="process.allowManualCc"
                />
              </template>
              <el-switch v-model="ccForm.allowManualCc" />
              <div class="form-tip">
                关闭后，办理人不能临时添加知会对象，只执行节点预配置的收件人规则。
              </div>
            </el-form-item>
            <SettingsSection
              title="收件人与展示"
              description="配置收件人规则及知会列表中的说明"
              :default-expanded="ccForm.enabled"
            >
              <template #summary>
                <el-tag size="small" type="info">{{ ccForm.recipientRules.length }} 条规则</el-tag>
              </template>

              <div v-for="(rule, index) in ccForm.recipientRules" :key="index" class="cc-rule-block">
                <div class="cc-rule-row">
                  <el-select v-model="rule.type" style="width:150px" @change="resetCcRuleValue(rule)">
                    <el-option label="固定用户" value="USER" />
                    <el-option label="角色成员" value="ROLE" />
                    <el-option label="用户组成员" value="GROUP" />
                    <el-option label="组织/部门成员" value="DEPARTMENT" />
                    <el-option label="流程发起人" value="STARTER" />
                    <el-option label="当前办理人" value="CURRENT_ASSIGNEE" />
                    <el-option label="历史办理人" value="HISTORY_APPROVERS" />
                    <el-option label="实体字段用户" value="ENTITY_FIELD" />
                    <el-option label="受控解析器" value="RESOLVER" />
                  </el-select>
                  <UserSelector
                    v-if="rule.type === 'USER'"
                    v-model="rule.values"
                    multiple
                    value-key="code"
                    placeholder="请选择知会用户"
                    title="选择知会用户"
                    style="flex: 1"
                  />
                  <el-select v-else-if="rule.type === 'ROLE'" v-model="rule.values" multiple filterable style="flex:1" placeholder="选择角色">
                    <el-option v-for="item in roleOptions" :key="item.value" :label="item.label" :value="item.value" />
                  </el-select>
                  <el-select v-else-if="rule.type === 'GROUP'" v-model="rule.values" multiple filterable style="flex:1" placeholder="选择用户组">
                    <el-option v-for="item in groupOptions" :key="item.value" :label="item.label" :value="item.value" />
                  </el-select>
                  <el-select v-else-if="rule.type === 'DEPARTMENT'" v-model="rule.values" multiple filterable style="flex:1" placeholder="选择组织或部门">
                    <el-option v-for="item in organizationOptions" :key="item.value" :label="item.label" :value="item.value" />
                  </el-select>
                  <el-input v-else-if="rule.type === 'ENTITY_FIELD'" v-model="rule.fieldCode" style="flex:1" placeholder="实体用户字段编码" />
                  <ExtensionCapabilityPicker
                    v-else-if="rule.type === 'RESOLVER'"
                    v-model="rule.resolverCode"
                    capability-type="PERSON_RESOLVER"
                    placeholder="输入名称或编码搜索知会人员接口"
                    :context-params="ccResolverContext"
                    :current-option="ccResolverCurrentOption(rule)"
                    style="flex:1"
                    @selected="option => onCcResolverSelected(rule, option)"
                  />
                  <el-input v-else style="flex:1" :model-value="ccRuleStaticText(rule.type)" disabled />
                  <el-checkbox v-if="rule.type === 'DEPARTMENT'" v-model="rule.includeChildren">含下级</el-checkbox>
                  <el-button type="danger" link aria-label="删除收件人规则" title="删除收件人规则" :disabled="ccForm.recipientRules.length <= 1" @click="removeCcRule(index)">
                    <el-icon><Delete /></el-icon>
                  </el-button>
                </div>
                <div
                  v-if="rule.type === 'RESOLVER'"
                  class="cc-extra-params-editor"
                >
                  <JsonConfigLabel
                    label="extraParams"
                    help-key="process.ccExtraParams"
                  />
                  <el-input
                    v-model="rule.extraParamsText"
                    type="textarea"
                    :rows="2"
                    class="cc-extra-params"
                    placeholder='JSON 对象，例如 {"level": 2}'
                  />
                </div>
              </div>
              <el-button type="primary" link @click="addCcRule"><el-icon><Plus /></el-icon>添加收件人规则</el-button>
              <el-form-item label="知会说明">
                <el-input v-model="ccForm.summary" type="textarea" :rows="2" placeholder="展示在收件人的知会列表中" />
              </el-form-item>
              <el-alert type="info" :closable="false" show-icon :title="ccNaturalSummary" />
            </SettingsSection>
          </template>
          </el-form>
        </SettingsSection>
      </section>

      <!-- ========== 流程动作 ========== -->
      <section v-if="isActionConfigurable && activeTab === 'actions'" class="config-section config-section--actions">
        <FlowActionConfigPanel
          :process-id="processId"
          :scope-type="isSequenceFlow ? 'SEQUENCE_FLOW' : 'NODE'"
          :element-id="element?.id"
          :element-name="element?.businessObject?.name || element?.id"
          :bpmn-type="element?.type"
          @changed="emit('action-changed')"
        />
      </section>

      <!-- ========== 高级配置 ========== -->
      <section v-if="hasAdvancedConfig && activeTab === 'advanced'" class="config-section">
        <el-form :model="advancedForm" label-width="120px" size="small">
          <el-form-item label="异步执行">
            <el-switch v-model="advancedForm.async" @change="onAsyncChange" />
          </el-form-item>
          
          <template v-if="advancedForm.async">
            <el-form-item label="异步前">
            <el-switch v-model="advancedForm.asyncBefore" />
            </el-form-item>
            <el-form-item label="异步后">
            <el-switch v-model="advancedForm.asyncAfter" />
            </el-form-item>
          </template>
          
          <el-form-item label="跳过表达式">
            <el-input 
              v-model="advancedForm.skipExpression" 
              placeholder="如：${skip}"
            />
          </el-form-item>

          <SettingsSection
            v-if="isUserTask"
            title="任务 SLA"
            description="按已发布策略计算首次响应和办结时限"
            :default-expanded="slaForm.enabled"
          >
            <template #summary>
              <el-tag :type="slaForm.enabled ? 'success' : 'info'" size="small">
                {{ slaForm.enabled ? selectedSlaPolicyName || '已启用' : '未启用' }}
              </el-tag>
            </template>
            <el-form-item label="启用 SLA">
              <el-switch v-model="slaForm.enabled" />
            </el-form-item>
            <template v-if="slaForm.enabled">
              <el-form-item label="SLA 策略" required>
                <el-select
                  v-model="slaForm.policyCode"
                  filterable
                  placeholder="选择已发布策略"
                  style="width: 100%"
                >
                  <el-option
                    v-for="policy in slaPolicyOptions"
                    :key="policy.policyCode"
                    :label="`${policy.policyName}（v${policy.version}）`"
                    :value="policy.policyCode"
                  />
                </el-select>
              </el-form-item>
              <el-form-item label="日历来源">
                <template #label>
                  <ConfigHelpLabel
                    label="日历来源"
                    help-key="process.slaCalendarSource"
                  />
                </template>
                <el-select v-model="slaForm.calendarSource" style="width: 100%">
                  <el-option label="节点指定" value="NODE" />
                  <el-option label="流程指定" value="PROCESS" />
                  <el-option label="业务归属部门" value="BUSINESS_DEPT" />
                  <el-option label="发起人部门" value="STARTER_DEPT" />
                  <el-option label="系统默认" value="SYSTEM_DEFAULT" />
                </el-select>
              </el-form-item>
              <el-form-item
                v-if="slaForm.calendarSource === 'NODE'"
                label="节点日历"
                required
              >
                <el-select v-model="slaForm.calendarCode" filterable style="width: 100%">
                  <el-option
                    v-for="calendar in workCalendarOptions"
                    :key="calendar.calendarCode"
                    :label="`${calendar.calendarName}（${calendar.timezoneId}）`"
                    :value="calendar.calendarCode"
                  />
                </el-select>
              </el-form-item>
              <el-form-item
                v-if="slaForm.calendarSource === 'PROCESS'"
                label="流程日历"
                required
              >
                <el-select v-model="slaForm.processCalendarCode" filterable style="width: 100%">
                  <el-option
                    v-for="calendar in workCalendarOptions"
                    :key="calendar.calendarCode"
                    :label="`${calendar.calendarName}（${calendar.timezoneId}）`"
                    :value="calendar.calendarCode"
                  />
                </el-select>
              </el-form-item>
              <el-form-item
                v-if="slaForm.calendarSource === 'BUSINESS_DEPT'"
                label="部门字段"
                required
              >
                <el-select
                  v-model="slaForm.businessFieldCode"
                  filterable
                  placeholder="选择业务数据中的部门字段"
                  style="width: 100%"
                >
                  <el-option
                    v-for="field in entityFields"
                    :key="getProcessConditionFieldCode(field)"
                    :label="field.fieldName || field.fieldLabel || field.fieldCode"
                    :value="getProcessConditionFieldCode(field)"
                  />
                </el-select>
              </el-form-item>
              <el-alert
                type="info"
                :closable="false"
                show-icon
                title="发布时冻结策略与日历快照；转办不会重新计算截止时间。"
              />
            </template>
          </SettingsSection>
          
          <SettingsSection
            title="自动跳过"
            description="节点到达后不生成停留，直接继续流转"
            :default-expanded="advancedForm.skipNode"
          >
            <template #summary>
              <el-tag :type="advancedForm.skipNode ? 'warning' : 'info'" size="small">
                {{ advancedForm.skipNode ? '已启用' : '未启用' }}
              </el-tag>
            </template>

            <el-form-item label="是否跳过">
              <el-switch
                v-model="advancedForm.skipNode"
                active-text="是"
                inactive-text="否"
              />
            </el-form-item>

            <el-alert v-if="advancedForm.skipNode" type="warning" :closable="false" show-icon>
              <template #title>
                执行到此节点时直接流转
              </template>
            </el-alert>
          </SettingsSection>

          <SettingsSection
            title="标识与备注"
            description="节点技术标识和设计说明"
          >
            <el-form-item label="节点ID">
              <el-input v-model="basicForm.id" disabled />
            </el-form-item>
            <el-form-item label="设计备注" class="doc-item">
              <el-input
                v-model="basicForm.documentation"
                type="textarea"
                :rows="3"
                placeholder="记录节点设计说明..."
              />
            </el-form-item>
          </SettingsSection>
        </el-form>
      </section>
      </div>

      <div v-if="activeTab !== 'actions'" class="tab-footer">
        <el-button type="primary" @click="applyNodeConfiguration">应用到画布</el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted, toRaw } from 'vue'
import { useRouter } from 'vue-router'
import { Plus, Delete, View, WarningFilled } from '@element-plus/icons-vue'
import { getEntityStatusList } from '@/api/entityStatus'
import { deleteStatusMappings, getStatusMappings, saveStatusMappings } from '@/api/entityFlowStatus'
import { processApi } from '@/api/process'
import request from '@/utils/request'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  buildNodeScopedMultiInstanceCollection,
  buildAssigneeConfig,
  getProcessConditionFieldCode,
  getProcessConditionFieldType
} from '@/shared/process-config'
import FlowActionConfigPanel from '@/components/FlowActionConfigPanel.vue'
import FlowConditionGroupEditor from '@/components/FlowConditionGroupEditor.vue'
import ExtensionCapabilityPicker from '@/components/ExtensionCapabilityPicker.vue'
import ConfigHelpLabel from '@/components/ConfigHelpLabel.vue'
import SettingsSection from '@/components/SettingsSection.vue'
import UserSelector from '@/components/UserSelector.vue'
import JsonConfigLabel from '@/components/JsonConfigLabel.vue'
import NextApproverConfigEditor from '@/components/NextApproverConfigEditor.vue'
import {
  createNextApproverSelectionConfig,
  validateNextApproverSelectionConfig
} from '@/shared/next-approver'
import { parseJsonConfig } from '@/utils/jsonConfig'
import {
  buildFlowConditionExpression,
  createFlowConditionGroup,
  parseFlowConditionConfig,
  parseFlowConditionExpression,
  serializeFlowConditionConfig
} from '@/utils/flowConditionGroups'

const router = useRouter()

const props = defineProps({
  element: { type: Object, required: true },
  processId: { type: String, default: '' }
})

const emit = defineEmits(['save', 'update-status-mapping', 'action-changed'])
const activeTab = ref('basic')

// ========== 节点类型判断 ==========
const isUserTask = computed(() => props.element?.type === 'bpmn:UserTask')

// 注：自动跳过不再限制"仅第一个用户任务节点"，由后端 WorkflowAutoSkipService
// 监听 ACTIVITY_STARTED 事件按运行时实际到达的节点判断，避免 BFS 在网关分支下误判。
const isServiceTask = computed(() => props.element?.type === 'bpmn:ServiceTask')
const isSendTask = computed(() => props.element?.type === 'bpmn:SendTask')
const isCcConfigurable = computed(() => isUserTask.value || isServiceTask.value || isSendTask.value)
const isReceiveTask = computed(() => props.element?.type === 'bpmn:ReceiveTask')
const isManualTask = computed(() => props.element?.type === 'bpmn:ManualTask')
const isBusinessRuleTask = computed(() => props.element?.type === 'bpmn:BusinessRuleTask')
const isScriptTask = computed(() => props.element?.type === 'bpmn:ScriptTask')

const isCallActivity = computed(() => props.element?.type === 'bpmn:CallActivity')
const isSubProcess = computed(() => props.element?.type === 'bpmn:SubProcess')
const isTask = computed(() => props.element?.type?.includes('Task') || props.element?.type?.includes('Activity'))
const isStartEvent = computed(() => props.element?.type === 'bpmn:StartEvent')
const isSequenceFlow = computed(() => props.element?.type === 'bpmn:SequenceFlow')
const isActionConfigurable = computed(() => Boolean(props.element?.id) && props.element?.type !== 'bpmn:Process')
const isGateway = computed(() => props.element?.type?.includes('Gateway'))
const hasAdvancedConfig = computed(() => isTask.value || isGateway.value)

// 字段类型标签
function getFieldTypeLabel(type) {
  const typeMap = {
    'string': '文本',
    'number': '数字',
    'date': '日期',
    'datetime': '日期时间',
    'select': '选择（下拉单选）',
    'select_multiple': '选择（下拉多选）',
    'radio': '选择（单选框）',
    'checkbox': '选择（复选框）',
    'textarea': '多行文本',
    'file': '文件',
    'user': '用户选择',
    'dept': '部门选择'
  }
  return typeMap[type] || type
}

// 跳转到表单设计
function goToFormDesign() {
  ElMessage.info('请前往实体设计页面配置表单')
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
  const map = {
    class: 'com.workflow.delegate.DemoJavaDelegate',
    expression: "${demoExpressionService.execute('pending')}",
    delegateExpression: '${demoServiceTask}',
    rest: 'http://localhost:8080/api/demo/hello?name=${userId}'
  }
  return map[serviceForm.value.implementationType] || ''
})

// 服务任务各实现类型的默认示例
const SERVICE_EXAMPLES = {
  class: 'com.workflow.delegate.DemoJavaDelegate',
  expression: "${demoExpressionService.execute('pending')}",
  delegateExpression: '${demoServiceTask}',
  rest: 'http://localhost:8080/api/demo/hello?name=${userId}'
}

// ========== 表单数据 ==========
const basicForm = ref({ id: '', name: '', documentation: '' })
const assigneeResolverContext = { usage: 'ASSIGNEE' }
const multiInstanceResolverContext = { usage: 'MULTI_INSTANCE' }
const nextApproverConfigEditorRef = ref()
const assigneeForm = ref({
  assignee: '',
  candidateUsers: '',
  candidateGroups: '',
  candidateUserIds: [],
  candidateGroupIds: [],
  candidateRoleIds: [],
  isMultiInstance: false,
  multiInstanceType: 'parallel',
  collection: '',
  elementVariable: 'assignee',
  completionCondition: '',
  // 会签人员配置（独立于执行人/候选人）
  multiInstanceUsers: '',
  multiInstanceUserIds: [],
  multiInstanceUsernames: '',
  multiInstanceGroupIds: [],
  multiInstanceGroupCodes: '',
  multiInstanceRoleIds: [],
  multiInstanceRoleCodes: '',
  // 新增字段
  assigneeType: 'user', // user/group/role/expression/interface
  resolverCode: '',
  resolverDisplayName: '',
  extraParams: {},
  extraParamsText: '{}',
  interfaceType: 'resolver',
  interfaceName: '',
  interfaceMethod: 'selectAssignee',
  interfaceParams: '',
  restMethod: 'POST',
  resultMapping: 'assignee',
  collectionSource: 'variable', // variable/interface
  collectionInterface: '',
  collectionResolverCode: '',
  collectionResolverDisplayName: '',
  collectionExtraParams: {},
  collectionExtraParamsText: '{}',
  nextApproverSelection: createNextApproverSelectionConfig()
})
const assigneeResolverCurrentOption = computed(() => {
  if (!assigneeForm.value.resolverCode) return null
  return {
    key: assigneeForm.value.resolverCode,
    displayName:
      assigneeForm.value.resolverDisplayName
      || assigneeForm.value.resolverCode
  }
})
const collectionResolverCurrentOption = computed(() => {
  if (!assigneeForm.value.collectionResolverCode) return null
  return {
    key: assigneeForm.value.collectionResolverCode,
    displayName:
      assigneeForm.value.collectionResolverDisplayName
      || assigneeForm.value.collectionResolverCode
  }
})
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
const sendForm = ref({ channels: ['message'], to: '', subject: '', content: '', templateKey: '' })
const receiveForm = ref({ messageRef: '', hasTimeout: false, timeout: 30, timeoutUnit: 'MINUTE', timeoutAction: 'error' })
const manualForm = ref({ description: '', completionCriteria: '', responsible: '', estimatedHours: 0 })
const ruleForm = ref({ decisionRef: '', inputVariables: '', resultVariable: '', mapDecisionResult: true })

const callForm = ref({ calledElement: '', callActivityType: 'bpmn', inputParameters: '', outputParameters: '', businessKey: '' })
const conditionForm = ref({ type: '', expression: '' })
const conditionRoot = ref(createFlowConditionGroup())
const conditionParseWarning = ref('')

// 实体字段列表
const entityFields = ref([])

// 连线状态配置表单
const statusForm = ref({
  sourceNodeId: '',
  sourceNodeName: '',
  targetNodeId: '',
  targetNodeName: '',
  entityStatusCode: '',
  conditionExpression: '',
  description: ''
})

// 实体预定义的状态列表
const entityStatusList = ref([])

// 当前选中的状态名称
const selectedStatusName = computed(() => {
  const status = entityStatusList.value.find(s => s.statusCode === statusForm.value.entityStatusCode)
  return status?.statusName || ''
})
const hasCondition = ref(false)
const sourceNodeApprovalOptions = ref([])

const formConfig = ref({ 
  formKey: '',
  formSource: 'entity',  // 默认实体表单
  entityFormId: '',
  entityFormIds: [],
  isReadonly: false,
  entityCode: ''
})
const advancedForm = ref({ async: false, asyncBefore: false, asyncAfter: false, skipExpression: '', skipNode: false })
const slaForm = ref({
  enabled: false,
  policyCode: '',
  calendarSource: 'SYSTEM_DEFAULT',
  calendarCode: '',
  processCalendarCode: '',
  businessFieldCode: ''
})
const slaPolicyOptions = ref([])
const workCalendarOptions = ref([])
const selectedSlaPolicyName = computed(() => {
  const policy = slaPolicyOptions.value.find(item => item.policyCode === slaForm.value.policyCode)
  return policy?.policyName || ''
})
const organizationOptions = ref([])
const ccResolverContext = { usage: 'CC' }
const createCcRule = () => ({
  type: 'USER',
  values: [],
  includeChildren: false,
  fieldCode: '',
  resolverCode: '',
  extraParams: {},
  extraParamsText: '{}',
  resolverDisplayName: ''
})
const ccForm = ref({
  enabled: false,
  timings: ['TASK_COMPLETE'],
  channels: ['IN_APP'],
  includeOperator: false,
  allowManualCc: true,
  recipientRules: [createCcRule()],
  summary: ''
})

// 审批配置
const approvalForm = ref({
  enabled: true,
  commentLabel: '审批意见',
  options: [
    { value: 'approve', label: '通过', type: 'primary', showComment: true },
    { value: 'reject', label: '驳回', type: 'danger', showComment: true }
  ]
})

function addApprovalOption() {
  approvalForm.value.options.push({ value: '', label: '', type: 'primary', showComment: true, remarkRequired: false })
}

async function removeApprovalOption(index) {
  if (approvalForm.value.options.length <= 1) return
  try {
    await ElMessageBox.confirm(
      '删除后，运行时审批人将不能再选择该结果。确认删除吗？',
      '删除审批选项',
      {
        type: 'warning',
        confirmButtonText: '确认删除',
        cancelButtonText: '取消'
      }
    )
    approvalForm.value.options.splice(index, 1)
  } catch {
    // 用户取消
  }
}

// 组、角色选项
const groupOptions = ref([])
const roleOptions = ref([])

function addCcRule() {
  ccForm.value.recipientRules.push(createCcRule())
}

function removeCcRule(index) {
  if (ccForm.value.recipientRules.length > 1) {
    ccForm.value.recipientRules.splice(index, 1)
  }
}

function resetCcRuleValue(rule) {
  rule.values = []
  rule.fieldCode = ''
  rule.resolverCode = ''
  rule.extraParams = {}
  rule.extraParamsText = '{}'
  rule.resolverDisplayName = ''
}

function ccResolverCurrentOption(rule) {
  if (!rule.resolverCode) return null
  return {
    key: rule.resolverCode,
    displayName: rule.resolverDisplayName || rule.resolverCode
  }
}

function onCcResolverSelected(rule, option) {
  rule.resolverDisplayName = option?.displayName || ''
}

function normalizeCcRulesForSave() {
  return ccForm.value.recipientRules.map((rule, index) => {
    let extraParams = {}
    if (rule.type === 'RESOLVER') {
      if (!rule.resolverCode) {
        throw new Error(`第 ${index + 1} 条知会规则请选择人员接口`)
      }
      try {
        extraParams = rule.extraParamsText?.trim()
          ? JSON.parse(rule.extraParamsText)
          : {}
      } catch {
        throw new Error(`第 ${index + 1} 条知会规则的 extraParams 不是合法 JSON`)
      }
      if (!extraParams || Array.isArray(extraParams) || typeof extraParams !== 'object') {
        throw new Error(`第 ${index + 1} 条知会规则的 extraParams 必须是 JSON 对象`)
      }
    }
    return {
      type: rule.type,
      values: Array.isArray(rule.values) ? rule.values : [],
      includeChildren: rule.includeChildren === true,
      fieldCode: rule.fieldCode || '',
      resolverCode: rule.resolverCode || '',
      extraParams
    }
  })
}

function ccRuleStaticText(type) {
  return {
    STARTER: '流程发起人',
    CURRENT_ASSIGNEE: '当前任务办理人',
    HISTORY_APPROVERS: '流程历史办理人'
  }[type] || '无需额外参数'
}

const ccNaturalSummary = computed(() => {
  if (!ccForm.value.enabled) return '当前节点未启用知会'
  const timingMap = {
    TASK_CREATE: '任务创建时',
    TASK_COMPLETE: '任务完成时',
    EXPLICIT: '执行到本知会节点时'
  }
  const ruleMap = {
    USER: '固定用户',
    ROLE: '所选角色成员',
    GROUP: '所选用户组成员',
    DEPARTMENT: '所选组织/部门成员',
    STARTER: '流程发起人',
    CURRENT_ASSIGNEE: '当前办理人',
    HISTORY_APPROVERS: '历史办理人',
    ENTITY_FIELD: '实体字段中的用户',
    RESOLVER: '受控人员解析器结果'
  }
  const timings = ccForm.value.timings.map(item => timingMap[item] || item).join('、')
  const recipients = ccForm.value.recipientRules.map(item => ruleMap[item.type] || item.type).join('、')
  return `${timings}，知会 ${recipients || '未配置收件人'}；通知异步发送，不阻塞流程`
})

// 实体表单选项
const entityFormOptions = ref([])
const selectedFormFields = ref([])
function normalizeEntityFormIds(value) {
  const ids = Array.isArray(value) ? value : (value ? [value] : [])
  return [...new Set(ids.map(id => String(id || '').trim()).filter(Boolean))]
}

function parseEntityFormIds(value) {
  if (!value) return []
  if (Array.isArray(value)) return normalizeEntityFormIds(value)
  const raw = String(value).trim()
  if (!raw) return []
  if (raw.startsWith('[')) {
    try {
      return normalizeEntityFormIds(JSON.parse(raw))
    } catch (e) {
      console.warn('解析 entityFormIds 失败，按逗号列表处理:', e)
    }
  }
  return normalizeEntityFormIds(raw.split(','))
}

function getSelectedEntityFormId() {
  const directId = normalizeEntityFormIds(formConfig.value.entityFormId)[0]
  return directId || normalizeEntityFormIds(formConfig.value.entityFormIds)[0] || ''
}

// 加载组列表
async function loadGroups() {
  try {
    const res = await request.get('/system/group/enabled')
    if (res && Array.isArray(res)) {
      groupOptions.value = res.map(group => ({
        id: group.id,
        code: group.groupCode,
        label: group.groupName,
        value: group.groupCode
      }))
    }
  } catch (e) {
    console.error('加载组列表失败:', e)
  }
}

// 加载角色列表
async function loadRoles() {
  try {
    const res = await request.get('/system/role/enabled')
    if (res && Array.isArray(res)) {
      roleOptions.value = res.map(role => ({
        id: role.id,
        code: role.roleCode,
        label: role.roleName,
        value: role.roleCode
      }))
    }
  } catch (e) {
    console.error('加载角色列表失败:', e)
  }
}

async function loadOrganizations() {
  try {
    const res = await request.get('/system/org/enabled')
    if (res && Array.isArray(res)) {
      organizationOptions.value = res.map(item => ({
        label: item.orgName,
        value: item.orgCode || item.id
      }))
    }
  } catch (e) {
    console.error('加载组织部门失败:', e)
  }
}

// 绑定的实体信息
const boundEntity = ref(null)
const ENTITY_NOT_BOUND_MESSAGE = '该流程未绑定实体'
let entityFormsLoadingProcessId = ''
let entityFormsLoadingPromise = null

function isEntityNotBoundError(error) {
  const messages = [
    error?.message,
    error?.source?.message,
    error?.source?.msg,
    error?.response?.data?.message,
    error?.response?.data?.msg
  ]
  return messages.some(message => String(message || '').includes(ENTITY_NOT_BOUND_MESSAGE))
}

function resetEntityFormsState() {
  boundEntity.value = null
  entityFormOptions.value = []
}

// 加载流程绑定的实体及表单列表
async function loadEntityForms() {
  const processId = String(props.processId || '').trim()
  if (!processId) {
    resetEntityFormsState()
    return
  }

  if (entityFormsLoadingPromise && entityFormsLoadingProcessId === processId) {
    return entityFormsLoadingPromise
  }

  const loadingPromise = (async () => {
    let entityRes
    try {
      entityRes = await request.get(`/entity/process/${processId}`, { silentError: true })
    } catch (error) {
      if (String(props.processId || '').trim() !== processId) return
      resetEntityFormsState()
      if (isEntityNotBoundError(error)) return
      console.error('加载流程绑定实体失败:', error)
      ElMessage.error(`加载流程绑定实体失败: ${error.message || '未知错误'}`)
      return
    }

    if (String(props.processId || '').trim() !== processId) return
    if (!entityRes?.id) {
      resetEntityFormsState()
      return
    }

    boundEntity.value = entityRes
    entityFormOptions.value = []

    try {
      const formsRes = await request.get(`/entity-form/entity/${entityRes.id}`, { silentError: true })
      if (String(props.processId || '').trim() === processId) {
        entityFormOptions.value = Array.isArray(formsRes) ? formsRes : []
      }
    } catch (error) {
      if (String(props.processId || '').trim() !== processId) return
      entityFormOptions.value = []
      console.error('加载实体表单列表失败:', error)
      ElMessage.error(`加载实体表单列表失败: ${error.message || '未知错误'}`)
    }
  })()

  entityFormsLoadingProcessId = processId
  entityFormsLoadingPromise = loadingPromise

  try {
    return await loadingPromise
  } finally {
    if (entityFormsLoadingPromise === loadingPromise) {
      entityFormsLoadingProcessId = ''
      entityFormsLoadingPromise = null
    }
  }
}

// 获取默认表单
async function getDefaultForm(entityId) {
  try {
    const res = await request.get(`/entity-form/entity/${entityId}/default`)
    return res || null
  } catch (e) {
    console.log('获取默认表单失败:', e)
    return null
  }
}

// 加载表单字段
async function loadFormFields(formId) {
  try {
    const res = await request.get(`/entity-form/${formId}/fields`)
    if (res && Array.isArray(res)) {
      selectedFormFields.value = res
    }
  } catch (e) {
    console.error('加载表单字段失败:', e)
    selectedFormFields.value = []
  }
}

const subProcesses = ref([])
const subProcessesLoading = ref(false)

// 在组件挂载时加载数据
onMounted(() => {
  loadGroups()
  loadRoles()
  loadOrganizations()
  loadEntityFields()
  loadSubProcesses()
  loadSlaOptions()
})

// 监听 processId 变化，当流程ID传入后加载实体表单
watch(() => props.processId, (newProcessId) => {
  if (newProcessId) {
    console.log('processId 变化，重新加载实体表单:', newProcessId)
    loadEntityForms()
    loadEntityFields()
    loadSubProcesses()
  }
}, { immediate: true })

// 监听组/角色列表加载完成，重新计算编码映射
watch(() => groupOptions.value.length, () => {
  if (isUserTask.value && assigneeForm.value.candidateGroups) {
    assigneeForm.value.candidateGroupIds = getGroupIdsFromCodes(assigneeForm.value.candidateGroups)
  }
})

watch(() => roleOptions.value.length, () => {
  if (isUserTask.value && assigneeForm.value.candidateGroups) {
    assigneeForm.value.candidateRoleIds = getRoleIdsFromCodes(assigneeForm.value.candidateGroups)
  }
})

async function loadSubProcesses() {
  subProcessesLoading.value = true
  try {
    const processes = await processApi.getPublishedList()
    subProcesses.value = (Array.isArray(processes) ? processes : [])
      .filter(process => String(process.id || '') !== String(props.processId || ''))
      .map(process => ({
        key: process.processKey,
        name: `${process.processName} (${process.processKey})`
      }))
      .filter(process => process.key)
  } catch (error) {
    console.error('加载已发布子流程失败:', error)
    subProcesses.value = []
  } finally {
    subProcessesLoading.value = false
  }
}

// ========== 监听和初始化 ==========
watch(() => props.element, (newElement) => {
  // 切换节点时重置activeTab为basic
  activeTab.value = 'basic'
}, { immediate: true })

// 用户选择器直接使用 username 作为流程配置值
function getUserIdsFromUsernames(usernames) {
  if (!usernames) return []
  return usernames.split(',')
    .map(username => username.trim())
    .filter(Boolean)
}

// 根据组 code 列表获取组 value 列表（el-select-v2 的 v-model 绑定 value）
function getGroupIdsFromCodes(codes) {
  if (!codes) return []
  const codeList = codes.split(',').filter(c => c && !c.startsWith('ROLE_'))
  return codeList.map(code => {
    const group = groupOptions.value.find(g => g.code === code || g.value === code)
    return group?.value || code
  }).filter(Boolean)
}

// 根据角色 code 列表获取角色 value 列表（el-select-v2 的 v-model 绑定 value）
function getRoleIdsFromCodes(codes) {
  if (!codes) return []
  const roleCodes = codes.split(',').filter(c => c && c.startsWith('ROLE_')).map(c => c.replace('ROLE_', ''))
  return roleCodes.map(code => {
    const role = roleOptions.value.find(r => r.code === code || r.value === code)
    return role?.value || code
  }).filter(Boolean)
}

watch(() => props.element, async (newElement) => {
  if (newElement?.businessObject) {
    const bo = toRaw(newElement).businessObject
    const extProps = getExtensionProperties(bo)
    
    console.log('加载节点配置:', bo.id, bo.name, '扩展属性:', extProps)
    
    basicForm.value = { id: bo.id || '', name: bo.name || '', documentation: bo.documentation?.[0]?.text || '' }
    
    if (isUserTask.value) {
      const loop = bo.loopCharacteristics
      
      // 解析 assigneeConfig（包含执行人类型、接口配置等）
      let assigneeConfig = {}
      if (extProps['assigneeConfig']) {
        try {
          assigneeConfig = JSON.parse(extProps['assigneeConfig'])
        } catch (e) {
          console.error('解析 assigneeConfig 失败:', e)
        }
      }
      
      // 解析 multiInstanceConfig（多实例高级配置）
      let multiInstanceConfig = {}
      if (extProps['multiInstanceConfig']) {
        try {
          multiInstanceConfig = JSON.parse(extProps['multiInstanceConfig'])
        } catch (e) {
          console.error('解析 multiInstanceConfig 失败:', e)
        }
      }
      
      // 处理候选人和候选组
      let candidateUsers = bo.get('candidateUsers') || bo.get('flowable:candidateUsers') || ''
      const rawCandidateGroups = bo.get('candidateGroups') || bo.get('flowable:candidateGroups') || ''
      let assignee = bo.get('assignee') || bo.get('flowable:assignee') || ''
      // 当 BPMN 属性为空时，从扩展属性恢复（多实例模式下 candidateGroups 可能被覆盖）
      const candidateGroups = rawCandidateGroups || 
          ((assigneeConfig.assigneeType === 'group' || assigneeConfig.assigneeType === 'role') ? assigneeConfig.assigneeValue : '') || ''
      // 多实例模式下 BPMN 的 assignee/candidateUsers 会被替换为表达式，从扩展属性恢复实际人员
      if (assigneeConfig.assigneeType === 'user') {
        // 当 BPMN assignee 是表达式或为空时，使用扩展属性中的实际执行人
        if (assigneeConfig.assigneeValue && (!assignee || assignee.startsWith('${'))) {
          assignee = assigneeConfig.assigneeValue
        }
        if (assigneeConfig.candidateUsers && (!candidateUsers || candidateUsers.startsWith('${'))) {
          candidateUsers = assigneeConfig.candidateUsers
        }
      }
      
      assigneeForm.value = { 
        // 基础执行人配置
        assignee: assignee, 
        candidateUsers: candidateUsers, 
        candidateGroups: candidateGroups, 
        candidateUserIds: getUserIdsFromUsernames(candidateUsers),
        candidateGroupIds: getGroupIdsFromCodes(candidateGroups),
        candidateRoleIds: getRoleIdsFromCodes(candidateGroups),
        // 从扩展属性恢复 assigneeValue（兜底：当 BPMN 属性被多实例覆盖时）
        assigneeValue: assigneeConfig.assigneeValue || '',
        
        // 多实例配置
        isMultiInstance: !!loop, 
        multiInstanceType: loop?.isSequential ? 'sequential' : 'parallel', 
        collection: loop?.collection || multiInstanceConfig.collection || '${_wfMultiInstanceUsers_}', 
        elementVariable: loop?.elementVariable || multiInstanceConfig.elementVariable || 'assignee', 
        completionCondition: loop?.completionCondition?.body || multiInstanceConfig.completionCondition || '',
        
        // 会签人员配置（从扩展属性恢复）
        multiInstanceUsers: assigneeConfig.multiInstanceUsers || '',
        multiInstanceUserIds: assigneeConfig.multiInstanceUserIds || [],
        multiInstanceUsernames: assigneeConfig.multiInstanceUsernames || '',
        multiInstanceGroupIds: assigneeConfig.multiInstanceGroupIds || [],
        multiInstanceGroupCodes: assigneeConfig.multiInstanceGroupCodes || '',
        multiInstanceRoleIds: assigneeConfig.multiInstanceRoleIds || [],
        multiInstanceRoleCodes: assigneeConfig.multiInstanceRoleCodes || '',
        
        // 执行人类型和接口配置（从扩展属性）
        assigneeType: assigneeConfig.assigneeType || (assignee ? 'user' : candidateGroups ? 'group' : 'user'),
        resolverCode: assigneeConfig.resolverCode || assigneeConfig.interfaceName || '',
        resolverDisplayName: assigneeConfig.resolverDisplayName || '',
        extraParams: assigneeConfig.extraParams || {},
        extraParamsText: JSON.stringify(assigneeConfig.extraParams || parseLegacyParams(assigneeConfig.interfaceParams), null, 2),
        interfaceType: 'resolver',
        interfaceName: assigneeConfig.resolverCode || assigneeConfig.interfaceName || '',
        interfaceMethod: assigneeConfig.interfaceMethod || '',
        interfaceParams: assigneeConfig.interfaceParams || '',
        restMethod: assigneeConfig.restMethod || 'GET',
        resultMapping: assigneeConfig.resultMapping || '',
        collectionSource: assigneeConfig.collectionSource || multiInstanceConfig.collectionSource || 'interface',
        collectionInterface: assigneeConfig.collectionResolverCode
          || multiInstanceConfig.collectionResolverCode
          || assigneeConfig.collectionInterface
          || multiInstanceConfig.collectionInterface
          || '',
        collectionResolverCode: assigneeConfig.collectionResolverCode
          || multiInstanceConfig.collectionResolverCode
          || assigneeConfig.collectionInterface
          || multiInstanceConfig.collectionInterface
          || '',
        collectionResolverDisplayName: assigneeConfig.collectionResolverDisplayName || '',
        collectionExtraParams: assigneeConfig.collectionExtraParams
          || multiInstanceConfig.collectionExtraParams
          || {},
        collectionExtraParamsText: JSON.stringify(
          assigneeConfig.collectionExtraParams
            || multiInstanceConfig.collectionExtraParams
            || {},
          null,
          2
        ),
        nextApproverSelection: createNextApproverSelectionConfig(
          assigneeConfig.nextApproverSelection
        )
      }
      
      // 加载审批配置
      let approvalConfig = null
      if (extProps['approvalConfig']) {
        try {
          approvalConfig = JSON.parse(extProps['approvalConfig'])
        } catch (e) {
          console.error('解析 approvalConfig 失败:', e)
        }
      }
      if (approvalConfig) {
        approvalForm.value = {
          enabled: approvalConfig.enabled !== false,
          commentLabel: approvalConfig.commentLabel || '审批意见',
          options: Array.isArray(approvalConfig.options) && approvalConfig.options.length > 0
            ? approvalConfig.options.map(opt => ({ ...opt, remarkRequired: opt.remarkRequired !== undefined ? opt.remarkRequired : false }))
            : [
                { value: 'approve', label: '通过', type: 'primary', showComment: true, remarkRequired: false },
                { value: 'reject', label: '驳回', type: 'danger', showComment: true, remarkRequired: false }
              ]
        }
      } else {
        // 重置为默认值
        approvalForm.value = {
          enabled: true,
          commentLabel: '审批意见',
          options: [
            { value: 'approve', label: '通过', type: 'primary', showComment: true, remarkRequired: false },
            { value: 'reject', label: '驳回', type: 'danger', showComment: true, remarkRequired: false }
          ]
        }
      }
      if (extProps.slaConfig) {
        try {
          slaForm.value = {
            ...slaForm.value,
            ...JSON.parse(extProps.slaConfig)
          }
        } catch (error) {
          console.error('解析 slaConfig 失败:', error)
        }
      } else {
        slaForm.value = {
          enabled: false,
          policyCode: '',
          calendarSource: 'SYSTEM_DEFAULT',
          calendarCode: '',
          processCalendarCode: '',
          businessFieldCode: ''
        }
      }
    }
    if (isCcConfigurable.value) {
      let ccConfig = null
      if (extProps['ccConfig']) {
        try {
          ccConfig = JSON.parse(extProps['ccConfig'])
        } catch (e) {
          console.error('解析 ccConfig 失败:', e)
        }
      }
      const defaultTimings = (isServiceTask.value || isSendTask.value) ? ['EXPLICIT'] : ['TASK_COMPLETE']
      ccForm.value = ccConfig
        ? {
            enabled: ccConfig.enabled === true,
            timings: Array.isArray(ccConfig.timings) && ccConfig.timings.length ? ccConfig.timings : defaultTimings,
            channels: Array.isArray(ccConfig.channels) && ccConfig.channels.length ? ccConfig.channels : ['IN_APP'],
            includeOperator: ccConfig.includeOperator === true,
            allowManualCc: ccConfig.allowManualCc !== false,
            recipientRules: Array.isArray(ccConfig.recipientRules) && ccConfig.recipientRules.length
              ? ccConfig.recipientRules.map(rule => {
                  const extraParams = rule.extraParams || rule.params || {}
                  return {
                    ...createCcRule(),
                    ...rule,
                    values: Array.isArray(rule.values) ? rule.values : [],
                    extraParams,
                    extraParamsText: JSON.stringify(extraParams, null, 2)
                  }
                })
              : [createCcRule()],
            summary: ccConfig.summary || ''
          }
        : {
            enabled: false,
            timings: defaultTimings,
            channels: ['IN_APP'],
            includeOperator: false,
            allowManualCc: true,
            recipientRules: [createCcRule()],
            summary: ''
          }
    }
    if (isServiceTask.value) {
      // 优先根据 BPMN 标准属性判断实现类型（class/expression/delegateExpression）
      const hasStandardImpl = bo.class || bo.expression || bo.delegateExpression
      const restConfigStr = extProps['restConfig']
      if (restConfigStr && !hasStandardImpl) {
        // 只有没有标准实现时才走 REST 配置
        try {
          const restConfig = JSON.parse(restConfigStr)
          serviceForm.value = { implementationType: 'rest', implementation: '', resultVariable: extProps['serviceResultVariable'] || '' }
          restForm.value = { ...restForm.value, ...restConfig }
        } catch (e) {
          console.error('解析 REST 配置失败:', e)
          const implType = 'class'
          serviceForm.value = { implementationType: implType, implementation: SERVICE_EXAMPLES[implType] || '', resultVariable: extProps['serviceResultVariable'] || '' }
        }
      } else {
        const implType = bo.class ? 'class' : bo.expression ? 'expression' : bo.delegateExpression ? 'delegateExpression' : 'class'
        const implValue = bo.class || bo.expression || bo.delegateExpression || SERVICE_EXAMPLES[implType] || ''
        serviceForm.value = { implementationType: implType, implementation: implValue, resultVariable: extProps['serviceResultVariable'] || '' }
      }
    }
    if (isSendTask.value) {
      // 加载发送任务配置
      const sendConfigStr = extProps['sendConfig']
      if (sendConfigStr) {
        try {
          const sendConfig = JSON.parse(sendConfigStr)
          sendForm.value = { ...sendForm.value, ...sendConfig }
        } catch (e) {
          console.error('解析 sendConfig 失败:', e)
        }
      }
    }
    if (isReceiveTask.value) {
      // 加载接收任务配置
      const receiveConfigStr = extProps['receiveConfig']
      if (receiveConfigStr) {
        try {
          const receiveConfig = JSON.parse(receiveConfigStr)
          receiveForm.value = { ...receiveForm.value, ...receiveConfig }
        } catch (e) {
          console.error('解析 receiveConfig 失败:', e)
        }
      }
    }
    if (isManualTask.value) {
      // 加载手动任务配置
      const manualConfigStr = extProps['manualConfig']
      if (manualConfigStr) {
        try {
          const manualConfig = JSON.parse(manualConfigStr)
          manualForm.value = { ...manualForm.value, ...manualConfig }
        } catch (e) {
          console.error('解析 manualConfig 失败:', e)
        }
      }
    }
    if (isBusinessRuleTask.value) {
      // 加载业务规则任务配置
      const ruleConfigStr = extProps['ruleConfig']
      if (ruleConfigStr) {
        try {
          const ruleConfig = JSON.parse(ruleConfigStr)
          ruleForm.value = { ...ruleForm.value, ...ruleConfig }
        } catch (e) {
          console.error('解析 ruleConfig 失败:', e)
        }
      }
    }
    if (isCallActivity.value) {
      // 加载调用活动配置
      const callConfigStr = extProps['callConfig']
      if (callConfigStr) {
        try {
          const callConfig = JSON.parse(callConfigStr)
          callForm.value = { ...callForm.value, ...callConfig }
        } catch (e) {
          console.error('解析 callConfig 失败:', e)
        }
      }
    }
    if (isSequenceFlow.value) {
      // 解析条件表达式
      let expressionBody = bo.conditionExpression?.body || ''
      conditionForm.value = { 
        type: bo.conditionExpression ? 'expression' : bo.sourceRef?.default === bo ? 'default' : '', 
        expression: expressionBody 
      }
      hasCondition.value = !!bo.conditionExpression
      
      const savedConditionRoot = parseFlowConditionConfig(extProps['conditionGroupConfig'])
      const parsedConditionRoot = savedConditionRoot || parseFlowConditionExpression(expressionBody)
      conditionRoot.value = parsedConditionRoot || createFlowConditionGroup()
      conditionParseWarning.value = expressionBody && !parsedConditionRoot
        ? '原表达式会继续保留且不会被自动覆盖。若要使用条件组，请先确认并清空原表达式。'
        : ''
      
      // 加载连线状态配置
      loadStatusConfig(bo)
      
      // 加载源节点的审批选项（用于条件配置 approved 下拉）
      loadSourceNodeApprovalOptions(bo)
      
      // 加载实体字段（用于条件表达式编辑器）
      if (boundEntity.value?.id) {
        loadEntityFields()
      }
    }
    if (isUserTask.value || isStartEvent.value) {
      // 从扩展属性中读取表单绑定信息
      const entityFormIds = parseEntityFormIds(extProps['entityFormIds'])
      const entityFormId = extProps['entityFormId']
      const selectedEntityFormIds = (
        entityFormIds.length ? entityFormIds : normalizeEntityFormIds(entityFormId)
      ).slice(0, 1)
      const entityFormReadonly = extProps['entityFormReadonly'] === 'true'
      const entityCode = extProps['entityCode'] || ''
      
      if (selectedEntityFormIds.length) {
        // 实体表单绑定
        formConfig.value = {
          formSource: 'entity',
          formKey: '',
          entityFormId: selectedEntityFormIds[0],
          entityFormIds: selectedEntityFormIds,
          isReadonly: entityFormReadonly,
          entityCode: entityCode
        }
        // 加载表单字段
        loadFormFields(selectedEntityFormIds[0])
      } else if (bo.formKey) {
        // 自定义表单
        formConfig.value = {
          formSource: 'custom',
          formKey: bo.formKey,
          entityFormId: '',
          entityFormIds: [],
          isReadonly: false,
          entityCode: ''
        }
      } else if (boundEntity.value?.id) {
        // 无表单配置，尝试使用默认表单
        const defaultForm = await getDefaultForm(boundEntity.value.id)
        if (defaultForm) {
          console.log('使用默认表单:', defaultForm.formName)
          formConfig.value = {
            formSource: 'entity',
            formKey: '',
            entityFormId: defaultForm.id,
            entityFormIds: [defaultForm.id],
            isReadonly: false,
            entityCode: boundEntity.value.entityCode || ''
          }
          loadFormFields(defaultForm.id)
          // 自动保存到BPMN
          updateExtensionProperty('entityFormId', defaultForm.id)
          updateExtensionProperty('entityFormIds', null)
          updateExtensionProperty('entityFormReadonly', 'false')
          updateExtensionProperty('entityCode', boundEntity.value.entityCode || '')
        } else {
          // 无默认表单
          formConfig.value = {
            formSource: 'none',
            formKey: '',
            entityFormId: '',
            entityFormIds: [],
            isReadonly: false,
            entityCode: ''
          }
        }
      } else {
        // 无表单
        formConfig.value = {
          formSource: 'none',
          formKey: '',
          entityFormId: '',
          entityFormIds: [],
          isReadonly: false,
          entityCode: ''
        }
      }
    }
    if (isTask.value || isGateway.value) {
      advancedForm.value = { 
        async: bo.async || bo.asyncBefore || bo.asyncAfter, 
        asyncBefore: bo.asyncBefore || false, 
        asyncAfter: bo.asyncAfter || false, 
        skipExpression: bo.skipExpression?.body || '',
        skipNode: extProps['skipNode'] === 'true'
      }
    }
  }
}, { immediate: true })

// ========== 更新方法 ==========
function getModeling() { return props.element?._modeler?.get('modeling') }
function getModdle() { return props.element?._modeler?.get('moddle') }

// 获取扩展属性
function getExtensionProperties(bo) {
  const props = {}
  if (!bo.extensionElements) return props
  const extElements = bo.extensionElements.get('values') || []
  
  // 支持 flowable:Properties，兼容旧数据 camunda:Properties
  let propElement = extElements.find(v => v.$type === 'flowable:Properties')
  if (!propElement) {
    propElement = extElements.find(v => v.$type === 'camunda:Properties')
  }
  
  if (propElement) {
    // properties 可能在 values 或 properties 属性中
    const values = propElement.get('values') || propElement.get('properties') || propElement.values || propElement.properties || []
    values.forEach(p => {
      if (p && p.name) {
        props[p.name] = p.value
      }
    })
  }
  return props
}

function updateProperty(prop, value) {
  const modeling = getModeling()
  if (!modeling) return
  const updates = {}
  if (value === null || value === undefined) {
    updates[prop] = null
  } else {
    updates[prop] = value
  }
  modeling.updateProperties(toRaw(props.element), updates)
}

function updateDocumentation() {
  const modeling = getModeling(), moddle = getModdle()
  if (!modeling || !moddle) return
  const docs = basicForm.value.documentation ? [moddle.create('bpmn:Documentation', { text: basicForm.value.documentation })] : []
  modeling.updateProperties(toRaw(props.element), { documentation: docs })
}

function onMultiInstanceChange(enabled) {
  if (enabled) {
    assigneeForm.value.collection = buildNodeScopedMultiInstanceCollection(
      basicForm.value.id || props.element?.businessObject?.id,
      assigneeForm.value.collection
    )
  }
}

function updateMultiInstance() {
  if (!assigneeForm.value.isMultiInstance) return
  const modeling = getModeling(), moddle = getModdle()
  if (!modeling || !moddle) return
  // 使用内部系统变量，由后端监听器自动根据审批人配置计算
  const collection = buildNodeScopedMultiInstanceCollection(
    basicForm.value.id || props.element?.businessObject?.id,
    assigneeForm.value.collection
  )
  assigneeForm.value.collection = collection
  const loop = moddle.create('bpmn:MultiInstanceLoopCharacteristics', {
    isSequential: assigneeForm.value.multiInstanceType === 'sequential',
    collection: collection,
    elementVariable: assigneeForm.value.elementVariable || 'assignee'
  })
  if (assigneeForm.value.completionCondition) {
    loop.completionCondition = moddle.create('bpmn:FormalExpression', { body: assigneeForm.value.completionCondition })
  }
  // 多实例任务必须设置 assignee 为 elementVariable 表达式，
  // 否则 Flowable 会沿用原来的 candidateGroups，导致所有人都能看到所有会签任务
  modeling.updateProperties(toRaw(props.element), { 
    loopCharacteristics: loop,
    assignee: '${' + (assigneeForm.value.elementVariable || 'assignee') + '}',
    candidateGroups: undefined,
    candidateUsers: undefined
  })

  // 保存多实例高级配置到扩展属性（用于回显）
  const multiInstanceConfig = {
    collection: collection,
    elementVariable: assigneeForm.value.elementVariable || 'assignee',
    completionCondition: assigneeForm.value.completionCondition,
    collectionSource: assigneeForm.value.collectionSource,
    collectionInterface: assigneeForm.value.collectionResolverCode,
    collectionResolverCode: assigneeForm.value.collectionResolverCode,
    collectionExtraParams: parseJsonObjectQuietly(
      assigneeForm.value.collectionExtraParamsText)
  }
  updateExtensionProperty('multiInstanceConfig', JSON.stringify(multiInstanceConfig))
}

function onServiceTypeChange() {
  const type = serviceForm.value.implementationType
  if (type === 'rest') {
    if (!restForm.value.url) restForm.value.url = SERVICE_EXAMPLES.rest
  } else {
    if (!serviceForm.value.implementation) {
      serviceForm.value.implementation = SERVICE_EXAMPLES[type] || ''
    }
  }
}

function updateServiceImplementation() {
  const modeling = getModeling()
  if (!modeling) return
  const updates = { class: undefined, expression: undefined, delegateExpression: undefined }
  if (serviceForm.value.implementation) updates[serviceForm.value.implementationType] = serviceForm.value.implementation
  modeling.updateProperties(toRaw(props.element), updates)
  // 清除可能残留的 REST 配置扩展属性，避免回显时误判为 REST 类型
  updateExtensionProperty('restConfig', null)
}

function getFullExpression() {
  if (conditionParseWarning.value) return conditionForm.value.expression || ''
  return buildFlowConditionExpression(conditionRoot.value, getFieldType)
}

// 获取源网关/节点对象(element)
function getSourceElement() {
  const el = toRaw(props.element)
  if (!el) return null
  // 对于 sequenceFlow，source 属性指向源节点 element
  // 使用 toRaw 确保返回原始对象，避免 Vue Proxy 问题
  return toRaw(el.source)
}

function onConditionTypeChange(type) {
  if (type === 'expression') {
    conditionParseWarning.value = ''
    if (!conditionRoot.value?.children?.length) {
      conditionRoot.value = createFlowConditionGroup()
    }
    updateCondition()
  } else if (type === 'default') {
    conditionParseWarning.value = ''
  } else {
    conditionForm.value.expression = ''
    conditionRoot.value = createFlowConditionGroup()
    conditionParseWarning.value = ''
  }
}

function updateCondition() {
  if (conditionForm.value.type !== 'expression' || conditionParseWarning.value) return
  const expression = buildFlowConditionExpression(conditionRoot.value, getFieldType)
  conditionForm.value.expression = expression
}

function resetConditionGroups() {
  conditionRoot.value = createFlowConditionGroup()
  conditionParseWarning.value = ''
  conditionForm.value.expression = ''
  updateCondition()
}

// 加载实体字段
async function loadEntityFields() {
  if (!boundEntity.value?.id) return
  try {
    const res = await request.get(`/entity-form/entity/${boundEntity.value.id}/fields`)
    if (res && Array.isArray(res)) {
      entityFields.value = res
    }
  } catch (e) {
    console.error('加载实体字段失败:', e)
  }
}

// 获取字段类型
function getFieldType(fieldName) {
  if (fieldName === 'approved') return 'select'
  const field = entityFields.value.find(f =>
    getProcessConditionFieldCode(f) === fieldName
    || f.fieldName === fieldName)
  return getProcessConditionFieldType(field)
}

function onAsyncChange() {
  if (!advancedForm.value.async) {
    advancedForm.value.asyncBefore = false
    advancedForm.value.asyncAfter = false
  }
}

function updateAsync() {
  const modeling = getModeling()
  if (!modeling) return
  modeling.updateProperties(toRaw(props.element), { async: advancedForm.value.async, asyncBefore: advancedForm.value.asyncBefore, asyncAfter: advancedForm.value.asyncAfter })
}

function updateSkipExpression() {
  const modeling = getModeling(), moddle = getModdle()
  if (!modeling || !moddle) return
  if (advancedForm.value.skipExpression) {
    const expr = moddle.create('bpmn:FormalExpression', { body: advancedForm.value.skipExpression })
    modeling.updateProperties(toRaw(props.element), { skipExpression: expr })
  } else modeling.updateProperties(toRaw(props.element), { skipExpression: undefined })
}

function updateSkipNode() {
  // 使用扩展属性存储跳过节点配置
  updateExtensionProperty('skipNode', advancedForm.value.skipNode ? 'true' : 'false')
}

async function loadSlaOptions() {
  try {
    const [policies, calendars] = await Promise.all([
      request.get('/task-sla-policies/published'),
      request.get('/work-calendars')
    ])
    slaPolicyOptions.value = Array.isArray(policies) ? policies : []
    workCalendarOptions.value = (Array.isArray(calendars) ? calendars : [])
      .filter(item => item.status === 'PUBLISHED')
  } catch (error) {
    console.warn('加载SLA策略或工作日历失败:', error)
  }
}

function updateSlaConfig() {
  if (!slaForm.value.enabled) {
    updateExtensionProperty('slaConfig', JSON.stringify({ enabled: false }))
    return true
  }
  if (!slaForm.value.policyCode) {
    ElMessage.warning('请选择已发布的SLA策略')
    return false
  }
  if (slaForm.value.calendarSource === 'NODE' && !slaForm.value.calendarCode) {
    ElMessage.warning('请选择节点工作日历')
    return false
  }
  if (slaForm.value.calendarSource === 'PROCESS' && !slaForm.value.processCalendarCode) {
    ElMessage.warning('请选择流程工作日历')
    return false
  }
  if (slaForm.value.calendarSource === 'BUSINESS_DEPT' && !slaForm.value.businessFieldCode) {
    ElMessage.warning('请选择业务归属部门字段')
    return false
  }
  updateExtensionProperty('slaConfig', JSON.stringify(slaForm.value))
  return true
}

// ========== 执行人配置更新方法 ==========
function onAssigneeTypeChange(type) {
  // 切换类型时清空之前的配置
  assigneeForm.value.assignee = ''
  assigneeForm.value.candidateUsers = ''
  assigneeForm.value.candidateGroups = ''
  assigneeForm.value.candidateUserIds = []
  assigneeForm.value.candidateGroupIds = []
  assigneeForm.value.candidateRoleIds = []
  if (type !== 'interface') {
    assigneeForm.value.resolverCode = ''
    assigneeForm.value.resolverDisplayName = ''
    assigneeForm.value.extraParams = {}
    assigneeForm.value.extraParamsText = '{}'
  }
}

// ========== 表单配置更新方法 ==========
function onFormSourceChange(source) {
  // 切换表单来源时清空之前的配置
  if (source === 'entity') {
    formConfig.value.formKey = ''
  } else if (source === 'custom') {
    formConfig.value.entityFormId = ''
    formConfig.value.entityFormIds = []
    formConfig.value.isReadonly = false
  } else {
    // none - 清除所有配置
    formConfig.value.formKey = ''
    formConfig.value.entityFormId = ''
    formConfig.value.entityFormIds = []
    formConfig.value.isReadonly = false
  }
}

async function onEntityFormChange(formId) {
  const selectedFormId = normalizeEntityFormIds(formId)[0] || ''
  formConfig.value.entityFormIds = selectedFormId ? [selectedFormId] : []
  formConfig.value.entityFormId = selectedFormId

  if (selectedFormId) {
    await loadFormFields(selectedFormId)
    const selectedForm = entityFormOptions.value.find(f => f.id === selectedFormId)
    formConfig.value.entityCode = selectedForm?.entityCode || boundEntity.value?.entityCode || ''
  } else {
    selectedFormFields.value = []
    formConfig.value.entityCode = ''
  }
}

function updateNodeFormBind() {
  if (!props.element) return
  const rawElement = toRaw(props.element)
  const bo = rawElement.businessObject
  const modeling = getModeling()
  
  const entityFormId = getSelectedEntityFormId()

  if (formConfig.value.formSource === 'entity' && entityFormId) {
    // 实体表单绑定
    formConfig.value.entityFormId = entityFormId
    formConfig.value.entityFormIds = [entityFormId]
    if (modeling) {
      modeling.updateProperties(rawElement, { 'flowable:formKey': null, 'flowable:formData': null })
    }
    // 扩展属性存储表单绑定信息
    updateExtensionProperty('entityFormId', entityFormId)
    updateExtensionProperty('entityFormIds', null)
    updateExtensionProperty('entityFormReadonly', formConfig.value.isReadonly ? 'true' : 'false')
    updateExtensionProperty('entityCode', formConfig.value.entityCode)
  } else if (formConfig.value.formSource === 'custom' && formConfig.value.formKey) {
    // 自定义表单使用 formKey
    updateProperty('formKey', formConfig.value.formKey)
    if (modeling) {
      modeling.updateProperties(rawElement, { 'flowable:formData': null })
    }
    updateExtensionProperty('entityFormId', null)
    updateExtensionProperty('entityFormIds', null)
    updateExtensionProperty('entityFormReadonly', null)
    updateExtensionProperty('entityCode', null)
  } else {
    // 无表单
    updateProperty('formKey', '')
    updateExtensionProperty('entityFormId', null)
    updateExtensionProperty('entityFormIds', null)
    updateExtensionProperty('entityFormReadonly', null)
    updateExtensionProperty('entityCode', null)
  }
}

function updateExtensionProperty(name, value) {
  if (!props.element) {
    console.warn('updateExtensionProperty: element 为空')
    return
  }
  const moddle = getModdle()
  const modeling = getModeling()
  if (!moddle || !modeling) {
    console.warn('updateExtensionProperty: moddle 或 modeling 为空')
    return
  }
  const bo = toRaw(props.element).businessObject
  if (!bo) {
    console.warn('updateExtensionProperty: businessObject 为空')
    return
  }
  
  try {
    // 创建或获取 extensionElements
    let extensionElements = bo.extensionElements
    if (!extensionElements) {
      extensionElements = moddle.create('bpmn:ExtensionElements')
    }
    
    // 获取 values 数组
    let values = extensionElements.get('values')
    if (!values) {
      values = []
    }
    
    // 查找或创建 flowable:Properties 元素
    let propElement = values.find(v => v.$type === 'flowable:Properties')
    if (!propElement) {
      console.log('创建新的 flowable:Properties')
      propElement = moddle.create('flowable:Properties')
      values.push(propElement)
    }
    
    // 获取或创建 values 数组（flowable:Properties 的 moddle 属性名为 values）
    let propValues = propElement.get('values') || []
    if (!propValues || !Array.isArray(propValues)) {
      propValues = []
    }
    
    // 查找或更新属性
    let existingProp = propValues.find(p => p.name === name)
    
    if (value !== null && value !== undefined && value !== '') {
      if (!existingProp) {
        console.log('创建新的 flowable:Property:', name, value)
        existingProp = moddle.create('flowable:Property', { name: name, value: String(value) })
        propValues.push(existingProp)
      } else {
        console.log('更新现有属性:', name, value)
        existingProp.value = String(value)
      }
    } else if (existingProp) {
      console.log('清除属性:', name)
      const idx = propValues.indexOf(existingProp)
      if (idx > -1) propValues.splice(idx, 1)
    }
    
    // 更新 values（使用 moddle set 方法确保正确序列化）
    propElement.set('values', propValues)
    
    // 关键：使用 modeling.updateProperties 通知 bpmn-js 属性已更改
    // 注意：使用 toRaw 避免 Vue Proxy 与 BPMN.js 对象冲突
    modeling.updateProperties(toRaw(props.element), { extensionElements: extensionElements })
    
    console.log('扩展属性已保存:', name, value)
  } catch (error) {
    console.error('updateExtensionProperty 失败:', error)
    ElMessage.error('保存失败: ' + (error.message || '未知错误'))
    throw error
  }
}

function onCollectionSourceChange() {
  assigneeForm.value.collection = ''
  assigneeForm.value.collectionInterface = ''
  assigneeForm.value.collectionResolverCode = ''
  assigneeForm.value.collectionResolverDisplayName = ''
  assigneeForm.value.collectionExtraParams = {}
  assigneeForm.value.collectionExtraParamsText = '{}'
}

function onAssigneeResolverSelected(option) {
  assigneeForm.value.resolverDisplayName = option?.displayName || ''
  assigneeForm.value.interfaceName = option?.key || ''
}

function onCollectionResolverSelected(option) {
  assigneeForm.value.collectionResolverDisplayName =
    option?.displayName || ''
  assigneeForm.value.collectionInterface = option?.key || ''
}

function parseLegacyParams(value) {
  if (!value) return {}
  if (typeof value === 'object' && !Array.isArray(value)) return value
  try {
    const parsed = JSON.parse(value)
    return parsed && !Array.isArray(parsed) && typeof parsed === 'object'
      ? parsed
      : {}
  } catch {
    return {}
  }
}

function parseJsonObject(value, label) {
  let parsed
  try {
    parsed = value?.trim() ? JSON.parse(value) : {}
  } catch {
    throw new Error(`${label} 不是合法 JSON`)
  }
  if (!parsed || Array.isArray(parsed) || typeof parsed !== 'object') {
    throw new Error(`${label} 必须是 JSON 对象`)
  }
  return parsed
}

function parseJsonObjectQuietly(value) {
  try {
    return parseJsonObject(value, 'extraParams')
  } catch {
    return {}
  }
}

function updateAssignee() {
  // v-model 已保存选择值，实际 BPMN 写入统一由“应用到画布”完成。
}

function updateCandidateUsers() {
  assigneeForm.value.candidateUsers =
    (assigneeForm.value.candidateUserIds || []).join(',')
}

function updateCandidateGroups() {
  // candidateGroupIds 里存的是 groupCode（el-select-v2 的 value）
  const selectedGroups = groupOptions.value.filter(g => assigneeForm.value.candidateGroupIds?.includes(g.value))
  assigneeForm.value.candidateGroups = selectedGroups.map(g => g.code).join(',')
}

function updateCandidateRoles() {
  // candidateRoleIds 里存的是 roleCode（el-select-v2 的 value）
  const selectedRoles = roleOptions.value.filter(r => assigneeForm.value.candidateRoleIds?.includes(r.value))
  // 角色也存储在candidateGroups中，通过前缀区分
  const roleCodes = selectedRoles.map(r => 'ROLE_' + r.code).join(',')
  assigneeForm.value.candidateGroups = roleCodes
}

function updateMultiInstanceUsers() {
  // 用户选择器保存 username；组和角色选择器保存各自编码
  const selectedGroups = groupOptions.value.filter(g => assigneeForm.value.multiInstanceGroupIds?.includes(g.value))
  const selectedRoles = roleOptions.value.filter(r => assigneeForm.value.multiInstanceRoleIds?.includes(r.value))
  
  const userNames = assigneeForm.value.multiInstanceUserIds || []
  const groupCodes = selectedGroups.map(g => g.code)
  const roleCodes = selectedRoles.map(r => r.code)
  
  // 保存结构化的会签人员编码，方便后端解析
  assigneeForm.value.multiInstanceUsers = [...userNames, ...groupCodes, ...roleCodes.map(c => 'ROLE_' + c)].join(',')
  assigneeForm.value.multiInstanceUsernames = userNames.join(',')
  assigneeForm.value.multiInstanceGroupCodes = groupCodes.join(',')
  assigneeForm.value.multiInstanceRoleCodes = roleCodes.join(',')
  
}

function updateAssigneeInterface() {
  const extraParams = parseJsonObject(
    assigneeForm.value.extraParamsText,
    '办理人接口 extraParams')
  assigneeForm.value.extraParams = extraParams
  assigneeForm.value.interfaceName = assigneeForm.value.resolverCode
  const interfaceConfig = {
    type: 'resolver',
    usage: 'ASSIGNEE',
    resolverCode: assigneeForm.value.resolverCode,
    extraParams
  }
  updateExtensionProperty('assigneeInterface', JSON.stringify(interfaceConfig))
}

function updateAssigneeConfig() {
  const config = buildAssigneeConfig(assigneeForm.value)
  // 使用 updateExtensionProperty 存储 JSON 字符串
  updateExtensionProperty('assigneeConfig', JSON.stringify(config))
}

// REST接口配置更新
function updateRestConfig() {
  const modeling = getModeling()
  if (!modeling) return
  // 将REST配置存储到扩展属性中（使用 flowable:Properties）
  const restConfig = { ...restForm.value }
  updateExtensionProperty('restConfig', JSON.stringify(restConfig))
  // 清除其他实现方式
  modeling.updateProperties(toRaw(props.element), { 
    class: undefined,
    expression: undefined,
    delegateExpression: undefined
  })
}

function getRestBodyPlaceholder() {
  return '{\n  "entityDataId": "${entityDataId}",\n  "status": "approved"\n}'
}

function getConfigurationSections() {
  const sections = ['basic']
  if (isUserTask.value) sections.push('assignee', 'form', 'approval')
  if (isServiceTask.value) sections.push('service')
  if (isSendTask.value) sections.push('send')
  if (isReceiveTask.value) sections.push('receive')
  if (isManualTask.value) sections.push('manual')
  if (isBusinessRuleTask.value) sections.push('rule')
  if (isCallActivity.value) sections.push('call')
  if (isSequenceFlow.value) sections.push('condition')
  if (isStartEvent.value) sections.push('form')
  if (isCcConfigurable.value) sections.push('cc')
  if (hasAdvancedConfig.value) sections.push('advanced')
  return sections
}

function applyConfigurationSection(section) {
  try {
    // 检查 element 是否有效
    if (!props.element || !props.element.businessObject) {
      ElMessage.warning('请先选择流程节点')
      return false
    }
    
    switch (section) {
      case 'basic':
        updateProperty('name', basicForm.value.name)
        updateDocumentation()
        break
      case 'assignee': {
        const modeling = getModeling()
        if (!modeling) {
          ElMessage.warning('模型未初始化')
          return
        }
        const nextApproverValidation =
          nextApproverConfigEditorRef.value?.validate?.()
          || validateNextApproverSelectionConfig(
            assigneeForm.value.nextApproverSelection
          )
        if (!nextApproverValidation.valid) {
          ElMessage.warning(nextApproverValidation.message)
          return false
        }
        const updates = {}
        if (assigneeForm.value.isMultiInstance) {
          if (assigneeForm.value.collectionSource === 'interface') {
            if (!assigneeForm.value.collectionResolverCode) {
              ElMessage.warning('请选择会签人员接口')
              return
            }
            try {
              assigneeForm.value.collectionExtraParams = parseJsonObject(
                assigneeForm.value.collectionExtraParamsText,
                '会签人员接口 extraParams')
            } catch (error) {
              ElMessage.warning(error.message)
              return
            }
          }
          modeling.updateProperties(toRaw(props.element), {
            assignee: null,
            candidateUsers: null,
            candidateGroups: null
          })
          updateAssigneeConfig()
          updateMultiInstance()
          break
        }
        // 统一计算并写入执行人相关 BPMN 属性，不依赖 @change 事件
        updates.loopCharacteristics = undefined
        updateExtensionProperty('multiInstanceConfig', null)
        if (assigneeForm.value.assigneeType === 'user') {
          const usersStr =
            (assigneeForm.value.candidateUserIds || []).join(',')
          assigneeForm.value.candidateUsers = usersStr
          updates.assignee = assigneeForm.value.assignee || null
          updates.candidateUsers = usersStr || null
          updates.candidateGroups = null
        } else if (assigneeForm.value.assigneeType === 'group') {
          const selectedGroups = groupOptions.value.filter(g => assigneeForm.value.candidateGroupIds?.includes(g.value))
          const groupsStr = selectedGroups.map(g => g.code).join(',')
          assigneeForm.value.candidateGroups = groupsStr
          updates.assignee = null
          updates.candidateUsers = null
          updates.candidateGroups = groupsStr || null
        } else if (assigneeForm.value.assigneeType === 'role') {
          const selectedRoles = roleOptions.value.filter(r => assigneeForm.value.candidateRoleIds?.includes(r.value))
          const roleCodes = selectedRoles.map(r => 'ROLE_' + r.code).join(',')
          assigneeForm.value.candidateGroups = roleCodes
          updates.assignee = null
          updates.candidateUsers = null
          updates.candidateGroups = roleCodes || null
        } else if (assigneeForm.value.assigneeType === 'expression') {
          updates.assignee = assigneeForm.value.assignee || null
          updates.candidateUsers = assigneeForm.value.candidateUsers || null
          updates.candidateGroups = assigneeForm.value.candidateGroups || null
        } else if (assigneeForm.value.assigneeType === 'interface') {
          if (!assigneeForm.value.resolverCode) {
            ElMessage.warning('请选择办理人接口')
            return
          }
          try {
            assigneeForm.value.extraParams = parseJsonObject(
              assigneeForm.value.extraParamsText,
              '办理人接口 extraParams')
          } catch (error) {
            ElMessage.warning(error.message)
            return
          }
          updates.assignee = null
          updates.candidateUsers = null
          updates.candidateGroups = null
          updateAssigneeInterface()
        } else {
          updateExtensionProperty('assigneeInterface', null)
        }
        modeling.updateProperties(toRaw(props.element), updates)
        if (assigneeForm.value.assigneeType !== 'interface') {
          updateExtensionProperty('assigneeInterface', null)
        }
        updateAssigneeConfig()
        break
      }
      case 'service':
        if (serviceForm.value.implementationType === 'rest') {
          if (!restForm.value.url?.trim()) {
            ElMessage.warning('请填写 REST 请求 URL')
            return
          }
          if (restForm.value.contentType === 'multipart/form-data') {
            ElMessage.warning('REST 服务任务暂不支持 multipart/form-data')
            return
          }
          try {
            parseJsonObject(restForm.value.headers, 'REST 请求头')
            parseJsonObject(restForm.value.queryParams, 'REST 查询参数')
            parseJsonObject(restForm.value.resultMapping, 'REST 结果映射')
            if (restForm.value.method !== 'GET'
                && restForm.value.body?.trim()) {
              parseJsonConfig(restForm.value.body, {
                fieldName: 'REST 请求体',
                expectedType: 'object-or-array'
              })
            }
          } catch (error) {
            ElMessage.warning(error.message)
            return
          }
          updateRestConfig()
        } else {
          if (!serviceForm.value.implementation?.trim()) {
            ElMessage.warning('请填写服务任务实现')
            return
          }
          updateServiceImplementation()
          updateExtensionProperty('serviceResultVariable', serviceForm.value.resultVariable)
        }
        break
      case 'send':
        if (!sendForm.value.channels?.length) {
          ElMessage.warning('请至少选择一个发送渠道')
          return
        }
        if (sendForm.value.channels.some(channel => channel !== 'message')) {
          ElMessage.warning('发送任务当前仅支持站内信渠道')
          return
        }
        if (!sendForm.value.to?.trim()) {
          ElMessage.warning('请填写发送任务接收人')
          return
        }
        // 发送任务配置保存到扩展属性
        updateExtensionProperty('sendConfig', JSON.stringify(sendForm.value))
        break
      case 'receive':
        if (receiveForm.value.hasTimeout) {
          if (!Number.isInteger(receiveForm.value.timeout) || receiveForm.value.timeout < 1) {
            ElMessage.warning('接收任务超时时间必须是正整数')
            return
          }
          if (!['MINUTE', 'HOUR', 'DAY'].includes(receiveForm.value.timeoutUnit)) {
            ElMessage.warning('请选择有效的接收任务超时单位')
            return
          }
          if (!['error', 'continue'].includes(receiveForm.value.timeoutAction)) {
            ElMessage.warning('请选择有效的接收任务超时处理方式')
            return
          }
        }
        updateExtensionProperty('receiveConfig', JSON.stringify(receiveForm.value))
        break
      case 'manual':
        updateExtensionProperty('manualConfig', JSON.stringify(manualForm.value))
        break
      case 'rule':
        if (!ruleForm.value.decisionRef?.trim()) {
          ElMessage.warning('请填写业务规则任务的决策表 Key')
          return
        }
        try {
          parseJsonObject(ruleForm.value.inputVariables, '业务规则输入变量')
        } catch (error) {
          ElMessage.warning(error.message)
          return
        }
        updateExtensionProperty('ruleConfig', JSON.stringify(ruleForm.value))
        break
      case 'call':
        if (!callForm.value.calledElement?.trim()) {
          ElMessage.warning('请选择或填写子流程 Key')
          return
        }
        try {
          parseJsonObject(callForm.value.inputParameters, '调用活动输入参数')
          parseJsonObject(callForm.value.outputParameters, '调用活动输出参数')
        } catch (error) {
          ElMessage.warning(error.message)
          return
        }
        updateExtensionProperty('callConfig', JSON.stringify(callForm.value))
        break
      case 'condition':
        // 根据条件类型执行相应的保存逻辑
        if (conditionForm.value.type === 'default') {
          // 保存默认流设置
          const modeling = getModeling()
          const source = getSourceElement()
          if (modeling && source) {
            modeling.updateProperties(toRaw(props.element), { conditionExpression: undefined })
            updateExtensionProperty('conditionGroupConfig', null)
            modeling.updateProperties(toRaw(source), { default: toRaw(props.element).businessObject })
          }
        } else if (conditionForm.value.type === 'expression') {
          if (conditionParseWarning.value) {
            ElMessage.info('原条件表达式已保留；转换为条件组后才能进行可视化编辑')
          } else {
            updateCondition()
            const modeling = getModeling()
            const moddle = getModdle()
            const source = getSourceElement()
            const expression = conditionForm.value.expression
            if (modeling && source && toRaw(source.businessObject)?.default === toRaw(props.element).businessObject) {
              modeling.updateProperties(toRaw(source), { default: undefined })
            }
            if (modeling && moddle && expression) {
              const condition = moddle.create('bpmn:FormalExpression', { body: expression })
              modeling.updateProperties(toRaw(props.element), { conditionExpression: condition })
              updateExtensionProperty('conditionGroupConfig', serializeFlowConditionConfig(conditionRoot.value))
            } else if (modeling) {
              modeling.updateProperties(toRaw(props.element), { conditionExpression: undefined })
              updateExtensionProperty('conditionGroupConfig', null)
            }
          }
        } else {
          // 无条件：清除条件表达式和默认流设置
          const modeling = getModeling()
          const source = getSourceElement()
          if (modeling) {
            modeling.updateProperties(toRaw(props.element), { conditionExpression: undefined })
            updateExtensionProperty('conditionGroupConfig', null)
            if (source && toRaw(source.businessObject)?.default === toRaw(props.element).businessObject) {
              modeling.updateProperties(toRaw(source), { default: undefined })
            }
          }
        }
        break
      case 'actions':
        // 流程动作自动保存，无需额外操作
        break
      case 'form':
        updateNodeFormBind()
        break
      case 'approval':
        updateExtensionProperty('approvalConfig', JSON.stringify(approvalForm.value))
        break
      case 'cc': {
        if (ccForm.value.enabled && ccForm.value.timings.length === 0) {
          ElMessage.warning('请至少选择一个知会触发时机')
          return
        }
        if (ccForm.value.enabled && ccForm.value.recipientRules.length === 0) {
          ElMessage.warning('请至少配置一个知会收件人规则')
          return
        }
        let recipientRules
        try {
          recipientRules = normalizeCcRulesForSave()
        } catch (error) {
          ElMessage.warning(error.message)
          return
        }
        const config = {
          ...ccForm.value,
          recipientRules,
          summary: ccForm.value.summary || ccNaturalSummary.value
        }
        updateExtensionProperty('ccConfig', JSON.stringify(config))
        break
      }
      case 'advanced':
        updateAsync()
        updateSkipExpression()
        updateSkipNode()
        if (isUserTask.value && !updateSlaConfig()) return false
        break
      default:
        console.warn('未知的配置分区:', section)
        ElMessage.warning('当前页面无需保存')
        return false
    }
    return true
  } catch (error) {
    console.error('保存失败:', error)
    ElMessage.error('保存失败: ' + (error.message || '未知错误'))
    return false
  }
}

async function applyNodeConfiguration() {
  for (const section of getConfigurationSections()) {
    if (!applyConfigurationSection(section)) return
  }
  if (isSequenceFlow.value) {
    statusForm.value.conditionExpression = conditionForm.value.type === 'expression'
      ? getFullExpression()
      : ''
    if (!(await saveStatusConfig())) return
  }

  // 节点配置仅写入 bpmn-js 内存模型，尚未落库；落库由顶部“保存草稿”统一完成。
  ElMessage.success('已应用到画布，请点击「保存草稿」完成保存')
  emit('save')
}

// ========== 连线状态配置相关方法 ==========

/**
 * 加载连线状态配置
 */
async function loadStatusConfig(bo) {
  // 重置表单
  statusForm.value = {
    sourceNodeId: '',
    sourceNodeName: '',
    targetNodeId: '',
    targetNodeName: '',
    entityStatusCode: '',
    conditionExpression: '',
    description: ''
  }
  
  // 获取源节点和目标节点信息
  const sourceRef = bo.sourceRef
  const targetRef = bo.targetRef
  
  statusForm.value.sourceNodeId = sourceRef?.id || ''
  statusForm.value.sourceNodeName = sourceRef?.name || sourceRef?.id || ''
  statusForm.value.targetNodeId = targetRef?.id || ''
  statusForm.value.targetNodeName = targetRef?.name || targetRef?.id || ''
  statusForm.value.conditionExpression = bo.conditionExpression?.body || ''
  
  // 从扩展属性中读取状态配置
  const extProps = getExtensionProperties(bo)
  statusForm.value.entityStatusCode = extProps['entityStatusCode'] || ''
  statusForm.value.description = extProps['statusDescription'] || ''
  
  console.log('加载连线状态配置:', bo.id, '扩展属性:', extProps, '状态码:', statusForm.value.entityStatusCode)
  
  // 如果扩展属性为空，尝试从后端 API 加载（兼容旧数据或发布后的数据）
  if (!statusForm.value.entityStatusCode && props.processId && sourceRef?.id && targetRef?.id) {
    try {
      const backendMappings = await getStatusMappings(props.processId)
      const matching = backendMappings?.find(
        m => m.sourceNodeId === sourceRef.id && m.targetNodeId === targetRef.id
      )
      if (matching) {
        statusForm.value.entityStatusCode = matching.entityStatusCode || ''
        statusForm.value.description = matching.description || ''
        console.log('从后端加载到状态映射:', matching)
      }
    } catch (e) {
      console.warn('从后端加载状态映射失败:', e)
    }
  }
  
  // 加载实体预定义的状态列表（如果 boundEntity 已加载）
  if (boundEntity.value?.entityCode) {
    await loadEntityStatusList()
  }
}

/**
 * 加载实体预定义的状态列表
 */
/**
 * 从指定节点向上追溯，找到最近的用户任务节点（用于网关后的连线）
 * 如果源节点本身就是 UserTask 直接返回；如果是网关则沿唯一的 incoming 继续向上找
 */
function findUpstreamUserTaskBo(nodeBo, visited = new Set()) {
  if (!nodeBo || visited.has(nodeBo.id)) return null
  visited.add(nodeBo.id)

  if (nodeBo.$type === 'bpmn:UserTask') return nodeBo

  const isGateway = nodeBo.$type === 'bpmn:ExclusiveGateway' ||
                    nodeBo.$type === 'bpmn:ParallelGateway' ||
                    nodeBo.$type === 'bpmn:InclusiveGateway'
  if (!isGateway) return null

  const incoming = nodeBo.incoming || []
  // 优先找直接连进来的 UserTask
  for (const seqFlow of incoming) {
    const source = seqFlow.sourceRef
    if (source?.$type === 'bpmn:UserTask') return source
  }
  // 否则递归向上追溯
  for (const seqFlow of incoming) {
    const found = findUpstreamUserTaskBo(seqFlow.sourceRef, visited)
    if (found) return found
  }
  return null
}

/**
 * 加载审批结果选项（用于连线条件中 approved 属性的下拉选择）
 * 只聚合当前连线上游最近用户任务节点配置的审批选项，避免把流程中其他节点的选项混进来
 */
function loadSourceNodeApprovalOptions(bo) {
  sourceNodeApprovalOptions.value = []
  if (!bo) return

  const sourceBo = findUpstreamUserTaskBo(bo.sourceRef)
  if (!sourceBo) {
    console.log('未找到连线上游的用户任务节点，无需加载审批选项:', bo.id)
    return
  }

  const optionMap = new Map()
  const extProps = getExtensionProperties(sourceBo)
  const approvalConfigStr = extProps['approvalConfig']
  if (approvalConfigStr) {
    try {
      const approvalConfig = JSON.parse(approvalConfigStr)
      if (approvalConfig.options && Array.isArray(approvalConfig.options)) {
        approvalConfig.options.forEach(opt => {
          const value = String(opt.value)
          if (!optionMap.has(value)) {
            optionMap.set(value, opt.label || opt.value)
          }
        })
      }
    } catch (e) {
      console.warn('解析源节点审批配置失败:', e)
    }
  }

  sourceNodeApprovalOptions.value = Array.from(optionMap.entries()).map(([value, label]) => ({
    label,
    value
  }))

  console.log('加载审批结果选项:', bo.id, '源节点:', sourceBo.id, '选项:', sourceNodeApprovalOptions.value)
}

async function loadEntityStatusList() {
  // 从流程配置中获取实体编码
  const entityCode = boundEntity.value?.entityCode
  if (!entityCode) {
    console.warn('流程未绑定实体，无法加载状态列表')
    return
  }
  
  try {
    entityStatusList.value = await getEntityStatusList(entityCode) || []
    console.log('加载实体状态列表:', entityStatusList.value)
  } catch (error) {
    console.error('加载实体状态列表失败:', error)
    entityStatusList.value = []
  }
}

// 监听 boundEntity 变化，当流程绑定实体后加载状态列表和实体字段
watch(() => boundEntity.value, async (newVal) => {
  if (newVal?.entityCode && isSequenceFlow.value) {
    console.log('流程已绑定实体，加载状态列表和实体字段:', newVal.entityCode)
    await loadEntityStatusList()
    await loadEntityFields()
  }
}, { immediate: true })

/**
 * 保存状态配置
 */
async function saveStatusConfig() {
  try {
    // 获取选中的状态详情
    const selectedStatus = entityStatusList.value.find(s => s.statusCode === statusForm.value.entityStatusCode)
    
    // 保存到 BPMN 扩展属性
    updateExtensionProperty('entityStatusCode', statusForm.value.entityStatusCode)
    updateExtensionProperty('entityStatusName', selectedStatus?.statusName || '')
    updateExtensionProperty('statusCategory', selectedStatus?.statusCategory || '')
    updateExtensionProperty('statusDescription', statusForm.value.description)
    
    // 同时保存到后端数据库。先合并已有映射，避免单条连线保存覆盖其他连线。
    if (props.processId && boundEntity.value?.entityCode) {
      const currentFlowId = String(props.element?.id || '')
      const existingMappings = await getStatusMappings(props.processId) || []
      const mappings = existingMappings.filter(mapping =>
        String(mapping.sequenceFlowId || '') !== currentFlowId)
      if (statusForm.value.entityStatusCode) {
        mappings.push({
          sequenceFlowId: props.element?.id,
          sourceNodeId: statusForm.value.sourceNodeId,
          sourceNodeName: statusForm.value.sourceNodeName,
          targetNodeId: statusForm.value.targetNodeId,
          targetNodeName: statusForm.value.targetNodeName,
          entityStatusCode: statusForm.value.entityStatusCode,
          description: statusForm.value.description
        })
      }
      if (mappings.length) {
        await saveStatusMappings(props.processId, {
          processKey: '', // 后端会自动补充
          entityCode: boundEntity.value.entityCode,
          mappings
        })
      } else {
        await deleteStatusMappings(props.processId)
      }
    }
    
    emit('update-status-mapping', {
      elementId: props.element?.id,
      sourceNodeId: statusForm.value.sourceNodeId,
      sourceNodeName: statusForm.value.sourceNodeName,
      targetNodeId: statusForm.value.targetNodeId,
      targetNodeName: statusForm.value.targetNodeName,
      entityStatusCode: statusForm.value.entityStatusCode,
      entityStatusName: selectedStatus?.statusName || '',
      statusCategory: selectedStatus?.statusCategory || '',
      conditionExpression: statusForm.value.conditionExpression,
      description: statusForm.value.description
    })
    return true
  } catch (error) {
    console.error('保存状态配置失败:', error)
    ElMessage.error('实体状态保存失败: ' + (error.message || '未知错误'))
    return false
  }
}
</script>

<style scoped>
.node-config-panel { height: 100%; min-height: 0; display: flex; flex-direction: column; overflow: hidden; }
.no-selection { flex: 1; display: flex; align-items: center; justify-content: center; }
.config-tabs {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.config-tab-nav {
  display: flex;
  flex-shrink: 0;
  overflow-x: auto;
  padding: 0 12px;
  border-bottom: 1px solid #dcdfe6;
  background: #fff;
  scrollbar-width: thin;
}

.config-tab-button {
  position: relative;
  flex: 0 0 auto;
  min-width: 72px;
  height: 44px;
  padding: 0 14px;
  border: 0;
  background: transparent;
  color: #303133;
  cursor: pointer;
  font: inherit;
}

.config-tab-button:hover {
  color: #409eff;
}

.config-tab-button.active {
  color: #409eff;
  font-weight: 600;
}

.config-tab-button.active::after {
  position: absolute;
  right: 10px;
  bottom: 0;
  left: 10px;
  height: 2px;
  background: #409eff;
  content: '';
}

.config-tab-button:focus-visible {
  outline: 2px solid #409eff;
  outline-offset: -2px;
}

.config-tab-content {
  flex: 1;
  min-height: 0;
  height: auto;
  padding: 15px;
  overflow-x: hidden;
  overflow-y: auto;
  overscroll-behavior: contain;
  scrollbar-gutter: stable;
}

.config-section + .config-section {
  margin-top: 12px;
}

.config-section--actions {
  min-height: 100%;
}
.form-tip { font-size: 12px; color: #909399; margin-top: 5px; }
:deep(.el-divider__text) { font-size: 12px; color: #909399; }
.unit { margin-left: 8px; color: #606266; }
.code-input :deep(textarea) { font-family: monospace; }
.cc-extra-params-editor {
  display: grid;
  grid-template-columns: 100px minmax(0, 1fr);
  align-items: start;
  gap: 8px;
  margin-top: 8px;
}

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
.action-params-list { display: flex; flex-direction: column; gap: 8px; width: 100%; }
.action-param-row { width: 100%; }
.action-param-actions { display: flex; justify-content: flex-end; }

/* 表单选择相关样式 */
.form-option { display: flex; align-items: center; gap: 8px; }
.form-name { font-weight: 500; }
.form-key { color: #909399; font-size: 12px; }


.preview-field { display: flex; align-items: center; gap: 8px; padding: 6px 8px; background-color: #fff; border-radius: 3px; border: 1px solid #e4e7ed; }
.preview-field.required { border-left: 3px solid #f56c6c; }
.preview-field.readonly { border-left: 3px solid #e6a23c; }
.field-label { font-weight: 500; min-width: 80px; }
.field-type { color: #909399; font-size: 12px; }

/* Tab 页脚保存按钮 */
.tab-footer {
  display: flex;
  flex-shrink: 0;
  justify-content: center;
  padding: 12px 15px;
  border-top: 1px solid #e4e7ed;
  background: #fff;
  box-shadow: 0 -4px 12px rgba(31, 45, 61, 0.06);
}

/* 条件配置样式 */
.condition-variables {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.variable-tag {
  cursor: pointer;
  transition: all 0.2s;
}

.variable-tag:hover {
  transform: translateY(-1px);
  box-shadow: 0 2px 4px rgba(0,0,0,0.1);
}

.variable-tag .var-desc {
  font-size: 11px;
  color: #909399;
  margin-left: 4px;
  font-weight: normal;
}

.operator-ref {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.op-group {
  display: flex;
  align-items: center;
  gap: 8px;
}

.op-group .el-tag {
  min-width: 36px;
  text-align: center;
  font-family: monospace;
}

.op-desc {
  font-size: 12px;
  color: #909399;
  margin-left: 8px;
}

.default-flow-tip {
  margin-top: 8px;
  font-size: 13px;
  line-height: 1.8;
}

.default-flow-tip p {
  margin: 0;
}

.condition-group-editor {
  margin-bottom: 15px;
}

.condition-group-tip,
.condition-parse-warning {
  margin-bottom: 12px;
}

.expression-preview {
  margin-top: 15px;
}

.expression-preview :deep(.el-input__inner),
.expression-preview :deep(.el-textarea__inner) {
  font-family: monospace;
  color: #409eff;
}

.hint-icon {
  margin-left: 4px;
  color: #909399;
  cursor: help;
  vertical-align: middle;
}

.script-toolbar {
  margin-bottom: 6px;
  display: flex;
  gap: 8px;
}

.script-test-result {
  margin-top: 8px;
}

.script-test-result .test-result-content {
  margin-top: 6px;
}

.script-test-result .result-item {
  margin-bottom: 6px;
  display: flex;
  align-items: flex-start;
  flex-wrap: wrap;
  gap: 4px;
}

.script-test-result .result-label {
  font-size: 12px;
  color: #606266;
  min-width: 70px;
}

/* 审批配置样式 */
.approval-options-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.approval-option-item {
  background-color: #f5f7fa;
  border-radius: 4px;
  padding: 10px;
  border: 1px solid #e4e7ed;
}
.approval-option-actions {
  display: flex;
  align-items: center;
  justify-content: flex-start;
  gap: 2px;
  padding: 2px 6px;
  background-color: #f0f2f5;
  border-radius: 4px;
}

.cc-rule-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  background: #f8f9fb;
}

.cc-rule-block {
  margin-bottom: 10px;
}

.cc-extra-params {
  margin-top: 6px;
}

.script-test-result .result-vars {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.script-test-result .var-tag {
  font-family: monospace;
}

.script-test-result .test-error {
  font-size: 12px;
  color: #f56c6c;
  margin-top: 4px;
  word-break: break-all;
}
</style>
