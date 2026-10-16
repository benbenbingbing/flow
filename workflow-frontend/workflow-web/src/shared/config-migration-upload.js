/**
 * 仅在后端完成包体校验并返回来源确认提示后重试。
 * 捕获的 file 和来源在两次请求中保持一致，确认摘要只对这一次上传生效。
 * 返回 null 表示用户取消；校验失败或上传失败继续交由调用方处理。
 */
export async function uploadWithSignatureConfirmation(file, sourceEnvironment, upload, confirm) {
  const result = await upload(file, sourceEnvironment)
  if (!result?.confirmationRequired) return result
  if (!await confirm(result.message)) return null
  const imported = await upload(file, sourceEnvironment, result.checksum)
  if (imported?.confirmationRequired) throw new Error('发布包仍需确认，请重新上传')
  return imported
}
