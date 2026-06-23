import { gzipSync } from 'node:zlib'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'

const dist = resolve('dist')
const manifest = JSON.parse(readFileSync(resolve(dist, '.vite/manifest.json'), 'utf8'))
const entry = Object.values(manifest).find((item) => item.isEntry)

if (!entry) throw new Error('Bundle budget: application entry not found in Vite manifest')

function collect(chunk, seen = new Set()) {
  if (!chunk || seen.has(chunk.file)) return seen
  if (chunk.file.endsWith('.js')) seen.add(chunk.file)
  for (const key of chunk.imports || []) collect(manifest[key], seen)
  return seen
}

function gzipBytes(files) {
  return [...files].reduce(
    (sum, file) => sum + gzipSync(readFileSync(resolve(dist, file))).byteLength,
    0,
  )
}

const initialFiles = collect(entry)
const initialBytes = gzipBytes(initialFiles)
const initialLimit = 250 * 1024
const routeLimit = 80 * 1024
const violations = []

if (initialBytes > initialLimit) {
  violations.push(`initial JS ${(initialBytes / 1024).toFixed(1)} KiB exceeds 250 KiB`)
}

let largestRoute = { key: '', bytes: 0 }
for (const key of entry.dynamicImports || []) {
  const routeFile = manifest[key]?.file
  const bytes = routeFile?.endsWith('.js') ? gzipBytes(new Set([routeFile])) : 0
  if (bytes > largestRoute.bytes) largestRoute = { key, bytes }
  if (bytes > routeLimit) {
    violations.push(`${key} ${(bytes / 1024).toFixed(1)} KiB exceeds 80 KiB`)
  }
}

console.log(`Bundle budget: initial JS ${(initialBytes / 1024).toFixed(1)} KiB gzip`)
console.log(
  `Bundle budget: largest route ${largestRoute.key} ${(largestRoute.bytes / 1024).toFixed(1)} KiB gzip`,
)
if (violations.length) throw new Error(`Bundle budget failed:\n${violations.join('\n')}`)
