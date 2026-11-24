package com.workflow.admin.externalsystem.infrastructure.adapter;

import com.workflow.admin.externalsystem.api.response.ExternalSystemViews.ExternalSystemSummary;
import com.workflow.admin.externalsystem.application.ExternalSystemService;
import com.workflow.admin.externalsystem.application.ExternalSystemServiceAccess;
import com.workflow.port.system.SysExternalSystemPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** 为其他模块提供外部系统查询。读取对接参数前由调用方授权。 */
@Component
@RequiredArgsConstructor
public class SysExternalSystemAdapter implements SysExternalSystemPort {
    private final ExternalSystemService systems;
    private final ExternalSystemServiceAccess services;

    @Override
    public Page pageSystems(int pageNumber, int pageSize, String systemName, String systemCode, String status) {
        var page = systems.page(pageNumber, pageSize, systemName, systemCode, status);
        return new Page(page.getRecords().stream().map(this::summary).toList(), page.getTotal(),
                page.getPageNum(), page.getPageSize());
    }

    @Override
    public Optional<SystemSummary> findById(String id) {
        return systems.findSummaryById(id).map(this::summary);
    }

    @Override
    public Optional<SystemSummary> findByCode(String systemCode) {
        return systems.findSummaryByCode(systemCode).map(this::summary);
    }

    @Override
    public Optional<Configuration> findConfigurationByCode(String systemCode) {
        return findConfiguration(systemCode);
    }

    @Override
    public Optional<Configuration> findConfigurationForService(String systemCode, String serviceCode, String credential) {
        String code = systemCode == null ? null : systemCode.trim();
        services.require(serviceCode, credential, code);
        return findConfiguration(code).filter(configuration ->
                code.equals(configuration.system().systemCode()) && "0".equals(configuration.system().status()));
    }

    private Optional<Configuration> findConfiguration(String systemCode) {
        return systems.findDetailByCode(systemCode).map(detail -> {
            var parameters = detail.parameters().stream()
                    .map(parameter -> new Parameter(parameter.nameEn(), parameter.nameZh(),
                            parameter.value(), parameter.sortOrder()))
                    .toList();
            var system = new SystemSummary(detail.id(), detail.systemCode(), detail.systemName(),
                    detail.status(), detail.address(), detail.description(), parameters.size(), detail.updateTime());
            return new Configuration(system, parameters);
        });
    }

    private SystemSummary summary(ExternalSystemSummary system) {
        return new SystemSummary(system.id(), system.systemCode(), system.systemName(), system.status(),
                system.address(), system.description(), system.parameterCount(), system.updateTime());
    }
}
