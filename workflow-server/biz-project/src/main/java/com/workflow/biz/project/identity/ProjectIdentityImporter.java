package com.workflow.biz.project.identity;

import com.workflow.contracts.identity.model.*;
import com.workflow.contracts.identity.port.IdentityWritePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;

/** 业务接入示例：只转换业务快照并主动调用 Port，不注册调度、不替业务取数。 */
@Service
@RequiredArgsConstructor
public class ProjectIdentityImporter {
    private final IdentityWritePort identities;
    private static final String SOURCE = "project-directory";

    /**
     * 保存业务已获取的人员快照。组织须已通过同来源写入，externalId/版本须来自业务权威系统。
     * 返回稳定的本地身份供后续关联；异常直接交还业务，由调用方决定重试时机。
     */
    public IdentitySaveResult saveUser(DirectoryUser user) {
        // 默认只保存产品基础信息；业务自行建表并声明扩展后，再按需携带扩展对象。
        return identities.saveUser(new UserSaveModel(new ExternalIdentityKey(SOURCE, user.externalId()), user.version(),
                new UserBaseData(user.account(), user.displayName(), user.email(), user.phone(), null,
                        user.organizationId(), user.departmentId(), user.enabled()), List.of()));
    }

    /** 保存业务组织快照；父级允许后到，框架保存来源引用并在祖先补齐后自动关联组织树。 */
    public IdentitySaveResult saveOrganization(DirectoryOrganization unit) {
        return identities.saveOrganization(new OrganizationSaveModel(new ExternalIdentityKey(SOURCE, unit.externalId()), unit.version(),
                new OrganizationBaseData(unit.code(), unit.name(), unit.type(), unit.parentId(), null,
                        null, null, null, null, 0, unit.enabled()), List.of()));
    }

    /** organizationId/departmentId 是同来源的外部 ID，不能传产品本地 ID。 */
    public record DirectoryUser(String externalId, long version, String account, String displayName,
            String email, String phone, String organizationId, String departmentId, boolean enabled) { }
    public record DirectoryOrganization(String externalId, long version, String code, String name,
            OrganizationUnitType type, String parentId, boolean enabled) { }
}
