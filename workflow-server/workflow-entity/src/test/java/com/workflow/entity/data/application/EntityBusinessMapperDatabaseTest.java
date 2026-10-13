package com.workflow.entity.data.application;

import com.workflow.core.database.mybatis.ParameterizedSql;
import com.workflow.core.database.mybatis.ParameterizedSqlProvider;
import com.workflow.entity.data.infrastructure.persistence.mapper.EntityMultiValueMapper;
import com.workflow.entity.data.infrastructure.persistence.mapper.EntityRecordTeamMapper;
import com.workflow.entity.data.infrastructure.persistence.mapper.PermissionSqlEvaluationMapper;
import com.workflow.entity.data.infrastructure.persistence.mapper.SystemEntityReadMapper;
import com.workflow.testsupport.EntityMapperTestFactory;
import org.apache.ibatis.type.JdbcType;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/** 真实 MyBatis 执行保证重构后的绑定、大小写列标签、空值以及事务回滚语义。 */
class EntityBusinessMapperDatabaseTest {
    @Test void providerKeepsQuotedQuestionMarksAndRejectsArityMismatch() {
        var statement = ParameterizedSql.of("SELECT '?' AS \"?\", 'it''s?' AS label WHERE ? IS NULL /* ? */ -- ?\n", ParameterizedSql.typed(null, JdbcType.VARCHAR));
        assertThat(ParameterizedSqlProvider.sql(Map.of("statement", statement)))
                .isEqualTo("SELECT '?' AS \"?\", 'it''s?' AS label WHERE #{statement.parameters[0].value,jdbcType=VARCHAR} IS NULL /* ? */ -- ?\n");
        assertThatThrownBy(() -> ParameterizedSqlProvider.sql(Map.of("statement", ParameterizedSql.of("SELECT ?"))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ParameterizedSqlProvider.sql(Map.of("statement", ParameterizedSql.of("SELECT 1", "extra"))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ParameterizedSqlProvider.sql(Map.of("statement", ParameterizedSql.of("SELECT ${unsafe}"))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void mapperPreservesNullAndCaseInsensitiveProjectionAndBindsUserValues() {
        var source = database(); var jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE RECORDS(ID VARCHAR(64) PRIMARY KEY, DISPLAY_NAME VARCHAR(100), CREATED_AT TIMESTAMP)");
        jdbc.update("INSERT INTO RECORDS VALUES ('one',NULL,?)", LocalDateTime.of(2026, 10, 13, 9, 0));
        var mapper = EntityMapperTestFactory.mapper(source, EntityMultiValueMapper.class);
        var rows = mapper.selectReferenceOptions(ParameterizedSql.of("SELECT DISPLAY_NAME, ID FROM RECORDS WHERE ID=?", "one"));
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)).containsKey("display_name").containsEntry("display_name", null).containsEntry("id", "one");
        var reader = EntityMapperTestFactory.mapper(source, SystemEntityReadMapper.class);
        assertThat(reader.countRecords(ParameterizedSql.of("SELECT COUNT(*) FROM RECORDS WHERE ID=?", "' OR 1=1 --"))).isZero();
        assertThat(reader.lookupLabel(ParameterizedSql.of("SELECT DISPLAY_NAME FROM RECORDS WHERE ID=?", "one"))).isNull();
        var permissions = EntityMapperTestFactory.mapper(source, PermissionSqlEvaluationMapper.class);
        assertThat(permissions.evaluateUserPredicate(ParameterizedSql.of("SELECT CASE WHEN ? IS NULL THEN 1 ELSE 0 END", ParameterizedSql.typed(null, JdbcType.VARCHAR)))).isOne();
        assertThat(permissions.evaluateUserPredicate(ParameterizedSql.of("SELECT CASE WHEN ? = 'text' THEN 1 ELSE 0 END", ParameterizedSql.typed("text", JdbcType.CLOB)))).isOne();
    }

    @Test void writesParticipateInTheExistingSpringTransaction() {
        var source = database(); var jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE TEAM(ID VARCHAR(64) PRIMARY KEY, DESCRIPTION VARCHAR(100))");
        var mapper = EntityMapperTestFactory.mapper(source, EntityRecordTeamMapper.class);
        var tx = new TransactionTemplate(new DataSourceTransactionManager(source));
        tx.executeWithoutResult(status -> {
            mapper.insertEvent(ParameterizedSql.of("INSERT INTO TEAM VALUES (?,?)", "one", null));
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM TEAM", Integer.class)).isOne();
            status.setRollbackOnly();
        });
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM TEAM", Integer.class)).isZero();
    }

    private JdbcDataSource database() {
        var source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:mapper_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        return source;
    }
}
