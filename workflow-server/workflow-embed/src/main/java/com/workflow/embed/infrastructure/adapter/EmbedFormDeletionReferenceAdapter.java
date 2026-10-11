package com.workflow.embed.infrastructure.adapter;
import com.workflow.contracts.entity.ui.port.FormDeletionReferencePort;
import com.workflow.embed.infrastructure.persistence.mapper.EmbedRuntimeReleaseMapper;
import org.springframework.stereotype.Component;
import java.util.List;
/** 已发布 Embed 视图对稳定表单身份的删除保护。 */
@Component
public class EmbedFormDeletionReferenceAdapter implements FormDeletionReferencePort {
    private final EmbedRuntimeReleaseMapper mapper;
    public EmbedFormDeletionReferenceAdapter(EmbedRuntimeReleaseMapper mapper) { this.mapper = mapper; }
    @Override public List<String> references(String formId) {
        return mapper.findFormReferences(formId).stream().map(id -> "Embed 视图 " + id).toList();
    }
}
