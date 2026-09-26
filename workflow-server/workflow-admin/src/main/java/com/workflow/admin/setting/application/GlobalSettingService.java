package com.workflow.admin.setting.application;

import com.workflow.admin.setting.api.error.GlobalSettingException;
import com.workflow.admin.setting.api.request.GlobalSettingRequests;
import com.workflow.admin.setting.api.response.GlobalSettingView;
import com.workflow.admin.setting.api.response.MobileThemeView;

import com.workflow.contracts.audit.model.AuditRiskLevel;
import com.workflow.contracts.audit.model.AuditModule;
import com.workflow.contracts.audit.model.AuditAction;
import com.workflow.contracts.audit.annotation.SystemAudit;
import com.fasterxml.jackson.databind.JsonNode;
import com.workflow.admin.identity.user.infrastructure.persistence.mapper.SysUserMapper;
import com.workflow.admin.identity.user.infrastructure.persistence.record.SysUser;
import com.workflow.admin.security.context.UserContext;
import com.workflow.admin.setting.infrastructure.persistence.mapper.GlobalSettingMapper;
import com.workflow.admin.setting.infrastructure.persistence.record.GlobalSettingRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import static com.workflow.admin.setting.application.GlobalSettingRegistry.*;

/** 全局设置与个人偏好服务；个人归属始终从有效登录身份获取。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GlobalSettingService {
    private final GlobalSettingMapper mapper;
    private final SysUserMapper userMapper;
    private final GlobalSettingRegistry registry;

    /**
     * 读取当前用户的有效值，按个人、系统、程序默认值依次回退；读取不创建记录。
     *
     * @param key 键，后续用于授权校验、关联或幂等去重
     * @return 读取后的{@code mine}结果，供调用方继续处理
     */
    @Transactional(readOnly = true)
    public GlobalSettingView readMine(String key) {
        String actor = requireActor();
        Definition definition = requireReadable(key);
        return read(definition, USER, actor);
    }

    /**
     * 查询所有已注册的系统设置，包含没有覆盖记录的程序默认值。
     *
     * @return 全局设置视图集合，供调用方遍历或展示
     */
    @Transactional(readOnly = true)
    public List<GlobalSettingView> listSystem() {
        requireActor();
        Map<String, GlobalSettingRecord> rows = mapper.findAll(SYSTEM, "0").stream()
                .collect(Collectors.toMap(GlobalSettingRecord::getSettingKey, Function.identity()));
        return registry.all().stream().filter(item -> item.scopes().contains(SYSTEM))
                .map(item -> resolve(item, rows.get(item.key()), null, SYSTEM)).toList();
    }

    /**
     * 服务端读取系统有效值，不要求登录身份、不经过面向页面的脱敏视图。
     * 每次访问数据库以使密钥修改立即生效；未配置且没有默认值时拒绝业务操作。
     * 仅供内部业务调用，禁止将返回值透传至公共接口或日志。
     *
     * @param key 键，后续用于授权校验、关联或幂等去重
     * @return 读取后的系统值结果，供调用方继续处理
     */
    @Transactional(readOnly = true)
    public JsonNode readSystemValue(String key) {
        Definition definition = registry.require(key);
        requireScope(definition, SYSTEM);
        JsonNode value = validValue(definition, mapper.find(SYSTEM, "0", key));
        if (value == null) value = definition.defaultValue();
        if (value.isNull()) throw GlobalSettingException.invalid("请先在全局设置中配置有效的" + definition.name());
        return value.deepCopy();
    }

    /**
     * 公开外观只投影白名单字段；读取限定 SYSTEM，忽略任何遗留个人覆盖。
     *
     * @return 读取后的{@code mobile}{@code theme}结果，供调用方继续处理
     */
    @Transactional(readOnly = true)
    public MobileThemeView readMobileTheme() {
        JsonNode value = readSystemValue(MOBILE_THEME);
        return new MobileThemeView(value.path("version").intValue(), value.path("preset").textValue(),
                value.path("primaryColor").textValue(), value.path("backgroundColor").textValue(),
                value.path("surfaceColor").textValue());
    }

    /**
     * 保存当前用户的覆盖值；未知键、仅系统设置或过期版本均拒绝写入。
     *
     * @param key 键，后续用于授权校验、关联或幂等去重
     * @param request 本次请求，后续经校验后用于保存{@code mine}
     * @return 保存后的{@code mine}结果，供调用方继续处理
     */
    @Transactional(rollbackFor = Exception.class)
    public GlobalSettingView saveMine(String key, GlobalSettingRequests.Save request) {
        String actor = requireActor();
        Definition definition = requireReadable(key);
        save(definition, USER, actor, actor, request);
        return read(definition, USER, actor);
    }

    /**
     * 删除个人覆盖恢复继承，不把当前系统值复制成新的个人覆盖。
     *
     * @param key 键，后续用于授权校验、关联或幂等去重
     * @param request 本次请求，后续经校验后用于处理重置{@code mine}
     * @return 处理后的重置{@code mine}结果，供调用方继续处理
     */
    @Transactional(rollbackFor = Exception.class)
    public GlobalSettingView resetMine(String key, GlobalSettingRequests.Reset request) {
        String actor = requireActor();
        Definition definition = requireReadable(key);
        reset(definition, USER, actor, request);
        return read(definition, USER, actor);
    }

    /**
     * 管理员保存系统默认值，个人覆盖保持生效；系统操作接入现有审计事务。
     *
     * @param key 键，后续用于授权校验、关联或幂等去重
     * @param request 本次请求，后续经校验后用于保存系统
     * @return 保存后的系统结果，供调用方继续处理
     */
    @Transactional(rollbackFor = Exception.class)
    @SystemAudit(module = AuditModule.SYSTEM, action = AuditAction.UPSERT,
            operation = "保存全局设置", targetType = "SYS_GLOBAL_SETTING", targetIdArg = 0,
            risk = AuditRiskLevel.MEDIUM, required = true, captureArguments = false, captureResult = false)
    public GlobalSettingView saveSystem(String key, GlobalSettingRequests.Save request) {
        String actor = requireActor();
        Definition definition = registry.require(key);
        save(definition, SYSTEM, "0", actor, request);
        return read(definition, SYSTEM, "0");
    }

    /**
     * 管理员删除系统覆盖，未设置个人偏好的用户回到程序默认值。
     *
     * @param key 键，后续用于授权校验、关联或幂等去重
     * @param request 本次请求，后续经校验后用于处理重置系统
     * @return 处理后的重置系统结果，供调用方继续处理
     */
    @Transactional(rollbackFor = Exception.class)
    @SystemAudit(module = AuditModule.SYSTEM, action = AuditAction.DELETE,
            operation = "恢复全局设置默认值", targetType = "SYS_GLOBAL_SETTING", targetIdArg = 0,
            risk = AuditRiskLevel.MEDIUM, required = true, captureArguments = false, captureResult = false)
    public GlobalSettingView resetSystem(String key, GlobalSettingRequests.Reset request) {
        requireActor();
        Definition definition = registry.require(key);
        reset(definition, SYSTEM, "0", request);
        return read(definition, SYSTEM, "0");
    }

    /**
     * 读取全局设置；查询结果供调用方展示或继续处理。
     *
     * @param definition 定义，作为 {@code resolve} 的输入影响后续处理
     * @param scope 作用域，作为 {@code USER.equals} 的输入影响后续处理
     * @param owner 归属方，供本方法读取全局设置时使用
     * @return 读取后的全局设置结果，供调用方继续处理
     */
    private GlobalSettingView read(Definition definition, String scope, String owner) {
        // 仅系统设置必须忽略历史或手工写入的 USER 行，不能借继承流程绕过作用域限制。
        GlobalSettingRecord current = definition.scopes().contains(scope) ? mapper.find(scope, owner, definition.key()) : null;
        GlobalSettingRecord inherited = USER.equals(scope) && definition.scopes().contains(SYSTEM)
                ? mapper.find(SYSTEM, "0", definition.key()) : null;
        return resolve(definition, current, inherited, scope);
    }

    /**
     * 只对合法值进行继承判断，false/0/空字符串都是实际覆盖；对象按整项覆盖。
     *
     * @param definition 定义，作为 {@code validValue} 的输入影响后续处理
     * @param current 当前，作为 {@code validValue} 的输入影响后续处理
     * @param inherited {@code inherited}，作为 {@code validValue} 的输入影响后续处理
     * @param scope 作用域，供本方法解析全局设置时使用
     * @return 解析后的全局设置结果，供调用方继续处理
     */
    private GlobalSettingView resolve(Definition definition, GlobalSettingRecord current,
                                      GlobalSettingRecord inherited, String scope) {
        JsonNode value = validValue(definition, current);
        String source = scope;
        if (value == null) {
            value = validValue(definition, inherited);
            source = SYSTEM;
        }
        if (value == null) {
            value = definition.defaultValue().deepCopy();
            source = "DEFAULT";
        }
        return new GlobalSettingView(definition.key(), definition.name(), definition.remark(),
                definition.valueType().name(), definition.sensitive() ? null : value,
                definition.sensitive() ? null : definition.defaultValue().deepCopy(), source,
                definition.scopes().contains(USER), current == null ? null
                : new GlobalSettingView.StoredVersion(current.getId(), current.getVersion()),
                definition.sensitive(), !value.isNull());
    }

    /**
     * 处理有效值，并将结果传给后续步骤。
     *
     * @param definition 定义，作为 {@code registry.parse} 的输入影响后续处理
     * @param row 行，作为 {@code registry.parse} 的输入影响后续处理
     * @return 处理后的有效值结果，供调用方继续处理
     */
    private JsonNode validValue(Definition definition, GlobalSettingRecord row) {
        if (row == null) return null;
        try {
            // 同一键的个人和系统值必须保持同一业务类型，禁止借手工改类型绕过注册规则。
            if (!definition.valueType().name().equals(row.getSettingValueType())) {
                throw GlobalSettingException.invalid("设置值类型与注册定义不一致");
            }
            return registry.parse(definition, row.getSettingValue());
        } catch (GlobalSettingException exception) {
            // 历史或手工写入的非法文本不影响页面加载，不记录具体设置内容。
            log.warn("忽略非法设置值: key={}, scope={}, id={}", definition.key(), row.getScopeType(), row.getId());
            return null;
        }
    }

    /**
     * 校验期望记录后以唯一索引/CAS完成写入；首次创建竞争也返回统一的 409。
     *
     * @param definition 定义，作为 {@code requireScope} 的输入影响后续处理
     * @param scope 作用域，作为 {@code requireScope} 的输入影响后续处理
     * @param owner 归属方，作为 {@code mapper.find} 的输入影响后续处理
     * @param actor 操作人，作为 {@code row.setUpdatedBy} 的输入影响后续处理
     * @param request 本次请求，后续经校验后用于保存全局设置
     */
    private void save(Definition definition, String scope, String owner, String actor,
                      GlobalSettingRequests.Save request) {
        requireScope(definition, scope);
        if (request == null) throw GlobalSettingException.invalid("请求不能为空");
        String value = registry.parse(definition, request.settingValue()).toString();
        GlobalSettingRecord current = mapper.find(scope, owner, definition.key());
        checkExpected(current, request.expectedId(), request.expectedVersion());
        boolean create = current == null;
        GlobalSettingRecord row = create ? new GlobalSettingRecord() : current;
        row.setScopeType(scope);
        row.setOwnerId(owner);
        row.setSettingKey(definition.key());
        row.setName(definition.name());
        row.setSettingValueType(definition.valueType().name());
        row.setRemark(definition.remark());
        row.setSettingValue(value);
        row.setUpdatedBy(actor);
        row.setUpdateTime(LocalDateTime.now(ZoneOffset.UTC));
        if (create) {
            row.setVersion(0L);
            row.setCreatedBy(actor);
            row.setCreateTime(row.getUpdateTime());
            try {
                mapper.insert(row);
            } catch (DuplicateKeyException exception) {
                throw GlobalSettingException.conflict();
            }
        } else if (mapper.updateValue(row) != 1) {
            throw GlobalSettingException.conflict();
        }
    }

    /**
     * 处理重置，并将结果传给后续步骤。
     *
     * @param definition 定义，作为 {@code requireScope} 的输入影响后续处理
     * @param scope 作用域，作为 {@code requireScope} 的输入影响后续处理
     * @param owner 归属方，作为 {@code mapper.find} 的输入影响后续处理
     * @param request 本次请求，后续经校验后用于处理重置
     */
    private void reset(Definition definition, String scope, String owner, GlobalSettingRequests.Reset request) {
        requireScope(definition, scope);
        // 密钥没有通用默认值，恢复默认会使导出和验签失去稳定的系统身份。
        if (definition.sensitive()) throw GlobalSettingException.invalid("敏感设置不支持恢复默认值，请直接保存新值");
        if (request == null) throw GlobalSettingException.invalid("请求不能为空");
        GlobalSettingRecord current = mapper.find(scope, owner, definition.key());
        checkExpected(current, request.expectedId(), request.expectedVersion());
        if (current != null && mapper.deleteVersion(scope, owner, definition.key(),
                current.getId(), current.getVersion()) != 1) {
            throw GlobalSettingException.conflict();
        }
    }

    /**
     * 同时检查 ID 和版本，防止删除重建后版本归零造成旧请求误命中新行。
     *
     * @param current 当前，供本方法检查预期时使用
     * @param id 目标记录 ID，后续用于定位具体数据或配置
     * @param version 版本，供本方法检查预期时使用
     */
    private void checkExpected(GlobalSettingRecord current, String id, Long version) {
        if ((id == null) != (version == null) || (id != null && (id.isBlank() || version < 0))) {
            throw GlobalSettingException.invalid("期望记录 ID 和版本必须同时提供且合法");
        }
        if (current == null ? id != null : !Objects.equals(current.getId(), id)
                || !Objects.equals(current.getVersion(), version)) {
            throw GlobalSettingException.conflict();
        }
    }

    /**
     * 校验并获取可读；不满足约束时阻止后续处理。
     *
     * @param key 键，后续用于授权校验、关联或幂等去重
     * @return 校验并获取后的可读结果，供调用方继续处理
     */
    private Definition requireReadable(String key) {
        Definition definition = registry.require(key);
        if (!definition.clientReadable()) {
            throw new GlobalSettingException(403, "SETTING_FORBIDDEN", "该设置不允许个人页面读取");
        }
        return definition;
    }

    /**
     * 校验并获取作用域；不满足约束时阻止后续处理。
     *
     * @param definition 定义，供本方法校验并获取作用域时使用
     * @param scope 作用域，供本方法校验并获取作用域时使用
     */
    private void requireScope(Definition definition, String scope) {
        if (!definition.scopes().contains(scope)) {
            throw new GlobalSettingException(403, "SETTING_SCOPE_FORBIDDEN", "该设置不允许在此作用域修改");
        }
    }

    /**
     * 除拦截器认证外，再校验归属用户仍有效，避免内部调用写入无效用户。
     *
     * @return 校验并获取后的操作人文本，供调用方比较或展示
     */
    private String requireActor() {
        String id = UserContext.getUserId();
        SysUser user = id == null || id.isBlank() || "0".equals(id) ? null : userMapper.selectById(id);
        if (user == null || !"0".equals(user.getStatus()) || !Integer.valueOf(0).equals(user.getDeleted())) {
            throw new GlobalSettingException(403, "SETTING_USER_INVALID", "当前用户不存在或已停用");
        }
        return id;
    }
}
