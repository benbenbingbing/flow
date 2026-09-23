package com.workflow.listener;

import com.workflow.integration.database.api.DatabaseVendor;
import com.workflow.integration.database.api.DatabaseDialects;
import com.workflow.core.database.JdbcWriteAttempt;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.admin.authorization.role.infrastructure.persistence.mapper.SysRoleMapper;
import com.workflow.admin.identity.group.infrastructure.persistence.mapper.SysGroupMapper;
import com.workflow.admin.identity.group.infrastructure.persistence.mapper.SysUserGroupMapper;
import com.workflow.admin.identity.user.infrastructure.persistence.mapper.SysUserMapper;
import com.workflow.admin.identity.user.infrastructure.persistence.mapper.SysUserRoleMapper;
import com.workflow.admin.identity.user.infrastructure.persistence.record.SysUser;
import com.workflow.admin.organization.infrastructure.persistence.mapper.SysOrganizationMapper;
import com.workflow.process.assignment.application.PersonResolverRuntimeService;
import com.workflow.process.audit.infrastructure.persistence.mapper.ProcessOperationLogMapper;
import com.workflow.process.cc.application.ProcessCcConfigService;
import com.workflow.process.cc.application.ProcessCcNotificationPublisher;
import com.workflow.process.cc.application.ProcessCcRuntimeService;
import com.workflow.process.cc.application.ProcessCcService;
import com.workflow.process.cc.application.ProcessCcSnapshotService;
import com.workflow.entity.data.application.EntityDataDynamicService;
import com.workflow.entity.data.api.response.EntityDataDTO;
import com.workflow.process.definition.infrastructure.persistence.mapper.ProcessDefinitionConfigMapper;
import com.workflow.process.definition.infrastructure.persistence.mapper.ProcessVersionHistoryMapper;
import com.workflow.process.definition.infrastructure.persistence.record.ProcessVersionHistory;
import com.workflow.process.cc.infrastructure.flowable.ProcessCcEventListener;
import com.workflow.process.cc.infrastructure.persistence.mapper.ProcessCcRecordMapper;
import com.workflow.process.cc.infrastructure.persistence.record.ProcessCcRecord;
import com.workflow.process.task.application.TaskIdentityAccessService;
import com.workflow.process.task.infrastructure.persistence.mapper.ProcessTaskMapper;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.ProcessEngineConfiguration;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.clearInvocations;

/**
 * 使用真实 Spring Flowable、MyBatis 收件箱和事务代理验证知会链路。
 * 从创建任务到完成最后一个任务均经引擎派发，提交后通过首页使用的 Mapper 查询，
 * 避免仅手工构造 BPMN 引擎事件而遗漏 task-service 事件实现差异。
 */
class ProcessCcFlowableIntegrationTest {
    private ProcessEngine engine;
    private DataSourceTransactionManager transactionManager;
    private ProcessCcService ccService;
    private ProcessCcNotificationPublisher notifications;
    private ProcessVersionHistoryMapper versionMapper;
    private ProcessDefinitionConfigMapper configMapper;
    private EntityDataDynamicService entityDataService;

    @BeforeEach
    void setUp() throws Exception {
        var dataSource = new DriverManagerDataSource("jdbc:h2:mem:cc_"
                + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");
        transactionManager = new DataSourceTransactionManager(dataSource);
        var configuration = new SpringProcessEngineConfiguration();
        configuration.setDataSource(dataSource);
        configuration.setTransactionManager(transactionManager);
        configuration.setDatabaseSchemaUpdate(ProcessEngineConfiguration.DB_SCHEMA_UPDATE_TRUE);
        configuration.setHistory("full");
        configuration.setAsyncExecutorActivate(false);
        engine = configuration.buildProcessEngine();
        var jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("""
                CREATE TABLE process_cc_record (
                  id VARCHAR(64) PRIMARY KEY, process_instance_id VARCHAR(64),
                  process_definition_id VARCHAR(255), process_key VARCHAR(128),
                  process_name VARCHAR(255), business_key VARCHAR(255),
                  node_id VARCHAR(128), node_name VARCHAR(255),
                  cc_user_id VARCHAR(64), cc_user_name VARCHAR(255),
                  cc_type VARCHAR(32), cc_timing VARCHAR(32),
                  operator_id VARCHAR(64), operator_name VARCHAR(255), comment VARCHAR(1000),
                  source_task_id VARCHAR(128), source_type VARCHAR(32),
                  recipient_rule_snapshot CLOB, unique_key VARCHAR(512) UNIQUE,
                  read_status VARCHAR(16), read_time TIMESTAMP,
                  create_time TIMESTAMP, update_time TIMESTAMP, deleted INT
                )
                """);
        // 执行实际新增迁移，确保实体映射和新旧数据库升级后的字段一致。
        try (var migration = getClass().getResourceAsStream("/db/migration/V092__process_cc_name_snapshot.sql")) {
            assertNotNull(migration);
            jdbc.execute(new String(migration.readAllBytes(), StandardCharsets.UTF_8));
        }
        var mybatis = new MybatisConfiguration();
        mybatis.setDatabaseId("MYSQL");
        mybatis.setMapUnderscoreToCamelCase(true);
        mybatis.addMapper(ProcessCcRecordMapper.class);
        var factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setConfiguration(mybatis);
        var mapper = new SqlSessionTemplate(factory.getObject()).getMapper(ProcessCcRecordMapper.class);
        versionMapper = mock(ProcessVersionHistoryMapper.class);
        configMapper = mock(ProcessDefinitionConfigMapper.class);
        entityDataService = mock(EntityDataDynamicService.class);
        var snapshots = new ProcessCcSnapshotService(engine.getRepositoryService(), engine.getRuntimeService(),
                engine.getHistoryService(), versionMapper, configMapper, entityDataService);
        ccService = transactional(new ProcessCcService(mapper, snapshots, new JdbcWriteAttempt(jdbc, DatabaseDialects.insert(DatabaseVendor.MYSQL))));
        notifications = mock(ProcessCcNotificationPublisher.class);
        SysUserMapper users = mock(SysUserMapper.class);
        for (String username : List.of("admin", "observer")) {
            SysUser user = new SysUser();
            user.setId(username + "-id");
            user.setUsername(username);
            user.setStatus(SysUser.Status.ENABLED.getValue());
            user.setDeleted(0);
            when(users.selectByUsername(username)).thenReturn(user);
        }
        var configs = new ProcessCcConfigService(engine.getRepositoryService());
        var runtime = transactional(new ProcessCcRuntimeService(
                engine.getTaskService(), mock(ProcessTaskMapper.class), mock(ProcessOperationLogMapper.class),
                ccService, notifications, configs, users, mock(SysRoleMapper.class),
                mock(SysUserRoleMapper.class), mock(SysGroupMapper.class), mock(SysUserGroupMapper.class),
                mock(SysOrganizationMapper.class), new ObjectMapper(), List.of(),
                mock(PersonResolverRuntimeService.class), mock(TaskIdentityAccessService.class)));
        engine.getRuntimeService().addEventListener(new ProcessCcEventListener(runtime, configs,
                engine.getRuntimeService(), engine.getHistoryService(), engine.getRepositoryService()));
    }

    @AfterEach
    void tearDown() {
        if (engine != null) engine.close();
    }

    /** 开启包含办理人后，本人应在发起、节点创建、节点完成及流程结束收到配置的知会。 */
    @Test
    void configuredTaskAndProcessEventsPersistInHomeInbox() {
        deploy(true, "admin", true);
        ProcessInstance instance = start();
        assertEquals(List.of("PROCESS_START", "TASK_CREATE"), timings("admin"));
        assertEquals(2, ccService.countUnreadCc("admin"));

        complete(instance.getId());

        assertEquals(List.of("PROCESS_COMPLETE", "PROCESS_START", "TASK_COMPLETE", "TASK_CREATE"),
                timings("admin"));
        var page = ccService.getUserCcPage("admin", 1, 10, null, null, null, null);
        assertEquals(4, page.getTotal());
        assertEquals(4, ccService.countUnreadCc("admin"));
        page.getRecords().forEach(record -> {
            assertEquals(instance.getId(), record.getProcessInstanceId(), record.getCcTiming());
            assertEquals("UNREAD", record.getReadStatus());
            assertEquals("cc-flow", record.getProcessKey());
            assertEquals("business-1", record.getBusinessKey());
        });
        verify(notifications, times(4)).enqueue(any(), eq(List.of("IN_APP")));
    }

    /** 关闭包含办理人仍按原规则排除本人，不能通过事件修复绕过此配置。 */
    @Test
    void excludesOperatorWhenConfigured() {
        deploy(false, "admin", false);
        complete(start().getId());
        assertEquals(0, ccService.countUnreadCc("admin"));
        verifyNoInteractions(notifications);
    }

    /** 收件人与办理人不同时，无需开启包含办理人也应收到节点创建和完成知会。 */
    @Test
    void notifiesOtherRecipientAtTaskCreationAndCompletion() {
        deploy(false, "observer", false);
        complete(start().getId());
        assertEquals(List.of("TASK_COMPLETE", "TASK_CREATE"), timings("observer"));
    }

    /** 无 BPMN 名称时固化发布名称和标准数据名称；读取、搜索不再访问源表，完成事件也能取历史定位。 */
    @Test
    void newCcStoresNamesAndReadsOnlyInboxSnapshots() {
        deploy(true, "admin", true, "");
        ProcessVersionHistory version = new ProcessVersionHistory();
        version.setProcessName("发布时的流程名称");
        when(versionMapper.findByDeploymentId(anyString())).thenReturn(Optional.of(version));
        EntityDataDTO data = new EntityDataDTO();
        data.setName("采购申请原名称");
        // 自定义字段中同名值不应替代流程数据的标准 name。
        data.setData(Map.of("name", "自定义字段名称"));
        when(entityDataService.findById("purchase", "record-1")).thenReturn(data);
        ProcessInstance instance = engine.getRuntimeService().startProcessInstanceByKey("cc-flow", "business-1",
                Map.of("entityCode", "purchase", "entityDataId", "record-1"));
        var created = ccService.getUserCcPage("admin", 1, 10, null, null, null, null);
        assertEquals(2, created.getTotal());
        created.getRecords().forEach(record -> {
            assertEquals("发布时的流程名称", record.getProcessName());
            assertEquals("采购申请原名称", record.getDataName());
        });

        data.setName("采购申请新名称");
        complete(instance.getId());
        clearInvocations(versionMapper, configMapper, entityDataService);
        var originalNames = ccService.getUserCcPage("admin", 1, 10, "采购申请原名称", null, null, null);
        assertEquals(2, originalNames.getTotal());
        assertEquals(2, originalNames.getRecords().size());
        var newNames = ccService.getUserCcPage("admin", 1, 10, "采购申请新名称", null, null, null);
        assertEquals(2, newNames.getTotal());
        assertEquals(2, newNames.getRecords().size());
        assertEquals(4, ccService.getUserCcPage("admin", 1, 10, "发布时的流程名称", null, null, null).getTotal());
        assertEquals(4, ccService.countUnreadCc("admin"));
        verifyNoInteractions(versionMapper, configMapper, entityDataService);
    }

    /** 人工等直接创建入口同样补齐名称，而不是仅修改自动事件监听器。 */
    @Test
    void directCcCreationAlsoCapturesNames() {
        deploy(false, "admin", false);
        EntityDataDTO data = new EntityDataDTO();
        data.setName("人工知会的数据名称");
        when(entityDataService.findById("purchase", "record-1")).thenReturn(data);
        ProcessInstance instance = engine.getRuntimeService().startProcessInstanceByKey("cc-flow", "business-1",
                Map.of("entityCode", "purchase", "entityDataId", "record-1"));
        ProcessCcRecord record = new ProcessCcRecord();
        record.setProcessInstanceId(instance.getId());
        record.setProcessDefinitionId(instance.getProcessDefinitionId());
        record.setCcUserId("admin");
        record.setCcType("MANUAL");
        ccService.createCcRecord(record);

        var stored = ccService.getUserCcPage("admin", 1, 10, null, null, null, null).getRecords().get(0);
        assertEquals("知会回归流程", stored.getProcessName());
        assertEquals("人工知会的数据名称", stored.getDataName());
    }

    /** 流程发起回滚时，提交后监听器不得留下实际不存在流程的知会。 */
    @Test
    void rolledBackStartDoesNotCreateCc() {
        deploy(true, "admin", true);
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            start();
            status.setRollbackOnly();
        });
        assertEquals(0, ccService.countUnreadCc("admin"));
        verifyNoInteractions(notifications);
    }

    /** 最后一个任务审批回滚时，原有创建知会保留，不能提前生成完成知会。 */
    @Test
    void rolledBackCompletionDoesNotCreateCompletionCc() {
        deploy(true, "admin", false);
        ProcessInstance instance = start();
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            complete(instance.getId());
            status.setRollbackOnly();
        });
        assertEquals(List.of("TASK_CREATE"), timings("admin"));
        assertNotNull(engine.getTaskService().createTaskQuery()
                .processInstanceId(instance.getId()).singleResult());
    }

    private List<String> timings(String username) {
        return ccService.getUserCcPage(username, 1, 10, null, null, null, null).getRecords()
                .stream().map(ProcessCcRecord::getCcTiming).sorted().toList();
    }

    private ProcessInstance start() {
        return engine.getRuntimeService().startProcessInstanceByKey("cc-flow", "business-1",
                Map.of("startUserId", "admin"));
    }

    private void complete(String instanceId) {
        var task = engine.getTaskService().createTaskQuery().processInstanceId(instanceId).singleResult();
        // 携带审批变量使引擎使用 EntityWithVariables 完成事件，覆盖另一种实际事件实现。
        engine.getTaskService().complete(task.getId(), Map.of("approved", true));
    }

    private void deploy(boolean includeOperator, String recipient, boolean processCc) {
        deploy(includeOperator, recipient, processCc, "知会回归流程");
    }

    private void deploy(boolean includeOperator, String recipient, boolean processCc, String processName) {
        String config = """
                <extensionElements><flowable:properties><flowable:property name="ccConfig" value='
                {"enabled":true,"includeOperator":%s,"channels":["IN_APP"],
                 "timings":["TASK_CREATE","TASK_COMPLETE","PROCESS_START","PROCESS_COMPLETE"],
                 "recipientRules":[{"type":"USER","values":["%s"]}]}' />
                </flowable:properties></extensionElements>
                """.formatted(includeOperator, recipient);
        engine.getRepositoryService().createDeployment().addString("cc.bpmn20.xml", """
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                  xmlns:flowable="http://flowable.org/bpmn" targetNamespace="test">
                  <process id="cc-flow" name="%s" isExecutable="true">
                    %s
                    <startEvent id="start" />
                    <sequenceFlow id="to-review" sourceRef="start" targetRef="review" />
                    <userTask id="review" name="知会测试" flowable:assignee="admin">%s</userTask>
                    <sequenceFlow id="to-end" sourceRef="review" targetRef="end" />
                    <endEvent id="end" />
                  </process>
                </definitions>
                """.formatted(processName, processCc ? config : "", config)).deploy();
    }

    /** 使用服务实际声明的事务边界，避免直接调用对象掩盖提交后写库的问题。 */
    @SuppressWarnings("unchecked")
    private <T> T transactional(T target) {
        var interceptor = new TransactionInterceptor();
        interceptor.setTransactionManager(transactionManager);
        interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        var factory = new ProxyFactory(target);
        factory.setProxyTargetClass(true);
        factory.addAdvice(interceptor);
        return (T) factory.getProxy();
    }
}
