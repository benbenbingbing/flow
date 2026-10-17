# 自定义业务模块

每个 `biz-*` 目录是完整的 npm workspace，代码、扩展声明、测试和说明都在模块内维护。
产品公共包位于 `../packages/`，业务只使用这些包的公开入口和宿主能力。

```text
custom/biz-project/
├── package.json              # @flow/biz-project，公开各端子入口
├── src/                      # shared/api/pc/mobile
├── manifests/                # 同一份清单声明 PC 与移动实现
├── tests/
└── README.md
```

新增模块使用 `custom/biz-名称/`，包名为 `@flow/biz-名称`，并提供 `manifests/`。
前端根 workspace 已覆盖 `custom/*`；从前端根执行 `npm install` 更新锁文件与本地链接，
在 `config/frontend-modules.json` 选择交付模块，然后运行 `npm run build`。
不需要在产品包、应用依赖或注册表再登记业务。

只创建实际需要的 `src` 子目录；扩展声明引用本模块 `exports` 公开的实现，
无需将业务清单复制到产品包。开发监听会追踪模块包描述、清单和源码的变化。

[完整开发约定与验证命令](../../docs/frontend-business-modules.md)
