package com.workflow.project.custom;

import com.workflow.contracts.migration.MigrationAssetRecorder;

/**
 * 旧版迁移资产登记端口的兼容替换示例。
 *
 * <p>该接口已经废弃，行为继承
 * {@link ProjectCustomMigrationAssetHandler}。类不注册为 Spring Bean，
 * 仅供仍依赖旧契约的业务迁移。</p>
 */
@Deprecated
@SuppressWarnings("deprecation")
public class ProjectCustomMigrationAssetRecorder
        extends ProjectCustomMigrationAssetHandler
        implements MigrationAssetRecorder {
}
