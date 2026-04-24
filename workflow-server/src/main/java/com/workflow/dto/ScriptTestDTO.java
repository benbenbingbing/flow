package com.workflow.dto;

import lombok.Data;
import java.util.Map;

/**
 * 脚本测试请求DTO
 */
@Data
public class ScriptTestDTO {
    
    /**
     * 脚本类型：javascript、groovy、python
     */
    private String scriptFormat;
    
    /**
     * 脚本内容
     */
    private String script;
    
    /**
     * 结果变量名
     */
    private String resultVariable;
    
    /**
     * 测试变量（模拟流程变量）
     */
    private Map<String, Object> testVariables;
}
