package com.workflow.entity.excel.runtime;

import com.workflow.core.result.PageResult;
import com.workflow.entity.data.api.response.EntityDataDTO;
import com.workflow.entity.data.application.EntityDataDynamicService;
import com.workflow.entity.data.application.SystemEntityReadService;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityField;
import com.workflow.entity.excel.config.EntityExcelReferenceMetadata;
import com.workflow.entity.excel.config.EntityExcelReferenceMetadata.*;
import com.workflow.entity.permission.application.EntityActionCapabilityService;
import com.workflow.entity.permission.application.EntityPermissionAction;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Consumer;

/** 关联字段的有界批量解析；只查当前用户可读数据，缓存仅存活于本次文件处理。 */
@Service
@RequiredArgsConstructor
public class EntityExcelReferenceService {
    private final EntityExcelReferenceMetadata metadata;
    // 动态数据服务依赖列表/Excel 扩展，延迟获取可避免重新引入配置服务的构造器循环。
    private final ObjectProvider<EntityDataDynamicService> dynamic;
    private final ObjectProvider<SystemEntityReadService> system;
    private final ObjectProvider<EntityActionCapabilityService> capabilities;

    /** 为一个文件列固定目标元数据和转换选项；匹配与输出按各自方向独立校验。 */
    public Session compile(EntityField field, Map<String, Object> options, boolean importing) {
        Target target = metadata.resolve(field);
        Settings settings = EntityExcelReferenceMetadata.settings(target, options, importing, !importing);
        return new Session(target, settings);
    }

    public final class Session {
        private final Target target;
        private final Settings settings;
        private final Map<String, Set<String>> matches = new HashMap<>();
        private final Map<String, EntityDataDTO> records = new HashMap<>();
        private Session(Target target, Settings settings) { this.target = target; this.settings = settings; }

        /** 按文件读取批次收集文本，各属性分别作 IN 查询；不能取第一条，否则重名会误关联。 */
        public void prepareImport(Collection<String> cells) {
            matches.clear();
            Set<String> tokens = new LinkedHashSet<>();
            for (String text : cells) {
                try { tokens.addAll(parts(text)); }
                catch (IllegalArgumentException ignored) { /* 格式错误留给单元格读取报告精确行列。 */ }
            }
            tokens.forEach(token -> matches.put(token, new LinkedHashSet<>()));
            for (String field : settings.matchFields()) {
                Property property = target.properties().get(field);
                Map<String, List<String>> originals = new LinkedHashMap<>();
                Map<String, Object> values = new LinkedHashMap<>();
                for (String token : tokens) {
                    try {
                        Object value = typed(token, property.fieldType());
                        String key = text(value);
                        values.put(key, value);
                        originals.computeIfAbsent(key, ignored -> new ArrayList<>()).add(token);
                    } catch (IllegalArgumentException ignored) { /* 例如文本不适合数字属性时，仍可由名称属性匹配。 */ }
                }
                query(property.queryField(), new ArrayList<>(values.values()), row -> {
                    Object value = propertyValue(row, property);
                    if (value == null) return;
                    String key = text(typed(text(value), property.fieldType()));
                    for (String original : originals.getOrDefault(key, List.of())) {
                        // 只需保留两个不同 ID 即能判定歧义，避免常见非唯一属性消耗过量内存。
                        Set<String> ids = matches.get(original);
                        if (ids.size() < 2) ids.add(row.getId());
                    }
                });
            }
        }

        /** 将当前批次预取过的显示值解析为 ID；歧义、缺失和格式错误交由调用方定位到单元格。 */
        public Object read(String value) {
            List<String> parts = parts(value);
            if (parts.isEmpty()) return target.multiple() ? List.of() : null;
            Set<String> ids = new LinkedHashSet<>();
            for (String part : parts) {
                Set<String> found = matches.getOrDefault(part, Set.of());
                if (found.isEmpty()) throw new IllegalArgumentException("关联值不存在或无权访问：" + part);
                if (found.size() != 1) throw new IllegalArgumentException("关联值匹配多条记录，请改用唯一属性：" + part);
                ids.add(found.iterator().next());
            }
            return target.multiple() ? List.copyOf(ids) : ids.iterator().next();
        }

        /** 导出分页与保存后复核均按 ID 批量读取，禁止用 extData 的显示名绕过目标权限。 */
        public void prepareIds(Collection<?> values) {
            records.clear();
            Set<String> ids = new LinkedHashSet<>();
            for (Object value : values) {
                try { ids.addAll(ids(value)); }
                catch (IllegalArgumentException ignored) { /* 单元格/最终行校验负责报告不合法的存储结构。 */ }
            }
            query("id", new ArrayList<>(ids), row -> records.put(row.getId(), row));
        }

        /** 前置处理只能返回规范 ID；再次确认存在和访问权限，不把表单返回值当 Excel 名称重解析。 */
        public Object validateIds(Object value) {
            List<String> ids = ids(value);
            for (String id : ids) if (!records.containsKey(id))
                throw new IllegalArgumentException("关联记录不存在或无权访问");
            return target.multiple() ? ids : ids.isEmpty() ? null : ids.get(0);
        }

        /** 按源记录的关联顺序输出属性，多值自动转义；缺失目标或属性时拒绝生成不完整文件。 */
        public String write(Object value) {
            List<String> ids = ids(value);
            if (ids.isEmpty()) return null;
            List<String> output = new ArrayList<>();
            Property property = target.properties().get(settings.exportField());
            for (String id : ids) {
                EntityDataDTO row = records.get(id);
                if (row == null) throw new IllegalArgumentException("关联记录不存在或无权访问：" + target.entityName());
                Object raw = propertyValue(row, property);
                if (raw == null || text(raw).isBlank()) throw new IllegalArgumentException("关联记录的导出属性为空：" + property.fieldName());
                String display = text(typed(text(raw), property.fieldType()));
                output.add(target.multiple() ? display.replace("\\", "\\\\").replace(settings.separator(), "\\" + settings.separator()) : display);
            }
            return String.join(settings.separator(), output);
        }

        /** 模板使用和解析器一致的属性及分隔规则，避免说明与实际行为不一致。 */
        public String instructions() {
            return "按" + String.join("、", settings.matchFields()) + "精确匹配已有记录，结果必须唯一"
                    + (target.multiple() ? "；多个值用" + ("\n".equals(settings.separator()) ? "换行" : "英文分号")
                    + "分隔，值中的分隔符或反斜杠须在前面加反斜杠" : "");
        }

        private List<String> parts(String text) {
            if (text == null || text.isBlank()) return List.of();
            return target.multiple() ? split(text, settings.separator().charAt(0)) : List.of(text.strip());
        }

        private List<String> ids(Object value) {
            if (value == null || value instanceof String text && text.isBlank()) return List.of();
            if (!target.multiple()) {
                if (!(value instanceof String id)) throw new IllegalArgumentException("单选实体必须为记录 ID");
                return List.of(id);
            }
            if (!(value instanceof Collection<?> list)) throw new IllegalArgumentException("多选实体必须为记录 ID 数组");
            Set<String> ids = new LinkedHashSet<>();
            for (Object item : list) {
                if (!(item instanceof String id) || id.isBlank()) throw new IllegalArgumentException("多选实体包含无效记录 ID");
                ids.add(id);
            }
            return List.copyOf(ids);
        }

        /** 每次查询重新检查目标权限；分页遍历全部匹配结果，超过资源限额明确失败而不误判为唯一。 */
        private void query(String field, List<?> values, Consumer<EntityDataDTO> consumer) {
            if (values.isEmpty()) return;
            if (!target.system()) capabilities.getObject().requireStandardPermission(target.entityCode(), EntityPermissionAction.VIEW);
            for (int offset = 0; offset < values.size(); offset += 100) {
                Map<String, Object> filter = Map.of(field, values.subList(offset, Math.min(values.size(), offset + 100)), field + "_op", "IN");
                long read = 0;
                for (long page = 1; ; page++) {
                    PageResult<EntityDataDTO> result = target.system()
                            ? system.getObject().findPage(target.entityCode(), filter, page, 200)
                            : dynamic.getObject().findPage(target.entityCode(), null, filter, page, 200, "id", "ASC");
                    if (result.getTotal() > 10_000) throw new IllegalArgumentException("关联属性匹配记录过多，请使用编码或其他唯一属性");
                    var rows = result.getRecords();
                    rows.forEach(consumer);
                    read += rows.size();
                    if (read >= result.getTotal() || rows.isEmpty()) break;
                    if (page >= 50) throw new IllegalArgumentException("关联属性匹配记录过多，请使用唯一属性");
                }
            }
        }
    }

    /** 可逆的多值文本协议，支持转义分隔符和反斜杠；空项或悬空转义须报错而非静默丢数据。 */
    static List<String> split(String value, char separator) {
        List<String> parts = new ArrayList<>();
        StringBuilder part = new StringBuilder();
        boolean escaped = false;
        for (char c : value.replace("\r\n", "\n").toCharArray()) {
            if (escaped) {
                if (c != separator && c != '\\') throw new IllegalArgumentException("多选实体转义符只能用于分隔符或反斜杠");
                part.append(c); escaped = false;
            } else if (c == '\\') escaped = true;
            else if (c == separator) { addPart(parts, part); part.setLength(0); }
            else part.append(c);
        }
        if (escaped) throw new IllegalArgumentException("多选实体值末尾存在未完成的转义");
        addPart(parts, part);
        return parts;
    }
    private static void addPart(List<String> parts, StringBuilder part) {
        if (part.toString().isBlank()) throw new IllegalArgumentException("多选实体分隔符之间不能有空项");
        parts.add(part.toString().strip());
    }

    /** 匹配和输出共用基础属性格式，数字消除无意义的小数零；日期使用 ISO 格式以便再次导入。 */
    private static Object typed(String value, EntityField.FieldType type) {
        try {
            return switch (type) {
                case INTEGER -> new BigDecimal(value).intValueExact();
                case LONG -> new BigDecimal(value).longValueExact();
                case DECIMAL -> new BigDecimal(value).stripTrailingZeros();
                case BOOLEAN -> {
                    if ("true".equalsIgnoreCase(value) || "1".equals(value)) yield true;
                    if ("false".equalsIgnoreCase(value) || "0".equals(value)) yield false;
                    throw new IllegalArgumentException();
                }
                case DATE -> LocalDate.parse(value);
                case DATETIME -> LocalDateTime.parse(value.replace(' ', 'T'));
                default -> value;
            };
        } catch (RuntimeException failure) { throw new IllegalArgumentException("关联属性值格式不正确", failure); }
    }
    private static String text(Object value) { return value instanceof BigDecimal number ? number.toPlainString() : value.toString(); }
    private static Object propertyValue(EntityDataDTO row, Property property) {
        String code = property.queryField();
        if (row.getData() != null && row.getData().containsKey(code)) return row.getData().get(code);
        return switch (code) {
            case "id" -> row.getId(); case "code" -> row.getCode(); case "name" -> row.getName(); case "status" -> row.getStatus();
            case "create_time" -> row.getCreateTime(); case "update_time" -> row.getUpdateTime();
            case "create_by" -> row.getCreateBy(); case "update_by" -> row.getUpdateBy();
            case "submitter_id" -> row.getSubmitterId(); case "dept_id" -> row.getDeptId(); case "submit_time" -> row.getSubmitTime();
            default -> null;
        };
    }
}
