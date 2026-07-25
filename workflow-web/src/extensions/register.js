import { registerDemoExtensions } from '@/demo'

let applicationExtensionsRegistered = false

export function registerApplicationExtensions({ enableDemo = false } = {}) {
  if (applicationExtensionsRegistered) return
  applicationExtensionsRegistered = true
  if (enableDemo) registerDemoExtensions()
}
