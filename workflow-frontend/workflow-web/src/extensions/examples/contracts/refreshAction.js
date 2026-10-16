/** 列表 JS 动作示例：宿主不等待函数完成，因此异步错误在处理器中收口。 */
export async function refreshAction(context) {
  try {
    await context.refresh()
  } catch (error) {
    console.error('刷新失败', error)
  }
}
