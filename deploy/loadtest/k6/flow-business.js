import http from 'k6/http';
import { check, fail, sleep } from 'k6';
import { SharedArray } from 'k6/data';
import { Counter, Rate, Trend } from 'k6/metrics';

const apiBaseUrl = required('LOADTEST_API_BASE_URL').replace(/\/$/, '');
const healthUrl = required('LOADTEST_HEALTH_URL');
const profile = (__ENV.LOADTEST_PROFILE || 'smoke').toLowerCase();
const runId = sanitizeRunId(__ENV.LOADTEST_RUN_ID || `manual_${Date.now()}`);
const allowWrites = booleanEnv('LOADTEST_ALLOW_WRITES', false);
const enableFileLifecycle = booleanEnv('LOADTEST_ENABLE_FILE_LIFECYCLE', false);
const baseRate = integerEnv('LOADTEST_BASE_RATE', 8, 1);
const peakRate = integerEnv('LOADTEST_PEAK_RATE', baseRate * 3, baseRate);
const spikeRate = integerEnv('LOADTEST_SPIKE_RATE', baseRate * 6, peakRate);
const maxVus = integerEnv('LOADTEST_MAX_VUS', 120, 2);
const writePercent = allowWrites
  ? numberEnv('LOADTEST_WRITE_PERCENT', 3, 0, 30)
  : 0;
const authRatePerMinute = integerEnv('LOADTEST_AUTH_RATE_PER_MINUTE', 2, 1);
const tokenRefreshMs = integerEnv(
  'LOADTEST_TOKEN_REFRESH_SECONDS',
  600,
  60,
) * 1000;
const entityCode = (__ENV.LOADTEST_ENTITY_CODE || '').trim();
const entityListKey = (__ENV.LOADTEST_ENTITY_LIST_KEY || '').trim();
const summaryDirectory = __ENV.LOADTEST_SUMMARY_DIR || '/results';

const credentials = new SharedArray('load-test-credentials', () => {
  if (__ENV.LOADTEST_CREDENTIALS_FILE) {
    const parsed = JSON.parse(open(__ENV.LOADTEST_CREDENTIALS_FILE));
    if (!Array.isArray(parsed) || parsed.length === 0) {
      throw new Error('LOADTEST_CREDENTIALS_FILE must contain a non-empty JSON array');
    }
    return parsed.map(validateCredential);
  }
  return [validateCredential({
    username: __ENV.LOADTEST_USERNAME,
    password: __ENV.LOADTEST_PASSWORD,
  })];
});

const entityCreateBody = loadOptionalJson('LOADTEST_ENTITY_CREATE_BODY_FILE');
const entityUpdateBody = loadOptionalJson('LOADTEST_ENTITY_UPDATE_BODY_FILE');

if ((entityCreateBody || entityUpdateBody) && !entityCode) {
  throw new Error('LOADTEST_ENTITY_CODE is required when entity body files are configured');
}
if (allowWrites && entityCode && !entityCreateBody) {
  throw new Error('entity writes require LOADTEST_ENTITY_CREATE_BODY_FILE');
}

const businessErrors = new Rate('flow_business_errors');
const catastrophicErrors = new Rate('flow_catastrophic_errors');
const readDuration = new Trend('flow_read_duration', true);
const writeDuration = new Trend('flow_write_duration', true);
const authDuration = new Trend('flow_auth_duration', true);
const createdRecords = new Counter('flow_created_records');
const deletedRecords = new Counter('flow_deleted_records');
const cleanupFailures = new Counter('flow_cleanup_failures');
const tokenRefreshes = new Counter('flow_token_refreshes');

export const options = {
  scenarios: scenariosFor(profile),
  thresholds: {
    http_req_failed: [
      `rate<${numberEnv('LOADTEST_HTTP_ERROR_RATE', 0.005, 0, 1)}`,
    ],
    flow_business_errors: [
      `rate<${numberEnv('LOADTEST_BUSINESS_ERROR_RATE', 0.005, 0, 1)}`,
    ],
    flow_catastrophic_errors: [{
      threshold: 'rate<0.20',
      abortOnFail: true,
      delayAbortEval: '2m',
    }],
    flow_read_duration: [
      `p(95)<${integerEnv('LOADTEST_READ_P95_MS', 750, 1)}`,
      `p(99)<${integerEnv('LOADTEST_P99_MS', 2500, 1)}`,
    ],
    flow_write_duration: [
      `p(95)<${integerEnv('LOADTEST_WRITE_P95_MS', 1500, 1)}`,
      `p(99)<${integerEnv('LOADTEST_P99_MS', 2500, 1)}`,
    ],
    checks: ['rate>0.995'],
  },
  discardResponseBodies: false,
  insecureSkipTLSVerify: booleanEnv('LOADTEST_INSECURE_SKIP_TLS_VERIFY', false),
  noConnectionReuse: false,
  userAgent: `flow-loadtest/${runId}`,
  summaryTrendStats: ['avg', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

let accessToken = null;
let tokenIssuedAt = 0;

export function setup() {
  const response = http.get(healthUrl, {
    tags: { operation: 'health_preflight', kind: 'canary' },
    timeout: '10s',
  });
  if (response.status < 200 || response.status >= 300) {
    fail(`health preflight failed: status=${response.status}`);
  }
  const credential = credentials[0];
  const token = login(credential, 'login_preflight');
  if (!token) {
    fail('login preflight failed');
  }
  return { preflightToken: token };
}

export function mixedBusiness() {
  if (allowWrites && Math.random() * 100 < writePercent) {
    runWriteOperation();
  } else {
    runReadOperation();
  }
  sleep(randomBetween(0.05, 0.25));
}

export function loginTraffic() {
  const credential = credentialForVu();
  login(credential, 'login_traffic');
  sleep(randomBetween(0.2, 0.8));
}

export function healthCanary() {
  const response = http.get(healthUrl, {
    tags: { operation: 'health_canary', kind: 'canary' },
    timeout: '5s',
  });
  const ok = response.status >= 200 && response.status < 300;
  catastrophicErrors.add(!ok, { operation: 'health_canary' });
  check(response, { 'health canary is available': () => ok });
  sleep(10);
}

export function teardown() {
  // Per-iteration writes clean up immediately. cleanup.sh covers interrupted runs.
}

export function handleSummary(data) {
  const metrics = data.metrics || {};
  const value = (name, field) => metrics[name]?.values?.[field];
  const lines = [
    `run_id=${runId}`,
    `profile=${profile}`,
    `http_requests=${value('http_reqs', 'count') ?? 0}`,
    `http_failed_rate=${value('http_req_failed', 'rate') ?? 0}`,
    `business_error_rate=${value('flow_business_errors', 'rate') ?? 0}`,
    `read_p95_ms=${value('flow_read_duration', 'p(95)') ?? 'n/a'}`,
    `read_p99_ms=${value('flow_read_duration', 'p(99)') ?? 'n/a'}`,
    `write_p95_ms=${value('flow_write_duration', 'p(95)') ?? 'n/a'}`,
    `write_p99_ms=${value('flow_write_duration', 'p(99)') ?? 'n/a'}`,
    `created_records=${value('flow_created_records', 'count') ?? 0}`,
    `deleted_records=${value('flow_deleted_records', 'count') ?? 0}`,
    `cleanup_failures=${value('flow_cleanup_failures', 'count') ?? 0}`,
  ];
  return {
    stdout: `${lines.join('\n')}\n`,
    [`${summaryDirectory}/summary.json`]: JSON.stringify(data, null, 2),
    [`${summaryDirectory}/summary.txt`]: `${lines.join('\n')}\n`,
  };
}

function runReadOperation() {
  const operations = [
    ['current_user', '/auth/current'],
    ['permissions', '/auth/permissions'],
    ['group_list', '/system/group/list'],
    ['group_enabled', '/system/group/enabled'],
    ['dict_page', '/system/dict/page?pageNum=1&pageSize=20'],
    ['dict_list', '/system/dict/list'],
    ['user_page', '/system/user/page?pageNum=1&pageSize=20'],
    ['task_todo', '/process-task/todo?pageNum=1&pageSize=20'],
    ['task_done', '/process-task/done?pageNum=1&pageSize=20'],
    ['task_statistics', '/process-task/statistics'],
  ];
  if (entityCode) {
    const encoded = encodeURIComponent(entityCode);
    const listQuery = entityListKey
      ? `&listKey=${encodeURIComponent(entityListKey)}`
      : '';
    operations.push(
      ['entity_list', `/entity-data/entity/${encoded}?pageNum=1&pageSize=20${listQuery}`],
      ['entity_count', `/entity-data/entity/${encoded}/count`],
    );
  }
  const selected = operations[Math.floor(Math.random() * operations.length)];
  apiJson('GET', selected[1], null, selected[0], 'read');
}

function runWriteOperation() {
  const operations = [groupLifecycle, dictionaryLifecycle];
  if (enableFileLifecycle) operations.push(fileLifecycle);
  if (entityCode && entityCreateBody) operations.push(entityLifecycle);
  operations[Math.floor(Math.random() * operations.length)]();
}

function groupLifecycle() {
  const suffix = uniqueSuffix();
  const code = `load_${runId}_${suffix}`.slice(0, 96);
  const create = apiJson('POST', '/system/group', {
    groupName: `Load test ${suffix}`,
    groupCode: code,
    description: `Automated load test ${runId}`,
    sort: 9999,
    status: '0',
  }, 'group_create', 'write');
  const id = create.data?.id;
  if (!create.ok || !id) return;
  createdRecords.add(1, { resource: 'group' });
  try {
    apiJson('POST', `/system/group/${encodeURIComponent(id)}/status?status=1`, null,
      'group_disable', 'write');
    apiJson('GET', `/system/group/${encodeURIComponent(id)}`, null,
      'group_detail', 'read');
  } finally {
    const deleted = apiJson('POST', `/system/group/${encodeURIComponent(id)}/delete`, null,
      'group_delete', 'write');
    trackCleanup(deleted.ok, 'group');
  }
}

function dictionaryLifecycle() {
  const suffix = uniqueSuffix();
  const code = `load_${runId}_${suffix}`.slice(0, 96);
  const create = apiJson('POST', '/system/dict', {
    dictCode: code,
    dictName: `Load test ${suffix}`,
    description: `Automated load test ${runId}`,
    sort: 9999,
    status: '0',
  }, 'dict_create', 'write');
  const id = create.data?.id;
  if (!create.ok || !id) return;
  createdRecords.add(1, { resource: 'dictionary' });
  try {
    apiJson('POST', `/system/dict/${encodeURIComponent(id)}/status?status=1`, null,
      'dict_disable', 'write');
    apiJson('GET', `/system/dict/${encodeURIComponent(id)}`, null,
      'dict_detail', 'read');
  } finally {
    const deleted = apiJson('POST', `/system/dict/${encodeURIComponent(id)}/delete`, null,
      'dict_delete', 'write');
    trackCleanup(deleted.ok, 'dictionary');
  }
}

function fileLifecycle() {
  const token = ensureToken();
  if (!token) return;
  const content = `flow load test ${runId} ${uniqueSuffix()}\n`;
  const response = http.post(`${apiBaseUrl}/file/upload`, {
    file: http.file(content, `load-${runId}.txt`, 'text/plain'),
  }, requestParams('file_upload', token, false, 'write'));
  const parsed = recordResponse(response, 'file_upload', 'write');
  const url = parsed.data?.url;
  if (!parsed.ok || !url) return;
  createdRecords.add(1, { resource: 'file' });
  try {
    const preview = http.get(
      `${apiBaseUrl}/file/preview?url=${encodeURIComponent(url)}`,
      requestParams('file_preview', token, false, 'read'),
    );
    recordHttpOnlyResponse(preview, 'file_preview', 'read');
  } finally {
    const deleted = apiJson('POST', `/file?url=${encodeURIComponent(url)}`, null,
      'file_delete', 'write');
    trackCleanup(deleted.ok, 'file');
  }
}

function entityLifecycle() {
  const suffix = uniqueSuffix();
  const body = JSON.parse(JSON.stringify(entityCreateBody));
  body.entityCode = entityCode;
  body.title = body.title || `Load test ${runId} ${suffix}`;
  body.name = body.name || body.title;
  body.code = body.code || `load_${runId}_${suffix}`;
  const create = apiJson('POST', '/entity-data', body, 'entity_create', 'write');
  const id = create.data?.id;
  if (!create.ok || !id) return;
  createdRecords.add(1, { resource: 'entity' });
  const listQuery = entityListKey
    ? `?listKey=${encodeURIComponent(entityListKey)}`
    : '';
  try {
    apiJson('GET', `/entity-data/entity/${encodeURIComponent(entityCode)}/detail/${encodeURIComponent(id)}${listQuery}`,
      null, 'entity_detail', 'read');
    if (entityUpdateBody) {
      apiJson('POST', `/entity-data/entity/${encodeURIComponent(entityCode)}/detail/${encodeURIComponent(id)}/update${listQuery}`,
        entityUpdateBody, 'entity_update', 'write');
    }
  } finally {
    const deleted = apiJson('POST', `/entity-data/entity/${encodeURIComponent(entityCode)}/detail/${encodeURIComponent(id)}/delete${listQuery}`,
      null, 'entity_delete', 'write');
    trackCleanup(deleted.ok, 'entity');
  }
}

function apiJson(method, path, body, operation, kind) {
  let token = ensureToken();
  if (!token) return { ok: false, data: null, response: null };
  let response = sendJson(method, path, body, operation, kind, token);
  if (response.status === 401) {
    accessToken = null;
    token = ensureToken(true);
    if (!token) return { ok: false, data: null, response };
    response = sendJson(method, path, body, operation, kind, token);
  }
  return recordResponse(response, operation, kind);
}

function sendJson(method, path, body, operation, kind, token) {
  const payload = body === null || body === undefined ? null : JSON.stringify(body);
  return http.request(
    method,
    `${apiBaseUrl}${path}`,
    payload,
    requestParams(operation, token, true, kind),
  );
}

function requestParams(operation, token, json, kind) {
  const headers = {
    Authorization: `Bearer ${token}`,
    Accept: 'application/json',
    'X-Request-Id': `load-${runId}-${uniqueSuffix()}`,
  };
  if (json) headers['Content-Type'] = 'application/json';
  if (kind === 'write') {
    headers['X-Business-Trace-Key'] = `load_${runId}_${uniqueSuffix()}`;
  }
  return {
    headers,
    tags: { operation, kind },
    timeout: kind === 'write' ? '30s' : '15s',
  };
}

function recordResponse(response, operation, kind) {
  let payload = null;
  try {
    payload = response.json();
  } catch (_) {
    // The error metric below records malformed or non-JSON business responses.
  }
  const httpOk = response.status >= 200 && response.status < 300;
  const businessOk = payload !== null
    && [0, 200, '0', '200'].includes(payload.code);
  const ok = httpOk && businessOk;
  businessErrors.add(!ok, { operation, kind });
  catastrophicErrors.add(!httpOk, { operation, kind });
  durationMetric(kind).add(response.timings.duration, { operation });
  check(response, { [`${operation} succeeds`]: () => ok });
  return { ok, data: payload?.data, payload, response };
}

function recordHttpOnlyResponse(response, operation, kind) {
  const ok = response.status >= 200 && response.status < 300;
  businessErrors.add(!ok, { operation, kind });
  catastrophicErrors.add(!ok, { operation, kind });
  durationMetric(kind).add(response.timings.duration, { operation });
  check(response, { [`${operation} succeeds`]: () => ok });
  return ok;
}

function durationMetric(kind) {
  if (kind === 'write') return writeDuration;
  if (kind === 'auth') return authDuration;
  return readDuration;
}

function ensureToken(force = false) {
  if (!force && accessToken && Date.now() - tokenIssuedAt < tokenRefreshMs) {
    return accessToken;
  }
  const token = login(credentialForVu(), force ? 'login_refresh' : 'login_vu');
  if (token) {
    accessToken = token;
    tokenIssuedAt = Date.now();
    tokenRefreshes.add(1);
  }
  return token;
}

function login(credential, operation) {
  const response = http.post(
    `${apiBaseUrl}/auth/login`,
    JSON.stringify(credential),
    {
      headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
      tags: { operation, kind: 'auth' },
      timeout: '15s',
    },
  );
  const parsed = recordResponse(response, operation, 'auth');
  return parsed.ok ? parsed.data?.token : null;
}

function trackCleanup(ok, resource) {
  if (ok) {
    deletedRecords.add(1, { resource });
  } else {
    cleanupFailures.add(1, { resource });
  }
}

function credentialForVu() {
  const index = ((__VU || 1) - 1) % credentials.length;
  return credentials[index];
}

function validateCredential(value) {
  const username = String(value?.username || '').trim();
  const password = String(value?.password || '');
  if (!username || !password) {
    throw new Error('each load-test credential requires username and password');
  }
  return { username, password };
}

function loadOptionalJson(environmentName) {
  const file = (__ENV[environmentName] || '').trim();
  return file ? JSON.parse(open(file)) : null;
}

function scenariosFor(selectedProfile) {
  const profiles = {
    smoke: {
      duration: '2m',
      business: [{ name: 'smoke', start: '0s', duration: '2m', rate: 2 }],
    },
    baseline: {
      duration: '35m',
      business: [
        { name: 'warmup', start: '0s', duration: '5m', rate: Math.max(1, Math.ceil(baseRate / 2)) },
        { name: 'baseline', start: '5m', duration: '30m', rate: baseRate },
      ],
    },
    soak: {
      duration: '6h30m',
      business: [
        { name: 'warmup', start: '0s', duration: '15m', rate: Math.max(1, Math.ceil(baseRate / 2)) },
        { name: 'steady', start: '15m', duration: '1h30m', rate: baseRate },
        { name: 'peak', start: '1h45m', duration: '30m', rate: peakRate },
        { name: 'soak', start: '2h15m', duration: '3h45m', rate: baseRate },
        { name: 'spike', start: '6h', duration: '10m', rate: spikeRate },
        { name: 'recovery', start: '6h10m', duration: '20m', rate: baseRate },
      ],
    },
    stress: {
      duration: '1h',
      business: [
        { name: 'base', start: '0s', duration: '10m', rate: baseRate },
        { name: 'double', start: '10m', duration: '10m', rate: baseRate * 2 },
        { name: 'quadruple', start: '20m', duration: '10m', rate: baseRate * 4 },
        { name: 'maximum', start: '30m', duration: '10m', rate: spikeRate },
        { name: 'recovery', start: '40m', duration: '20m', rate: baseRate },
      ],
    },
    spike: {
      duration: '20m',
      business: [
        { name: 'before', start: '0s', duration: '5m', rate: baseRate },
        { name: 'spike', start: '5m', duration: '5m', rate: spikeRate },
        { name: 'after', start: '10m', duration: '10m', rate: baseRate },
      ],
    },
  };
  const definition = profiles[selectedProfile];
  if (!definition) {
    throw new Error(`unsupported LOADTEST_PROFILE: ${selectedProfile}`);
  }
  const scenarios = {};
  for (const phase of definition.business) {
    scenarios[`business_${phase.name}`] = {
      executor: 'constant-arrival-rate',
      exec: 'mixedBusiness',
      startTime: phase.start,
      duration: phase.duration,
      rate: phase.rate,
      timeUnit: '1s',
      preAllocatedVUs: Math.min(maxVus, Math.max(2, phase.rate * 2)),
      maxVUs: maxVus,
      gracefulStop: '1m',
      tags: { phase: phase.name },
    };
  }
  scenarios.authentication = {
    executor: 'constant-arrival-rate',
    exec: 'loginTraffic',
    startTime: '0s',
    duration: definition.duration,
    rate: authRatePerMinute,
    timeUnit: '1m',
    preAllocatedVUs: Math.min(maxVus, Math.max(2, authRatePerMinute)),
    maxVUs: maxVus,
    gracefulStop: '30s',
    tags: { phase: 'authentication' },
  };
  scenarios.canary = {
    executor: 'constant-vus',
    exec: 'healthCanary',
    startTime: '0s',
    duration: definition.duration,
    vus: 1,
    gracefulStop: '15s',
    tags: { phase: 'canary' },
  };
  return scenarios;
}

function required(name) {
  const value = (__ENV[name] || '').trim();
  if (!value) throw new Error(`${name} is required`);
  return value;
}

function booleanEnv(name, fallback) {
  const value = __ENV[name];
  if (value === undefined || value === '') return fallback;
  if (value === 'true') return true;
  if (value === 'false') return false;
  throw new Error(`${name} must be true or false`);
}

function integerEnv(name, fallback, minimum) {
  const value = Number.parseInt(__ENV[name] || `${fallback}`, 10);
  if (!Number.isInteger(value) || value < minimum) {
    throw new Error(`${name} must be an integer >= ${minimum}`);
  }
  return value;
}

function numberEnv(name, fallback, minimum, maximum = Number.MAX_SAFE_INTEGER) {
  const value = Number(__ENV[name] || fallback);
  if (!Number.isFinite(value) || value < minimum || value > maximum) {
    throw new Error(`${name} must be between ${minimum} and ${maximum}`);
  }
  return value;
}

function sanitizeRunId(value) {
  const normalized = value.toLowerCase().replace(/[^a-z0-9_-]/g, '_').slice(0, 40);
  if (!normalized) throw new Error('LOADTEST_RUN_ID has no usable characters');
  return normalized;
}

function uniqueSuffix() {
  return `${__VU || 0}_${__ITER || 0}_${Math.floor(Math.random() * 1e9).toString(36)}`;
}

function randomBetween(minimum, maximum) {
  return minimum + Math.random() * (maximum - minimum);
}
