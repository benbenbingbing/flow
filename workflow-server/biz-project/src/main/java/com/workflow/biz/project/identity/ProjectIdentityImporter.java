package com.workflow.biz.project.identity;

import com.workflow.port.system.SysUserPort;
import com.workflow.port.system.SysOrganizationPort;

import com.workflow.port.system.model.identity.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;

/** 业务接入示例：只转换业务快照并主动调用 Port，不注册调度、不替业务取数。 */
@Service
@RequiredArgsConstructor
public class ProjectIdentityImporter {
    private final SysUserPort users;
    private final SysOrganizationPort organizations;
    private static final String SOURCE = "project-directory";

    /**
     * 保存业务已获取的人员快照。组织须已通过同来源写入，externalId/版本须来自业务权威系统。
     * 返回稳定的本地身份供后续关联；异常直接交还业务，由调用方决定重试时机。
     */
    public IdentitySaveResult saveUser(DirectoryUser user) {
        return saveUser(user, null);
    }

    /**
     * 同时提交转换前的单条来源 JSON；必须由取数方提供原文，重新序列化 DirectoryUser 无法恢复未映射字段。
     * @param rawDataJson 可选单条原文，调用前剔除凭据；启用 raw-data-required 的来源必须传入
     * @return 基础资料写入结果，原文单独变化时仍返回 UNCHANGED
     */
    public IdentitySaveResult saveUser(DirectoryUser user, String rawDataJson) {
        return saveUser(user, rawDataJson, user.mainFields());
    }

    /** 显式指定本次提交的主表字段组，传 null 保留现值，携带时整组替换。 */
    public IdentitySaveResult saveUser(DirectoryUser user, String rawDataJson, IdentityMainFields mainFields) {
        return saveUser(user, rawDataJson, mainFields, null);
    }

    /** 受控修复可附带已核实的本地用户 ID；存在旧绑定时同时提供旧外部 ID。 */
    public IdentitySaveResult saveUser(DirectoryUser user, String rawDataJson, IdentityMainFields mainFields,
                                       IdentityBindingChange bindingChange) {
        // 零散可查询资料写主表槽位；独立业务扩展仍按原注册及建表约定接入。
        return users.saveUser(new UserSaveModel(new ExternalIdentityKey(SOURCE, user.externalId()), user.version(),
                new UserBaseData(user.account(), user.displayName(), user.email(), user.phone(), null,
                        user.organizationId(), user.departmentId(), user.enabled()), List.of(), rawDataJson, mainFields, bindingChange));
    }

    /** 保存业务组织快照；父级允许后到，框架保存来源引用并在祖先补齐后自动关联组织树。 */
    public IdentitySaveResult saveOrganization(DirectoryOrganization unit) {
        return saveOrganization(unit, null);
    }

    /** 提交组织及转换前的单条原文；乱序父级关联只更新派生结构，不改写此前留存的子级原文。 */
    public IdentitySaveResult saveOrganization(DirectoryOrganization unit, String rawDataJson) {
        return saveOrganization(unit, rawDataJson, unit.mainFields());
    }

    /** 显式指定本次提交的主表字段组，字段用途由业务约定，传 null 保留现值。 */
    public IdentitySaveResult saveOrganization(DirectoryOrganization unit, String rawDataJson, IdentityMainFields mainFields) {
        return saveOrganization(unit, rawDataJson, mainFields, null);
    }

    /** 受控修复复用现有组织主键，改绑由平台在组织树事务中迁移来源引用。 */
    public IdentitySaveResult saveOrganization(DirectoryOrganization unit, String rawDataJson, IdentityMainFields mainFields,
                                               IdentityBindingChange bindingChange) {
        return organizations.saveOrganization(new OrganizationSaveModel(new ExternalIdentityKey(SOURCE, unit.externalId()), unit.version(),
                new OrganizationBaseData(unit.code(), unit.name(), unit.type(), unit.parentId(), null,
                        null, null, null, null, 0, unit.enabled()), List.of(), rawDataJson, mainFields, bindingChange));
    }

    /**
     * organizationId/departmentId 是同来源的外部 ID，不能传产品本地 ID。
     * mainFields 由取数方显式映射，用于将来源资料同步到用户主表供查询和关联。
     */
    public record DirectoryUser(String externalId, long version, String account, String displayName,
            String email, String phone, String organizationId, String departmentId, boolean enabled,
            IdentityMainFields mainFields) {
        public DirectoryUser(String externalId, long version, String account, String displayName,
                String email, String phone, String organizationId, String departmentId, boolean enabled) {
            this(externalId, version, account, displayName, email, phone, organizationId, departmentId, enabled, null);
        }
    }

    /** mainFields 由取数方显式映射，用于将来源资料同步到组织主表供查询和关联。 */
    public record DirectoryOrganization(String externalId, long version, String code, String name,
            OrganizationUnitType type, String parentId, boolean enabled, IdentityMainFields mainFields) {
        public DirectoryOrganization(String externalId, long version, String code, String name,
                OrganizationUnitType type, String parentId, boolean enabled) {
            this(externalId, version, code, name, type, parentId, enabled, null);
        }
    }
}
