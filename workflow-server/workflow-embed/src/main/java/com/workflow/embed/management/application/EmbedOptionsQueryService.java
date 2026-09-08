package com.workflow.embed.management.application;

import com.workflow.embed.management.domain.EmbedManagementModel.ApplicationOption;
import com.workflow.embed.management.domain.EmbedManagementModel.IdentityProviderOption;
import com.workflow.embed.management.domain.EmbedManagementModel.OptionsFilter;
import com.workflow.embed.management.domain.EmbedManagementModel.Page;
import com.workflow.embed.management.port.EmbedManagementRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 为嵌入管理名称选择提供最小只读投影，不借用更高权限的应用或身份源管理用例。 */
@Service
public class EmbedOptionsQueryService {

    private final EmbedManagementRepository repository;

    public EmbedOptionsQueryService(EmbedManagementRepository repository) {
        this.repository = repository;
    }

    /** 分页查询应用名称和可用性；保留过期项供现有配置回显，由调用方展示过期状态。 */
    @Transactional(readOnly = true)
    public Page<ApplicationOption> applications(OptionsFilter filter) {
        return repository.findApplicationOptions(normalize(filter));
    }

    /** 分页查询身份源名称、类型和状态，不加载验证配置。 */
    @Transactional(readOnly = true)
    public Page<IdentityProviderOption> identityProviders(OptionsFilter filter) {
        return repository.findIdentityProviderOptions(normalize(filter));
    }

    /** 限制查询成本并防止页码乘法溢出回到第一页；无效输入遵循管理 API 的 400 契约。 */
    private static OptionsFilter normalize(OptionsFilter filter) {
        if (filter == null || filter.pageNum() < 1
                || filter.pageSize() < 1 || filter.pageSize() > 100
                || ((long) filter.pageNum() - 1) * filter.pageSize() > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("分页参数超出范围");
        }
        String keyword = filter.keyword() == null ? null : filter.keyword().trim();
        if (keyword != null && keyword.length() > 128) {
            throw new IllegalArgumentException("keyword 最大长度为 128");
        }
        return new OptionsFilter(keyword == null || keyword.isEmpty() ? null : keyword,
                filter.status(), filter.pageNum(), filter.pageSize());
    }
}
