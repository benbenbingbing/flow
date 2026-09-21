# 通用校验器

AmountValidator 实现 validate(value, context)，额度来自 context.params.maxAmount。空值交给必填规则；无全局状态或请求。清单注册为 amount@1，规则仅在表单字段绑定后执行。
