package com.workflow.project.custom;

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

    @Override
    public boolean supportsPermission(
            String entityCode,
            String permissionCode) {
        return StringUtils.hasText(entityCode)
                && permissionCode(entityCode)
                        .equals(permissionCode);
    }

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

    public String permissionCode(String entityCode) {
        String normalized =
                EntityPermissionAction
                        .normalizeEntityCode(entityCode);
        return "entity:" + normalized + ":" + SUFFIX;
    }
}
