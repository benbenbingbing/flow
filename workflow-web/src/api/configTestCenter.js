import request from '@/utils/request'

export function listConfigTestSuites(params) {
  return request({ url: '/api/config-test-center/suites', method: 'get', params })
}

export function getConfigTestSuite(suiteId) {
  return request({ url: `/api/config-test-center/suites/${suiteId}`, method: 'get' })
}

export function saveConfigTestSuite(data) {
  return request({ url: '/api/config-test-center/suites', method: 'post', data })
}

export function generateConfigTestSuite(data) {
  return request({ url: '/api/config-test-center/suites/generate', method: 'post', data })
}

export function deleteConfigTestSuite(suiteId) {
  return request({ url: `/api/config-test-center/suites/${suiteId}/delete`, method: 'post' })
}

export function runConfigTestSuite(suiteId, triggerType = 'MANUAL') {
  return request({
    url: `/api/config-test-center/suites/${suiteId}/run`,
    method: 'post',
    params: { triggerType }
  })
}

export function quickRunConfigTests(data) {
  return request({ url: '/api/config-test-center/quick-run', method: 'post', data })
}

export function listConfigTestRuns(suiteId) {
  return request({ url: `/api/config-test-center/suites/${suiteId}/runs`, method: 'get' })
}

export function getConfigTestReport(runId) {
  return request({ url: `/api/config-test-center/runs/${runId}`, method: 'get' })
}

export function getConfigTestGate(scopeType, scopeId) {
  return request({ url: `/api/config-test-center/gates/${scopeType}/${scopeId}`, method: 'get' })
}
