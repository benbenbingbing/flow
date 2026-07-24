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

/**
 * 流程节点审批选项服务。
 *
 * <p>管理审批节点可用的审批操作选项（如同意、驳回等），支持从配置文档解析替换、
 * 批量保存与删除，供前端审批按钮渲染使用。</p>
 */
@Service
@RequiredArgsConstructor
public class ProcessNodeApprovalOptionService {

    /** 审批选项文档解析的类型引用 */
    private static final TypeReference<List<Map<String, Object>>> OPTIONS_TYPE =
            new TypeReference<>() {};

    private final ProcessNodeApprovalOptionMapper optionMapper;
    private final JsonDocumentCodec codec;

    /**
     * 查询指定审批配置下的审批选项列表。
     *
     * @param approvalConfigId 审批配置ID，为空时返回空列表
     * @return 审批选项列表
     */
    public List<Map<String, Object>> findOptions(String approvalConfigId) {
        if (!StringUtils.hasText(approvalConfigId)) {
            return List.of();
        }
        return optionMapper.findByApprovalConfigId(approvalConfigId).stream()
                .map(this::toMap)
                .toList();
    }

    /**
     * 从 JSON 文档解析审批选项并整体替换指定审批配置的选项。
     *
     * @param approvalConfigId 审批配置ID
     * @param document         审批选项JSON文档
     */
    @Transactional(rollbackFor = Exception.class)
    public void replaceFromDocument(String approvalConfigId, String document) {
        List<Map<String, Object>> options = StringUtils.hasText(document)
                ? codec.read(document, OPTIONS_TYPE, "流程审批选项")
                : List.of();
        replace(approvalConfigId, options);
    }

    /**
     * 整体替换指定审批配置的审批选项（先删后插）。
     *
     * @param approvalConfigId 审批配置ID
     * @param options          审批选项列表，为空则仅清空
     */
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

    /**
     * 删除指定审批配置下的全部审批选项。
     *
     * @param approvalConfigId 审批配置ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(String approvalConfigId) {
        optionMapper.deleteByApprovalConfigId(approvalConfigId);
    }

    /** 将审批选项实体转换为前端可用的Map结构（合并文档与标准字段） */
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

    /** 将对象安全转换为去除首尾空白的字符串，为 null 时返回 null */
    private String text(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }
}
