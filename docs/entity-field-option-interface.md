# 实体选择字段的外部选项接口

## 1. 实现并部署 Provider

在后端业务模块的 Spring 扫描包下实现 `com.workflow.contracts.entity.ui.spi.UiDataSourceProvider`，加 `@Component` 注册为 Spring Bean。`getCode()` 的返回值用于在扩展管理中选择 Provider，`execute(...)` 返回由 `value`（保存编码）和 `label`（显示名称）组成的数组。没有候选项时返回空列表。

```java
import com.workflow.contracts.entity.list.model.DataScopePlan;
import com.workflow.contracts.entity.ui.context.UiInvocationContext;
import com.workflow.contracts.entity.ui.spi.UiDataSourceProvider;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class WarehouseOptionsProvider implements UiDataSourceProvider {
    @Override
    public String getCode() {
        return "WAREHOUSE_OPTIONS_PROVIDER";
    }

    @Override
    public String getDisplayName() {
        return "仓库选项";
    }

    @Override
    public Object execute(UiInvocationContext context, DataScopePlan dataScopePlan,
                          Map<String, Object> configuration, Map<String, Object> input) {
        return List.of(Map.of("value", "WH-01", "label", "一号仓库"));
    }
}
```

接入真实业务时可在 `execute(...)` 中调用外部只读服务并转换返回结果。通过 `input.get("formData")` 读取当前表单业务参数，认证身份和数据范围分别使用 `context` 和 `dataScopePlan`。

## 2. 新增扩展接口

部署后打开「定制开发 → 扩展管理 → 新增扩展接口」，填写接口名称和编码，并按下表配置。

| 配置项 | 示例 |
| --- | --- |
| 实现类型 | 已注册 Provider |
| Provider | 仓库选项（`WAREHOUSE_OPTIONS_PROVIDER`） |
| 实现入口 | `WAREHOUSE_OPTIONS`。同一 Provider 有多个入口时，代码通过 `context.common().providerOperationCode()` 识别 |
| 数据影响 | 只读查询（`READ`） |
| 业务上下文 | 表单（`FORM`） |
| 作用范围 | 选择实体，加入需要绑定选项接口的实体。也可选择全部实体 |
| 状态 | 启用 |

展开「实现配置与输入输出契约」，在「输出 Schema」中填写数组契约，再保存扩展接口。

```json
{
  "type": "array",
  "items": {
    "type": "object",
    "properties": {
      "value": { "type": "string" },
      "label": { "type": "string" }
    },
    "required": ["value", "label"]
  }
}
```

## 3. 绑定到实体字段

1. 在实体设计中将未发布的单选或多选字段的「选项来源」设为「只读接口」，选择已启用的扩展接口，再保存并发布实体。已发布字段的来源和绑定会锁定，需要切换时请新增字段。
2. 在表单中使用该实体字段及下拉、单选或多选组件，发布后表单会自动继承接口。设计预览前先保存表单节点。

## 保存和回显

- 单选保存字符串编码，多选保存编码数组（动态业务表按 JSON 文本存储，不写入代码表关联）。编码区分大小写，允许字母、数字及 `._:/@-`，首字符为字母或数字，长度 1～128。多选不允许重复，最多 50 项，序列化后最长 2000 字符。
- 保存不请求候选接口、不判断编码是否仍在外部结果内；仍执行必填和格式校验。历史值找不到名称时保留并显示原编码。本期不提供外部多选的列表成员筛选、历史名称快照或「实体字段赋值」动作中的外部候选。
- 数据权限、计费和敏感审批不能把「下拉中可见」当作授权。此类业务应使用单独的服务端校验或源于可信本地映射的路由变量。

## 流程连线

在网关连线的「表达式」条件组选择实体字段：单选输入编码并用「等于编码」；多选逐个输入编码，选择「包含任一编码」或「包含全部编码」。网关和路由预览均对已写入的流程变量做精确比较，不调用选项接口。缺值或类型不符时不命中，建议配置人工复核默认连线。

如果编码随租户或业务上下文复用，条件同时判断对应上下文；需要按物料分类等业务属性路由时，另建可信流程变量。此功能不自动保存整份候选或替业务生成路由标签。
