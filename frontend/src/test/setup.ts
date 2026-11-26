import '@testing-library/jest-dom/vitest'
import { cleanup, configure } from '@testing-library/react'
import { afterEach } from 'vitest'

// findBy*/waitFor: a busy laptop (a backend build running alongside) needs more than the default second
configure({ asyncUtilTimeout: 5000 })

// without Vitest globals Testing Library cannot register its own cleanup
afterEach(cleanup)
