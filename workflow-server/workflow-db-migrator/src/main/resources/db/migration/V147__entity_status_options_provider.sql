-- 平台只读选项接口；发布快照使用此稳定 ID。业务状态仍按表单所属实体实时查询。
INSERT INTO ui_extension_definition (
    id, extension_type, extension_key, display_name, version, snapshot_version,
    visibility_scope, implementation_type, provider_code, scope_type,
    interface_kind, interface_context_type, provider_operation_code, output_schema_document,
    status, revision, create_time, update_time, deleted
) VALUES (
    'platform_entity_status_options', 'INTERFACE', 'entity_status_options', '实体状态', 1, 1,
    'GLOBAL', 'REGISTERED_PROVIDER', 'entity_status_options', 'GLOBAL',
    'READ', 'FORM', 'list',
    '{"type":"array","items":{"type":"object","properties":{"value":{"type":"string"},"label":{"type":"string"}},"required":["value","label"]}}',
    'ACTIVE', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
);
