package com.workflow.admin.identity.provisioning.application;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.workflow.admin.identity.extension.application.IdentityExtensionService;
import com.workflow.admin.identity.user.application.SysUserService;
import com.workflow.admin.identity.user.infrastructure.persistence.mapper.SysUserMapper;
import com.workflow.admin.identity.user.infrastructure.persistence.record.SysUser;
import com.workflow.admin.organization.application.SysOrganizationService;
import com.workflow.admin.organization.infrastructure.persistence.record.SysOrganization;
import com.workflow.contracts.audit.model.*;
import com.workflow.contracts.audit.port.SystemAuditPort;
import com.workflow.contracts.identity.error.IdentityWriteException;
import com.workflow.contracts.identity.model.*;
import com.workflow.contracts.identity.port.IdentityWritePort;
import com.fasterxml.jackson.databind.*;
import lombok.RequiredArgsConstructor;
import com.workflow.admin.identity.provisioning.infrastructure.persistence.mapper.IdentityPersistenceMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.sql.Connection;
import java.util.*;

/** 被动的标准身份写入能力；不访问外部系统，不注册调度和后台重试。 */
@Service
@RequiredArgsConstructor
public class IdentityWriteService implements IdentityWritePort {
    private final IdentitySourceStore store;
    private final IdentityAccessService access;
    private final IdentityExtensionService extensions;
    private final IdentityPersistenceMapper persistence;
    private final ObjectMapper mapper;
    private final SysUserService users;
    private final SysUserMapper userMapper;
    private final SysOrganizationService organizations;
    private final SystemAuditPort audit;

    @Override
    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public IdentitySaveResult saveUser(UserSaveModel model) {
        if (model == null) fail("INVALID_MODEL", "model", "用户模型不能为空");
        lockDirectory();
        var writer = access.requireSource(model.key());
        if (model.base() == null) fail("INVALID_MODEL", "base", "基础快照不能为空");
        validateUser(model.base());
        var groups = extensions.prepareSource(IdentityObjectType.USER, model.extensions(), writer);
        String digest = digest(model.base(), groups);
        validateVersion(model.sourceVersion(), writer);
        var previous = store.find(IdentityObjectType.USER, model.key());
        var unchanged = previousResult(IdentityObjectType.USER, model.key(), model.sourceVersion(), digest, previous);
        if (unchanged != null) return unchanged;
        String id = previous == null ? IdWorker.getIdStr() : (String) previous.get("local_id");
        SysUser existing = previous == null ? null : userMapper.selectById(id);
        if (previous != null && existing == null) fail("SOURCE_OBJECT_RETIRED", "key", "映射的本地用户已删除或缺失");
        UserBaseData base = model.base();
        String org = store.resolveOrganization(model.key().sourceCode(), base.organizationExternalId());
        String dept = store.resolveOrganization(model.key().sourceCode(), base.departmentExternalId());
        boolean allowed = previous == null || IdentitySourceStore.flag(previous.get("local_allowed"));
        String status = base.enabled() && allowed ? "0" : "1";
        String username = existing == null ? "ext_" + id : existing.getUsername();
        SysUser candidate = new SysUser();
        candidate.setId(id); candidate.setOrgId(org); candidate.setDeptId(dept); candidate.setStatus(status);
        users.validateProvisionedMembership(candidate);
        if (existing == null) {
            persistence.insertUser(id, username, base, status, org, dept);
        } else {
            // 完整快照中的 null 必须真实清空；不能使用忽略 null 的默认 ORM 更新策略。
            persistence.updateUser(id, base, status, org, dept);
            if ("1".equals(status) && !"1".equals(existing.getStatus())) users.revokeSessions(id);
        }
        extensions.saveSource(IdentityObjectType.USER, id, groups);
        saveBinding(IdentityObjectType.USER, model.key(), model.sourceVersion(), digest, id, base.account(), base.enabled(), previous);
        record(writer.actorId(), IdentityObjectType.USER, id, model.key());
        return result(IdentityObjectType.USER, model.key(), id, username,
                previous == null ? IdentitySaveOutcome.CREATED : IdentitySaveOutcome.UPDATED, model.sourceVersion());
    }

    @Override
    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public IdentitySaveResult saveOrganization(OrganizationSaveModel model) {
        if (model == null) fail("INVALID_MODEL", "model", "组织模型不能为空");
        lockDirectory();
        var writer = access.requireSource(model.key());
        if (model.base() == null) fail("INVALID_MODEL", "base", "基础快照不能为空");
        var base = model.base();
        text(base.name(), 100, true, "name"); text(base.externalCode(), 100, false, "externalCode");
        text(base.phone(), 50, false, "phone"); text(base.email(), 100, false, "email");
        text(base.address(), 200, false, "address"); text(base.description(), 500, false, "description");
        if (base.type() == null) fail("INVALID_MODEL", "type", "组织类型不能为空");
        var groups = extensions.prepareSource(IdentityObjectType.ORGANIZATION, model.extensions(), writer);
        String digest = digest(base, groups);
        validateVersion(model.sourceVersion(), writer);
        var previous = store.find(IdentityObjectType.ORGANIZATION, model.key());
        var unchanged = previousResult(IdentityObjectType.ORGANIZATION, model.key(), model.sourceVersion(), digest, previous);
        if (unchanged != null) return unchanged;
        String id = previous == null ? IdWorker.getIdStr() : (String) previous.get("local_id");
        if (previous != null && persistence.findObject(IdentityObjectType.ORGANIZATION, id) == null) {
            fail("SOURCE_OBJECT_RETIRED", "key", "映射的本地组织已删除或缺失");
        }
        String parent = store.resolveOrganization(model.key().sourceCode(), base.parentExternalId());
        boolean allowed = previous == null || IdentitySourceStore.flag(previous.get("local_allowed"));
        SysOrganization org = new SysOrganization();
        org.setId(id); org.setOrgCode("ext_" + id); org.setOrgName(base.name());
        org.setType(base.type() == OrganizationUnitType.ORGANIZATION ? "org" : "dept");
        org.setParentId(parent == null ? "0" : parent); org.setBusinessLevelCode(base.businessLevelCode());
        org.setPhone(base.phone()); org.setEmail(base.email()); org.setAddress(base.address());
        org.setDescription(base.description()); org.setSortOrder(base.sortOrder());
        org.setStatus(base.enabled() && allowed ? "0" : "1"); org.setCreateBy(writer.actorId());
        organizations.saveProvisionedOrganization(org);
        persistence.clearableOrganizationFields(id, base);
        extensions.saveSource(IdentityObjectType.ORGANIZATION, id, groups);
        saveBinding(IdentityObjectType.ORGANIZATION, model.key(), model.sourceVersion(), digest, id, base.externalCode(), base.enabled(), previous);
        record(writer.actorId(), IdentityObjectType.ORGANIZATION, id, model.key());
        return result(IdentityObjectType.ORGANIZATION, model.key(), id, null,
                previous == null ? IdentitySaveOutcome.CREATED : IdentitySaveOutcome.UPDATED, model.sourceVersion());
    }

    @Override
    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public List<IdentitySaveResult> saveUsers(List<UserSaveModel> models) {
        lockDirectory();
        batch(models == null ? null : models.stream().map(m -> m == null ? null : m.key()).toList());
        List<IdentitySaveResult> results = new ArrayList<>();
        for (int i = 0; i < models.size(); i++) {
            try { results.add(saveUser(models.get(i))); }
            catch (IdentityWriteException error) { throw batchFailure(i, error); }
        }
        return results;
    }

    @Override
    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public List<IdentitySaveResult> saveOrganizations(List<OrganizationSaveModel> models) {
        lockDirectory();
        batch(models == null ? null : models.stream().map(m -> m == null ? null : m.key()).toList());
        // 先冻结目录结构，再拓扑处理当前批次的父子引用；返回顺序仍与输入一致。
        Map<String, OrganizationSaveModel> pending = new LinkedHashMap<>();
        for (var model : models) {
            if (model.base() == null) fail("INVALID_MODEL", "base", "基础快照不能为空");
            pending.put(model.key().externalId(), model);
        }
        Map<String, IdentitySaveResult> results = new HashMap<>();
        Set<String> visiting = new HashSet<>();
        for (var model : models) saveTree(model, models, pending, results, visiting);
        return models.stream().map(m -> results.get(m.key().externalId())).toList();
    }

    private void saveTree(OrganizationSaveModel model, List<OrganizationSaveModel> input, Map<String, OrganizationSaveModel> pending,
                          Map<String, IdentitySaveResult> results, Set<String> visiting) {
        String key = model.key().externalId();
        if (results.containsKey(key)) return;
        if (!visiting.add(key)) throw batchFailure(input.indexOf(model),
                new IdentityWriteException("INVALID_ORG_HIERARCHY", "parentExternalId", "组织批次包含环"));
        var parent = pending.get(model.base().parentExternalId());
        if (parent != null) saveTree(parent, input, pending, results, visiting);
        try { results.put(key, saveOrganization(model)); }
        catch (IdentityWriteException error) { throw batchFailure(input.indexOf(model), error); }
        visiting.remove(key);
    }

    /**
     * 来源写入需要最新已提交的目录视图。Spring 加入外层事务时不会自动调整隔离级别，
     * 因而拒绝已有的 REPEATABLE_READ 快照，避免目录锁之后仍按旧父链或旧状态保存。
     */
    private void lockDirectory() {
        Integer isolation = TransactionSynchronizationManager.getCurrentTransactionIsolationLevel();
        if (!Objects.equals(isolation, Connection.TRANSACTION_READ_COMMITTED)) {
            fail("INVALID_TRANSACTION_ISOLATION", "transaction", "来源写入的外层事务必须使用 READ_COMMITTED；也可在业务事务之外直接调用 Port");
        }
        store.lockDirectory();
    }

    private IdentityWriteException batchFailure(int index, IdentityWriteException error) {
        var failure = new IdentityWriteException(error.code(), "models[" + index + "]." + error.field(),
                "第 " + (index + 1) + " 条数据：" + error.getMessage());
        failure.initCause(error);
        return failure;
    }

    private void batch(List<ExternalIdentityKey> keys) {
        if (keys == null || keys.isEmpty() || keys.size() > 100) fail("INVALID_MODEL", "models", "每批必须为 1 至 100 条");
        Set<ExternalIdentityKey> seen = new HashSet<>();
        String source = null;
        for (var key : keys) {
            access.requireSource(key);
            if (source == null) source = key.sourceCode();
            if (!source.equals(key.sourceCode()) || !seen.add(key)) fail("INVALID_MODEL", "models", "批次必须同来源且不能重复对象");
        }
    }
    private void validateVersion(Long version, IdentityAccessService.Writer writer) {
        boolean versioned = writer.source().getVersionMode() == IdentityWriteProperties.VersionMode.VERSIONED;
        if (versioned && (version == null || version < 0) || !versioned && version != null) {
            fail("INVALID_MODEL", "sourceVersion", "版本与来源配置不匹配");
        }
    }
    private IdentitySaveResult previousResult(IdentityObjectType type, ExternalIdentityKey key, Long version,
                                               String digest, Map<String, Object> previous) {
        if (previous == null) return null;
        if (!"ACTIVE".equals(previous.get("binding_status"))) fail("SOURCE_OBJECT_RETIRED", "key", "来源对象已退休");
        String id = (String) previous.get("local_id");
        var local = persistence.findObject(type, id);
        if (local == null) {
            fail("SOURCE_OBJECT_RETIRED", "key", "来源映射的本地对象已缺失");
        }
        Long applied = previous.get("source_version") == null ? null : ((Number) previous.get("source_version")).longValue();
        if ((applied == null) != (version == null)) fail("SOURCE_VERSION_CONFLICT", "sourceVersion", "已有对象不能混用版本与无版本写入");
        IdentitySaveOutcome outcome = null;
        if (version != null && version < applied) outcome = IdentitySaveOutcome.STALE_IGNORED;
        else if (Objects.equals(previous.get("payload_hash"), digest) && Objects.equals(applied, version)) outcome = IdentitySaveOutcome.UNCHANGED;
        else if (version != null && version.equals(applied)) fail("SOURCE_VERSION_CONFLICT", "sourceVersion", "同一版本的数据内容不一致");
        if (outcome == null) return null;
        String username = type == IdentityObjectType.USER ? (String) local.get("username") : null;
        return result(type, key, id, username, outcome, applied);
    }
    private void saveBinding(IdentityObjectType type, ExternalIdentityKey key, Long version, String digest,
                             String id, String account, boolean enabled, Map<String, Object> previous) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("source_key", IdentitySourceStore.hash(type, key));
        values.put("source_code", key.sourceCode()); values.put("object_type", type.name());
        values.put("external_id", key.externalId()); values.put("local_id", id);
        values.put("external_account", account); values.put("source_version", version);
        values.put("payload_hash", digest); values.put("source_enabled", enabled);
        if (previous == null) persistence.insertBinding(values);
        else persistence.updateBinding(values);
    }

    private String digest(Object base, Map<String, Map<String, Object>> groups) {
        try {
            ObjectMapper canonical = mapper.copy().configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
                    .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
            return IdentitySourceStore.digest(canonical.writeValueAsString(Map.of("base", base, "extensions", groups)));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) { throw new IllegalStateException("无法规范化写入快照", e); }
    }
    private void record(String actor, IdentityObjectType type, String id, ExternalIdentityKey key) {
        audit.record(SystemAuditEvent.builder().operatorId(actor).module(AuditModule.SYSTEM).action(AuditAction.UPSERT)
                .operationName("来源身份写入").riskLevel(AuditRiskLevel.HIGH).result(AuditResult.SUCCESS).required(true)
                .targetType(type == IdentityObjectType.USER ? "SYS_USER" : "SYS_ORGANIZATION").targetId(id)
                .summary("来源=" + key.sourceCode()).build());
    }
    private void validateUser(UserBaseData base) {
        text(base.account(), 255, false, "account"); text(base.nickname(), 50, true, "nickname");
        text(base.email(), 100, false, "email"); text(base.phone(), 20, false, "phone");
        text(base.avatar(), 255, false, "avatar");
    }
    private void text(String value, int max, boolean required, String field) {
        if (required && (value == null || value.isBlank()) || value != null && value.codePointCount(0, value.length()) > max) {
            fail("INVALID_MODEL", field, "字段为空或超过长度：" + field);
        }
    }
    private IdentitySaveResult result(IdentityObjectType type, ExternalIdentityKey key, String id, String username,
                                      IdentitySaveOutcome outcome, Long version) {
        return new IdentitySaveResult(type, key.sourceCode(), key.externalId(), id, username, outcome, version);
    }
    private static void fail(String code, String field, String message) { throw new IdentityWriteException(code, field, message); }
}
