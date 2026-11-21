/**
 * The brand mark: a terracotta rounded square with notches on both sides and a cream "E" built from four rectangles.
 * Colours come from the tokens. To be checked against design/logo.svg once the mock-ups are in the repo.
 */
export function Logo({ size = 40 }: { size?: number }) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 40 40"
      role="img"
      aria-label="Events Booking"
      xmlns="http://www.w3.org/2000/svg"
    >
      <defs>
        <mask id="logo-notches">
          <rect width="40" height="40" fill="white" />
          <circle cx="0" cy="20" r="4.5" fill="black" />
          <circle cx="40" cy="20" r="4.5" fill="black" />
        </mask>
      </defs>
      <rect width="40" height="40" rx="10" fill="var(--color-accent)" mask="url(#logo-notches)" />
      <g fill="var(--color-on-accent)">
        <rect x="13" y="10" width="4.5" height="20" />
        <rect x="13" y="10" width="14" height="4" />
        <rect x="13" y="18" width="11" height="4" />
        <rect x="13" y="26" width="14" height="4" />
      </g>
    </svg>
  )
}
