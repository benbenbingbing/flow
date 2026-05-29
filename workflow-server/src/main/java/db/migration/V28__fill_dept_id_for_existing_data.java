package db.migration;

/**
 * 已废弃：Flyway Java Migration 依赖包路径问题，改为 Spring CommandLineRunner 实现。
 * 实际逻辑见 com.workflow.runner.DeptIdDataFixRunner
 */
public class V28__fill_dept_id_for_existing_data {
    // 占位类，避免编译失败
}
