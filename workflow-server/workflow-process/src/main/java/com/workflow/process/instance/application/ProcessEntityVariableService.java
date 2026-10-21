package com.workflow.process.instance.application;

import com.workflow.contracts.entity.model.EntityRecordData;
import com.workflow.contracts.entity.port.EntityRecordQueryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/** 为流程补齐未随自定义字段载荷传递的已知实体系统字段。 */
@Service
@RequiredArgsConstructor
public class ProcessEntityVariableService {

    private final EntityRecordQueryPort records;

    /**
     * 从可信实体记录读取缺失的名称变量，兼容已发起但未保存 name 的旧实例。
     *
     * <p>本方法只读实体并返回补丁，不改写输入或 Flowable；下一节点预览可直接合并，
     * 正式办理再由调用方在事务中写入。已有 name（包括显式 null）代表有效变量，
     * 不覆盖它。只补已知字段，不能把表达式中任意拼错的变量转换为 null。</p>
     *
     * @param variables 可信流程变量快照；通过 entityCode/entityDataId 定位实体记录
     * @return 缺失 name 的补丁；记录名称为 null 时仍保留键，无需补齐时返回空映射
     * @throws IllegalStateException 坐标完整但关联实体记录不存在时抛出，避免错误选择路由
     */
    public Map<String, Object> missingRecordVariables(Map<String, Object> variables) {
        if (variables == null || variables.containsKey("name")
                || !(variables.get("entityCode") instanceof String entityCode) || entityCode.isBlank()
                || !(variables.get("entityDataId") instanceof String recordId) || recordId.isBlank()) {
            return Map.of();
        }
        EntityRecordData record = records.findById(entityCode, recordId);
        if (record == null) {
            throw new IllegalStateException("流程关联的实体记录不存在: " + entityCode + "/" + recordId);
        }
        Map<String, Object> missing = new LinkedHashMap<>();
        // UEL 区分“变量不存在”和“变量值为 null”；空名称也必须能参与 == / != 判断。
        missing.put("name", record.getName());
        return missing;
    }
}
