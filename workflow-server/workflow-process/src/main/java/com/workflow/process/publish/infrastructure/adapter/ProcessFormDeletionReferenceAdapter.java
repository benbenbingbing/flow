package com.workflow.process.publish.infrastructure.adapter;
import com.workflow.contracts.entity.ui.port.FormDeletionReferencePort;
import com.workflow.process.publish.infrastructure.persistence.mapper.ProcessUiReleaseBindingMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.List;
/** 保留所有已发布流程的稳定表单引用，避免删除后运行任务和历史详情无法展示。 */
@Component
@RequiredArgsConstructor
public class ProcessFormDeletionReferenceAdapter implements FormDeletionReferencePort {
    private final ProcessUiReleaseBindingMapper mapper;
    @Override public List<String> references(String formId) {
        return mapper.findByFormId(formId).stream().map(row -> "流程 " + row.getProcessKey()
                + " / " + row.getNodeName()).distinct().toList();
    }
}
