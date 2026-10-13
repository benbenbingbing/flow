package com.workflow.entity.excel.config;

import com.workflow.contracts.entity.ui.port.FormDeletionReferencePort;
import com.workflow.entity.excel.persistence.ExcelConfigurationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.List;

/** 接入既有表单删除协议；外层持有全局表单发布锁，与 Excel 方案发布串行。 */
@Component
@RequiredArgsConstructor
public class ExcelFormDeletionReference implements FormDeletionReferencePort {
    private final ExcelConfigurationRepository repository;
    @Override
    public List<String> references(String formId) {
        return repository.publishedSchemeUsesForm(formId) ? List.of("Excel 导入方案写入表单") : List.of();
    }
}
