import { type ReactNode, useEffect, useId, useRef } from 'react'
import { createPortal } from 'react-dom'
import styles from './Modal.module.css'

export { styles as modalStyles }

/**
 * A window over the page (design/pages/P-modals): the screen is dimmed, the focus moves into the window and is kept
 * there, Esc or a click on the dimmed area closes it, and the focus goes back where it was.
 */
export function Modal({ title, label, narrow, onClose, children }: {
  title: string
  label?: string
  narrow?: boolean
  onClose: () => void
  children: ReactNode
}) {
  const titleId = useId()
  const box = useRef<HTMLDivElement>(null)
  const close = useRef(onClose)
  close.current = onClose

  useEffect(() => {
    const before = document.activeElement as HTMLElement | null
    const focusable = () =>
      Array.from(box.current?.querySelectorAll<HTMLElement>('button:not([disabled]), a[href], input, select, textarea') ?? [])
    focusable()[0]?.focus()
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') close.current()
      if (event.key !== 'Tab') return
      const items = focusable()
      if (items.length === 0) return
      const first = items[0]
      const last = items[items.length - 1]
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault()
        last.focus()
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault()
        first.focus()
      }
    }
    document.addEventListener('keydown', onKey)
    return () => {
      document.removeEventListener('keydown', onKey)
      before?.focus?.()
    }
  }, [])

  return createPortal(
    <div className={styles.scrim} onMouseDown={(event) => event.target === event.currentTarget && onClose()}>
      <div
        ref={box}
        className={`${styles.modal} ${narrow ? styles.narrow : ''}`}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
      >
        {label && <span className={styles.label}>{label}</span>}
        <h2 id={titleId} className={styles.title}>
          {title}
        </h2>
        {children}
      </div>
    </div>,
    document.body,
  )
}
