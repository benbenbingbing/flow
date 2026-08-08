function isObject(value) {
  return value !== null && typeof value === 'object'
}

export function areSubFormValuesEqual(left, right, seen = new WeakMap()) {
  if (Object.is(left, right)) return true
  if (!isObject(left) || !isObject(right)) return false

  if (left instanceof Date || right instanceof Date) {
    return left instanceof Date
      && right instanceof Date
      && left.getTime() === right.getTime()
  }

  const matchedRight = seen.get(left)
  if (matchedRight) return matchedRight === right
  seen.set(left, right)

  if (Array.isArray(left) || Array.isArray(right)) {
    if (!Array.isArray(left) || !Array.isArray(right)) return false
    if (left.length !== right.length) return false
    return left.every((item, index) =>
      areSubFormValuesEqual(item, right[index], seen)
    )
  }

  const leftKeys = Object.keys(left)
  const rightKeys = Object.keys(right)
  if (leftKeys.length !== rightKeys.length) return false
  return leftKeys.every(key =>
    Object.prototype.hasOwnProperty.call(right, key)
      && areSubFormValuesEqual(left[key], right[key], seen)
  )
}

export function cloneSubFormValue(value, seen = new WeakMap()) {
  if (!isObject(value)) return value
  if (value instanceof Date) return new Date(value.getTime())

  const cached = seen.get(value)
  if (cached) return cached

  const result = Array.isArray(value) ? [] : {}
  seen.set(value, result)
  Object.keys(value).forEach(key => {
    result[key] = cloneSubFormValue(value[key], seen)
  })
  return result
}
