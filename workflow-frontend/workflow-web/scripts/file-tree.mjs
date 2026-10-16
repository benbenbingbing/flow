import { readdirSync } from 'node:fs'
import path from 'node:path'

export function listFiles(roots, extension) {
  const files = []
  for (const root of roots) visit(root, files, extension)
  return files.sort()
}

function visit(directory, files, extension) {
  for (const entry of readdirSync(directory, { withFileTypes: true })) {
    const candidate = path.join(directory, entry.name)
    if (entry.isDirectory()) {
      visit(candidate, files, extension)
    } else if (entry.isFile() && candidate.endsWith(extension)) {
      files.push(candidate.split(path.sep).join('/'))
    }
  }
}
