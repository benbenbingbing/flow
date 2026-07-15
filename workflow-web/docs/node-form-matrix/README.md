# 节点表单多维验收

## 结论

- 新增数据使用流程首个可达用户任务配置的表单。
- 节点配置专属表单时，流程到达该节点展示对应表单。
- 节点未配置表单时，回退实体默认表单。
- 节点允许编辑时，保留表单字段自身的只读配置。
- 节点配置全局只读时，表单内所有字段均不可编辑。
- 审批提交仅保存后端根据发布快照判定为可编辑的字段，只读字段篡改不会落库。

## 真实流程矩阵

| 场景 | 期望 | 结果 |
| --- | --- | --- |
| 新增流程数据 | 使用首节点“首节点混合读写表单” | 通过 |
| 首节点指定表单 | 金额可编辑、锁定说明只读 | 通过 |
| 中间节点无表单 | 使用“默认回退表单” | 通过 |
| 默认回退字段权限 | 默认备注可编辑、锁定说明只读 | 通过 |
| 末节点指定表单 | 使用“末节点专属表单” | 通过 |
| 末节点全局只读 | 末节点说明、金额均只读 | 通过 |
| 数据保存 | 金额和默认备注保存 | 通过 |
| 只读防篡改 | 锁定说明和末节点说明保持原值 | 通过 |

## 数据结果

```json
{
  "amount": 66,
  "defaultMemo": "BROWSER_DEFAULT_UPDATED",
  "lockedNote": "LOCKED_ORIGINAL",
  "finalNote": "FINAL_ORIGINAL",
  "processStatus": "COMPLETED"
}
```

## 证据

- 接口全闭环：`latest.json`
- 浏览器验收夹具：`node-form-matrix-2607150445a7t.json`
- 新增首节点表单：`visual-new-data-first-node-form.png`
- 首节点混合读写：`visual-node1-mixed-readonly.png`
- 未配置节点默认回退：`visual-node2-default-fallback.png`
- 末节点全局只读：`visual-node3-global-readonly.png`
- 最终数据列表：`visual-final-data-list.png`
