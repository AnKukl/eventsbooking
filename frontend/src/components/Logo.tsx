import { useId } from 'react'

/**
 * The brand mark from design/logo.svg, geometry unchanged (do not edit). The colours are tokens holding the same
 * values as the file: --accent for the square, --card for the letter, --mask-keep / --mask-cut for the notch mask.
 * Logo.test.tsx compares every shape and colour with design/logo.svg.
 */
export function Logo({ label }: { label?: string }) {
  const mask = `ebm-${useId().replace(/:/g, '')}`
  return (
    <svg
      width="40"
      height="40"
      viewBox="0 0 40 40"
      xmlns="http://www.w3.org/2000/svg"
      role={label ? 'img' : undefined}
      aria-label={label}
      aria-hidden={label ? undefined : true}
    >
      <mask id={mask}>
        <rect width="40" height="40" rx="9" fill="var(--mask-keep)" />
        <circle cx="0" cy="20" r="4.5" fill="var(--mask-cut)" />
        <circle cx="40" cy="20" r="4.5" fill="var(--mask-cut)" />
      </mask>
      <rect width="40" height="40" rx="9" fill="var(--accent)" mask={`url(#${mask})`} />
      <rect x="13" y="11" width="3.2" height="18" fill="var(--card)" />
      <rect x="13" y="11" width="14" height="3.2" fill="var(--card)" />
      <rect x="13" y="18.4" width="11" height="3.2" fill="var(--card)" />
      <rect x="13" y="25.8" width="14" height="3.2" fill="var(--card)" />
    </svg>
  )
}
