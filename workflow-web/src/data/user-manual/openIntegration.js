const fieldColumns = [
  { key: 'field', label: '配置项' },
  { key: 'purpose', label: '作用' },
  { key: 'rule', label: '填写规则 / 默认值' },
  { key: 'effect', label: '运行效果与注意事项' }
]

const optionColumns = [
  { key: 'option', label: '选项 / 状态' },
  { key: 'meaning', label: '含义' },
  { key: 'usage', label: '何时使用' }
]

const apiColumns = [
  { key: 'method', label: '方法' },
  { key: 'path', label: '路径' },
  { key: 'scope', label: '所需 Scope' },
  { key: 'purpose', label: '用途与关键要求' }
]

const tokenExample = `BASE_URL='https://flow.example.com'
CLIENT_ID='<创建应用时获得的 Client ID>'
CLIENT_SECRET='<仅显示一次的 Client Secret>'

curl --request POST "$BASE_URL/oauth2/token" \\
  --user "$CLIENT_ID:$CLIENT_SECRET" \\
  --header 'Content-Type: application/x-www-form-urlencoded' \\
  --data-urlencode 'grant_type=client_credentials' \\
  --data-urlencode 'scope=process.definition.read process.instance.start process.instance.read'`

const listDefinitionsExample = `ACCESS_TOKEN='<oauth2/token 返回的 access_token>'

curl --request GET \\
  "$BASE_URL/api/open/v1/process-definitions?limit=50" \\
  --header "Authorization: Bearer $ACCESS_TOKEN" \\
  --header 'X-Trace-Id: erp-sync-20260801-001'`

const startProcessExample = `curl --request POST "$BASE_URL/api/open/v1/process-instances" \\
  --header "Authorization: Bearer $ACCESS_TOKEN" \\
  --header 'Content-Type: application/json' \\
  --header 'Idempotency-Key: erp-order-20260801-10001-start' \\
  --header 'X-Trace-Id: erp-order-20260801-10001' \\
  --data '{
    "processKey": "order_change",
    "businessReference": {
      "system": "ERP",
      "type": "ORDER",
      "id": "20260801-10001"
    },
    "initiator": {
      "externalUserId": "erp-user-0098"
    },
    "variables": {
      "orderNo": "20260801-10001",
      "changeReason": "客户调整交付日期",
      "amount": 12800.50
    }
  }'`

const contractExample = `[
  {
    "processKey": "order_change",
    "inputSchema": {
      "$schema": "https://json-schema.org/draft/2020-12/schema",
      "type": "object",
      "additionalProperties": false,
      "maxProperties": 20,
      "required": ["orderNo", "changeReason"],
      "properties": {
        "orderNo": {
          "type": "string",
          "maxLength": 64
        },
        "changeReason": {
          "type": "string",
          "maxLength": 500
        },
        "amount": {
          "type": "number",
          "minimum": 0
        }
      }
    },
    "allowedMessageKeys": [
      "payment_received",
      "external_cancelled"
    ]
  }
]`

const correlateMessageExample = `PROCESS_INSTANCE_ID='<启动流程返回的 processInstanceId>'

curl --request POST \\
  "$BASE_URL/api/open/v1/process-instances/$PROCESS_INSTANCE_ID/messages/payment_received" \\
  --header "Authorization: Bearer $ACCESS_TOKEN" \\
  --header 'Content-Type: application/json' \\
  --header 'Idempotency-Key: erp-payment-20260801-10001' \\
  --data '{
    "variables": {
      "orderNo": "20260801-10001",
      "changeReason": "客户调整交付日期",
      "amount": 12800.50
    }
  }'`

const webhookVerifyExample = `import crypto from 'node:crypto'

export function verifyFlowWebhook({ rawBody, headers, signingSecret }) {
  const eventId = headers['flow-webhook-id']
  const timestamp = headers['flow-webhook-timestamp']
  const received = headers['flow-webhook-signature']
  const signed = Buffer.concat([
    Buffer.from(\`\${eventId}.\${timestamp}.\`, 'utf8'),
    rawBody
  ])
  const expected = 'v1=' + crypto
    .createHmac('sha256', signingSecret)
    .update(signed)
    .digest('base64')

  const left = Buffer.from(received || '')
  const right = Buffer.from(expected)
  return left.length === right.length
    && crypto.timingSafeEqual(left, right)
}`

const connectorExample = `{
  "baseUrl": "https://api.example.com",
  "operations": {
    "lookupOrder": {
      "method": "GET",
      "path": "/v1/orders",
      "query": {
        "orderNo": "$input.orderNo",
        "tenant": "$context.tenantId"
      },
      "headers": {
        "X-Organization-Id": "$context.organizationId"
      },
      "response": {
        "remoteId": "/data/id",
        "remoteStatus": "/data/status"
      },
      "acceptedStatuses": [200],
      "authentication": {
        "type": "BEARER",
        "secretRef": "secret://integration/<APPLICATION_ID>/partner-token"
      },
      "timeoutMs": 4000,
      "maxAttempts": 2
    },
    "createTicket": {
      "method": "POST",
      "path": "/v1/tickets",
      "body": {
        "/subject": "$input.subject",
        "/business/orderNo": "$input.orderNo",
        "/operator/userId": "$context.userId"
      },
      "response": {
        "ticketId": "/data/ticketId"
      },
      "acceptedStatuses": [200, 201, 202],
      "authentication": {
        "type": "HEADER",
        "headerName": "X-Api-Key",
        "secretRef": "secret://integration/<APPLICATION_ID>/partner-api-key"
      },
      "timeoutMs": 8000,
      "maxAttempts": 1
    }
  }
}`

export default {
  eyebrow: 'USER MANUAL · OPEN INTEGRATION',
  title: '开放集成用户手册',
  subtitle: '面向系统管理员与集成开发人员，详细说明接入应用、OAuth 2.0、开放流程 API、流程输入契约、Webhook、集成 Secret 和 HTTP Connector 的配置及运行规则。',
  version: '当前开放集成 V1 基线',
  updatedAt: '2026-08-01',
  intro: [
    {
      title: '先建立应用，再开放能力',
      type: 'info',
      text: '所有外部访问都归属于一个接入应用。应用同时承载机器身份、Scope、允许流程、来源网段、限流、并发、输入契约、Webhook、Secret 和 Connector，不能直接把管理员登录令牌交给外部系统。'
    },
    {
      title: '密钥只显示一次',
      type: 'warning',
      text: 'Client Secret、Webhook Signing Secret 和集成 Secret 明文只在创建或轮换完成后显示一次。关闭弹窗前必须存入企业密钥管理系统，平台后续只显示尾部提示。'
    },
    {
      title: '能力由部署环境控制',
      type: 'success',
      text: '页面能否使用 Open API、Webhook、Secret 和 Connector，取决于服务端能力开关及密钥材料。页面提示“能力未启用”时，应先由运维完成环境配置，而不是反复新建应用。'
    }
  ],
  sections: [
    {
      id: 'integration-overview',
      index: '01',
      title: '认识开放集成',
      summary: '理解它是什么、解决什么问题，以及四类能力如何协作。',
      topics: [
        {
          id: 'integration-problems',
          title: '开放集成解决什么问题',
          lead: '开放集成是平台与 ERP、CRM、门户、数据中台、消息系统及第三方 SaaS 之间的受控边界，不是简单的“开放几个接口”。',
          blocks: [
            {
              type: 'table',
              columns: optionColumns,
              rows: [
                { option: '机器身份隔离', meaning: '每个外部系统拥有独立 Client ID 和 Client Secret，不复用人员账号。', usage: '可单独停用、轮换和审计某个接入方，不影响其他系统。' },
                { option: '最小权限', meaning: 'Scope 控制接口动作，允许流程控制业务对象，消息白名单控制可关联的流程消息。', usage: '避免拿到令牌后访问全部流程或执行未授权动作。' },
                { option: '输入治理', meaning: '每个允许流程都有 JSON Schema 输入契约。', usage: '在流程启动前拒绝缺字段、错类型和多余字段，防止脏数据进入运行时。' },
                { option: '可靠通知', meaning: 'Webhook 对流程和任务事件进行签名投递、自动重试和死信重放。', usage: '替代外部系统高频轮询流程状态。' },
                { option: '安全出站调用', meaning: 'HTTP Connector 通过声明式映射、主机白名单和 Secret 引用调用外部接口。', usage: '流程或表单需要查询外部数据时，不暴露任意 URL、脚本或明文凭据。' },
                { option: '容量与审计', meaning: '应用级限流、并发限制、来源网段及系统审计共同生效。', usage: '降低误调用、流量突发、凭据泄漏和不可追溯操作的风险。' }
              ]
            }
          ]
        },
        {
          id: 'integration-capability-map',
          title: '四类能力与数据方向',
          blocks: [
            {
              type: 'table',
              columns: optionColumns,
              rows: [
                { option: 'Open API：外部系统 → 平台', meaning: '通过 OAuth 2.0 Client Credentials 获取机器令牌，查询流程定义、启动流程、查询实例与任务、关联消息。', usage: 'ERP 发起变更流程、门户查询审批状态、业务系统发送到账消息。' },
                { option: '输入契约：平台入口校验', meaning: '为每个允许流程定义 variables 的 JSON Schema 和可接收消息。', usage: '将外部数据格式变成可发布、可审查的明确契约。' },
                { option: 'Webhook：平台 → 外部系统', meaning: '平台把流程/任务事件按 CloudEvents JSON 发送到 HTTPS 端点。', usage: '完成后回写 ERP、任务创建时通知工作台、失败时触发告警。' },
                { option: 'HTTP Connector：平台 → 外部系统', meaning: '流程运行时或配置运行时按已审核模板同步调用外部 HTTP JSON 接口。', usage: '校验订单、查询客户信息、创建外部工单、获取风险等级。' }
              ]
            },
            {
              type: 'callout',
              tone: 'warning',
              title: '不要混用 Client Secret 与集成 Secret',
              text: 'Client Secret 只用于外部系统向 /oauth2/token 证明接入应用身份；集成 Secret 只供平台的 HTTP Connector 解析外部系统凭据。二者生命周期、用途和引用方式完全不同。'
            }
          ]
        },
        {
          id: 'integration-recommended-flow',
          title: '推荐实施顺序',
          blocks: [
            {
              type: 'steps',
              items: [
                { title: '确认集成边界', text: '列出外部系统、数据方向、允许流程、需要的接口动作和事件，不要先勾选全部权限。' },
                { title: '准备平台与流程', text: '发布目标流程，确认流程 Key 稳定；由运维启用所需能力并配置签名/加密密钥。' },
                { title: '创建接入应用', text: '配置最小 Scope、允许流程、来源 CIDR、请求上限和并发上限，妥善保存一次性凭据。' },
                { title: '配置输入契约', text: '为每个允许流程定义 JSON Schema，并仅开放流程模型实际等待的消息 Key。' },
                { title: '联调入站 API', text: '先获取令牌并查询流程目录，再使用幂等键启动测试流程和发送消息。' },
                { title: '配置出站能力', text: '按需配置 Webhook；需要同步调用时先建 Secret，再建 Connector，最后在平台配置中引用。' },
                { title: '小流量上线', text: '验证审计、traceId、限流、重试、告警和密钥轮换预案后再逐步放量。' }
              ]
            }
          ]
        }
      ]
    },
    {
      id: 'integration-preparation',
      index: '02',
      title: '权限与环境准备',
      summary: '先确认谁能看、谁能改，以及部署环境是否真正启用了对应能力。',
      topics: [
        {
          id: 'integration-management-permissions',
          title: '管理端权限',
          blocks: [
            {
              type: 'table',
              columns: optionColumns,
              rows: [
                { option: 'system:integration:view', meaning: '查看开放集成页面、应用、输入契约、Webhook、Secret 元数据、Connector 和能力状态。', usage: '集成只读审计人员。' },
                { option: 'system:integration:manage', meaning: '新建应用、修改访问策略和契约、启停应用、维护 Webhook 和 Connector。', usage: '集成管理员。' },
                { option: 'system:integration:secret-rotate', meaning: '轮换/吊销应用凭据和 Webhook 密钥，创建/轮换/吊销/销毁集成 Secret。', usage: '密钥管理员；应限制人数。' },
                { option: 'system:integration:delivery-replay', meaning: '对 DEAD 状态的 Webhook 投递创建人工重放。', usage: '集成运维人员；每次重放必须填写原因。' }
              ]
            },
            {
              type: 'callout',
              tone: 'info',
              title: '管理权限不等于开放接口 Scope',
              text: '上表控制登录用户在管理页面能做什么；Scope 控制外部应用的机器令牌能调用什么。给管理员授权不会自动给接入应用增加 Scope，反之亦然。'
            }
          ]
        },
        {
          id: 'integration-capability-switches',
          title: '环境能力开关',
          blocks: [
            {
              type: 'table',
              columns: fieldColumns,
              rows: [
                { field: 'WORKFLOW_OPEN_API_ENABLED', purpose: '启用 OAuth 2.0 机器认证和 /api/open/v1 接口。', rule: '默认 false。启用时必须同时准备 RSA 签名公私钥、key-id、issuer 和 audience。', effect: '关闭时管理页面仍可显示应用，但外部系统不能获取令牌或调用开放 API。' },
                { field: 'WORKFLOW_OPEN_API_WEBHOOK_ENABLED', purpose: '启用 Webhook 管理、事件物化和投递工作器。', rule: '默认 false；需要 WORKFLOW_OPEN_API_WEBHOOK_MASTER_KEY。', effect: '关闭时 Webhook 页签显示“能力未启用”。' },
                { field: 'WORKFLOW_INTEGRATION_CONNECTOR_HTTP_ENABLED', purpose: '启用集成 Secret 和 HTTP Connector。', rule: '默认 false；需要 Connector master key 与版本。', effect: '关闭时 Secret 和 Connector 页签不可用。' },
                { field: 'WORKFLOW_HTTP_ALLOWED_HOSTS', purpose: '部署级出站主机总白名单。', rule: '由运维配置；应用 Connector 的允许主机还必须落在该范围内。', effect: '形成“部署白名单 + Connector 白名单”双重限制。' },
                { field: 'WORKFLOW_HTTP_ALLOW_HTTP / WORKFLOW_HTTP_ALLOW_PRIVATE_ADDRESSES', purpose: '控制是否允许明文 HTTP 和私网地址。', rule: '默认均为 false。开放集成管理配置要求 baseUrl 和 Webhook 使用 HTTPS。', effect: '不要为方便联调在生产放开；建议使用可验证的 HTTPS 测试域名。' }
              ]
            }
          ]
        },
        {
          id: 'integration-prerequisites',
          title: '业务与网络前置检查',
          blocks: [
            {
              type: 'checklist',
              items: [
                '目标流程已经发布，并确认稳定的 processKey，而不是只知道流程显示名称。',
                '流程所需外部变量已经梳理，字段名、类型、必填性和最大长度有明确约定。',
                '需要消息关联时，BPMN 中存在对应消息等待点，消息 Key 与契约完全一致。',
                '外部调用出口 IP 稳定，能够填写准确 CIDR；经过代理时已正确配置受信代理。',
                'Webhook 接收端具备公网或平台可达的 HTTPS 地址，能读取原始请求体并校验 HMAC。',
                'Connector 目标域名固定，不依赖通配符、IP 或运行时拼接 URL。',
                '企业密钥管理系统已准备好保存一次性明文和记录轮换负责人。'
              ]
            }
          ]
        }
      ]
    },
    {
      id: 'integration-application',
      index: '03',
      title: '接入应用',
      summary: '创建机器身份，配置访问范围、容量和来源网络，并管理凭据生命周期。',
      topics: [
        {
          id: 'integration-create-application',
          title: '新建应用的每个配置项',
          blocks: [
            {
              type: 'table',
              columns: fieldColumns,
              rows: [
                { field: '应用名称', purpose: '管理端识别接入方的业务名称。', rule: '必填，最多 128 字符。建议使用“系统 + 环境 + 用途”，如 ERP-生产-订单变更。', effect: '只用于管理和审计，不作为 OAuth client_id。' },
                { field: '责任组织', purpose: '记录归口团队或组织标识。', rule: '可选，最多 64 字符。当前为文本标识，不会自动继承组织权限。', effect: '便于责任追踪；不要误认为填写后会替代 Scope 或流程授权。' },
                { field: '说明', purpose: '记录数据方向、业务范围、联系人和变更单。', rule: '可选，最多 500 字符。', effect: '建议注明生产/测试、调用系统和停用条件。' },
                { field: 'Scope', purpose: '控制令牌可以执行的开放接口动作。', rule: '至少 1 个，最多 5 个；默认选中流程目录、启动和实例读取。', effect: 'OAuth 请求只能申请应用已授予 Scope 的子集；接口还会再次检查对应 Scope。' },
                { field: '允许流程', purpose: '控制应用能看到和操作哪些流程 Key。', rule: '可选，最多 100 个；必须以字母开头，仅允许字母、数字、点、下划线和短横线。', effect: '留空表示未授权任何流程，即使有 Scope 也不能启动或查看流程目录。' },
                { field: '每分钟请求上限', purpose: '限制该应用调用 /api/open/** 的速率。', rule: '1–10000，默认 60。', effect: '超限返回 429、errorCode=RATE_LIMIT_EXCEEDED，并可能携带 Retry-After。令牌端点另有部署级限流。' },
                { field: '并发上限', purpose: '限制同一应用同时处理的开放 API 请求数。', rule: '1–1000，默认 10。', effect: '并发租约耗尽时返回 429；应通过队列和退避控制调用方并发。' },
                { field: '来源 CIDR', purpose: '只允许指定出口网段获取令牌和调用开放 API。', rule: '每行一个，最多 32 项，每项最多 64 字符；留空表示不限制。', effect: '地址不匹配返回 SOURCE_ADDRESS_NOT_ALLOWED。生产环境建议明确填写，不要长期留空。' },
                { field: '创建并签发凭据', purpose: '保存应用并创建首个 Client Secret。', rule: '创建成功后显示 Client ID 和 Client Secret。', effect: 'Client Secret 仅显示一次；关闭弹窗后无法找回，只能轮换。' }
              ]
            },
            {
              type: 'callout',
              tone: 'warning',
              title: '当前可编辑范围',
              text: '创建后，“访问策略”可以修改 Scope 与允许流程。当前管理页面不提供名称、责任组织、说明、来源 CIDR、请求上限和并发上限的编辑入口；这些字段如需变更，应按变更流程新建替代应用并完成凭据切换。'
            }
          ]
        },
        {
          id: 'integration-scope-reference',
          title: '五个 Scope 怎么选',
          blocks: [
            {
              type: 'table',
              columns: optionColumns,
              rows: [
                { option: 'process.definition.read', meaning: '查询已授权且已发布的流程定义及其输入 Schema。', usage: '调用方需要发现可启动流程、版本或契约时授予。' },
                { option: 'process.instance.start', meaning: '使用业务引用和 variables 启动流程实例。', usage: '外部系统作为流程发起方时授予。' },
                { option: 'process.instance.read', meaning: '读取由当前接入应用绑定的流程实例状态。', usage: '调用方需要同步查询运行/完成状态时授予。' },
                { option: 'process.task.read', meaning: '查询当前应用所启动实例的活动任务摘要。', usage: '外部工作台需要展示待处理环节时授予；它不等于任务办理权限。' },
                { option: 'process.message.correlate', meaning: '向当前应用绑定的实例发送契约允许的消息。', usage: '到账、外部完成、取消等外部事件驱动流程继续时授予。' }
              ]
            },
            {
              type: 'callout',
              tone: 'success',
              title: '最小授权示例',
              text: '只负责发起且接收 Webhook 的系统通常只需 process.definition.read + process.instance.start；不要因为“以后可能用到”一次授予全部 Scope。'
            }
          ]
        },
        {
          id: 'integration-application-status',
          title: '应用与凭据状态',
          blocks: [
            {
              type: 'table',
              columns: optionColumns,
              rows: [
                { option: 'ACTIVE / 启用', meaning: '应用可获取新令牌，并按授权访问开放接口。', usage: '正常运行状态。' },
                { option: 'DISABLED / 停用', meaning: '立即阻止新令牌签发和新的开放 API 调用，可由管理员重新启用。', usage: '临时维护、异常流量或待确认安全事件。' },
                { option: 'REVOKED / 已吊销', meaning: '应用进入不可恢复状态，活跃凭据同时被吊销。', usage: '接入永久下线或确认凭据泄漏。当前 UI 的常用操作是停用应用、轮换或吊销凭据。' },
                { option: '轮换凭据', meaning: '签发新 Client Secret，旧凭据立即失效。Client ID 保持不变。', usage: '定期轮换或怀疑泄漏；调用方必须安排原子切换，不能依赖重叠窗口。' },
                { option: '吊销凭据', meaning: '撤销当前活跃 Client Secret，不自动创建新凭据。', usage: '紧急止损。需要恢复时使用轮换/重新签发流程，并重新保存新密钥。' }
              ]
            },
            {
              type: 'callout',
              tone: 'info',
              title: '版本号与并发修改',
              text: '应用详情中的 vN 是配置乐观锁版本。访问策略、输入契约、状态和凭据操作都会携带 expectedVersion；出现版本冲突时先刷新应用详情，再确认是否覆盖其他管理员的修改。'
            }
          ]
        }
      ]
    },
    {
      id: 'integration-contracts',
      index: '04',
      title: '访问策略与输入契约',
      summary: '把“允许做什么”和“允许传什么数据”配置成可校验的边界。',
      topics: [
        {
          id: 'integration-access-policy',
          title: '访问策略',
          blocks: [
            {
              type: 'steps',
              items: [
                { title: '打开访问策略', text: '选择应用，点击右上角“操作 → 访问策略”。' },
                { title: '收缩或增加 Scope', text: '至少保留一个 Scope。减少 Scope 会影响后续令牌申请和接口授权。' },
                { title: '维护允许流程', text: '输入已发布流程的 processKey。流程显示名称不能代替 Key。' },
                { title: '同步输入契约', text: '允许流程变化后，再打开“输入契约”。契约列表必须与当前允许流程完整一一对应，不能缺少或多出流程。' },
                { title: '通知调用方', text: 'Scope、流程授权或 Schema 变化都属于接口契约变更，应在调用方验证后再上线。' }
              ]
            },
            {
              type: 'callout',
              tone: 'warning',
              title: '流程授权是第二道门',
              text: 'Scope 只说明“允许启动流程”这一类动作；允许流程决定“具体可以启动哪个流程”。两者必须同时满足。查询流程目录只返回授权集合中当前已发布、调用方可见的流程。'
            }
          ]
        },
        {
          id: 'integration-contract-document',
          title: '输入契约文档结构',
          lead: '“操作 → 输入契约”编辑的是 JSON 数组。每个允许流程必须恰好有一条契约。',
          blocks: [
            {
              type: 'table',
              columns: fieldColumns,
              rows: [
                { field: 'processKey', purpose: '关联允许流程。', rule: '必填；以字母开头，最长 100 字符，只允许字母、数字、点、下划线和短横线。', effect: '必须与访问策略中的允许流程完整对应。' },
                { field: 'inputSchema', purpose: '校验启动和消息关联请求中的 variables。', rule: 'JSON Schema Draft 2020-12；根节点必须是 object。', effect: '不符合契约时返回 422、VARIABLE_VALIDATION_FAILED，并列出最多 20 个 violations。' },
                { field: 'allowedMessageKeys', purpose: '限制该流程可从开放 API 接收的消息 Key。', rule: '数组，最多 100 个；每项以字母开头，最长 128 字符。没有消息能力时填 []。', effect: '即使有 process.message.correlate Scope，未列入此处的消息仍返回 403。' }
              ]
            },
            {
              type: 'code',
              title: '完整输入契约示例',
              language: 'json',
              code: contractExample
            }
          ]
        },
        {
          id: 'integration-schema-rules',
          title: 'inputSchema 规则与限制',
          blocks: [
            {
              type: 'table',
              columns: fieldColumns,
              rows: [
                { field: 'type=object', purpose: '确保 variables 是命名字段对象。', rule: '根节点必须设置；嵌套对象也应明确 type。', effect: '根类型不是 object 无法保存契约。' },
                { field: 'additionalProperties=false', purpose: '拒绝未声明字段。', rule: '每个 object Schema 都必须显式设置为 false。', effect: '防止调用方拼错字段名时被静默忽略。' },
                { field: 'maxProperties', purpose: '限制对象字段数量。', rule: '每个 object 必填，0–100。', effect: '控制复杂度和请求规模。' },
                { field: 'maxItems', purpose: '限制数组长度。', rule: '每个 array 必填，0–1000。', effect: '防止无边界数组进入流程变量。' },
                { field: '$ref', purpose: '复用 Schema 定义。', rule: '只允许以 #/ 开头的本地引用。', effect: '不允许运行时访问外部 Schema URL。' },
                { field: 'pattern / patternProperties', purpose: '正则约束。', rule: 'V1 不支持。可使用 enum、长度、数值范围等确定性约束。', effect: '包含正则约束时拒绝保存。' },
                { field: '复杂度', purpose: '保护校验器。', rule: '最多 65535 字节、深度 32、节点 2048。', effect: '超过限制时拒绝保存契约。' },
                { field: '保留变量', purpose: '防止覆盖流程引擎注入的上下文。', rule: '根 properties 不得声明 initiator、submitterId、entityCode、entityDataId、integrationApplicationId、integrationTraceId 等保留名。', effect: '平台会自行写入集成身份、业务引用和追踪信息。' }
              ]
            }
          ]
        },
        {
          id: 'integration-contract-change',
          title: '契约变更策略',
          blocks: [
            {
              type: 'bullets',
              items: [
                '新增非必填字段通常是兼容变更；调用方可逐步采用。',
                '新增 required 字段、缩短 maxLength、收紧 enum、删除字段或改变类型属于破坏性变更。',
                '破坏性变更优先发布新的 processKey 或安排明确切换窗口，不要在生产调用高峰直接保存。',
                '先通过 process.definition.read 获取平台返回的 inputSchema，再在调用方做同样校验，可更早发现问题。',
                '消息请求与启动请求共用同一 inputSchema；如果消息只携带少量字段，应设计能兼容两类调用的 Schema，或避免复用不合适的消息 variables。'
              ]
            }
          ]
        }
      ]
    },
    {
      id: 'integration-open-api',
      index: '05',
      title: 'OAuth 与开放流程 API',
      summary: '获取短期机器令牌，使用幂等请求调用流程接口，并正确处理分页和错误。',
      topics: [
        {
          id: 'integration-token',
          title: '获取 Access Token',
          blocks: [
            {
              type: 'table',
              columns: fieldColumns,
              rows: [
                { field: '地址', purpose: 'OAuth 2.0 Token Endpoint。', rule: 'POST /oauth2/token。', effect: '使用表单编码，不是 JSON。' },
                { field: '客户端认证', purpose: '证明接入应用身份。', rule: 'HTTP Basic：用户名为 Client ID，密码为 Client Secret。', effect: '不要把 Secret 放入 URL、日志或前端浏览器代码。' },
                { field: 'grant_type', purpose: '选择授权模式。', rule: '固定 client_credentials。', effect: '不涉及人员登录、授权码或刷新令牌。' },
                { field: 'scope', purpose: '声明本次令牌需要的权限。', rule: '空格分隔，只能申请应用已授予 Scope 的子集。', effect: '申请越权 Scope 返回 invalid_scope。' },
                { field: '有效期', purpose: '限制泄漏令牌的暴露窗口。', rule: '部署默认 10 分钟，以 expires_in 为准。', effect: '调用方应缓存令牌并在到期前适度刷新，不要每个业务请求都重新取令牌。' }
              ]
            },
            {
              type: 'code',
              title: 'Client Credentials 请求',
              language: 'bash',
              code: tokenExample
            },
            {
              type: 'callout',
              tone: 'warning',
              title: '令牌端点也受网络与限流保护',
              text: '令牌请求同样检查应用状态、凭据、来源地址，并受客户端与来源地址的部署级每分钟限制。大量并发线程不要各自刷新令牌，应由调用方共享令牌缓存。'
            }
          ]
        },
        {
          id: 'integration-api-reference',
          title: '开放 API 清单',
          blocks: [
            {
              type: 'table',
              columns: apiColumns,
              rows: [
                { method: 'GET', path: '/api/open/v1/process-definitions', scope: 'process.definition.read', purpose: '返回授权且已发布的流程目录和 inputSchema；支持 cursor、limit。' },
                { method: 'POST', path: '/api/open/v1/process-instances', scope: 'process.instance.start', purpose: '启动流程；必须提供 Idempotency-Key、业务引用和 variables。' },
                { method: 'GET', path: '/api/open/v1/process-instances/{id}', scope: 'process.instance.read', purpose: '查询当前应用绑定的流程实例；其他应用的实例按不存在处理。' },
                { method: 'GET', path: '/api/open/v1/process-instances/{id}/tasks', scope: 'process.task.read', purpose: '查询活动任务摘要；支持 cursor、limit，不提供任务办理能力。' },
                { method: 'POST', path: '/api/open/v1/process-instances/{id}/messages/{messageKey}', scope: 'process.message.correlate', purpose: '发送契约允许的流程消息；必须提供 Idempotency-Key。' }
              ]
            },
            {
              type: 'code',
              title: '查询流程目录',
              language: 'bash',
              code: listDefinitionsExample
            },
            {
              type: 'bullets',
              items: [
                '分页 limit 默认由服务端决定，允许 1–200；响应 metadata.nextCursor 非空时继续请求下一页。',
                'cursor 是不透明字符串，不要解析或自行计算；数据变化后游标可能失效，应从第一页重新获取。',
                '请求体最大 1 MiB，超过后返回 413、PAYLOAD_TOO_LARGE。',
                '所有响应包含 traceId；响应头还会返回追踪标识和 no-store，排障时优先保存 traceId。'
              ]
            }
          ]
        },
        {
          id: 'integration-start-process',
          title: '启动流程',
          blocks: [
            {
              type: 'table',
              columns: fieldColumns,
              rows: [
                { field: 'Idempotency-Key', purpose: '保证重试不会重复启动流程。', rule: '必填，1–128 个可打印 ASCII 字符；同一业务动作保持不变。', effect: '相同键和相同请求返回历史结果，并带 Idempotent-Replay: true；相同键换请求内容会冲突。' },
                { field: 'processKey', purpose: '指定要启动的流程。', rule: '必须已发布、已授权，并有输入契约。', effect: '无流程授权返回 PROCESS_NOT_GRANTED。' },
                { field: 'businessReference.system', purpose: '外部业务系统稳定编码。', rule: '以字母开头，最长 64；如 ERP、CRM。', effect: '与 type、id 共同建立平台实例与外部单据的唯一绑定。' },
                { field: 'businessReference.type', purpose: '外部业务对象类型。', rule: '以字母开头，最长 64；如 ORDER、CUSTOMER_CHANGE。', effect: '用于事件回传和审计。' },
                { field: 'businessReference.id', purpose: '外部业务对象主键。', rule: '必填，最多 128 字符。', effect: '重复绑定会返回 409；应使用稳定业务主键而不是随机请求号。' },
                { field: 'initiator.externalUserId', purpose: '记录外部发起人。', rule: '可选；填写时最多 128 字符且不能为空白。', effect: '用于审计和流程上下文，不自动映射为平台登录用户。' },
                { field: 'variables', purpose: '传入流程业务变量。', rule: '必填 JSON 对象，最多 100 个顶层键，并通过 inputSchema。', effect: '违反 Schema 返回 422 并列出字段路径与原因。' }
              ]
            },
            {
              type: 'code',
              title: '启动流程请求',
              language: 'bash',
              code: startProcessExample
            }
          ]
        },
        {
          id: 'integration-message-correlation',
          title: '发送流程消息',
          blocks: [
            {
              type: 'paragraph',
              text: '消息关联用于通知一个已存在的流程实例“外部事件已经发生”。它不是任意修改流程变量的接口：应用必须拥有 process.message.correlate，实例必须由当前应用绑定，messageKey 必须列入该流程契约，variables 仍需通过 inputSchema。'
            },
            {
              type: 'code',
              title: '关联 payment_received 消息',
              language: 'bash',
              code: correlateMessageExample
            },
            {
              type: 'bullets',
              items: [
                '首次接受返回 HTTP 202，状态为 ACCEPTED；幂等重放返回历史结果。',
                '流程未等待该消息、流程已结束或状态冲突时可能返回 409、PROCESS_STATE_CONFLICT。',
                '不要把消息接口当成高频状态同步通道；状态通知优先使用 Webhook。'
              ]
            }
          ]
        },
        {
          id: 'integration-errors',
          title: '错误响应与重试策略',
          blocks: [
            {
              type: 'table',
              columns: optionColumns,
              rows: [
                { option: '400 INVALID_REQUEST', meaning: '参数、游标、ID、消息 Key 或 JSON 结构不合法。', usage: '修正请求后再试，不应原样自动重试。' },
                { option: '401 INVALID_ACCESS_TOKEN / invalid_client', meaning: '令牌无效，或 Client ID/Secret 错误、失效。', usage: '重新取令牌；仍失败则检查应用和凭据状态。' },
                { option: '403 INSUFFICIENT_SCOPE / PROCESS_NOT_GRANTED / SOURCE_ADDRESS_NOT_ALLOWED', meaning: 'Scope、流程、消息或来源网段未授权。', usage: '按最小权限原则调整配置，不要无条件重试。' },
                { option: '404 RESOURCE_NOT_FOUND', meaning: '资源不存在，或不属于当前接入应用。', usage: '核对实例 ID 与应用归属；服务端不会泄露其他应用资源。' },
                { option: '409 REQUEST_IN_PROGRESS / PROCESS_STATE_CONFLICT', meaning: '相同幂等请求正在执行，或流程状态不允许当前操作。', usage: '遵循 Retry-After；保持原 Idempotency-Key 重试。' },
                { option: '422 VARIABLE_VALIDATION_FAILED', meaning: 'variables 不符合输入契约。', usage: '读取 data.violations 修正字段路径、类型或约束。' },
                { option: '429 RATE_LIMIT_EXCEEDED', meaning: '应用请求速率或并发超过上限。', usage: '遵循 Retry-After、指数退避并降低调用方并发。' },
                { option: '503 INTEGRATION_TEMPORARILY_UNAVAILABLE', meaning: '平台依赖或集成能力暂时不可用。', usage: '使用同一幂等键做有上限的退避重试，并用 traceId 报障。' }
              ]
            }
          ]
        }
      ]
    },
    {
      id: 'integration-webhook',
      index: '06',
      title: 'Webhook',
      summary: '订阅流程事件，校验签名，理解自动重试、死信和人工重放。',
      topics: [
        {
          id: 'integration-webhook-config',
          title: '新建 Webhook 端点',
          blocks: [
            {
              type: 'table',
              columns: fieldColumns,
              rows: [
                { field: '端点名称', purpose: '管理端识别接收系统和用途。', rule: '必填，最多 128 字符。', effect: '建议包含系统、环境和事件用途。' },
                { field: '目标 URL', purpose: '接收 CloudEvent 的 HTTPS 地址。', rule: '必填，最多 2048 字符；必须 HTTPS，不能包含 username:password@ 用户信息。', effect: '平台固定 POST；目标还要通过部署级出站策略。' },
                { field: '事件类型', purpose: '选择该端点接收哪些业务事件。', rule: '至少 1 个，最多 6 个。', effect: '只为选中事件创建投递；一个应用可以按系统或事件拆分多个端点。' },
                { field: '状态', purpose: '控制订阅是否继续产生投递。', rule: '新建默认 ACTIVE；编辑时可切换 ACTIVE / DISABLED。', effect: '停用端点用于维护，不删除历史投递。' },
                { field: 'Signing Secret', purpose: '接收端验证请求确实来自平台且正文未被篡改。', rule: '新建后只显示一次。', effect: '必须保存原文；平台后续只显示版本和尾部提示。' }
              ]
            },
            {
              type: 'steps',
              items: [
                { title: '先部署验签端点', text: '接收端必须保留原始请求字节，先验签再解析 JSON 和执行业务。' },
                { title: '创建并保存密钥', text: '关闭一次性弹窗前，将 Signing Secret 存入接收系统的密钥管理。' },
                { title: '点击“验证”', text: '平台发送 com.flow.webhook.validation.v1 验证事件；确认返回 2xx。' },
                { title: '再订阅生产事件', text: '从最少事件类型开始，验证幂等处理和告警后再扩大订阅。' }
              ]
            }
          ]
        },
        {
          id: 'integration-webhook-events',
          title: '事件类型',
          blocks: [
            {
              type: 'table',
              columns: optionColumns,
              rows: [
                { option: 'com.flow.process.started.v1', meaning: '流程实例已启动，data.status=RUNNING。', usage: '外部系统登记平台实例 ID 或更新单据为审批中。' },
                { option: 'com.flow.task.created.v1', meaning: '流程产生活动任务，包含 taskId 和 taskDefinitionKey。', usage: '通知外部工作台或记录当前环节；不代表外部系统可直接办理任务。' },
                { option: 'com.flow.task.completed.v1', meaning: '任务已完成。', usage: '同步环节进度、审计或触发下游动作。' },
                { option: 'com.flow.process.completed.v1', meaning: '流程正常完成。', usage: '回写业务成功状态、触发归档。' },
                { option: 'com.flow.process.terminated.v1', meaning: '流程被终止。', usage: '回写取消/终止状态并停止等待。' },
                { option: 'com.flow.process.failed.v1', meaning: '流程运行失败。', usage: '触发告警和人工处置，避免外部单据长期停留在处理中。' }
              ]
            },
            {
              type: 'paragraph',
              text: '请求 Content-Type 为 application/cloudevents+json。CloudEvent 包含 id、source、type、subject、time、dataschema、traceid 和 data；data 至少包含 processInstanceId、processKey、businessReference 及事件状态/时间，任务事件还包含 taskId 和 taskDefinitionKey。'
            }
          ]
        },
        {
          id: 'integration-webhook-signature',
          title: '签名校验',
          blocks: [
            {
              type: 'table',
              columns: fieldColumns,
              rows: [
                { field: 'Flow-Webhook-Id', purpose: '稳定事件 ID。', rule: '参与签名。', effect: '接收端应以它做幂等去重，重复投递只处理一次。' },
                { field: 'Flow-Webhook-Timestamp', purpose: '签名时的 Unix 秒时间戳。', rule: '参与签名。', effect: '建议拒绝与当前时间偏差过大的请求，降低重放风险。' },
                { field: 'Flow-Webhook-Signature', purpose: 'HMAC-SHA256 签名。', rule: '格式 v1=<Base64>。', effect: '比较时使用常量时间函数。' },
                { field: '签名原文', purpose: '保证事件标识、时间和正文完整性。', rule: 'eventId + "." + timestamp + "." + rawBody 原始字节。', effect: '必须使用读取 JSON 前的原始请求体；重新序列化 JSON 会导致验签失败。' },
                { field: 'X-Trace-Id', purpose: '跨系统追踪。', rule: '来自事件 traceId，缺失时回退到 eventId。', effect: '写入接收端日志，便于与平台投递记录关联。' }
              ]
            },
            {
              type: 'code',
              title: 'Node.js 验签核心示例',
              language: 'javascript',
              code: webhookVerifyExample
            },
            {
              type: 'callout',
              tone: 'warning',
              title: '验签成功后再返回 2xx',
              text: '推荐先验签、落入本地可靠队列或事务日志，再快速返回 2xx；耗时业务异步处理。未经持久化就返回 2xx，后续本地失败平台不会再次投递。'
            }
          ]
        },
        {
          id: 'integration-webhook-delivery',
          title: '投递、重试与重放',
          blocks: [
            {
              type: 'table',
              columns: optionColumns,
              rows: [
                { option: 'PENDING / 待投递', meaning: '已创建投递，等待工作器领取。', usage: '短暂存在属于正常；持续堆积需检查工作器与队列。' },
                { option: 'IN_PROGRESS / 投递中', meaning: '工作器已取得租约并正在发送。', usage: '实例异常后租约可恢复，避免永久卡住。' },
                { option: 'RETRY / 待重试', meaning: '遇到超时、网络错误或可重试 HTTP 状态。', usage: '408、409、425、429 和 5xx 会重试，并尊重有上限的 Retry-After。' },
                { option: 'SUCCEEDED / 成功', meaning: '端点返回 2xx。', usage: '平台停止重试；接收端仍需按事件 ID 幂等。' },
                { option: 'DEAD / 死信', meaning: '不可重试错误或最多 8 次尝试耗尽。', usage: '修复端点后由有权限人员填写 3–256 字符原因并重放。' },
                { option: '人工重放', meaning: '为原事件创建新的投递尝试，而不是改写历史结果。', usage: '只对 DEAD 开放；每次操作留审计，接收端仍会看到相同业务事件语义。' }
              ]
            },
            {
              type: 'bullets',
              items: [
                '非 2xx 响应会在投递记录显示 HTTP 状态和脱敏摘要，先修复接收端再重放。',
                '事件和投递有保留周期；当前事件默认保留 30 天，过期后不能无限期重放。',
                '轮换 Webhook 密钥后，旧密钥只在服务端配置的重叠窗口内有效。接收端应临时同时接受新旧密钥，完成切换后移除旧密钥。'
              ]
            }
          ]
        }
      ]
    },
    {
      id: 'integration-secrets',
      index: '07',
      title: '集成 Secret',
      summary: '安全保存 Connector 使用的外部凭据，按名称引用并管理版本。',
      topics: [
        {
          id: 'integration-secret-config',
          title: 'Secret 配置项',
          blocks: [
            {
              type: 'table',
              columns: fieldColumns,
              rows: [
                { field: 'Secret 名称', purpose: 'Connector 中稳定引用凭据。', rule: '必填，最多 64 字符；以字母开头，只允许字母、数字、点、下划线和短横线。', effect: '同一应用同名只能有一个 ACTIVE 版本；名称应表达用途，不包含密钥值。' },
                { field: 'Secret 值', purpose: '保存 API Token、API Key、Basic 用户名或密码。', rule: '可留空由服务端生成；手工填写为 8–65536 字节。', effect: '创建/轮换后只显示一次，数据库保存加密密文。' },
                { field: 'Secret 引用', purpose: '让 Connector 引用凭据而不是写明文。', rule: '固定格式 secret://integration/{applicationId}/{secretName}。', effect: '只能引用当前应用自己的 ACTIVE Secret。' },
                { field: '尾部提示', purpose: '帮助管理员识别当前值。', rule: '列表只显示末尾若干字符。', effect: '提示不等于明文恢复能力。' },
                { field: '版本', purpose: '控制并发修改和轮换历史。', rule: '从 v1 递增。', effect: '轮换请求必须携带当前 expectedSecretVersion。' }
              ]
            },
            {
              type: 'callout',
              tone: 'info',
              title: 'BASIC 认证需要两个 Secret',
              text: 'BASIC 的 usernameSecretRef 和 secretRef 都必须是当前应用的 Secret 引用。即使用户名不敏感，也统一通过 Secret 管理，避免配置文档混入凭据。'
            }
          ]
        },
        {
          id: 'integration-secret-lifecycle',
          title: '创建、轮换、吊销与销毁',
          blocks: [
            {
              type: 'table',
              columns: optionColumns,
              rows: [
                { option: 'ACTIVE / 活跃', meaning: 'Connector 可以解析该名称的当前版本。', usage: '正常使用。' },
                { option: '轮换', meaning: '先吊销当前版本，再创建同名的下一个 ACTIVE 版本。引用字符串不变。', usage: '外部凭据变更时使用；保存后新调用立即使用新值。' },
                { option: 'REVOKED / 已吊销', meaning: '该版本不再可解析，但加密材料仍保留以供审计。', usage: '紧急停用或轮换后的历史版本。若当前名称没有新活跃版本，Connector 调用会失败。' },
                { option: 'DESTROYED / 已销毁', meaning: '永久删除密文、数据密钥和加密元数据，不可恢复。', usage: '只允许销毁已吊销版本；用于满足彻底清除要求。' }
              ]
            },
            {
              type: 'checklist',
              items: [
                'Secret 名称不包含环境密码、Token 片段或人员信息。',
                '一次性明文已进入企业密钥管理系统，未出现在工单评论和聊天记录。',
                '轮换前确认外部系统新凭据已生效，轮换后立即执行 Connector 测试。',
                '吊销前检索所有引用该名称的 Connector，避免误伤生产调用。',
                '销毁前确认审计和合规保留要求，销毁操作无法撤销。'
              ]
            }
          ]
        }
      ]
    },
    {
      id: 'integration-connector',
      index: '08',
      title: 'HTTP Connector',
      summary: '以声明式 JSON 安全调用外部 HTTP API，并把响应映射回平台运行时。',
      topics: [
        {
          id: 'integration-connector-use',
          title: 'Connector 用在哪里',
          blocks: [
            {
              type: 'paragraph',
              text: 'Connector 配置定义“允许调用哪个固定主机、有哪些固定操作、输入如何映射到请求、响应如何抽取”。保存后可在平台支持 INTEGRATION_CONNECTOR 的数据源或流程动作中选择 connectorConfigId 和 operation；运行时只传 input 与上下文，不允许传入任意 URL、方法或脚本。'
            },
            {
              type: 'bullets',
              items: [
                '适合请求/响应式调用：查询订单、客户、库存、风险服务，或创建外部工单。',
                '不适合长耗时批处理和无限重试；这类场景应使用消息或异步任务。',
                'Connector 编码当前固定为 http-json；所有配置修改和调用结果都进入集成审计。',
                '先在“Secret”页签创建认证凭据，再在 Connector authentication 中引用。'
              ]
            }
          ]
        },
        {
          id: 'integration-connector-editor',
          title: '外层配置项',
          blocks: [
            {
              type: 'table',
              columns: fieldColumns,
              rows: [
                { field: '配置名称', purpose: '管理端识别一组外部 API 操作。', rule: '必填，最多 128 字符；同一应用内唯一。', effect: '建议按外部服务命名，如 ERP Order API。' },
                { field: '状态', purpose: '控制运行时能否加载配置。', rule: '新建默认 ACTIVE；编辑时可切换 ACTIVE / DISABLED。', effect: '停用后所有引用该配置的调用失败，适合维护窗口。' },
                { field: '允许主机', purpose: '限制 baseUrl 的目标主机。', rule: '每行一个精确域名，1–100 个；不支持通配符和 IP，自动转小写。', effect: 'baseUrl 主机必须精确命中；重定向和 DNS 解析仍受服务端出站安全策略检查。' },
                { field: '声明式配置', purpose: '定义 baseUrl 和 1–50 个 operations。', rule: 'JSON 对象，最多 256 KiB；不允许未知根字段。', effect: '保存时服务端完整解析，配置不合法不会进入运行时。' },
                { field: '版本', purpose: '避免并发覆盖。', rule: '编辑时携带 expectedVersion。', effect: '冲突时刷新后重新合并修改。' }
              ]
            }
          ]
        },
        {
          id: 'integration-connector-json',
          title: '声明式 JSON 每个字段',
          blocks: [
            {
              type: 'table',
              columns: fieldColumns,
              rows: [
                { field: 'baseUrl', purpose: '所有操作共享的服务基址。', rule: '必填 HTTPS；主机在允许主机中；不能包含用户信息、查询串或 fragment。可以包含固定基础路径。', effect: 'operation.path 追加到基础路径，不能运行时切换主机。' },
                { field: 'operations', purpose: '按稳定编码声明可执行操作。', rule: '必填对象，1–50 项；编码以字母开头，最多 64 字符。', effect: '平台配置引用 operation 编码，改名会导致已有引用失效。' },
                { field: 'method', purpose: 'HTTP 方法。', rule: 'GET、POST、PUT、PATCH、DELETE。', effect: 'GET 和 DELETE 不允许配置 body。' },
                { field: 'path', purpose: '固定请求路径。', rule: '必须以单个 / 开头；不能含 ?、#、反斜杠、${...} 或动态模板，最长 2048。', effect: '动态值只能通过 query、headers 或 body 映射传递。' },
                { field: 'query', purpose: '生成 URL 查询参数。', rule: '对象：参数名 → $input 或 $context 来源，最多 100 项。', effect: '值会做 URL 编码；空值映射为空字符串。' },
                { field: 'headers', purpose: '生成业务请求头。', rule: '对象：Header 名 → 来源；禁止 Authorization、Cookie、Host、Content-Length 等受保护 Header。', effect: '认证 Header 由 authentication 专门生成。' },
                { field: 'body', purpose: '生成 JSON 请求体。', rule: '对象：JSON Pointer → 来源；最多 100 项、最多 16 层，不允许父子 Pointer 冲突。', effect: '只适用于 POST/PUT/PATCH。' },
                { field: 'response', purpose: '从 JSON 响应抽取平台结果字段。', rule: '对象：结果字段名 → JSON Pointer；最多 100 项。', effect: '结果同时包含 httpStatus；Pointer 不存在时字段值为 null。' },
                { field: 'acceptedStatuses', purpose: '定义业务成功 HTTP 状态。', rule: '1–20 个互不重复的 2xx；省略时为 200/201/202/204。', effect: '不在集合内返回 CONNECTOR_REMOTE_REJECTED。' },
                { field: 'authentication', purpose: '生成目标系统认证信息。', rule: 'NONE、BASIC、BEARER、HEADER；凭据必须是当前应用 Secret 引用。', effect: '配置文档不保存凭据明文。' },
                { field: 'timeoutMs', purpose: '单次远程请求超时。', rule: '100–30000，默认 5000；同时受部署最大超时约束。', effect: '超时可按 maxAttempts 重试。' },
                { field: 'maxAttempts', purpose: '失败最大尝试次数。', rule: '1–4，默认 1。', effect: '网络异常及部分临时状态会退避重试；非幂等写操作建议保持 1，除非远端支持 Idempotency-Key。' }
              ]
            },
            {
              type: 'code',
              title: 'GET 查询 + POST 写入配置示例',
              language: 'json',
              code: connectorExample
            }
          ]
        },
        {
          id: 'integration-connector-sources',
          title: '映射来源与认证类型',
          blocks: [
            {
              type: 'table',
              columns: optionColumns,
              rows: [
                { option: '$input.xxx', meaning: '引用调用 Connector 时传入的 input，支持最多 8 层点路径。', usage: '业务参数，如 $input.orderNo、$input.customer.id。' },
                { option: '$context.serviceId', meaning: '当前接口服务标识。', usage: '外部接口需要来源追踪时传递。' },
                { option: '$context.usage / configType / configId', meaning: '当前数据源或配置类型及标识。', usage: '区分列表、表单或动作场景。' },
                { option: '$context.releaseId / releaseVersion', meaning: '当前发布快照。', usage: '外部系统需要审计平台配置版本。' },
                { option: '$context.entityId / entityCode / listKey', meaning: '实体与列表上下文。', usage: '实体列表、表单数据源使用 INTEGRATION_CONNECTOR 时传递业务模型标识。' },
                { option: '$context.userId / tenantId / organizationId / departmentId', meaning: '当前用户和组织上下文。', usage: '目标系统做租户隔离或审计；目标系统仍应自行鉴权，不能盲目信任 Header。' },
                { option: 'NONE', meaning: '不添加认证信息。', usage: '仅限无需认证且网络已受控的服务。' },
                { option: 'BASIC', meaning: '用 usernameSecretRef + secretRef 生成 Authorization: Basic。', usage: '目标 API 使用 Basic Auth。' },
                { option: 'BEARER', meaning: '用 secretRef 生成 Authorization: Bearer。', usage: '固定服务 Token。' },
                { option: 'HEADER', meaning: '把 secretRef 放入指定 headerName。', usage: 'X-Api-Key 等自定义认证头；headerName 不能是受保护 Header。' }
              ]
            }
          ]
        },
        {
          id: 'integration-connector-test',
          title: '连接测试与上线注意事项',
          blocks: [
            {
              type: 'steps',
              items: [
                { title: '保存配置', text: '服务端会校验 JSON、主机、路径、映射、Secret 归属和认证字段。' },
                { title: '打开“测试”', text: '选择一个 operation，并填写不含真实敏感数据的 JSON 对象，最多 100 个顶层字段。' },
                { title: '核对测试结果', text: '确认 success、code、message、httpStatus 和 response 映射字段符合预期。' },
                { title: '接入平台配置', text: '在支持 INTEGRATION_CONNECTOR 的数据源/动作中选择配置 ID、操作编码并映射输入。' },
                { title: '验证失败路径', text: '测试超时、非成功状态、空响应字段、Secret 吊销、配置停用和目标不可达时的业务处理。' }
              ]
            },
            {
              type: 'callout',
              tone: 'warning',
              title: '写操作与自动重试',
              text: 'Connector 会自动附带 Idempotency-Key 和 X-Flow-Trace-Id。目标系统应支持幂等键；如果不能保证写操作幂等，POST/PUT/PATCH 的 maxAttempts 应配置为 1，避免超时后重复创建数据。'
            }
          ]
        }
      ]
    },
    {
      id: 'integration-operations',
      index: '09',
      title: '运维、排障与上线',
      summary: '用明确的检查顺序定位问题，并把凭据、容量、重试和审计纳入日常治理。',
      topics: [
        {
          id: 'integration-troubleshooting',
          title: '常见问题排查',
          blocks: [
            {
              type: 'table',
              columns: optionColumns,
              rows: [
                { option: '页面显示“能力未启用”', meaning: '对应服务端 capability 为 false，或密钥材料不完整。', usage: '检查 Open API、Webhook、Connector 环境变量和服务启动日志；新建应用不能解决环境能力问题。' },
                { option: '获取令牌返回 invalid_client', meaning: 'Client ID/Secret 错误、凭据已轮换/吊销、应用停用/过期，或来源地址不允许。', usage: '核对应用状态、凭据尾部提示、出口 IP 和最近轮换时间。' },
                { option: '有 Scope 仍然看不到流程', meaning: '允许流程为空、processKey 错误、流程未发布或调用方不可见。', usage: '检查“访问策略”和“输入契约”，用流程 Key 而不是名称。' },
                { option: '启动返回 422', meaning: 'variables 不符合 JSON Schema。', usage: '读取 data.violations；检查 required、类型、多余字段、maxLength 和保留变量。' },
                { option: 'Webhook 验签失败', meaning: '使用了解析后的 JSON、密钥版本错误、签名原文顺序错误或 Base64/十六进制混淆。', usage: '保留 rawBody，按 eventId.timestamp.rawBody 计算 HMAC-SHA256 Base64，并确认 v1= 前缀。' },
                { option: 'Webhook 一直 DEAD', meaning: '端点持续非 2xx、网络策略拒绝、超时或重试耗尽。', usage: '先修复并使用“验证”，再填写原因重放；不要反复重放未修复的端点。' },
                { option: 'Connector 保存失败', meaning: '主机、baseUrl、路径、未知字段、JSON Pointer、认证或 Secret 引用不合法。', usage: '从最小 NONE 认证 GET 配置开始，逐项增加映射。' },
                { option: 'Connector 调用失败', meaning: '配置停用、Secret 吊销、目标超时、HTTP 状态不在 acceptedStatuses 或响应不是预期 JSON。', usage: '先在管理页执行同 operation 测试，再用 traceId 对照平台和目标系统日志。' },
                { option: '配置版本冲突', meaning: '另一管理员已修改应用、端点、Secret 或 Connector。', usage: '刷新后比较新版本，重新合并，不要连续盲点保存。' }
              ]
            }
          ]
        },
        {
          id: 'integration-credential-rotation',
          title: '凭据轮换运行手册',
          blocks: [
            {
              type: 'table',
              columns: optionColumns,
              rows: [
                { option: '应用 Client Secret', meaning: '旧值在轮换后立即失效。', usage: '先准备调用方可快速替换和回滚的密钥发布机制，选择低峰原子切换。' },
                { option: 'Webhook Signing Secret', meaning: '旧值只在服务端重叠窗口内有效。', usage: '接收端先支持新旧双密钥，再轮换平台，验证后移除旧值。' },
                { option: '集成 Secret', meaning: '同名引用不变，当前 ACTIVE 版本立即切换。', usage: '先确认目标系统新凭据可用，轮换后立刻测试全部相关 operation。' }
              ]
            },
            {
              type: 'bullets',
              items: [
                '轮换操作都属于高风险审计事件，应记录负责人、变更单、时间和验证结果。',
                '不要在同一窗口同时轮换 Client Secret、Webhook 密钥和 Connector Secret，否则失败时难以定位。',
                '发现泄漏时先停用应用或吊销凭据止损，再做根因排查和新凭据签发。'
              ]
            }
          ]
        },
        {
          id: 'integration-go-live-checklist',
          title: '上线检查清单',
          blocks: [
            {
              type: 'checklist',
              items: [
                '接入应用名称、责任组织、说明能够明确定位系统、环境和负责人。',
                'Scope 为最小集合，允许流程只包含已批准的稳定 processKey。',
                '生产来源 CIDR 已配置，出口 IP 和代理信任链已实际验证。',
                '每个允许流程都有完整输入契约，调用方通过契约样例和错误用例测试。',
                '启动与消息调用使用稳定 Idempotency-Key，并实现 409/429/503 有上限退避。',
                '调用方共享短期令牌缓存，不在日志、前端代码和 URL 中暴露 Client Secret。',
                'Webhook 先验签、按事件 ID 去重、可靠落盘后返回 2xx，并配置死信告警。',
                'Connector 主机为精确 HTTPS 域名，凭据全部使用当前应用的 Secret 引用。',
                '非幂等写操作 maxAttempts=1，或目标系统已验证支持 Idempotency-Key。',
                '平台与外部系统日志都记录 traceId，审计人员能关联一次完整调用链。',
                'Client Secret、Signing Secret、集成 Secret 都有轮换周期、责任人和应急吊销方案。',
                '已验证应用停用、凭据吊销、Webhook DEAD、Connector 失败等异常路径不会造成业务重复或数据丢失。'
              ]
            }
          ]
        }
      ]
    }
  ]
}
