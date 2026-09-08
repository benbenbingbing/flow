-- 客户端可为每次 Launch 选择表单展示方式，并将选择固定到浏览器 Session。
-- 默认 seamless 兼容未传参数的调用方以及迁移前已存在的 Launch/Session。
ALTER TABLE `embed_launch`
  ADD COLUMN `ui_form_presentation` varchar(16) CHARACTER SET utf8mb4
    COLLATE utf8mb4_bin NOT NULL DEFAULT 'seamless' AFTER `ui_theme`,
  ADD CONSTRAINT `chk_embed_launch_form_presentation`
    CHECK (`ui_form_presentation` IN ('seamless','dialog'));

ALTER TABLE `embed_session`
  ADD COLUMN `ui_form_presentation` varchar(16) CHARACTER SET utf8mb4
    COLLATE utf8mb4_bin NOT NULL DEFAULT 'seamless' AFTER `ui_theme`,
  ADD CONSTRAINT `chk_embed_session_form_presentation`
    CHECK (`ui_form_presentation` IN ('seamless','dialog'));
