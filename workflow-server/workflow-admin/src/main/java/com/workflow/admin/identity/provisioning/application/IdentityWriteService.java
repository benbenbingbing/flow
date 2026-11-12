package com.workflow.admin.identity.provisioning.application;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.workflow.admin.authorization.role.infrastructure.persistence.mapper.SysRoleMapper;
import com.workflow.admin.authorization.role.infrastructure.persistence.record.SysRole;
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
    private final SysRoleMapper roleMapper;
    private final SysOrganizationService organizations;
    private final IdentityOrganizationHierarchyService organizationHierarchy;
    private final SystemAuditPort audit;
    private final IdentitySourceSnapshotService snapshots;

    @Override
    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public IdentitySaveResult saveUser(UserSaveModel model) {
        if (model == null) fail("INVALID_MODEL", "model", "用户模型不能为空");
        snapshots.checkSizes(Collections.singletonList(model.rawDataJson()), false);
        lockDirectory();
        var writer = access.requireSource(model.key());
        if (model.base() == null) fail("INVALID_MODEL", "base", "基础快照不能为空");
        validateUser(model.base());
        var groups = extensions.prepareSource(IdentityObjectType.USER, model.extensions(), writer);
        String digest = digest(model.base(), groups);
        validateVersion(model.sourceVersion(), writer);
        var previous = store.find(IdentityObjectType.USER, model.key());
        var unchanged = previousResult(IdentityObjectType.USER, model.key(), model.sourceVersion(), digest, previous);
        if (unchanged != null) {
            // 旧版本完全忽略；相同业务版本允许补录或刷新原文，不重写用户、扩展和角色。
            if (unchanged.outcome() == IdentitySaveOutcome.UNCHANGED) {
                String raw = snapshots.validate(model.rawDataJson(), writer);
                snapshots.save(IdentityObjectType.USER, model.key(), unchanged.localId(), model.sourceVersion(), digest, raw, writer, false);
            }
            return unchanged;
        }
        String raw = snapshots.validate(model.rawDataJson(), writer);
        String id = previous == null ? IdWorker.getIdStr() : (String) previous.get("local_id");
        SysUser existing = previous == null ? null : userMapper.selectById(id);
        if (previous != null && existing == null) fail("SOURCE_OBJECT_RETIRED", "key", "映射的本地用户已删除或缺失");
        UserBaseData base = model.base();
        String org = store.resolveOrganization(model.key().sourceCode(), base.organizationExternalId());
        String dept = store.resolveOrganization(model.key().sourceCode(), base.departmentExternalId());
        boolean allowed = previous == null || IdentitySourceStore.flag(previous.get("local_allowed"));
        String status = base.enabled() && allowed ? "0" : "1";
        String username = identityCode(IdentityObjectType.USER, base.account(), previous);
        SysUser candidate = new SysUser();
        candidate.setId(id); candidate.setOrgId(org); candidate.setDeptId(dept); candidate.setStatus(status);
        users.validateProvisionedMembership(candidate);
        if (existing == null) {
            persistence.insertUser(id, username, base, status, org, dept);
            assignDefaultRole(id, writer.source());
        } else {
            // 完整快照中的 null 必须真实清空；不能使用忽略 null 的默认 ORM 更新策略。
            persistence.updateUser(id, base, status, org, dept);
            if ("1".equals(status) && !"1".equals(existing.getStatus())) users.revokeSessions(id);
        }
        extensions.saveSource(IdentityObjectType.USER, id, groups);
        saveBinding(IdentityObjectType.USER, model.key(), model.sourceVersion(), digest, id, base.account(), base.enabled(), previous);
        snapshots.save(IdentityObjectType.USER, model.key(), id, model.sourceVersion(), digest, raw, writer, true);
        record(writer.actorId(), IdentityObjectType.USER, id, model.key());
        return result(IdentityObjectType.USER, model.key(), id, username,
                previous == null ? IdentitySaveOutcome.CREATED : IdentitySaveOutcome.UPDATED, model.sourceVersion());
    }

    @Override
    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public IdentitySaveResult saveOrganization(OrganizationSaveModel model) {
        if (model == null) fail("INVALID_MODEL", "model", "组织模型不能为空");
        snapshots.checkSizes(Collections.singletonList(model.rawDataJson()), false);
        lockDirectory();
        var writer = access.requireSource(model.key());
        if (model.base() == null) fail("INVALID_MODEL", "base", "基础快照不能为空");
        var base = model.base();
        text(base.name(), 100, true, "name"); text(base.externalCode(), 100, true, "externalCode");
        text(base.phone(), 50, false, "phone"); text(base.email(), 100, false, "email");
        text(base.address(), 200, false, "address"); text(base.description(), 500, false, "description");
        if (base.type() == null) fail("INVALID_MODEL", "type", "组织类型不能为空");
        var groups = extensions.prepareSource(IdentityObjectType.ORGANIZATION, model.extensions(), writer);
        String digest = digest(base, groups);
        validateVersion(model.sourceVersion(), writer);
        var previous = store.find(IdentityObjectType.ORGANIZATION, model.key());
        var unchanged = previousResult(IdentityObjectType.ORGANIZATION, model.key(), model.sourceVersion(), digest, previous);
        if (unchanged != null) {
            if (unchanged.outcome() == IdentitySaveOutcome.UNCHANGED) {
                String raw = snapshots.validate(model.rawDataJson(), writer);
                snapshots.save(IdentityObjectType.ORGANIZATION, model.key(), unchanged.localId(), model.sourceVersion(), digest, raw, writer, false);
            }
            return unchanged;
        }
        String raw = snapshots.validate(model.rawDataJson(), writer);
        String id = previous == null ? IdWorker.getIdStr() : (String) previous.get("local_id");
        if (previous != null && persistence.findObject(IdentityObjectType.ORGANIZATION, id) == null) {
            fail("SOURCE_OBJECT_RETIRED", "key", "映射的本地组织已删除或缺失");
        }
        boolean allowed = previous == null || IdentitySourceStore.flag(previous.get("local_allowed"));
        SysOrganization org = new SysOrganization();
        org.setId(id);
        org.setOrgCode(identityCode(IdentityObjectType.ORGANIZATION, base.externalCode(), previous));
        org.setOrgName(base.name());
        org.setType(base.type() == OrganizationUnitType.ORGANIZATION ? "org" : "dept");
        organizationHierarchy.prepare(org, model.key(), base.parentExternalId());
        org.setBusinessLevelCode(base.businessLevelCode());
        org.setPhone(base.phone()); org.setEmail(base.email()); org.setAddress(base.address());
        org.setDescription(base.description()); org.setSortOrder(base.sortOrder());
        org.setStatus(base.enabled() && allowed ? "0" : "1"); org.setCreateBy(writer.actorId());
        organizations.saveProvisionedOrganization(org);
        persistence.clearableOrganizationFields(id, base);
        extensions.saveSource(IdentityObjectType.ORGANIZATION, id, groups);
        saveBinding(IdentityObjectType.ORGANIZATION, model.key(), model.sourceVersion(), digest, id, base.externalCode(), base.enabled(), previous);
        organizationHierarchy.reconcile(model.key(), base.parentExternalId(), org);
        snapshots.save(IdentityObjectType.ORGANIZATION, model.key(), id, model.sourceVersion(), digest, raw, writer, true);
        record(writer.actorId(), IdentityObjectType.ORGANIZATION, id, model.key());
        return result(IdentityObjectType.ORGANIZATION, model.key(), id, null,
                previous == null ? IdentitySaveOutcome.CREATED : IdentitySaveOutcome.UPDATED, model.sourceVersion());
    }

    @Override
    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public List<IdentitySaveResult> saveUsers(List<UserSaveModel> models) {
        lockDirectory();
        batch(models == null ? null : models.stream().map(m -> m == null ? null : m.key()).toList());
        snapshots.checkSizes(models.stream().map(UserSaveModel::rawDataJson).toList(), true);
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
        snapshots.checkSizes(models.stream().map(OrganizationSaveModel::rawDataJson).toList(), true);
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

    /**
     * 首次创建时原样使用来源编码，供各环境按相同用户名或组织编码解析配置引用。
     * 已有映射以首次保存的来源编码校验后续快照，防止改名使任务和配置中的引用失效。
     *
     * @param type 决定目标表与错误字段名
     * @param requested 已通过非空及长度校验的 account 或 externalCode
     * @param previous 已有来源映射，保存创建时的固定来源编码
     * @return 原样传入的来源编码，供本地 username 或 orgCode 使用
     * @throws IdentityWriteException 来源编码已被占用或试图修改已有编码时拒绝写入
     */
    private String identityCode(IdentityObjectType type, String requested, Map<String, Object> previous) {
        boolean user = type == IdentityObjectType.USER;
        String field = user ? "account" : "externalCode";
        if (previous != null) {
            if (!Objects.equals(requested, previous.get("external_account"))) {
                fail("SOURCE_CODE_IMMUTABLE", field, "来源编码创建后不可修改");
            }
        } else if (persistence.countIdentityCode(type, requested) > 0) {
            // 与数据库唯一索引使用相同的比较规则，并覆盖逻辑删除记录占用的编码。
            fail("IDENTITY_CODE_CONFLICT", field, "用户名或组织编码已被占用");
        }
        return requested;
    }

    /**
     * 为来源首次创建的用户分配可选默认角色，与基础资料、映射及审计共用当前事务。
     * 仅由新增分支调用，后续来源更新继续保留本地维护的角色集合。
     *
     * @param userId 本次新建用户的本地 ID，用于保存用户角色关联
     * @param source 已授权来源的部署配置，空角色编码表示省略默认角色分配
     * @throws IdentityWriteException 配置的角色不存在、已删除或未启用时拒绝并回滚本次创建
     */
    private void assignDefaultRole(String userId, IdentityWriteProperties.Source source) {
        String roleCode = source.getDefaultRoleCode();
        if (roleCode == null || roleCode.isBlank()) return;
        SysRole role = roleMapper.selectOne(Wrappers.<SysRole>lambdaQuery()
                .eq(SysRole::getRoleCode, roleCode)
                .eq(SysRole::getStatus, SysRole.Status.ENABLED.getValue()));
        if (role == null) {
            fail("DEFAULT_ROLE_NOT_AVAILABLE", "defaultRoleCode", "来源配置的默认角色不存在或未启用");
        }
        users.saveUserRoles(userId, List.of(role.getId()));
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
        text(base.account(), 50, true, "account"); text(base.nickname(), 50, true, "nickname");
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
