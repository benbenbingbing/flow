package com.workflow.database;

import com.workflow.core.database.schema.JdbcSchemaMetadata;
import com.workflow.integration.database.schema.dialect.OceanBaseOracleSchemaDdlDialect;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import javax.sql.DataSource;
import java.sql.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 回归 OB 驱动未实现 getSchema 且登录名含租户路由时的元数据隔离，禁止退回跨 schema 查询。 */
class OceanBaseOracleSchemaMetadataTest {
    @Test
    void oracleJdbcTemplatePreservesBootSettingsAndExceptionTranslation() {
        var properties = new org.springframework.boot.autoconfigure.jdbc.JdbcProperties();
        var settings = properties.getTemplate();
        settings.setFetchSize(25);
        settings.setMaxRows(100);
        settings.setQueryTimeout(java.time.Duration.ofSeconds(19));
        settings.setIgnoreWarnings(false);
        settings.setSkipResultsProcessing(true);
        settings.setSkipUndeclaredResults(true);
        settings.setResultsMapCaseInsensitive(true);
        var translator = mock(org.springframework.jdbc.support.SQLExceptionTranslator.class);
        var configuration = new com.workflow.config.database.DatabaseConfiguration();
        var jdbc = configuration.jdbcTemplate(mock(DataSource.class), new OceanBaseOracleSchemaDdlDialect(),
                properties, translator);
        assertInstanceOf(com.workflow.core.database.jdbc.OceanBaseOracleJdbcTemplate.class, jdbc);
        assertEquals(25, jdbc.getFetchSize());
        assertEquals(100, jdbc.getMaxRows());
        assertEquals(19, jdbc.getQueryTimeout());
        assertFalse(jdbc.isIgnoreWarnings());
        assertTrue(jdbc.isSkipResultsProcessing());
        assertTrue(jdbc.isSkipUndeclaredResults());
        assertTrue(jdbc.isResultsMapCaseInsensitive());
        assertSame(translator, jdbc.getExceptionTranslator());
        var mysql = configuration.jdbcTemplate(mock(DataSource.class),
                new com.workflow.integration.database.schema.dialect.MySqlSchemaDdlDialect(), properties, translator);
        assertEquals(JdbcTemplate.class, mysql.getClass());
    }

    @Test
    void applicationPoolEnablesOracleMetadataCommentsByDefault() {
        var properties = new org.springframework.boot.autoconfigure.jdbc.DataSourceProperties();
        properties.setUrl("jdbc:oceanbase://localhost:2883");
        properties.setUsername("ZDW@tenant#cluster");
        properties.setPassword("test-only");
        // 配置构造阶段不连接数据库；用现有驱动类检查真实应用池收到的厂商属性。
        properties.setDriverClassName("com.mysql.cj.jdbc.Driver");
        var configuration = new com.workflow.config.database.DatabaseDataSourceConfiguration();
        try (var pool = configuration.dataSource(properties, new OceanBaseOracleSchemaDdlDialect())) {
            assertEquals("true", pool.getDataSourceProperties().getProperty("remarksReporting"));
            assertEquals("true", pool.getDataSourceProperties().getProperty("useOraclePrepareExecute"));
            assertEquals("true", pool.getDataSourceProperties().getProperty("usePieceData"));
        }
    }

    @Test
    void obtainsCurrentSchemaWithoutCallingUnsupportedDriverMethod() throws Exception {
        DataSource source = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        DatabaseMetaData meta = mock(DatabaseMetaData.class);
        Statement statement = mock(Statement.class);
        ResultSet scope = mock(ResultSet.class);
        ResultSet tables = mock(ResultSet.class);
        when(source.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(meta);
        when(connection.getSchema()).thenThrow(new AbstractMethodError("Unimplemented method: getSchema()"));
        when(meta.getUserName()).thenReturn("ZDW@tenant#cluster");
        when(meta.getSearchStringEscape()).thenReturn("\\");
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery("SELECT SYS_CONTEXT('USERENV','CURRENT_SCHEMA') FROM DUAL")).thenReturn(scope);
        when(scope.next()).thenReturn(true);
        when(scope.getString(1)).thenReturn("ZDW");
        when(meta.getTables(eq(null), eq("ZDW"), eq("ENTITY\\_FORM"), any())).thenReturn(tables);
        when(tables.next()).thenReturn(true, false);
        when(tables.getString("TABLE_NAME")).thenReturn("ENTITY_FORM");

        var metadata = new JdbcSchemaMetadata(new JdbcTemplate(source), new OceanBaseOracleSchemaDdlDialect());
        assertTrue(metadata.tableExists("entity_form"));
        verify(connection, never()).getSchema();
        verify(meta, never()).getUserName();
        verify(meta).getTables(eq(null), eq("ZDW"), eq("ENTITY\\_FORM"), any());
    }

    @Test
    void missingCurrentSchemaFailsBeforeScanningOtherOwners() throws Exception {
        DataSource source = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        ResultSet scope = mock(ResultSet.class);
        when(source.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery(anyString())).thenReturn(scope);
        when(scope.next()).thenReturn(false);
        var metadata = new JdbcSchemaMetadata(new JdbcTemplate(source), new OceanBaseOracleSchemaDdlDialect());

        assertThrows(org.springframework.dao.DataAccessException.class, () -> metadata.tableExists("entity_form"));
        verify(connection, never()).getMetaData();
    }
}
