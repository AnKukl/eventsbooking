// Runs Vitest from a path with an upper-case drive letter. On Windows a terminal opened as "c:\..." makes Vitest load
// its own module twice (c: and C: are different module ids) and every test fails with "reading 'config'".
import { spawnSync } from 'node:child_process'
import { join } from 'node:path'

const upperDrive = (path) => path.replace(/^[a-z]:/, (drive) => drive.toUpperCase())

const cwd = upperDrive(process.cwd())
const vitest = join(cwd, 'node_modules', 'vitest', 'vitest.mjs')
const result = spawnSync(process.execPath, [vitest, ...process.argv.slice(2)], { cwd, stdio: 'inherit' })
process.exit(result.status ?? 1)
