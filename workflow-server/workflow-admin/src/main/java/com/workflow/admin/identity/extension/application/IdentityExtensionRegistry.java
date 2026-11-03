package com.workflow.admin.identity.extension.application;

import com.workflow.admin.identity.provisioning.application.IdentityWriteProperties;
import com.workflow.contracts.identity.annotation.*;
import com.workflow.contracts.identity.error.IdentityWriteException;
import com.workflow.contracts.identity.model.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import com.workflow.core.database.port.SchemaMetadataPort;
import org.springframework.stereotype.Component;
import java.lang.reflect.*;
import java.time.*;
import java.math.BigDecimal;
import java.util.*;

/** 启动时建立可信类型/表列白名单；只读元数据，不创建表，也不实例化取数任务。 */
@Component
@RequiredArgsConstructor
public class IdentityExtensionRegistry implements SmartInitializingSingleton {
    private final IdentityWriteProperties properties;
    private final SchemaMetadataPort metadata;
    private final Map<String, Descriptor> descriptors = new LinkedHashMap<>();

    @Override
    public void afterSingletonsInstantiated() {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(IdentityExtension.class));
        for (String base : properties.getScanPackages()) {
            for (var bean : scanner.findCandidateComponents(base)) {
                try { register(Class.forName(bean.getBeanClassName())); }
                catch (ClassNotFoundException e) { throw new IllegalStateException("无法加载扩展类型", e); }
            }
        }
    }

    /** 注册具体扩展，拒绝编码冲突、未声明字段和不满足独立表契约的结构。 */
    public void register(Class<?> type) {
        var annotation = type.getAnnotation(IdentityExtension.class);
        if (annotation == null || Modifier.isAbstract(type.getModifiers())) fail("扩展必须是带声明的具体类");
        IdentityObjectType target = UserExtension.class.isAssignableFrom(type) ? IdentityObjectType.USER
                : OrganizationExtension.class.isAssignableFrom(type) ? IdentityObjectType.ORGANIZATION : null;
        if (target == null) fail("扩展必须继承 UserExtension 或 OrganizationExtension");
        if (!annotation.code().matches("[a-z][a-z0-9_.-]{0,63}")
                || annotation.title().isBlank() || annotation.readPermission().isBlank()
                || annotation.writePermission().isBlank()) fail("扩展编码、标题和读写权限不能为空或非法");
        identifier(annotation.table());
        if (!annotation.table().startsWith("biz_")) fail("扩展表必须位于 biz_ 命名空间");
        String key = target + ":" + annotation.code();
        if (descriptors.containsKey(key) || descriptors.values().stream()
                .anyMatch(d -> d.annotation().table().equalsIgnoreCase(annotation.table()))) fail("扩展编码或表重复");
        try { type.getConstructor(); }
        catch (NoSuchMethodException e) { fail("扩展必须提供 public 无参构造方法"); }
        List<Property> fields = new ArrayList<>();
        Set<String> columns = new HashSet<>(List.of("user_id", "organization_id", "row_version"));
        for (Class<?> c = type; c != UserExtension.class && c != OrganizationExtension.class; c = c.getSuperclass()) {
            for (Field field : c.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) continue;
                var definition = field.getAnnotation(ExtensionField.class);
                if (definition == null || Modifier.isFinal(field.getModifiers())) fail("每个扩展字段必须可写并声明 ExtensionField");
                if (!supported(field.getType())) fail("不支持的扩展字段类型：" + field.getName());
                String column = definition.column().isBlank() ? snake(field.getName()) : definition.column();
                identifier(column);
                if (!columns.add(column.toLowerCase(Locale.ROOT))) fail("扩展列名重复或占用框架字段");
                if (definition.label().isBlank() || definition.maxLength() < 1 || definition.maxLength() > 4000) fail("字段标题或长度非法");
                ExtensionControl control = definition.control() == ExtensionControl.AUTO ? control(field.getType()) : definition.control();
                if (control == ExtensionControl.SELECT && (field.getType() != String.class || definition.options().length == 0)) fail("下拉框必须使用字符串及明确选项");
                if (control != ExtensionControl.SELECT && control != control(field.getType())) fail("控件与字段类型不匹配");
                field.setAccessible(true);
                fields.add(new Property(field, column, definition, control));
            }
        }
        if (fields.isEmpty()) fail("扩展至少声明一个字段");
        var descriptor = new Descriptor(target, type, annotation, List.copyOf(fields));
        validateTable(descriptor);
        descriptors.put(key, descriptor);
    }

    private void validateTable(Descriptor descriptor) {
        var physical = metadata.columns(descriptor.annotation().table());
        Set<String> columns = new HashSet<>();
        Set<String> primary = new HashSet<>();
        for (var column : physical) {
            columns.add(column.name().toLowerCase(Locale.ROOT));
            if (column.primaryKey()) primary.add(column.name().toLowerCase(Locale.ROOT));
        }
        Set<String> expected = new HashSet<>(List.of(descriptor.ownerColumn(), "row_version"));
        descriptor.fields().forEach(f -> expected.add(f.column().toLowerCase(Locale.ROOT)));
        if (!columns.equals(expected) || !primary.equals(Set.of(descriptor.ownerColumn()))) {
            fail("扩展表 " + descriptor.annotation().table() + " 与声明不一致；先执行对应迁移，主键必须为 " + descriptor.ownerColumn());
        }
    }

    public Descriptor require(IdentityObjectType target, String code) {
        var result = descriptors.get(target + ":" + code);
        if (result == null) throw new IdentityWriteException("EXTENSION_NOT_REGISTERED", "extensions", "扩展未注册：" + code);
        return result;
    }
    public Descriptor require(IdentityObjectType target, Class<?> type) {
        return descriptors.values().stream().filter(d -> d.target() == target && d.type() == type).findFirst()
                .orElseThrow(() -> new IdentityWriteException("EXTENSION_NOT_REGISTERED", "extensions", "扩展类型未注册"));
    }
    public List<Descriptor> list(IdentityObjectType target) {
        return descriptors.values().stream().filter(d -> d.target() == target).toList();
    }
    private static boolean supported(Class<?> type) {
        return Set.of(String.class, Boolean.class, Integer.class, Long.class, Double.class, BigDecimal.class,
                LocalDate.class, LocalDateTime.class, Instant.class).contains(type);
    }
    private static ExtensionControl control(Class<?> type) {
        if (type == String.class) return ExtensionControl.TEXT;
        if (type == Boolean.class) return ExtensionControl.BOOLEAN;
        if (type == LocalDate.class) return ExtensionControl.DATE;
        if (type == LocalDateTime.class || type == Instant.class) return ExtensionControl.DATETIME;
        return ExtensionControl.NUMBER;
    }
    private static String snake(String value) { return value.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT); }
    private static void identifier(String value) { if (!value.matches("[a-z][a-z0-9_]{0,62}")) fail("非法扩展表或列名"); }
    private static void fail(String message) { throw new IllegalStateException(message); }

    public record Property(Field field, String column, ExtensionField definition, ExtensionControl control) { }
    public record Descriptor(IdentityObjectType target, Class<?> type, IdentityExtension annotation, List<Property> fields) {
        public String ownerColumn() { return target == IdentityObjectType.USER ? "user_id" : "organization_id"; }
    }
}
