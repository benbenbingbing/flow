/**
 * 将鉴权文件流交给浏览器。宿主注入自己的 API 和错误提示，PC、Embed、移动端共享行为。
 * 新窗口在点击事件内创建，避免等到异步读取结束才打开而被浏览器拦截。
 */
export function createBrowserFileAccess(fileApi, {
  browser = globalThis.window, document = globalThis.document, urls = globalThis.URL,
  schedule = globalThis.setTimeout, notifyError = () => {}
} = {}) {
  const fileId = file => typeof file === 'string' ? file : file?.fileId || ''
  const fallbackName = file => file?.name || file?.originalName || '附件'
  function save(result, file) {
    const url = urls.createObjectURL(result.blob)
    try {
      const link = document.createElement('a')
      link.href = url
      link.download = result.filename || fallbackName(file)
      document.body.appendChild(link)
      try { link.click() } finally { link.remove() }
    } finally {
      // 浏览器开始读取下载资源需要时间，立即 revoke 会导致部分浏览器下载失败。
      schedule(() => urls.revokeObjectURL(url), 60000)
    }
  }
  async function access(file, download) {
    const id = fileId(file)
    if (!/^[A-Za-z0-9_-]{1,64}$/.test(id)) return false
    let popup
    try {
      if (!download) {
        popup = browser.open('about:blank', '_blank')
        if (!popup) throw new Error('请允许弹出窗口后重试预览')
        popup.opener = null
      }
      const result = await (download ? fileApi.download(id) : fileApi.preview(id))
      if (download || !result.inline) {
        popup?.close()
        save(result, file)
      } else if (!popup.closed) {
        const url = urls.createObjectURL(result.blob)
        try { popup.location.replace(url) }
        catch (error) { urls.revokeObjectURL(url); throw error }
        // 预览资源在窗口关闭后释放；不设固定过期时间，以便 PDF 后续翻页、打印。
        const release = () => {
          if (popup.closed) urls.revokeObjectURL(url)
          else schedule(release, 1000)
        }
        schedule(release, 1000)
      }
      return true
    } catch (error) {
      popup?.close()
      notifyError(error)
      return false
    }
  }
  return { preview: file => access(file, false), download: file => access(file, true) }
}
