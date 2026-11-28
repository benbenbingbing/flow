# 自定义业务模块

每个 `custom/biz-*` 是一个 npm workspace。模块内按扩展类型和注册名组织清单、实现和专属测试。

```text
custom/biz-project/
├── package.json
├── src/
│   ├── extensions/
│   │   └── forms/ProjectMemberChangeForm/
│   │       ├── ProjectMemberChangeForm.v1.extension.json
│   │       ├── pc/
│   │       ├── mobile/
│   │       ├── shared/
│   │       └── tests/
│   ├── shared/   # 多个扩展共用的模型，按需创建
│   └── api/      # 多个扩展共用的接口，按需创建
└── tests/
```

新模块使用 `@flow/biz-名称`，声明运行依赖和构建、开发、测试命令。从前端工作区更新依赖和锁文件，在 `config/frontend-modules.json` 选择交付模块。此后新增已有类型的扩展只改本模块，导入产品契约和宿主能力时使用公开 SDK。

[完整开发约定与验证命令](../../docs/frontend-business-modules.md)
