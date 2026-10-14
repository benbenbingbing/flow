package com.workflow.contracts.entity.port;

import java.util.List;

/** 流程人员来源的有限关系路径。只传业务坐标，物理表列和访问范围由实体模块决定。 */
public interface EntityUserPathPort {
    int MAX_DEPTH = 8;
    int MAX_USERS = 200;

    /** 基于已发布定义编译路径；versions 为空用于设计/发布，非空则只读指定版本。 */
    PathDescription describe(String entityCode, List<Step> path, List<Version> versions);

    /** 逐级加载关系选项；prefix 可为空，已选用户节点仍可继续展开。只返回元数据。 */
    List<Option> options(String entityCode, List<Step> prefix);

    /** 从可信流程根记录读取全部人员；调用方须先验证流程实体绑定，禁止传任意数据范围。 */
    List<String> readUserKeys(String entityCode, String recordId, List<Step> path, List<Version> versions);

    /** 来源/目标编码用于跨环境依赖映射，执行时须与服务端关系定义一致，不能覆盖真实目标。 */
    record Step(String type, String code, String targetEntityCode, String sourceEntityCode) {
        public Step(String type, String code, String targetEntityCode) { this(type, code, targetEntityCode, null); }
    }

    /** 动态实体钉定发布历史及指纹；系统关系使用固定的服务端契约版本。 */
    record Version(String entityCode, String historyId, String schemaHash, Integer systemVersion) { }

    /** multiple 表示整条路径可能返回多人，不能只看最后一级字段。 */
    record PathDescription(String targetEntityCode, boolean multiple, boolean terminal, List<Version> versions, List<Step> path) { }

    /** disabledReason 非空时保留失效关系供解释；用户关系同时可结束且可继续展开。 */
    record Option(Step step, String label, String targetEntityCode, boolean multiple,
                  boolean terminal, String disabledReason) { }
}
