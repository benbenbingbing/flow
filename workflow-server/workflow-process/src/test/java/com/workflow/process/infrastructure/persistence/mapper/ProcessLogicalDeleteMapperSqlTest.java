package com.workflow.process.infrastructure.persistence.mapper;

import com.workflow.process.action.infrastructure.persistence.mapper.FlowActionMapper;
import com.workflow.process.definition.infrastructure.persistence.mapper.ProcessDefinitionConfigMapper;
import com.workflow.process.definition.infrastructure.persistence.mapper.ProcessVersionHistoryMapper;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProcessLogicalDeleteMapperSqlTest {

    private static final List<Class<?>> LOGICAL_DELETE_MAPPERS = List.of(
            FlowActionMapper.class,
            ProcessDefinitionConfigMapper.class,
            ProcessVersionHistoryMapper.class);

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
}
