package com.workflow.dbmigrator;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** 执行实际 V136，验证唯一键、外键、种子目录及重复 migrate/validate。 */
class GroupMemberTagsMigrationTest {
    @TempDir Path dir;
    @Test void migratesExistingMembershipAndRegistersResolverOnlyOnce() throws Exception {
        String url="jdbc:h2:mem:group_migration_"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        try(var connection=DriverManager.getConnection(url,"sa","");var sql=connection.createStatement()) {
            sql.execute("CREATE TABLE sys_group(id VARCHAR(64) PRIMARY KEY)");
            sql.execute("CREATE TABLE sys_user_group(id VARCHAR(64) PRIMARY KEY)");
            sql.execute("CREATE TABLE process_person_resolver_definition(id VARCHAR(64) PRIMARY KEY,resolver_code VARCHAR(100),display_name VARCHAR(200),description VARCHAR(1000),bean_name VARCHAR(200),implementation_version INT,contract_version INT,supported_usages_document TEXT,extra_param_schema_document TEXT,dynamic_extra_params INT,enabled INT,revision INT,create_time TIMESTAMP,update_time TIMESTAMP,deleted INT)");
            sql.execute("INSERT INTO sys_group VALUES('g')");sql.execute("INSERT INTO sys_user_group VALUES('m')");
            Path file=dir.resolve("V136__group_member_tags.sql");
            try(var stream=getClass().getResourceAsStream("/db/migration/"+file.getFileName())) { assertNotNull(stream); Files.copy(stream,file); }
            var flyway=Flyway.configure().dataSource(url,"sa","").locations("filesystem:"+dir).baselineOnMigrate(true).baselineVersion("135").load();
            assertEquals(1,flyway.migrate().migrationsExecuted);flyway.validate();assertEquals(0,flyway.migrate().migrationsExecuted);
            sql.execute("INSERT INTO sys_group_tag(id,group_id,tag_name) VALUES('t','g','安全')");
            sql.execute("INSERT INTO sys_user_group_tag VALUES('m','t')");
            assertThrows(SQLException.class,()->sql.execute("INSERT INTO sys_user_group_tag VALUES('m','t')"));
            assertThrows(SQLException.class,()->sql.execute("INSERT INTO sys_group_tag(id,group_id,tag_name) VALUES('t2','g','安全')"));
            assertThrows(SQLException.class,()->sql.execute("INSERT INTO sys_user_group_tag VALUES('missing','t')"));
            assertThrows(SQLException.class,()->sql.execute("INSERT INTO sys_user_group_tag VALUES('m','missing')"));
            try(var rows=sql.executeQuery("SELECT enabled,bean_name FROM process_person_resolver_definition WHERE resolver_code='groupMembersByTag'")) { assertTrue(rows.next());assertEquals(1,rows.getInt(1));assertEquals("groupMembersByTagPersonResolver",rows.getString(2));assertFalse(rows.next()); }
        }
    }
    @Test void allSevenInitializersIncludeEquivalentColumnsConstraintsAndResolverSeed() throws Exception {
        for(String dialect:List.of("mysql","pg","oracle","dameng","kingbase","obmysql","oboracle")) {
            String text=Files.readString(Path.of("../../docs/sql/"+dialect+".sql")).replace("`","").replace("\"","").toLowerCase(Locale.ROOT);
            boolean oracle=List.of("oracle","dameng","oboracle").contains(dialect);
            String type=oracle?"varchar2(64 char)":"varchar(64)";
            assertTrue(text.contains("user_group_id "+type+" not null"),dialect);
            assertTrue(text.contains("tag_id "+type+" not null"),dialect);
            for(String expected:List.of("uk_group_tag_name unique (group_id, tag_name)","primary key (user_group_id, tag_id)","foreign key (user_group_id) references sys_user_group(id)","foreign key (tag_id) references sys_group_tag(id)","idx_member_tag_lookup","'groupmembersbytag'","'person_resolver_group_tags_001'")) assertTrue(text.contains(expected),dialect+": "+expected);
            assertTrue(text.contains("供流程配置稳定引用"),dialect);
            assertTrue(text.contains("离组时清理绑定"),dialect);
        }
    }
}
