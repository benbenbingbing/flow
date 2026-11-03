package com.workflow.admin.identity.extension.application;

import com.workflow.admin.identity.provisioning.application.IdentityAccessService;
import com.workflow.contracts.identity.error.IdentityWriteException;
import com.workflow.contracts.identity.model.*;
import com.workflow.contracts.identity.port.IdentityExtensionQueryPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import com.workflow.admin.identity.provisioning.infrastructure.persistence.mapper.IdentityPersistenceMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;

/** 通用独立表资料读写；Mapper 中全部 SQL 标识符来自启动注册白名单，业务值使用参数绑定。 */
@Service
@RequiredArgsConstructor
public class IdentityExtensionService implements IdentityExtensionQueryPort {
    private final IdentityExtensionRegistry registry;
    private final IdentityAccessService access;
    private final IdentityPersistenceMapper persistence;
    private final ObjectMapper mapper;

    /** 提供当前用户可以查看的字段描述；目标资料的范围在实际读取/保存时重新校验。 */
    public List<IdentityExtensionSchema> schemas(IdentityObjectType target, String actor) {
        return registry.list(target).stream().filter(d -> access.hasPermission(actor, d.annotation().readPermission()))
                .map(d -> new IdentityExtensionSchema(d.annotation().code(), d.annotation().title(), d.annotation().maintenance(),
                        d.annotation().maintenance() == ExtensionMaintenance.LOCAL && access.hasPermission(actor, d.annotation().writePermission()),
                        d.fields().stream().map(f -> new IdentityExtensionFieldView(f.field().getName(), f.definition().label(), f.control(),
                                f.definition().required(), f.definition().maxLength(), List.of(f.definition().options()),
                                stringNumber(f.field().getType()))).toList())).toList();
    }

    /** 详情只返回有权读取的组，缺失资料不生成空行。 */
    public Map<String, IdentityExtensionValue> readAll(IdentityObjectType target, String id) {
        String actor = access.requireObject(target, id);
        Map<String, IdentityExtensionValue> result = new LinkedHashMap<>();
        for (var descriptor : registry.list(target)) {
            if (!access.hasPermission(actor, descriptor.annotation().readPermission())) continue;
            var value = read(descriptor, id);
            if (value != null) {
                // Long 和精确小数通过字符串传给浏览器，避免 JS Number 在编辑其他字段时丢失精度。
                Map<String, Object> data = new LinkedHashMap<>(value.data());
                for (var field : descriptor.fields()) {
                    String name = field.field().getName();
                    if (stringNumber(field.field().getType()) && data.get(name) != null) data.put(name, data.get(name).toString());
                }
                result.put(descriptor.annotation().code(), new IdentityExtensionValue(value.rowVersion(), data));
            }
        }
        return result;
    }

    /** 本地管理只允许 LOCAL 组，必须携带版本；任一组失败由调用方事务整体回滚。 */
    @Transactional(rollbackFor = Exception.class)
    public void saveLocal(IdentityObjectType target, String id, Map<String, IdentityExtensionEdit> changes) {
        if (changes == null || changes.isEmpty()) return;
        String actor = access.requireObject(target, id);
        for (var entry : changes.entrySet()) {
            var descriptor = registry.require(target, entry.getKey());
            access.requirePermission(actor, descriptor.annotation().writePermission());
            if (descriptor.annotation().maintenance() != ExtensionMaintenance.LOCAL) fail("EXTENSION_WRITE_NOT_ALLOWED", "来源资料不能通过页面修改");
            var edit = entry.getValue();
            if (edit == null || edit.data() == null || edit.expectedVersion() == null || edit.expectedVersion() < 0) fail("INVALID_MODEL", "扩展编辑必须携带数据和版本");
            var data = normalize(descriptor, edit.data());
            write(descriptor, id, data, edit.expectedVersion());
        }
    }

    /** 在来源保存事务之前冻结并校验扩展，摘要与实际保存使用同一份规范化数据。 */
    public Map<String, Map<String, Object>> prepareSource(IdentityObjectType target, List<?> values, IdentityAccessService.Writer writer) {
        if (values == null) fail("INVALID_MODEL", "extensions 不能为空，请使用空列表表示不修改扩展");
        Map<String, Map<String, Object>> result = new TreeMap<>();
        for (Object value : values) {
            if (value == null) fail("INVALID_MODEL", "扩展对象不能为空");
            var descriptor = registry.require(target, value.getClass());
            String code = descriptor.annotation().code();
            if (descriptor.annotation().maintenance() != ExtensionMaintenance.SOURCE || !writer.source().getExtensions().contains(code)) {
                fail("EXTENSION_WRITE_NOT_ALLOWED", "来源不允许维护扩展：" + code);
            }
            access.requirePermission(writer.actorId(), descriptor.annotation().writePermission());
            Map<String, Object> fields = new LinkedHashMap<>();
            try { for (var f : descriptor.fields()) fields.put(f.field().getName(), f.field().get(value)); }
            catch (IllegalAccessException e) { throw new IllegalStateException(e); }
            if (result.put(code, normalize(descriptor, fields)) != null) fail("INVALID_MODEL", "同一扩展不能重复提交");
        }
        return result;
    }

    /** 来源写入已受目录锁和 sourceVersion 保护，仍以行版本条件更新以检测其他非法写入。 */
    public void saveSource(IdentityObjectType target, String id, Map<String, Map<String, Object>> values) {
        for (var entry : values.entrySet()) {
            var descriptor = registry.require(target, entry.getKey());
            var previous = read(descriptor, id);
            write(descriptor, id, entry.getValue(), previous == null ? 0 : previous.rowVersion());
        }
    }

    @Override
    public <T extends UserExtension> Optional<T> findUserExtension(String id, Class<T> type) {
        return find(IdentityObjectType.USER, id, type);
    }
    @Override
    public <T extends OrganizationExtension> Optional<T> findOrganizationExtension(String id, Class<T> type) {
        return find(IdentityObjectType.ORGANIZATION, id, type);
    }
    private <T> Optional<T> find(IdentityObjectType target, String id, Class<T> type) {
        var descriptor = registry.require(target, type);
        String actor = access.requireObject(target, id);
        access.requirePermission(actor, descriptor.annotation().readPermission());
        var value = read(descriptor, id);
        if (value == null) return Optional.empty();
        try {
            T instance = type.getConstructor().newInstance();
            for (var f : descriptor.fields()) f.field().set(instance, value.data().get(f.field().getName()));
            return Optional.of(instance);
        } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
    }

    private IdentityExtensionValue read(IdentityExtensionRegistry.Descriptor d, String id) {
        var row = persistence.readExtension(d.annotation().table(), d.ownerColumn(), id);
        if (row == null) return null;
        Map<String, Object> fields = new LinkedHashMap<>();
        for (var field : d.fields()) {
            Object value = row.get(field.column());
            if (value instanceof Timestamp timestamp) value = field.field().getType() == Instant.class ? timestamp.toInstant() : timestamp.toLocalDateTime();
            if (value instanceof java.sql.Date date) value = date.toLocalDate();
            if (value != null) value = mapper.convertValue(value, field.field().getType());
            fields.put(field.field().getName(), value);
        }
        return new IdentityExtensionValue(((Number) row.get("row_version")).longValue(), fields);
    }

    private Map<String, Object> normalize(IdentityExtensionRegistry.Descriptor descriptor, Map<String, Object> fields) {
        Set<String> known = new HashSet<>();
        descriptor.fields().forEach(f -> known.add(f.field().getName()));
        if (!known.containsAll(fields.keySet())) fail("INVALID_MODEL", "包含未声明的扩展字段");
        Map<String, Object> result = new LinkedHashMap<>();
        for (var property : descriptor.fields()) {
            String name = property.field().getName();
            Object value = fields.get(name);
            if (value == null && property.definition().required()) fail("INVALID_MODEL", "扩展字段必填：" + name);
            if (value != null) {
                Class<?> type = property.field().getType();
                if (type == String.class && !(value instanceof String)
                        || type == Boolean.class && !(value instanceof Boolean)
                        || Number.class.isAssignableFrom(type) && !(value instanceof Number) && !(stringNumber(type) && value instanceof String)
                        || value instanceof Map || value instanceof Collection) fail("INVALID_MODEL", "扩展字段类型错误：" + name);
                try {
                    // Jackson 默认允许把小数截断为整数；资料保存必须拒绝精度丢失和越界。
                    if (type == Integer.class) value = new BigDecimal(value.toString()).intValueExact();
                    else if (type == Long.class) value = new BigDecimal(value.toString()).longValueExact();
                    else value = mapper.convertValue(value, type);
                } catch (IllegalArgumentException | ArithmeticException e) { fail("INVALID_MODEL", "扩展字段值错误：" + name); }
                if (value instanceof String text) {
                    if (text.codePointCount(0, text.length()) > property.definition().maxLength()) fail("INVALID_MODEL", "扩展字段超长：" + name);
                    if (property.definition().required() && text.isBlank()) fail("INVALID_MODEL", "扩展字段不能为空白：" + name);
                    if (property.control() == ExtensionControl.SELECT && !List.of(property.definition().options()).contains(text)) fail("INVALID_MODEL", "扩展选项非法：" + name);
                }
                if (value instanceof Double n && !Double.isFinite(n)) fail("INVALID_MODEL", "数字必须有限：" + name);
            }
            result.put(name, value);
        }
        return result;
    }

    private void write(IdentityExtensionRegistry.Descriptor d, String id, Map<String, Object> values, long expected) {
        Map<String, Object> columns = new LinkedHashMap<>();
        for (var field : d.fields()) columns.put(field.column(), sqlValue(values.get(field.field().getName())));
        if (expected == 0) {
            try {
                persistence.insertExtension(d.annotation().table(), d.ownerColumn(), id, columns);
            } catch (DuplicateKeyException e) { fail("EXTENSION_VERSION_CONFLICT", "资料已被其他操作创建，请重新加载"); }
        } else if (persistence.updateExtension(d.annotation().table(), d.ownerColumn(), id, columns, expected) != 1) {
            fail("EXTENSION_VERSION_CONFLICT", "资料已发生变化，请重新加载");
        }
    }
    private Object sqlValue(Object value) { return value instanceof Instant instant ? Timestamp.from(instant) : value; }
    private boolean stringNumber(Class<?> type) { return type == Long.class || type == BigDecimal.class; }
    private static void fail(String code, String message) { throw new IdentityWriteException(code, "extensions", message); }
}
