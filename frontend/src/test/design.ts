import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'

/** Tests run from frontend/ (npm test); the approved design lies next to it in ../design. */
const FRONTEND = process.cwd()

function read(path: string): string {
  return readFileSync(path, 'utf8').replace(/\r\n/g, '\n')
}

/** A file of the approved design (eventsbooking/design) with normalised line endings. */
export function designFile(path: string): string {
  return read(resolve(FRONTEND, '..', 'design', path))
}

/** A file of the frontend, relative to frontend/. */
export function frontendFile(path: string): string {
  return read(resolve(FRONTEND, path))
}

/** The value of a custom property in src/styles/tokens.css, e.g. the hex value of tokenValue('--accent'). */
export function tokenValue(name: string): string {
  const match = frontendFile('src/styles/tokens.css').match(new RegExp(`${name}:\s*([^;]+);`))
  if (!match) throw new Error(`token ${name} is not defined`)
  return match[1].trim()
}
