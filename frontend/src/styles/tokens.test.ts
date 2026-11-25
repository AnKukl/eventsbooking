import { readdirSync, statSync } from 'node:fs'
import { join, relative } from 'node:path'
import { describe, expect, it } from 'vitest'
import { designFile, frontendFile } from '../test/design'

const COLOUR = /#[0-9a-fA-F]{3,8}\b|rgba?\(|hsla?\(/

/** Source files under a frontend folder, relative to frontend/, tests excluded. */
function sourceFiles(dir: string): string[] {
  return readdirSync(dir).flatMap((name) => {
    const path = join(dir, name)
    if (statSync(path).isDirectory()) return sourceFiles(path)
    return /\.(css|tsx?)$/.test(name) && !/\.test\.tsx?$/.test(name) ? [relative(process.cwd(), path)] : []
  })
}

describe('design tokens', () => {
  it('start with design/tokens.css copied verbatim', () => {
    expect(frontendFile('src/styles/tokens.css').startsWith(designFile('tokens.css'))).toBe(true)
  })

  it('are the only place with colours', () => {
    const offenders = sourceFiles(join(process.cwd(), 'src'))
      .filter((path) => !path.endsWith(join('styles', 'tokens.css')) && !path.endsWith('schema.d.ts'))
      .filter((path) => COLOUR.test(frontendFile(path)))

    expect(offenders).toEqual([])
  })

  it('index.html has no colours either', () => {
    expect(COLOUR.test(frontendFile('index.html'))).toBe(false)
  })
})
