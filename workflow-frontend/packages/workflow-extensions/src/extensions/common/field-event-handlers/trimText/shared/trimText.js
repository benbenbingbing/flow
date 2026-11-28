/** 可选择的公共处理函数；返回值不会自动回填，必须通过 ctx 的受控 helper 写入。 */
export function trimText({ value, setValue }) {
  if (typeof value === 'string') setValue(value.trim())
}
