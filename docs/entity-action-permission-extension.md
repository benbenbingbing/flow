# 实体操作权限扩展

## 后端扩展

### 自定义权限选项

实现 `EntityPermissionOptionProvider` 并注册为 Spring Bean：

```java
@Component
public class ContractPermissionProvider implements EntityPermissionOptionProvider {
    @Override
    public List<EntityPermissionOptionDTO> getOptions(String entityCode) {
        return List.of(new EntityPermissionOptionDTO(
                "archive",
                "contract:" + entityCode + ":archive",
                "归档",
                "归档合同数据",
                "CUSTOM"));
    }

    @Override
    public boolean supportsPermission(String entityCode, String permissionCode) {
        return permissionCode.equals("contract:" + entityCode + ":archive");
    }
}
```

### 自定义规则条件

实现 `EntityActionRuleConditionProvider`：

```java
@Component
public class CustomerLevelConditionProvider implements EntityActionRuleConditionProvider {
    @Override
    public String getType() {
        return "CRM:CUSTOMER_LEVEL";
    }

    @Override
    public boolean evaluate(
            EntityActionRuleDTO.RuleNode node,
            EntityDataDTO row,
            SysUser user,
            String statusCategory) {
        return Objects.equals(row.getData().get("customerLevel"), node.getValue());
    }
}
```

### 自定义后端动作

自定义 Controller 或 Service 必须调用统一入口：

```java
entityActionCapabilityService.requireCustomAction(
        entityCode,
        "archive",
        permissionCode,
        availabilityRule,
        entityData);
```

## 前端扩展

### 权限选项

```js
registerEntityPermissionOptionProvider(({ entityCode }) => [
  {
    action: 'archive',
    code: `entity:${entityCode}:custom:archive`,
    label: '归档',
    description: '归档当前数据',
    category: 'CUSTOM'
  }
])
```

### 规则条件编辑器

```js
registerEntityActionRuleCondition({
  type: 'CRM:CUSTOMER_LEVEL',
  label: '客户等级',
  component: CustomerLevelRuleEditor,
  createDefault: () => ({
    operator: 'EQ',
    value: 'VIP'
  })
})
```

自定义列表组件可以通过上下文中的 `canAction(row, buttonKey)` 和
`getActionReason(row, buttonKey)` 复用后端返回的能力结果。
