package com.workflow.biz.project.custom;

import com.workflow.core.logging.LogValue;
import com.workflow.entity.permission.api.response.EntityPermissionOptionDTO;
import com.workflow.entity.permission.application.EntityPermissionAction;
import com.workflow.entity.permission.application.EntityPermissionOptionProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 实体自定义权限选项示例。
 *
 * <p>为每个动态实体增加“项目复核”权限，编码格式为
 * {@code entity:{entityCode}:custom:project-review}。</p>
 */
@Slf4j
@Component
public class ProjectCustomPermissionOptionProvider
        implements EntityPermissionOptionProvider {

    public static final String SUFFIX =
            "custom:project-review";

    /**
     * 读取选项；查询结果供调用方展示或继续处理。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @return 实体权限选项集合，供调用方遍历或展示
     */
    @Override
    public List<EntityPermissionOptionDTO> getOptions(
            String entityCode) {
        if (!StringUtils.hasText(entityCode)) {
            return List.of();
        }
        String code = permissionCode(entityCode);
        log.info(
                "项目实体权限选项目录加载: entityCode={}, permissionCode={}",
                LogValue.safe(entityCode),
                LogValue.safe(code));
        return List.of(new EntityPermissionOptionDTO(
                "project-review",
                code,
                "项目复核",
                "由项目自定义扩展提供的实体按钮权限",
                "PROJECT_CUSTOM"));
    }

    /**
     * 判断是否支持权限；判断结果决定调用方的后续分支。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param permissionCode 权限编码，后续用于判断是否支持权限时定位或关联目标
     * @return 权限条件成立时为 true，否则为 false
     */
    @Override
    public boolean supportsPermission(
            String entityCode,
            String permissionCode) {
        return StringUtils.hasText(entityCode)
                && permissionCode(entityCode)
                        .equals(permissionCode);
    }

    /**
     * 校验权限；不满足约束时阻止后续处理。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param permissionCode 权限编码，后续用于校验权限时定位或关联目标
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    @Override
    public void validatePermission(
            String entityCode,
            String permissionCode) {
        if (!supportsPermission(
                entityCode,
                permissionCode)) {
            throw new IllegalArgumentException(
                    "不是当前实体的项目复核权限: "
                            + permissionCode);
        }
        log.info(
                "项目实体权限校验通过: entityCode={}, permissionCode={}",
                LogValue.safe(entityCode),
                LogValue.safe(permissionCode));
    }

    /**
     * 生成权限编码文本，供后续匹配或展示。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @return 处理后的权限编码文本，供调用方比较或展示
     */
    public String permissionCode(String entityCode) {
        String normalized =
                EntityPermissionAction
                        .normalizeEntityCode(entityCode);
        return "entity:" + normalized + ":" + SUFFIX;
    }
}
