/**
 * 同步工厂示例：创建一次无状态校验器；逐次参数从 context.params 读取。
 * services 可由应用安装入口传入，例如纯格式化函数；不要在工厂阶段请求接口。
 * validate 仍允许返回 Promise，但工厂本身必须同步完成。
 */
export function createTextLengthValidator({ services = {} } = {}) {
  const format = services.formatLengthError || (max => `文本不能超过 ${max} 个字符`)
  return {
    validate(value, { params = {} } = {}) {
      if (value === null || value === undefined || value === '') return true
      const max = Number(params.maxLength ?? 50)
      if (!Number.isInteger(max) || max < 1) throw new Error('maxLength 必须为正整数')
      if (typeof value !== 'string') return '请输入文本'
      return [...value].length <= max || format(max)
    }
  }
}
