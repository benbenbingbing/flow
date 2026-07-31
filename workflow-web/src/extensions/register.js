import { registerDemoExtensions } from '@/demo'
import { registerProjectExtensions } from '@/project'

let applicationExtensionsRegistered = false

export function registerApplicationExtensions({ enableDemo = false } = {}) {
  if (applicationExtensionsRegistered) return
  applicationExtensionsRegistered = true
  registerProjectExtensions()
  if (enableDemo) registerDemoExtensions()
}
