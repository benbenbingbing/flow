package com.workflow.entity.excel.migration;

import com.workflow.contracts.migration.port.EntityExcelMigrationPort;
import com.workflow.entity.excel.model.ExcelSchemeDocument;
import java.util.Map;
import java.util.Set;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 解决迁移时“页面按钮引用方案，方案引用已发布写入表单”的发布环。
 * 只用于管理员已预检的迁移事务；普通配置发布和所有运行请求不能借此跳过线上配置检查。
 * 关闭后必须通过正常配置服务发布方案并复核引用，失败由外层迁移事务整体回滚。
 */
public final class EntityExcelMigrationScope implements EntityExcelMigrationPort.PublicationScope {
    private static final ThreadLocal<Map<String, Plan>> CURRENT = new ThreadLocal<>();
    public record Scheme(String status, ExcelSchemeDocument config) { }
    public record Plan(Map<String, Scheme> schemes, boolean includesDefaults, String importDefault, String exportDefault) { }

    private EntityExcelMigrationScope(Map<String, Plan> plans) {
        if (!TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("Excel 迁移发布作用域必须位于实际事务中");
        if (CURRENT.get() != null) throw new IllegalStateException("Excel 迁移发布作用域不能嵌套");
        CURRENT.set(Map.copyOf(plans));
    }

    static EntityExcelMigrationScope open(Map<String, Plan> plans) { return new EntityExcelMigrationScope(plans); }

    /** 未包含在本批计划中的引用返回 false，调用方继续正常线上解析，不能扩大迁移豁免范围。 */
    public static boolean validateIfActive(String entityCode, String operation, String mode, String schemeCode) {
        Map<String, Plan> plans = CURRENT.get();
        if (plans == null || !plans.containsKey(entityCode)) return false;
        if (!TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("Excel 迁移事务已结束");
        Plan plan = plans.get(entityCode);
        String selected = schemeCode;
        if ("DEFAULT".equals(mode)) {
            if (schemeCode != null && !schemeCode.isBlank()) throw new IllegalArgumentException("DEFAULT 引用不能包含方案编码");
            if (!plan.includesDefaults()) return false;
            selected = "EXPORT".equals(operation) ? plan.exportDefault() : plan.importDefault();
            if (selected == null || selected.isBlank()) throw new IllegalArgumentException("迁移包未配置默认 Excel 方案: " + operation);
        } else if (!"EXPLICIT".equals(mode)) throw new IllegalArgumentException("Excel 方案引用模式不合法");
        if (selected == null || !plan.schemes().containsKey(selected)) return false;
        requireSupported(plan.schemes().get(selected), operation);
        return true;
    }

    /** 仅迁移发布兼容检查可读取最终计划；未覆盖同码方案返回 null，仍需验证原已发布方案。 */
    public static Scheme plannedSchemeIfActive(String entityCode, String schemeCode) {
        Map<String, Plan> plans = CURRENT.get();
        if (plans == null || !plans.containsKey(entityCode)) return null;
        if (!TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("Excel 迁移事务已结束");
        return plans.get(entityCode).schemes().get(schemeCode);
    }

    /** 所有新增最终方案也必须用本次真正发布的字段再次验证，不能仅依赖早期预检。 */
    public static Map<String, Scheme> plannedSchemesIfActive(String entityCode) {
        Map<String, Plan> plans = CURRENT.get();
        if (plans == null || !plans.containsKey(entityCode)) return Map.of();
        if (!TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("Excel 迁移事务已结束");
        return plans.get(entityCode).schemes();
    }

    /** 最终发布阶段必须恢复正常解析，不能在同一延期作用域中自证引用有效。 */
    static void requireClosed() {
        if (CURRENT.get() != null) throw new IllegalStateException("必须先关闭 Excel 迁移引用作用域再发布方案");
    }

    static void requireSupported(Scheme scheme, String operation) {
        if (operation == null || !Set.of("IMPORT", "EXPORT", "TEMPLATE").contains(operation))
            throw new IllegalArgumentException("Excel 操作不合法");
        ExcelSchemeDocument doc = scheme.config();
        boolean enabled = "EXPORT".equals(operation) ? doc.exportConfig() != null && doc.exportConfig().enabled()
                : doc.importConfig() != null && doc.importConfig().enabled();
        if (!"ACTIVE".equals(scheme.status()) || !enabled)
            throw new IllegalArgumentException("迁移 Excel 方案已停用或不支持操作: " + doc.schemeCode() + "/" + operation);
    }

    @Override public void close() { CURRENT.remove(); }
}
