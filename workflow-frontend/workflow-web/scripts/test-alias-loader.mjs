import { existsSync } from 'node:fs'
import { fileURLToPath, pathToFileURL } from 'node:url'
import path from 'node:path'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const srcRoot = path.join(root, 'src')

function resolveAliasPath(specifier) {
  const relativePath = specifier.slice(2)
  const basePath = path.join(srcRoot, relativePath)
  const candidates = [`${basePath}.js`, `${basePath}.ts`, path.join(basePath, 'index.js'), path.join(basePath, 'index.ts'), basePath]
  const resolved = candidates.find((candidate) => existsSync(candidate))
  return resolved ? pathToFileURL(resolved).href : null
}

export async function resolve(specifier, context, defaultResolve) {
  if (specifier.startsWith('@/')) {
    const resolved = resolveAliasPath(specifier)
    if (resolved) return { url: resolved, shortCircuit: true }
  }
  return defaultResolve(specifier, context, defaultResolve)
}
