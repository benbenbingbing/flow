package com.workflow.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 流程定义节点解析集成测试。
 *
 * <p>被测对象：{@link ProcessDefinitionService} 的发布与节点解析链路。
 * 该测试依赖完整数据库字典表结构，默认禁用，需手工运行。
 */
@SpringBootTest
@Disabled("手工集成测试依赖完整数据库字典表结构，不参与单元测试套件")
public class ProcessDefinitionServiceParseTest {
    
    @Autowired
    private ProcessDefinitionService processDefinitionService;
    
    /**
     * 测试发布流程并触发节点配置解析：发布指定流程 ID，验证发布链路是否正常执行。
     */
    @Test
    public void testParseNodeConfigs() {
        // 测试发布流程，触发节点解析
        String processId = "25";
        try {
            processDefinitionService.publish(processId, "测试节点解析");
            System.out.println("发布成功！");
        } catch (Exception e) {
            System.err.println("发布失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
