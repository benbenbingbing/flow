const pad = (value) => String(value).padStart(2, '0')

export function generateMigrationTag(date = new Date()) {
  return `REL-${date.getFullYear()}${pad(date.getMonth() + 1)}${pad(date.getDate())}-${pad(date.getHours())}${pad(date.getMinutes())}${pad(date.getSeconds())}`
}
