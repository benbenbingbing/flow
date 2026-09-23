package com.workflow.integration.database.schema;

import com.workflow.integration.database.api.SchemaDdlDialect;
import java.util.Locale;
import java.util.regex.Pattern;

/** 结构发布身份仅允许操作业务表及方言生成的审计对象，不能借队列修改系统表。 */
public final class SchemaStatementScope {
    private SchemaStatementScope() {}
    private static final String IDENTIFIER = "[\"`]([A-Za-z][A-Za-z0-9_]{0,62})[\"`]";
    private static final Pattern TABLE = Pattern.compile("(?is)^(?:CREATE TABLE(?: IF NOT EXISTS)?|ALTER TABLE|DROP TABLE(?: IF EXISTS)?|COMMENT ON (?:TABLE|COLUMN))\\s+" + IDENTIFIER + "(?=\\s|\\.|\\(|;|$)");
    private static final Pattern INDEX = Pattern.compile("(?is)^CREATE (?:UNIQUE )?INDEX(?: IF NOT EXISTS)?\\s+" + IDENTIFIER + "\\s+ON\\s+" + IDENTIFIER + "(?=\\s|\\(|$)");
    private static final Pattern AUDIT_NAME = Pattern.compile("\"([A-Za-z][A-Za-z0-9_]{0,62})\"");

    /** 先执行方言的完整语句校验，再限定目标对象；程序体必须匹配内部固定模板。 */
    public static void requireBusinessStatement(String ddl, SchemaDdlDialect dialect) {
        dialect.validateStatement(ddl);
        String sql = ddl.trim();
        var table = TABLE.matcher(sql);
        if (table.find() && businessTable(table.group(1))) return;
        var index = INDEX.matcher(sql);
        if (index.find() && businessTable(index.group(2))) return;
        if (AuditTimestampDdl.isGenerated(sql)) {
            // 审计函数/触发器的名称由方言从业务表派生；列名仅作列引用，不作为操作对象。
            var names = AUDIT_NAME.matcher(sql);
            if (names.find()) {
                String object = names.group(1).toLowerCase(Locale.ROOT);
                if (object.startsWith("fn_biz_") || object.startsWith("tr_biz_")) {
                    var target = Pattern.compile("(?i)\\bON\\s+" + IDENTIFIER).matcher(sql);
                    var function = Pattern.compile("(?i)\\bEXECUTE PROCEDURE\\s+" + IDENTIFIER).matcher(sql);
                    boolean safeFunction = !function.find() || function.group(1).toLowerCase(Locale.ROOT).startsWith("fn_biz_");
                    if (safeFunction && (!target.find() || businessTable(target.group(1)))) return;
                }
            }
        }
        throw new IllegalArgumentException("Schema DDL is not an allowed biz_ table operation");
    }

    private static boolean businessTable(String name) {
        return name.toLowerCase(Locale.ROOT).matches("biz_[a-z0-9_]{1,58}");
    }
}
