import { FlowEmbedWidget } from './FlowEmbedWidget.js'

export {
  FLOW_EMBED_COMMANDS,
  FLOW_EMBED_CAPABILITIES,
  FLOW_EMBED_EVENTS,
  FLOW_EMBED_PROTOCOL,
  FlowEmbedError,
  MAX_MESSAGE_BYTES,
  createSecureNonce,
  normalizeEmbedUrl,
  normalizeTargetOrigin
} from './protocol.js'
export { FlowEmbedWidget } from './FlowEmbedWidget.js'

export function mount(options) {
  return new FlowEmbedWidget(options)
}

export const FlowEmbed = Object.freeze({ mount })

export default FlowEmbed
