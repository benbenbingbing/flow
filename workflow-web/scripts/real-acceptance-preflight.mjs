const apiBase = process.env.API_BASE
  || process.env.WORKFLOW_API_BASE
  || 'http://localhost:8080/api'
const username = process.env.TEST_USERNAME?.trim()
const password = process.env.TEST_PASSWORD

if (!username || !password) {
  throw new Error(
    '真实验收必须显式设置 TEST_USERNAME 和 TEST_PASSWORD，未提供时不会创建测试数据'
  )
}

const response = await fetch(`${apiBase}/auth/login`, {
  method: 'POST',
  signal: AbortSignal.timeout(15000),
  headers: {
    'Content-Type': 'application/json'
  },
  body: JSON.stringify({ username, password })
})
const text = await response.text()
let payload
try {
  payload = text ? JSON.parse(text) : null
} catch {
  throw new Error(`登录预检返回非 JSON：HTTP ${response.status}`)
}

if (!response.ok || !payload || ![0, 200].includes(Number(payload.code))) {
  throw new Error(
    `登录预检失败：HTTP ${response.status}，${payload?.message || '未知错误'}`
  )
}

const user = payload.data
if (!user?.token) {
  throw new Error('登录预检未返回访问令牌')
}
if (user.passwordResetRequired) {
  throw new Error(`账号 ${username} 必须先完成密码修改，真实验收尚未开始`)
}

console.log('real acceptance preflight passed')
