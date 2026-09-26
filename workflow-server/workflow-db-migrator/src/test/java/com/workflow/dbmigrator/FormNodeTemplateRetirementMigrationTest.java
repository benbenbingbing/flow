package com.workflow.dbmigrator;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** 在显式指定的临时 MySQL 实例验证删除边界；不会读取开发/生产连接配置。 */
class FormNodeTemplateRetirementMigrationTest {
    @Test
    void removesOnlyFormTemplateBindingsAndPreservesCopiedConfigAndSharedListTemplates() throws Exception {
        String socket = System.getProperty("flow.form-template.mysql-socket");
        assumeTrue(socket != null, "需要显式指定临时 MySQL socket");
        if (!socket.startsWith("/private/tmp/flow-portability-mysql/")) throw new IllegalArgumentException("只允许专用临时 MySQL 实例");
        String database = "form_template_test_" + UUID.randomUUID().toString().replace("-", "");
        try {
            String result = mysql(socket, "CREATE DATABASE `" + database + "`; USE `" + database + "`;\n" + """
                    CREATE TABLE entity_form_node (id VARCHAR(40) PRIMARY KEY, template_id VARCHAR(40),
                      template_version INT, local_overrides_document LONGTEXT, props_document LONGTEXT,
                      legacy_props_document LONGTEXT);
                    CREATE TABLE ui_component_template (id VARCHAR(40) PRIMARY KEY, template_type VARCHAR(40));
                    CREATE TABLE ui_component_template_version (id VARCHAR(40) PRIMARY KEY, template_id VARCHAR(40));
                    CREATE TABLE ui_config_release (snapshot_document LONGTEXT, content_hash VARCHAR(64));
                    INSERT INTO entity_form_node VALUES ('node','field',1,'{}','{"label":"保留","required":true}',
                      '{"inactive":{"template":{"templateId":"field"},"component":"keep"}}');
                    INSERT INTO ui_component_template VALUES ('field','FIELD_GROUP'),('section','FORM_SECTION'),
                      ('sub','SUB_FORM'),('list','LIST_COLUMN_GROUP'),('button','BUTTON_GROUP');
                    INSERT INTO ui_component_template_version SELECT id,id FROM ui_component_template;
                    INSERT INTO ui_config_release VALUES ('{"templateId":"field"}', 'original-hash');
                    """ + migration() + """
                    SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
                      AND table_name='entity_form_node' AND column_name IN ('template_id','template_version','local_overrides_document');
                    SELECT props_document, JSON_EXTRACT(legacy_props_document,'$.inactive.component'),
                      JSON_CONTAINS_PATH(legacy_props_document,'one','$.inactive.template') FROM entity_form_node;
                    SELECT id FROM ui_component_template ORDER BY id;
                    SELECT template_id FROM ui_component_template_version ORDER BY template_id;
                    SELECT snapshot_document,content_hash FROM ui_config_release;
                    """);
            assertEquals("""
                    0
                    {"label":"保留","required":true}\t"keep"\t0
                    button
                    list
                    button
                    list
                    {"templateId":"field"}\toriginal-hash
                    """, result);
        } finally {
            mysql(socket, "DROP DATABASE IF EXISTS `" + database + "`;");
        }
    }

    private String mysql(String socket, String sql) throws Exception {
        ProcessBuilder builder = new ProcessBuilder(System.getProperty("flow.form-template.mysql-bin", "/usr/local/mysql/bin/mysql"),
                "--no-defaults", "--protocol=SOCKET", "--socket=" + socket, "--user=root", "--batch", "--skip-column-names")
                .redirectErrorStream(true);
        builder.environment().remove("MYSQL_PWD");
        Process process = builder.start();
        try (var input = process.getOutputStream()) { input.write(sql.getBytes(StandardCharsets.UTF_8)); }
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, process.waitFor(), output);
        return output;
    }

    private String migration() throws Exception {
        try (var input = Objects.requireNonNull(getClass().getResourceAsStream("/db/migration/V104__retire_form_node_templates.sql"))) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
