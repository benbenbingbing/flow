package com.workflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 流程配置系统主启动类
 * 
 * @description 基于Spring Boot和Flowable的工作流配置管理平台
 * @author Workflow Team
 * @version 1.0.0
 */
@SpringBootApplication
public class WorkflowApplication {

    /**
     * 应用程序入口方法
     * 
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(WorkflowApplication.class, args);
    }
}
