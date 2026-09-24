package com.workflow.process.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.workflow.process.action.infrastructure.persistence.mapper.FlowActionMapper;
import com.workflow.process.action.infrastructure.persistence.record.FlowAction;
import com.workflow.process.definition.infrastructure.persistence.mapper.ProcessDefinitionConfigMapper;
import com.workflow.process.definition.infrastructure.persistence.mapper.ProcessVersionHistoryMapper;
import com.workflow.process.definition.infrastructure.persistence.record.ProcessVersionHistory;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import org.mockito.ArgumentCaptor;

class ProcessLogicalDeleteMapperSqlTest {

    private static final List<Class<?>> LOGICAL_DELETE_MAPPERS = List.of(
            ProcessDefinitionConfigMapper.class);

    private static final Pattern ACTIVE_RECORD_PREDICATE = Pattern.compile(
            "(?:\\b[a-z_][a-z0-9_]*\\.)?\\bdeleted\\s*=\\s*0\\b");

    @Test
    void selectQueriesUseIndexFriendlyLogicalDeletePredicate() {
        for (Class<?> mapperType : LOGICAL_DELETE_MAPPERS) {
            int checkedQueries = 0;

            for (Method method : mapperType.getDeclaredMethods()) {
                Select select = method.getAnnotation(Select.class);
                if (select == null) {
                    continue;
                }

                // 绑定更新必须锁定已逻辑删除的定义，防止已发布流程与实体绑定并发冲突。
                if (mapperType == ProcessDefinitionConfigMapper.class
                        && method.getName().equals("selectAnyByIdForBindingUpdate")) {
                    continue;
                }
                checkedQueries++;
                String sql = String.join(" ", select.value())
                        .replaceAll("\\s+", " ")
                        .toLowerCase(Locale.ROOT);
                String queryName = mapperType.getSimpleName() + "." + method.getName();

                assertTrue(
                        ACTIVE_RECORD_PREDICATE.matcher(sql).find(),
                        () -> queryName + " must filter active records with deleted = 0");
                assertFalse(
                        sql.contains("deleted is null"),
                        () -> queryName + " must not use the deleted IS NULL fallback");
            }

            assertTrue(
                    checkedQueries > 0,
                    () -> mapperType.getSimpleName() + " must expose logical-delete queries to check");
        }
    }

    @Test
    void flowActionQueriesExcludeDeletedActions() {
        // 独立单测没有启动 MyBatis Mapper 扫描，先初始化字段缓存供 LambdaQueryWrapper 转 SQL。
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), FlowAction.class);
        FlowActionMapper mapper = mock(FlowActionMapper.class, CALLS_REAL_METHODS);
        doReturn(List.of()).when(mapper).selectList(any());

        mapper.findDraftActionsByProcessConfigId("process-1");
        mapper.findDraftActionsByBinding("process-1", "NODE", "task-1");
        mapper.findPublishedActionsByVersionId("version-1");
        mapper.findPublishedActionsByBinding("version-1", "NODE", "task-1", "TASK_COMPLETE");

        // 动作 Mapper 使用 Wrapper 构造查询，因此检查最终条件和绑定值，而不是查找 @Select 注解。
        ArgumentCaptor<Wrapper> queries = ArgumentCaptor.forClass(Wrapper.class);
        verify(mapper, times(4)).selectList(queries.capture());
        for (Wrapper<?> query : queries.getAllValues()) {
            assertTrue(query.getSqlSegment().toLowerCase(Locale.ROOT).contains("deleted"),
                    "流程动作查询必须包含逻辑删除条件");
            assertTrue(((AbstractWrapper<?, ?, ?>) query).getParamNameValuePairs().containsValue(0),
                    "流程动作查询必须只读取 deleted = 0 的记录");
        }
    }

    @Test
    void latestPublishedProcessVersionExcludesDeletedHistory() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), ProcessVersionHistory.class);
        ProcessVersionHistoryMapper mapper = mock(ProcessVersionHistoryMapper.class, CALLS_REAL_METHODS);
        doReturn(List.of()).when(mapper).selectList(any(Page.class), any());

        mapper.findLatestByProcessKey("flow-key");

        // 历史版本允许保留已删除记录；只有作为新实例入口的最新版本查询必须过滤它们。
        ArgumentCaptor<Wrapper> query = ArgumentCaptor.forClass(Wrapper.class);
        verify(mapper).selectList(any(Page.class), query.capture());
        assertTrue(query.getValue().getSqlSegment().toLowerCase(Locale.ROOT).contains("deleted"));
        assertTrue(((AbstractWrapper<?, ?, ?>) query.getValue()).getParamNameValuePairs().containsValue(0));
    }
}
