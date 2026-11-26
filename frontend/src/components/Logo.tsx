import { useId } from 'react'

/**
 * The brand mark from design/logo.svg, geometry unchanged (do not edit). The colours are tokens holding the same
 * values as the file: --accent for the square, --card for the letter, --mask-keep / --mask-cut for the notch mask.
 * Logo.test.tsx compares every shape and colour with design/logo.svg.
 */
export function Logo({ label, soft, size = 40 }: { label?: string; soft?: boolean; size?: number }) {
  const mask = `ebm-${useId().replace(/:/g, '')}`
  // the soft variant is the "Главная" button of the cabinet: the same shape in quiet colours
  const square = soft ? 'var(--line)' : 'var(--accent)'
  const letter = soft ? 'var(--ink-soft)' : 'var(--card)'
  return (
    <svg
      width={size}
      height={size}
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
      <rect width="40" height="40" rx="9" fill={square} mask={`url(#${mask})`} />
      <rect x="13" y="11" width="3.2" height="18" fill={letter} />
      <rect x="13" y="11" width="14" height="3.2" fill={letter} />
      <rect x="13" y="18.4" width="11" height="3.2" fill={letter} />
      <rect x="13" y="25.8" width="14" height="3.2" fill={letter} />
    </svg>
  )
}
