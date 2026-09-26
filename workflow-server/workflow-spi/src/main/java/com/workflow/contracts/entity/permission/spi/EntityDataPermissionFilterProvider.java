package com.workflow.contracts.entity.permission.spi;

import com.workflow.contracts.entity.permission.model.EntityActionRule;
import com.workflow.contracts.identity.model.IdentityUser;
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
    default void validate(String entityCode, EntityActionRule.RuleNode node) {
    }

    /**
     * 将节点编译为安全 SQL 片段。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param node 节点，供本方法转换为SQL时使用
     * @param user 目标用户信息，后续用于权限计算或业务规则判断
     * @return 不含外层括号的 SQL 条件片段
     */
    String toSql(String entityCode, EntityActionRule.RuleNode node, IdentityUser user);

    /**
     * 编译需要运行时值的扩展规则；参数容器与其他允许、拒绝、委托规则共享。
     * 动态值应写入 parameters 中未占用的键，以 #{permissionParameters.键名,jdbcType=VARCHAR}
     * 形式引用文本值，不得覆盖既有参数或把值拼入 SQL。默认兼容仅返回固定 SQL 的扩展。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param node 节点，供本方法转换为SQL时使用
     * @param user 目标用户信息，后续用于权限计算或业务规则判断
     * @param parameters 参数集合，供本方法转换为SQL时使用
     * @return 转换为后的SQL文本，供调用方比较或展示
     */
    default String toSql(String entityCode, EntityActionRule.RuleNode node, IdentityUser user,
                         Map<String, Object> parameters) {
        return toSql(entityCode, node, user);
    }
}
