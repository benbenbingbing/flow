package com.workflow.entity.permission.application;

import com.workflow.entity.permission.api.response.EntityActionRuleDTO;
import com.workflow.admin.identity.user.infrastructure.persistence.record.SysUser;
import java.util.Map;

/**
 * 数据权限过滤节点扩展点。
 */
public interface EntityDataPermissionFilterProvider {

    /**
     * 自定义节点类型，建议使用命名空间大写形式。
     *
     * @return 读取后的类型文本，供调用方比较或展示
     */
    String getType();

    /**
     * 校验节点配置的合法性，默认空实现。
     *
     * @param entityCode 实体编码
     * @param node       待校验的规则节点
     */
    default void validate(String entityCode, EntityActionRuleDTO.RuleNode node) {
    }

    /**
     * 将节点编译为安全 SQL 片段。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param node 节点，供本方法转换为SQL时使用
     * @param user 目标用户信息，后续用于权限计算或业务规则判断
     * @return 不含外层括号的 SQL 条件片段
     */
    String toSql(String entityCode, EntityActionRuleDTO.RuleNode node, SysUser user);

    /**
     * 编译需要运行时值的扩展规则；参数容器与其他允许、拒绝、委托规则共享。
     * 新扩展可使用 PermissionSqlParameters 分配绑定值；默认兼容仅返回固定 SQL 的旧扩展。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param node 节点，供本方法转换为SQL时使用
     * @param user 目标用户信息，后续用于权限计算或业务规则判断
     * @param parameters 参数集合，供本方法转换为SQL时使用
     * @return 转换为后的SQL文本，供调用方比较或展示
     */
    default String toSql(String entityCode, EntityActionRuleDTO.RuleNode node, SysUser user,
                         Map<String, Object> parameters) {
        return toSql(entityCode, node, user);
    }
}
