package com.workflow.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@Disabled("手工集成测试依赖完整数据库字典表结构，不参与单元测试套件")
public class ProcessDefinitionServiceParseTest {
    
    @Autowired
    private ProcessDefinitionService processDefinitionService;
    
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
