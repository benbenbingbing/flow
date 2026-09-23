package com.workflow.entity.definition.infrastructure.persistence;

/** 将流程编号别名规范为活动绑定索引使用的正数 BIGINT；不执行 SQL。 */
public final class ProcessDefinitionBindingKey {
    private ProcessDefinitionBindingKey() {}

    /**
     * 接受 ASCII 十进制数字及外围空格、前导零，范围为 1..Long.MAX_VALUE。
     * 非法或越界编号返回 null，使索引等值查询无匹配；禁止数据库把 12abc、负数等宽松转成其他有效编号。
     */
    public static Long parse(String processDefinitionId) {
        if (processDefinitionId == null) return null;
        String value = processDefinitionId.trim();
        if (value.isEmpty()) return null;
        long result = 0;
        for (int i = 0; i < value.length(); i++) {
            char digit = value.charAt(i);
            if (digit < '0' || digit > '9') return null;
            // 先检查再累加，避免超长输入溢出成另一个流程编号；前导零自然归一。
            int number = digit - '0';
            if (result > (Long.MAX_VALUE - number) / 10) return null;
            result = result * 10 + number;
        }
        return result == 0 ? null : result;
    }
}
