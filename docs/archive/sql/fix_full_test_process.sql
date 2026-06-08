-- 修复全流程测试流程
-- 在MySQL客户端中执行: source /Users/dawei/Documents/ddup/ai/flow/fix_full_test_process.sql

SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

-- 先删除旧数据
DELETE FROM node_config WHERE process_config_id IN (SELECT id FROM process_definition_config WHERE process_key = 'full_test_process');
DELETE FROM process_definition_config WHERE process_key = 'full_test_process';
DELETE FROM flyway_schema_history WHERE version = '8';

-- 插入流程定义
INSERT INTO process_definition_config (process_key, process_name, description, category, version, status, bpmn_xml, created_by, created_at, updated_at)
VALUES (
    'full_test_process',
    '全流程测试',
    '包含所有类型任务节点的完整测试流程',
    '测试流程',
    1,
    'PUBLISHED',
    '<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:flowable="http://flowable.org/bpmn" id="Definitions_1" targetNamespace="http://flowable.org/bpmn20">
  <process id="full_test_process" name="全流程测试" isExecutable="true">
    <startEvent id="StartEvent_1" name="流程发起">
      <outgoing>Flow_1</outgoing>
    </startEvent>
    <userTask id="UserTask_Apply" name="申请人填写" flowable:assignee="${initiator}">
      <incoming>Flow_1</incoming>
      <outgoing>Flow_2</outgoing>
    </userTask>
    <exclusiveGateway id="Gateway_Amount" name="金额判断">
      <incoming>Flow_2</incoming>
      <outgoing>Flow_HighAmount</outgoing>
      <outgoing>Flow_LowAmount</outgoing>
    </exclusiveGateway>
    <parallelGateway id="Gateway_Parallel_Start" name="并行处理">
      <incoming>Flow_HighAmount</incoming>
      <outgoing>Flow_P1</outgoing>
      <outgoing>Flow_P2</outgoing>
      <outgoing>Flow_P3</outgoing>
    </parallelGateway>
    <serviceTask id="ServiceTask_Audit" name="自动审核" flowable:class="com.workflow.delegate.AutoAuditDelegate">
      <incoming>Flow_P1</incoming>
      <outgoing>Flow_P1_End</outgoing>
    </serviceTask>
    <serviceTask id="ServiceTask_Notice" name="发送通知" flowable:class="com.workflow.delegate.SendNoticeDelegate">
      <incoming>Flow_P2</incoming>
      <outgoing>Flow_P2_End</outgoing>
    </serviceTask>
    <scriptTask id="ScriptTask_Calc" name="计算积分" scriptFormat="javascript">
      <incoming>Flow_P3</incoming>
      <outgoing>Flow_P3_End</outgoing>
      <script>execution.setVariable("score", 100);</script>
    </scriptTask>
    <parallelGateway id="Gateway_Parallel_End" name="并行汇聚">
      <incoming>Flow_P1_End</incoming>
      <incoming>Flow_P2_End</incoming>
      <incoming>Flow_P3_End</incoming>
      <outgoing>Flow_3</outgoing>
    </parallelGateway>
    <manualTask id="ManualTask_Record" name="线下记录">
      <incoming>Flow_LowAmount</incoming>
      <outgoing>Flow_4</outgoing>
    </manualTask>
    <inclusiveGateway id="Gateway_Inclusive_Start" name="可选审批">
      <incoming>Flow_3</incoming>
      <incoming>Flow_4</incoming>
      <outgoing>Flow_NeedDept</outgoing>
      <outgoing>Flow_NeedFinance</outgoing>
    </inclusiveGateway>
    <userTask id="UserTask_Dept" name="部门经理审批" flowable:candidateGroups="dept_manager">
      <incoming>Flow_NeedDept</incoming>
      <outgoing>Flow_DeptEnd</outgoing>
    </userTask>
    <userTask id="UserTask_Finance" name="财务审批" flowable:candidateGroups="finance">
      <incoming>Flow_NeedFinance</incoming>
      <outgoing>Flow_FinanceEnd</outgoing>
    </userTask>
    <inclusiveGateway id="Gateway_Inclusive_End" name="审批汇聚">
      <incoming>Flow_DeptEnd</incoming>
      <incoming>Flow_FinanceEnd</incoming>
      <outgoing>Flow_5</outgoing>
    </inclusiveGateway>
    <businessRuleTask id="BusinessRuleTask_Risk" name="风险评估" flowable:decisionTable="risk_decision" flowable:resultVariable="riskLevel">
      <incoming>Flow_5</incoming>
      <outgoing>Flow_6</outgoing>
    </businessRuleTask>
    <receiveTask id="ReceiveTask_Wait" name="等待回调" flowable:async="true">
      <incoming>Flow_6</incoming>
      <outgoing>Flow_7</outgoing>
    </receiveTask>
    <callActivity id="CallActivity_Sub" name="子流程审批" calledElement="sub_approval_process">
      <incoming>Flow_7</incoming>
      <outgoing>Flow_8</outgoing>
    </callActivity>
    <userTask id="UserTask_Final" name="归档确认" flowable:assignee="admin">
      <incoming>Flow_8</incoming>
      <outgoing>Flow_9</outgoing>
    </userTask>
    <endEvent id="EndEvent_1" name="流程结束">
      <incoming>Flow_9</incoming>
    </endEvent>
    <sequenceFlow id="Flow_1" sourceRef="StartEvent_1" targetRef="UserTask_Apply" />
    <sequenceFlow id="Flow_2" sourceRef="UserTask_Apply" targetRef="Gateway_Amount" />
    <sequenceFlow id="Flow_HighAmount" name="金额大于等于10000" sourceRef="Gateway_Amount" targetRef="Gateway_Parallel_Start">
      <conditionExpression xsi:type="tFormalExpression">${amount &gt;= 10000}</conditionExpression>
    </sequenceFlow>
    <sequenceFlow id="Flow_LowAmount" name="金额小于10000" sourceRef="Gateway_Amount" targetRef="ManualTask_Record">
      <conditionExpression xsi:type="tFormalExpression">${amount &lt; 10000}</conditionExpression>
    </sequenceFlow>
    <sequenceFlow id="Flow_P1" sourceRef="Gateway_Parallel_Start" targetRef="ServiceTask_Audit" />
    <sequenceFlow id="Flow_P2" sourceRef="Gateway_Parallel_Start" targetRef="ServiceTask_Notice" />
    <sequenceFlow id="Flow_P3" sourceRef="Gateway_Parallel_Start" targetRef="ScriptTask_Calc" />
    <sequenceFlow id="Flow_P1_End" sourceRef="ServiceTask_Audit" targetRef="Gateway_Parallel_End" />
    <sequenceFlow id="Flow_P2_End" sourceRef="ServiceTask_Notice" targetRef="Gateway_Parallel_End" />
    <sequenceFlow id="Flow_P3_End" sourceRef="ScriptTask_Calc" targetRef="Gateway_Parallel_End" />
    <sequenceFlow id="Flow_3" sourceRef="Gateway_Parallel_End" targetRef="Gateway_Inclusive_Start" />
    <sequenceFlow id="Flow_4" sourceRef="ManualTask_Record" targetRef="Gateway_Inclusive_Start" />
    <sequenceFlow id="Flow_NeedDept" name="需要部门审批" sourceRef="Gateway_Inclusive_Start" targetRef="UserTask_Dept">
      <conditionExpression xsi:type="tFormalExpression">${needDept == true}</conditionExpression>
    </sequenceFlow>
    <sequenceFlow id="Flow_NeedFinance" name="需要财务审批" sourceRef="Gateway_Inclusive_Start" targetRef="UserTask_Finance">
      <conditionExpression xsi:type="tFormalExpression">${needFinance == true}</conditionExpression>
    </sequenceFlow>
    <sequenceFlow id="Flow_DeptEnd" sourceRef="UserTask_Dept" targetRef="Gateway_Inclusive_End" />
    <sequenceFlow id="Flow_FinanceEnd" sourceRef="UserTask_Finance" targetRef="Gateway_Inclusive_End" />
    <sequenceFlow id="Flow_5" sourceRef="Gateway_Inclusive_End" targetRef="BusinessRuleTask_Risk" />
    <sequenceFlow id="Flow_6" sourceRef="BusinessRuleTask_Risk" targetRef="ReceiveTask_Wait" />
    <sequenceFlow id="Flow_7" sourceRef="ReceiveTask_Wait" targetRef="CallActivity_Sub" />
    <sequenceFlow id="Flow_8" sourceRef="CallActivity_Sub" targetRef="UserTask_Final" />
    <sequenceFlow id="Flow_9" sourceRef="UserTask_Final" targetRef="EndEvent_1" />
  </process>
</definitions>',
    'system',
    NOW(),
    NOW()
);

-- 获取流程ID并插入节点配置
SET @process_id = LAST_INSERT_ID();

INSERT INTO node_config (node_id, node_name, node_type, process_config_id, config_json) VALUES
('StartEvent_1', '流程发起', 'START', @process_id, '{}'),
('UserTask_Apply', '申请人填写', 'USER_TASK', @process_id, '{"assigneeType":"initiator"}'),
('Gateway_Amount', '金额判断', 'EXCLUSIVE_GATEWAY', @process_id, '{}'),
('Gateway_Parallel_Start', '并行处理', 'PARALLEL_GATEWAY', @process_id, '{}'),
('ServiceTask_Audit', '自动审核', 'SERVICE_TASK', @process_id, '{"delegate":"AutoAuditDelegate"}'),
('ServiceTask_Notice', '发送通知', 'SERVICE_TASK', @process_id, '{"delegate":"SendNoticeDelegate"}'),
('ScriptTask_Calc', '计算积分', 'SCRIPT_TASK', @process_id, '{"scriptFormat":"javascript"}'),
('Gateway_Parallel_End', '并行汇聚', 'PARALLEL_GATEWAY', @process_id, '{}'),
('ManualTask_Record', '线下记录', 'MANUAL_TASK', @process_id, '{}'),
('Gateway_Inclusive_Start', '可选审批', 'INCLUSIVE_GATEWAY', @process_id, '{}'),
('UserTask_Dept', '部门经理审批', 'USER_TASK', @process_id, '{"assigneeType":"group","assigneeValue":"dept_manager"}'),
('UserTask_Finance', '财务审批', 'USER_TASK', @process_id, '{"assigneeType":"group","assigneeValue":"finance"}'),
('Gateway_Inclusive_End', '审批汇聚', 'INCLUSIVE_GATEWAY', @process_id, '{}'),
('BusinessRuleTask_Risk', '风险评估', 'BUSINESS_RULE_TASK', @process_id, '{"decisionTable":"risk_decision"}'),
('ReceiveTask_Wait', '等待回调', 'RECEIVE_TASK', @process_id, '{"async":true}'),
('CallActivity_Sub', '子流程审批', 'CALL_ACTIVITY', @process_id, '{"calledElement":"sub_approval_process"}'),
('UserTask_Final', '归档确认', 'USER_TASK', @process_id, '{"assigneeType":"user","assigneeValue":"admin"}'),
('EndEvent_1', '流程结束', 'END', @process_id, '{}');

SELECT '全流程测试流程修复完成' as result;
