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
import com.workflow.port.platform.model.audit.*;
import com.workflow.port.platform.SystemOperationLogPort;
import com.workflow.port.system.error.identity.IdentityWriteException;
import com.workflow.port.system.model.identity.*;
import com.fasterxml.jackson.databind.*;
import lombok.RequiredArgsConstructor;
import com.workflow.admin.identity.provisioning.infrastructure.persistence.mapper.IdentityPersistenceMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.sql.Connection;
import java.util.*;

import static com.workflow.core.logging.LogValue.safe;

/** 被动的标准身份写入能力；不访问外部系统，不注册调度和后台重试。 */
@Service
@RequiredArgsConstructor
public class IdentityWriteService {
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
    private final SystemOperationLogPort audit;
    private final IdentitySourceSnapshotService snapshots;

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public IdentitySaveResult saveUser(UserSaveModel model) {
        if (model == null) fail("INVALID_MODEL", "model", "用户模型不能为空");
        snapshots.checkSizes(Collections.singletonList(model.rawDataJson()), false);
        lockDirectory();
        var writer = access.requireSource(model.key());
        if (model.base() == null) fail("INVALID_MODEL", "base", "用户基础资料（base）不能为空");
        validateUser(model.base());
        var groups = extensions.prepareSource(IdentityObjectType.USER, model.extensions(), writer);
        validateMainFields(model.mainFields());
        String digest = digest(model.base(), groups, model.mainFields());
        validateVersion(model.sourceVersion(), writer);
        UserBaseData base = model.base();
        var target = resolveBinding(IdentityObjectType.USER, model.key(), base.account(), model.bindingChange(), writer);
        var previous = target.previous();
        var unchanged = previousResult(IdentityObjectType.USER, model.key(), model.sourceVersion(), digest, previous);
        if (unchanged != null) {
            // 旧版本完全忽略；相同业务版本允许补录或刷新原文，不重写用户、扩展和角色。
            if (unchanged.outcome() == IdentitySaveOutcome.UNCHANGED) {
                String raw = snapshots.validate(model.rawDataJson(), writer);
                moveBinding(IdentityObjectType.USER, model.key(), target);
                snapshots.save(IdentityObjectType.USER, model.key(), unchanged.localId(), model.sourceVersion(), digest, raw, writer, false);
                if (target.moving()) {
                    recordBindingChange(writer.actorId(), IdentityObjectType.USER, model.key(), target);
                    return result(IdentityObjectType.USER, model.key(), unchanged.localId(), unchanged.canonicalUsername(),
                            IdentitySaveOutcome.UPDATED, model.sourceVersion());
                }
            }
            return unchanged;
        }
        String raw = snapshots.validate(model.rawDataJson(), writer);
        String id = target.creating() ? IdWorker.getIdStr() : target.localId();
        SysUser existing = target.creating() ? null : userMapper.selectById(id);
        if (!target.creating() && existing == null) missingLocalObject(IdentityObjectType.USER, model.key());
        String org = store.resolveOrganization(model.key().sourceCode(), base.organizationExternalId(), "organizationExternalId");
        String dept = store.resolveOrganization(model.key().sourceCode(), base.departmentExternalId(), "departmentExternalId");
        boolean allowed = target.localAllowed();
        String status = base.enabled() && allowed ? "0" : "1";
        String username = identityCode(IdentityObjectType.USER, base.account(), target);
        SysUser candidate = new SysUser();
        candidate.setId(id); candidate.setOrgId(org); candidate.setDeptId(dept); candidate.setStatus(status);
        // 归属规则由用户服务共享；同步提示带上调用方提交的外部 ID，便于从来源记录定位问题。
        try {
            users.validateProvisionedMembership(candidate);
        } catch (IllegalArgumentException error) {
            throw new IdentityWriteException("INVALID_MODEL", "base", "用户名「%s」的归属校验失败（所属组织外部 ID「%s」，部门外部 ID「%s」）。%s"
                    .formatted(safe(username), safe(base.organizationExternalId()), safe(base.departmentExternalId()), safe(error.getMessage())));
        }
        if (existing == null) {
            persistence.insertUser(id, username, base, status, org, dept);
            assignDefaultRole(id, writer.source());
        } else {
            // 完整快照中的 null 必须真实清空；不能使用忽略 null 的默认 ORM 更新策略。
            persistence.updateUser(id, base, status, org, dept);
            if ("1".equals(status) && !"1".equals(existing.getStatus())) users.revokeSessions(id);
        }
        if (model.mainFields() != null) persistence.updateUserMainFields(id, model.mainFields());
        extensions.saveSource(IdentityObjectType.USER, id, groups);
        moveBinding(IdentityObjectType.USER, model.key(), target);
        saveBinding(IdentityObjectType.USER, model.key(), model.sourceVersion(), digest, id, base.account(), base.enabled(), target);
        snapshots.save(IdentityObjectType.USER, model.key(), id, model.sourceVersion(), digest, raw, writer, true);
        recordSave(writer.actorId(), IdentityObjectType.USER, id, model.key(), target);
        return result(IdentityObjectType.USER, model.key(), id, username,
                target.creating() ? IdentitySaveOutcome.CREATED : IdentitySaveOutcome.UPDATED, model.sourceVersion());
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public IdentitySaveResult saveOrganization(OrganizationSaveModel model) {
        if (model == null) fail("INVALID_MODEL", "model", "组织模型不能为空");
        snapshots.checkSizes(Collections.singletonList(model.rawDataJson()), false);
        lockDirectory();
        var writer = access.requireSource(model.key());
        if (model.base() == null) fail("INVALID_MODEL", "base", "组织基础资料（base）不能为空");
        var base = model.base();
        text(base.name(), 100, true, "name"); text(base.externalCode(), 100, true, "externalCode");
        text(base.phone(), 50, false, "phone"); text(base.email(), 100, false, "email");
        text(base.address(), 200, false, "address"); text(base.description(), 500, false, "description");
        if (base.type() == null) fail("INVALID_MODEL", "type", "组织编码「%s」的类型（type）不能为空，应为 ORGANIZATION 或 DEPARTMENT"
                .formatted(safe(base.externalCode())));
        var groups = extensions.prepareSource(IdentityObjectType.ORGANIZATION, model.extensions(), writer);
        validateMainFields(model.mainFields());
        String digest = digest(base, groups, model.mainFields());
        validateVersion(model.sourceVersion(), writer);
        var target = resolveBinding(IdentityObjectType.ORGANIZATION, model.key(), base.externalCode(), model.bindingChange(), writer);
        var previous = target.previous();
        var unchanged = previousResult(IdentityObjectType.ORGANIZATION, model.key(), model.sourceVersion(), digest, previous);
        if (unchanged != null) {
            if (unchanged.outcome() == IdentitySaveOutcome.UNCHANGED) {
                String raw = snapshots.validate(model.rawDataJson(), writer);
                moveBinding(IdentityObjectType.ORGANIZATION, model.key(), target);
                if (target.moving()) {
                    organizationHierarchy.reconcileAfterBindingMove(model.key(), base.parentExternalId(), unchanged.localId());
                    recordBindingChange(writer.actorId(), IdentityObjectType.ORGANIZATION, model.key(), target);
                }
                snapshots.save(IdentityObjectType.ORGANIZATION, model.key(), unchanged.localId(), model.sourceVersion(), digest, raw, writer, false);
                if (target.moving()) {
                    return result(IdentityObjectType.ORGANIZATION, model.key(), unchanged.localId(), null,
                            IdentitySaveOutcome.UPDATED, model.sourceVersion());
                }
            }
            return unchanged;
        }
        String raw = snapshots.validate(model.rawDataJson(), writer);
        String id = target.creating() ? IdWorker.getIdStr() : target.localId();
        if (previous != null && persistence.findObject(IdentityObjectType.ORGANIZATION, id) == null) {
            missingLocalObject(IdentityObjectType.ORGANIZATION, model.key());
        }
        boolean allowed = target.localAllowed();
        SysOrganization org = new SysOrganization();
        org.setId(id);
        org.setOrgCode(identityCode(IdentityObjectType.ORGANIZATION, base.externalCode(), target));
        org.setOrgName(base.name());
        org.setType(base.type() == OrganizationUnitType.ORGANIZATION ? "org" : "dept");
        moveBinding(IdentityObjectType.ORGANIZATION, model.key(), target);
        organizationHierarchy.prepare(org, model.key(), base.parentExternalId());
        org.setBusinessLevelCode(base.businessLevelCode());
        org.setPhone(base.phone()); org.setEmail(base.email()); org.setAddress(base.address());
        org.setDescription(base.description()); org.setSortOrder(base.sortOrder());
        org.setStatus(base.enabled() && allowed ? "0" : "1"); org.setCreateBy(writer.actorId());
        // 组织领域服务也用于本地管理，此处只给同步调用补充来源编码和批量可识别的错误类型。
        try {
            organizations.saveProvisionedOrganization(org);
        } catch (IllegalArgumentException error) {
            throw new IdentityWriteException("INVALID_MODEL", "base", "组织编码「%s」的基础资料校验失败。%s"
                    .formatted(safe(base.externalCode()), safe(error.getMessage())));
        }
        persistence.clearableOrganizationFields(id, base);
        if (model.mainFields() != null) persistence.updateOrganizationMainFields(id, model.mainFields());
        extensions.saveSource(IdentityObjectType.ORGANIZATION, id, groups);
        saveBinding(IdentityObjectType.ORGANIZATION, model.key(), model.sourceVersion(), digest, id, base.externalCode(), base.enabled(), target);
        organizationHierarchy.reconcile(model.key(), base.parentExternalId(), org);
        snapshots.save(IdentityObjectType.ORGANIZATION, model.key(), id, model.sourceVersion(), digest, raw, writer, true);
        recordSave(writer.actorId(), IdentityObjectType.ORGANIZATION, id, model.key(), target);
        return result(IdentityObjectType.ORGANIZATION, model.key(), id, null,
                target.creating() ? IdentitySaveOutcome.CREATED : IdentitySaveOutcome.UPDATED, model.sourceVersion());
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public List<IdentitySaveResult> saveUsers(List<UserSaveModel> models) {
        lockDirectory();
        batch(models == null ? null : models.stream().map(m -> m == null ? null : m.key()).toList());
        snapshots.checkSizes(models.stream().map(UserSaveModel::rawDataJson).toList(), true);
        List<IdentitySaveResult> results = new ArrayList<>();
        for (int i = 0; i < models.size(); i++) {
            try { results.add(saveUser(models.get(i))); }
            catch (IdentityWriteException error) { throw batchFailure(i, models.get(i).key(), error); }
        }
        return results;
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public List<IdentitySaveResult> saveOrganizations(List<OrganizationSaveModel> models) {
        lockDirectory();
        batch(models == null ? null : models.stream().map(m -> m == null ? null : m.key()).toList());
        snapshots.checkSizes(models.stream().map(OrganizationSaveModel::rawDataJson).toList(), true);
        // 先冻结目录结构，再拓扑处理当前批次的父子引用；返回顺序仍与输入一致。
        Map<String, OrganizationSaveModel> pending = new LinkedHashMap<>();
        for (int i = 0; i < models.size(); i++) {
            var model = models.get(i);
            if (model.base() == null) throw batchFailure(i, model.key(),
                    new IdentityWriteException("INVALID_MODEL", "base", "组织基础资料（base）不能为空"));
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
        if (!visiting.add(key)) throw batchFailure(input.indexOf(model), model.key(),
                new IdentityWriteException("INVALID_ORG_HIERARCHY", "parentExternalId", "批次组织父链成环，请检查编码「%s」的上级外部 ID「%s」"
                        .formatted(safe(model.base().externalCode()), safe(model.base().parentExternalId()))));
        var parent = pending.get(model.base().parentExternalId());
        if (parent != null) saveTree(parent, input, pending, results, visiting);
        try { results.put(key, saveOrganization(model)); }
        catch (IdentityWriteException error) { throw batchFailure(input.indexOf(model), model.key(), error); }
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

    /** 批量定位保留机器可读字段路径，同时显示原始行号和来源键，便于跨批次查找记录。 */
    private IdentityWriteException batchFailure(int index, ExternalIdentityKey key, IdentityWriteException error) {
        String context = key == null ? "" : "（来源「%s」，外部 ID「%s」）".formatted(safe(key.sourceCode()), safe(key.externalId()));
        var failure = new IdentityWriteException(error.code(), "models[" + index + "]." + error.field(),
                "第 " + (index + 1) + " 条数据" + context + "同步失败。" + error.getMessage());
        failure.initCause(error);
        return failure;
    }

    private void batch(List<ExternalIdentityKey> keys) {
        int size = keys == null ? 0 : keys.size();
        if (size == 0 || size > 100) fail("INVALID_MODEL", "models", "同步批次须为 1 至 100 条，本批 %d 条".formatted(size));
        Set<ExternalIdentityKey> seen = new HashSet<>();
        String source = null;
        for (int i = 0; i < size; i++) {
            var key = keys.get(i);
            try {
                if (key == null) fail("INVALID_MODEL", "key", "同步记录或来源键不能为空");
                access.requireSource(key);
                if (source == null) source = key.sourceCode();
                if (!source.equals(key.sourceCode())) fail("INVALID_MODEL", "key.sourceCode", "批次来源须与首条记录的来源「%s」一致"
                        .formatted(safe(source)));
                if (!seen.add(key)) fail("INVALID_MODEL", "key.externalId", "该外部 ID 在本批次重复提交");
            } catch (IdentityWriteException error) {
                throw batchFailure(i, key, error);
            }
        }
    }
    private void validateVersion(Long version, IdentityAccessService.Writer writer) {
        boolean versioned = writer.source().getVersionMode() == IdentityWriteProperties.VersionMode.VERSIONED;
        if (versioned && (version == null || version < 0)) {
            fail("INVALID_MODEL", "sourceVersion", "VERSIONED 来源要求 sourceVersion 为非负整数，本次为 %s".formatted(safe(version)));
        }
        if (!versioned && version != null) {
            fail("INVALID_MODEL", "sourceVersion", "SERIAL 来源要求省略 sourceVersion，本次提交了版本 %d".formatted(version));
        }
    }

    /**
     * 在目录锁内确认修复对象，避免同步仅凭编码接管本地数据。已在新键上的同一对象可直接重试。
     * 这里只读取和校验，版本、原文及快照通过校验后才写入绑定和本地资料。
     *
     * @throws IdentityWriteException 本地对象不可管理、已删除、编码不匹配或来源键已有其他归属
     */
    private BindingTarget resolveBinding(IdentityObjectType type, ExternalIdentityKey key, String code,
                                         IdentityBindingChange change, IdentityAccessService.Writer writer) {
        var previous = store.find(type, key);
        if (change == null) return new BindingTarget(previous, null, false);

        String id = change.localId();
        text(id, 64, true, "bindingChange.localId");
        String oldKey = null;
        if (change.previousExternalId() != null) {
            var old = new ExternalIdentityKey(key.sourceCode(), change.previousExternalId());
            IdentityAccessService.validateKey(old);
            if (key.equals(old)) fail("INVALID_MODEL", "bindingChange.previousExternalId", "改绑的新旧外部 ID 均为「%s」，请提供不同的新键"
                    .formatted(safe(key.externalId())));
            oldKey = IdentitySourceStore.hash(type, old);
        }
        access.requireBindingChange(type, id, writer);
        var local = persistence.findObject(type, id);
        if (local == null) fail("OBJECT_NOT_FOUND", "bindingChange.localId", "绑定修复目标 localId「%s」已删除或不存在"
                .formatted(safe(id)));
        boolean user = type == IdentityObjectType.USER;
        var localCode = local.get(user ? "username" : "org_code");
        if (!Objects.equals(code, localCode)) {
            fail("IDENTITY_CODE_CONFLICT", user ? "account" : "externalCode", "%s「%s」与 localId「%s」的现有值「%s」不一致"
                    .formatted(user ? "用户名" : "组织编码", safe(code), safe(id), safe(localCode)));
        }
        if (user && !SysUser.isExternallyProvisioned((String) local.get("authentication_mode"))) {
            fail("SOURCE_BINDING_CONFLICT", "bindingChange.localId", "用户名「%s」的账号类型不支持绑定修复，仅支持外部预配账号".formatted(safe(code)));
        }
        if (previous != null) {
            if (!id.equals(previous.get("local_id"))) fail("SOURCE_BINDING_CONFLICT", "key", "%s已绑定其他本地记录，无法关联到 localId「%s」"
                    .formatted(sourceReference(key), safe(id)));
            return new BindingTarget(previous, local, false);
        }

        var bound = store.findLocal(type, id);
        if (oldKey == null) {
            if (bound != null) fail("SOURCE_BINDING_CONFLICT", "bindingChange.localId", "localId「%s」已有来源绑定。若要更换同来源外部 ID，请提交旧外部 ID 改绑"
                    .formatted(safe(id)));
        } else if (bound == null || !oldKey.equals(bound.get("source_key"))) {
            fail("SOURCE_BINDING_CONFLICT", "bindingChange.previousExternalId", "来源「%s」的旧外部 ID「%s」未绑定到 localId「%s」"
                    .formatted(safe(key.sourceCode()), safe(change.previousExternalId()), safe(id)));
        }
        snapshots.requireVacantKey(type, key);
        return new BindingTarget(bound, local, oldKey != null);
    }

    /** 同事务迁移绑定、原文和组织父级引用；调用前已校验旧版本及目标键占用情况。 */
    private void moveBinding(IdentityObjectType type, ExternalIdentityKey key, BindingTarget target) {
        if (!target.moving()) return;
        String oldKey = (String) target.previous().get("source_key");
        String newKey = IdentitySourceStore.hash(type, key);
        if (persistence.moveBinding(oldKey, newKey, key.externalId(), target.localId()) != 1) {
            fail("SOURCE_BINDING_CONFLICT", "bindingChange.previousExternalId", "localId「%s」的旧绑定已变更，请重新核对后提交改绑"
                    .formatted(safe(target.localId())));
        }
        snapshots.move(oldKey, newKey);
        if (type == IdentityObjectType.ORGANIZATION) {
            persistence.moveOrganizationParentReferences(oldKey, newKey, key.externalId());
        }
    }

    private record BindingTarget(Map<String, Object> previous, Map<String, Object> local, boolean moving) {
        boolean creating() { return previous == null && local == null; }
        boolean supplementing() { return previous == null && local != null; }
        String localId() { return (String) (previous == null ? local.get("id") : previous.get("local_id")); }

        /** 旧绑定缺失时无法区分两侧停用原因，沿用本地停用，等待管理员明确放行。 */
        boolean localAllowed() {
            if (previous != null) return IdentitySourceStore.flag(previous.get("local_allowed"));
            return creating() || "0".equals(local.get("status"));
        }
    }

    /**
     * 首次创建时原样使用来源编码，供各环境按相同用户名或组织编码解析配置引用。
     * 已有映射以首次保存的来源编码校验后续快照，防止改名使任务和配置中的引用失效。
     *
     * @param type 决定目标表与错误字段名
     * @param requested 已通过非空及长度校验的 account 或 externalCode
     * @param target 已定位的同步目标，补绑编码已经与本地对象核对
     * @return 原样传入的来源编码，供本地 username 或 orgCode 使用
     * @throws IdentityWriteException 来源编码已被占用或试图修改已有编码时拒绝写入
     */
    private String identityCode(IdentityObjectType type, String requested, BindingTarget target) {
        boolean user = type == IdentityObjectType.USER;
        String field = user ? "account" : "externalCode";
        String label = user ? "用户名" : "组织编码";
        var previous = target.previous();
        if (previous != null) {
            var current = previous.get("external_account");
            if (!Objects.equals(requested, current)) {
                fail("SOURCE_CODE_IMMUTABLE", field, "%s创建后固定，当前为「%s」，本次提交「%s」"
                        .formatted(label, safe(current), safe(requested)));
            }
        } else if (target.creating() && persistence.countIdentityCode(type, requested) > 0) {
            // 与数据库唯一索引使用相同的比较规则，并覆盖逻辑删除记录占用的编码。
            fail("IDENTITY_CODE_CONFLICT", field, "%s「%s」已被占用，请核对同步记录的外部 ID 和现有绑定"
                    .formatted(label, safe(requested)));
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
            fail("DEFAULT_ROLE_NOT_AVAILABLE", "defaultRoleCode", "同步来源配置的默认角色「%s」不存在或未启用，请检查 default-role-code"
                    .formatted(safe(roleCode)));
        }
        users.saveUserRoles(userId, List.of(role.getId()));
    }

    private IdentitySaveResult previousResult(IdentityObjectType type, ExternalIdentityKey key, Long version,
                                               String digest, Map<String, Object> previous) {
        if (previous == null) return null;
        if (!"ACTIVE".equals(previous.get("binding_status"))) fail("SOURCE_OBJECT_RETIRED", "key", sourceReference(key) + "的绑定已退役");
        String id = (String) previous.get("local_id");
        var local = persistence.findObject(type, id);
        if (local == null) {
            missingLocalObject(type, key);
        }
        Long applied = previous.get("source_version") == null ? null : ((Number) previous.get("source_version")).longValue();
        if ((applied == null) != (version == null)) fail("SOURCE_VERSION_CONFLICT", "sourceVersion",
                "%s的已同步版本为 %s，本次版本为 %s，版本化与无版本同步不能混用"
                        .formatted(sourceReference(key), safe(applied), safe(version)));
        IdentitySaveOutcome outcome = null;
        if (version != null && version < applied) outcome = IdentitySaveOutcome.STALE_IGNORED;
        else if (Objects.equals(previous.get("payload_hash"), digest) && Objects.equals(applied, version)) outcome = IdentitySaveOutcome.UNCHANGED;
        else if (version != null && version.equals(applied)) fail("SOURCE_VERSION_CONFLICT", "sourceVersion",
                "%s的版本 %d 已同步，本次内容与该版本不一致。如资料已变更，请提交更高版本"
                        .formatted(sourceReference(key), version));
        if (outcome == null) return null;
        String username = type == IdentityObjectType.USER ? (String) local.get("username") : null;
        return result(type, key, id, username, outcome, applied);
    }
    private void saveBinding(IdentityObjectType type, ExternalIdentityKey key, Long version, String digest,
                             String id, String account, boolean enabled, BindingTarget target) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("source_key", IdentitySourceStore.hash(type, key));
        values.put("source_code", key.sourceCode()); values.put("object_type", type.name());
        values.put("external_id", key.externalId()); values.put("local_id", id);
        values.put("external_account", account); values.put("source_version", version);
        values.put("payload_hash", digest); values.put("source_enabled", enabled);
        if (target.previous() == null) {
            values.put("local_allowed", target.localAllowed());
            persistence.insertBinding(values);
        } else {
            persistence.updateBinding(values);
        }
    }

    /** 主表扩展采用完整组快照；省略本组时沿用旧摘要，避免升级后旧调用误报同版本冲突。 */
    private String digest(Object base, Map<String, Map<String, Object>> groups, IdentityMainFields mainFields) {
        try {
            ObjectMapper canonical = mapper.copy().configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
                    .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("base", base); payload.put("extensions", groups);
            if (mainFields != null) payload.put("mainFields", mainFields);
            return IdentitySourceStore.digest(canonical.writeValueAsString(payload));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) { throw new IllegalStateException("无法规范化写入快照", e); }
    }
    private void recordSave(String actor, IdentityObjectType type, String id, ExternalIdentityKey key, BindingTarget target) {
        if (target.moving() || target.supplementing()) {
            recordBindingChange(actor, type, key, target);
            return;
        }
        audit.record(SystemAuditEvent.builder().operatorId(actor).module(AuditModule.SYSTEM).action(AuditAction.UPSERT)
                .operationName("来源身份写入").riskLevel(AuditRiskLevel.HIGH).result(AuditResult.SUCCESS).required(true)
                .targetType(type == IdentityObjectType.USER ? "SYS_USER" : "SYS_ORGANIZATION").targetId(id)
                .summary("来源=" + key.sourceCode()).build());
    }

    /** 审计记录可定位新旧键和本地主键；摘要不写入可能包含个人信息的外部 ID 原值。 */
    private void recordBindingChange(String actor, IdentityObjectType type, ExternalIdentityKey key, BindingTarget target) {
        String summary = "来源=" + key.sourceCode() + ", sourceKey=" + IdentitySourceStore.hash(type, key);
        if (target.moving()) summary += ", previousSourceKey=" + target.previous().get("source_key");
        audit.record(SystemAuditEvent.builder().operatorId(actor).module(AuditModule.SYSTEM).action(AuditAction.UPSERT)
                .operationName(target.moving() ? "来源身份改绑" : "来源身份补绑")
                .riskLevel(AuditRiskLevel.HIGH).result(AuditResult.SUCCESS).required(true)
                .targetType(type == IdentityObjectType.USER ? "SYS_USER" : "SYS_ORGANIZATION").targetId(target.localId())
                .summary(summary).build());
    }
    private void validateUser(UserBaseData base) {
        text(base.account(), 50, true, "account"); text(base.nickname(), 50, true, "nickname");
        text(base.email(), 100, false, "email"); text(base.phone(), 20, false, "phone");
        text(base.avatar(), 255, false, "avatar");
    }
    /** 不裁剪或转换业务编码；按字符数限制，保留前导零及原有空白供精确查询。 */
    private void validateMainFields(IdentityMainFields fields) {
        if (fields == null) return;
        text(fields.ext1(), 500, false, "mainFields.ext1");
        text(fields.ext2(), 500, false, "mainFields.ext2");
        text(fields.ext3(), 500, false, "mainFields.ext3");
        text(fields.ext4(), 500, false, "mainFields.ext4");
        text(fields.ext5(), 500, false, "mainFields.ext5");
    }
    private void text(String value, int max, boolean required, String field) {
        if (required && (value == null || value.isBlank())) {
            fail("INVALID_MODEL", field, "字段 %s 为必填项，不能为空白".formatted(field));
        }
        int length = value == null ? 0 : value.codePointCount(0, value.length());
        if (length > max) {
            fail("INVALID_MODEL", field, "字段 %s 最多 %d 个字符，本次提交了 %d 个字符".formatted(field, max, length));
        }
    }

    private void missingLocalObject(IdentityObjectType type, ExternalIdentityKey key) {
        fail("SOURCE_OBJECT_RETIRED", "key", "%s对应的本地%s已删除或不存在"
                .formatted(sourceReference(key), type == IdentityObjectType.USER ? "用户" : "组织"));
    }

    private String sourceReference(ExternalIdentityKey key) {
        return "来源「%s」的外部 ID「%s」".formatted(safe(key.sourceCode()), safe(key.externalId()));
    }
    private IdentitySaveResult result(IdentityObjectType type, ExternalIdentityKey key, String id, String username,
                                      IdentitySaveOutcome outcome, Long version) {
        return new IdentitySaveResult(type, key.sourceCode(), key.externalId(), id, username, outcome, version);
    }
    private static void fail(String code, String field, String message) { throw new IdentityWriteException(code, field, message); }
}
