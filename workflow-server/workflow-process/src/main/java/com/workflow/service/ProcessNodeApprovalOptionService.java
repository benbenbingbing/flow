package com.workflow.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.workflow.common.json.JsonDocumentCodec;
import com.workflow.entity.ProcessNodeApprovalOption;
import com.workflow.mapper.ProcessNodeApprovalOptionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ProcessNodeApprovalOptionService {

    private static final TypeReference<List<Map<String, Object>>> OPTIONS_TYPE =
            new TypeReference<>() {};

    private final ProcessNodeApprovalOptionMapper optionMapper;
    private final JsonDocumentCodec codec;

    public List<Map<String, Object>> findOptions(String approvalConfigId) {
        if (!StringUtils.hasText(approvalConfigId)) {
            return List.of();
        }
        return optionMapper.findByApprovalConfigId(approvalConfigId).stream()
                .map(this::toMap)
                .toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public void replaceFromDocument(String approvalConfigId, String document) {
        List<Map<String, Object>> options = StringUtils.hasText(document)
                ? codec.read(document, OPTIONS_TYPE, "流程审批选项")
                : List.of();
        replace(approvalConfigId, options);
    }

    @Transactional(rollbackFor = Exception.class)
    public void replace(String approvalConfigId, List<Map<String, Object>> options) {
        optionMapper.deleteByApprovalConfigId(approvalConfigId);
        int sort = 0;
        for (Map<String, Object> source : options == null
                ? List.<Map<String, Object>>of()
                : options) {
            String value = text(source.get("value"));
            if (!StringUtils.hasText(value)) {
                continue;
            }
            ProcessNodeApprovalOption option = new ProcessNodeApprovalOption();
            option.setApprovalConfigId(approvalConfigId);
            option.setOptionValue(value);
            option.setOptionLabel(StringUtils.hasText(text(source.get("label")))
                    ? text(source.get("label"))
                    : value);
            option.setStyleType(StringUtils.hasText(text(source.get("type")))
                    ? text(source.get("type"))
                    : "primary");
            option.setShowComment(!Boolean.FALSE.equals(source.get("showComment")));
            option.setRemarkRequired(Boolean.TRUE.equals(source.get("remarkRequired")));
            option.setSortOrder(sort++);
            option.setOptionDocument(codec.write(source, "流程审批选项"));
            option.setCreatedAt(LocalDateTime.now());
            option.setUpdatedAt(LocalDateTime.now());
            optionMapper.insert(option);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(String approvalConfigId) {
        optionMapper.deleteByApprovalConfigId(approvalConfigId);
    }

    private Map<String, Object> toMap(ProcessNodeApprovalOption option) {
        Map<String, Object> result = StringUtils.hasText(option.getOptionDocument())
                ? new LinkedHashMap<>(codec.readObject(
                        option.getOptionDocument(), "流程审批选项"))
                : new LinkedHashMap<>();
        result.put("value", option.getOptionValue());
        result.put("label", option.getOptionLabel());
        result.put("type", option.getStyleType());
        result.put("showComment", option.getShowComment());
        result.put("remarkRequired", option.getRemarkRequired());
        return result;
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }
}
