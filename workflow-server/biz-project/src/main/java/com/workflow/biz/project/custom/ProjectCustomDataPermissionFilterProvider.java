package com.workflow.biz.project.custom;

import com.workflow.contracts.identity.model.IdentityUser;
import com.workflow.core.logging.LogValue;
import com.workflow.contracts.entity.permission.model.EntityActionRule;
import com.workflow.contracts.entity.permission.spi.EntityDataPermissionFilterProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 数据权限 SQL 条件扩展示例。
 *
 * <p>类型为 {@value #TYPE}。示例固定返回 {@code 1=0}，用于验证自定义条件
 * 编译链路，同时保持默认拒绝；真实实现必须使用白名单字段和安全参数。</p>
 */
@Slf4j
@Component
public class ProjectCustomDataPermissionFilterProvider
        implements EntityDataPermissionFilterProvider {

    public static final String TYPE =
            "PROJECT:CUSTOM_FILTER";

    /**
     * 读取类型；查询结果供调用方展示或继续处理。
     *
     * @return 读取后的类型文本，供调用方比较或展示
     */
    @Override
    public String getType() {
        return TYPE;
    }

    /**
     * 校验项目自定义数据权限过滤提供者；不满足约束时阻止后续处理。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param node 节点，供本方法校验项目自定义数据权限过滤提供者时使用
     */
    @Override
    public void validate(
            String entityCode,
            EntityActionRule.RuleNode node) {
        log.info(
                "项目数据权限 SQL 条件校验: type={}, entityCode={}, field={}, operator={}",
                TYPE,
                LogValue.safe(entityCode),
                LogValue.safe(node == null
                        ? null : node.getField()),
                LogValue.safe(node == null
                        ? null : node.getOperator()));
    }

    /**
     * 转换为SQL；输出作为后续校验或处理的输入。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param node 节点，供本方法转换为SQL时使用
     * @param user 目标用户信息，后续用于权限计算或业务规则判断
     * @return 转换为后的SQL文本，供调用方比较或展示
     */
    @Override
    public String toSql(
            String entityCode,
            EntityActionRule.RuleNode node,
            IdentityUser user) {
        log.info(
                "项目数据权限 SQL 条件编译: type={}, entityCode={}, userId={}, result=DENY",
                TYPE,
                LogValue.safe(entityCode),
                LogValue.safe(user == null
                        ? null : user.id()));
        return "1=0";
    }
}
