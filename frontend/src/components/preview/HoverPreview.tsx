import { type FocusEvent, type PointerEvent, useCallback, useEffect, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import { Link } from 'react-router-dom'
import { useQueryClient } from '@tanstack/react-query'
import type { EventItem } from '../../api/events'
import { useFavorite } from '../../api/favorites'
import { prefetchPreview, usePreview } from '../../api/preview'
import { price, when } from '../../pages/home/format'
import styles from './HoverPreview.module.css'

/** design/README.md: opens after 2 s of hover (not 3 s as the board title still says), closes 200 ms after leaving. */
export const OPEN_DELAY_MS = 2000
export const CLOSE_DELAY_MS = 200

/** Phones and tablets have no hover: a tap simply follows the card's link. */
function canHover(): boolean {
  return typeof window.matchMedia !== 'function' || window.matchMedia('(hover: hover)').matches
}

/**
 * The full-screen preview of a card (design/pages/R-hover). Spread `trigger` on the card; render `overlay` anywhere.
 * Mouse: 2 s on the card opens it, leaving the preview closes it after 200 ms. Keyboard: focus inside the card opens it
 * after the same delay, Escape closes it and returns the focus. Touch: never opens.
 */
export function useHoverPreview(event: EventItem) {
  const client = useQueryClient()
  const [open, setOpen] = useState(false)
  const openTimer = useRef<ReturnType<typeof setTimeout> | undefined>(undefined)
  const closeTimer = useRef<ReturnType<typeof setTimeout> | undefined>(undefined)
  const returnFocus = useRef<HTMLElement | null>(null)
  /** set while we hand the focus back after closing, so that this focus does not schedule the preview again */
  const restoring = useRef(false)
  /** keyboard users get the focus on "Открыть событие"; a mouse open focuses the dialog itself (no focus ring) */
  const byKeyboard = useRef(false)

  const cancel = useCallback(() => {
    clearTimeout(openTimer.current)
    clearTimeout(closeTimer.current)
  }, [])

  useEffect(() => cancel, [cancel])

  const schedule = useCallback(() => {
    cancel()
    void prefetchPreview(client, event.id!)
    openTimer.current = setTimeout(() => setOpen(true), OPEN_DELAY_MS)
  }, [cancel, client, event.id])

  const close = useCallback(() => {
    cancel()
    setOpen(false)
    restoring.current = true
    returnFocus.current?.focus()
    restoring.current = false
    returnFocus.current = null
  }, [cancel])

  const closeSoon = useCallback(() => {
    clearTimeout(openTimer.current)
    clearTimeout(closeTimer.current)
    closeTimer.current = setTimeout(() => setOpen(false), CLOSE_DELAY_MS)
  }, [])

  useEffect(() => {
    if (!open) return
    const onKey = (key: KeyboardEvent) => key.key === 'Escape' && close()
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [open, close])

  const trigger = {
    onPointerEnter: (e: PointerEvent) => {
      if (e.pointerType === 'touch' || !canHover()) return
      byKeyboard.current = false
      schedule()
    },
    onPointerLeave: (e: PointerEvent) => {
      if (e.pointerType === 'touch') return
      if (!open) cancel()
    },
    onFocus: (e: FocusEvent<HTMLElement>) => {
      if (open || restoring.current) return
      returnFocus.current = e.target
      byKeyboard.current = true
      schedule()
    },
    onBlur: (e: FocusEvent<HTMLElement>) => {
      // focus moving within the card keeps the timer; leaving the card before it fires cancels it
      if (!open && !e.currentTarget.contains(e.relatedTarget as Node | null)) cancel()
    },
  }

  const overlay = open
    ? createPortal(
        <PreviewOverlay
          event={event}
          focusAction={byKeyboard.current}
          onEnter={() => clearTimeout(closeTimer.current)}
          onLeave={closeSoon}
          onClose={close}
        />,
        document.body,
      )
    : null

  return { trigger, overlay, open }
}

function PreviewOverlay({ event, focusAction, onEnter, onLeave, onClose }: {
  event: EventItem
  focusAction: boolean
  onEnter: () => void
  onLeave: () => void
  onClose: () => void
}) {
  const preview = usePreview(event.id!, true)
  const { toggle, ready } = useFavorite()
  const openButton = useRef<HTMLAnchorElement>(null)
  const dialog = useRef<HTMLDivElement>(null)
  const photo = preview.data?.media?.[0]?.url ?? event.coverUrl
  const chip = price(event)
  const fit = preview.data?.audienceFit ?? []
  const notFit = preview.data?.audienceNotFit ?? []

  useEffect(() => {
    ;(focusAction ? openButton.current : dialog.current)?.focus({ preventScroll: true })
  }, [focusAction])

  return (
    <div
      ref={dialog}
      tabIndex={-1}
      className={styles.overlay}
      role="dialog"
      aria-modal="true"
      aria-label={`Предпросмотр: ${event.title}`}
      onPointerEnter={onEnter}
      onPointerLeave={(e) => e.pointerType !== 'touch' && onLeave()}
      onClick={(e) => e.target === e.currentTarget && onClose()}
    >
      {photo && <img className="hover-preview-image" src={photo} alt={preview.data?.media?.[0]?.alt ?? event.title ?? ''} />}
      <div className="hover-preview-fade" onClick={onClose} />
      {chip && <span className={`price-chip ${styles.chip}`}>{chip}</span>}

      <div className={styles.content}>
        <span className={styles.meta}>{[when(event.startsAt!), event.venue].filter(Boolean).join(' · ')}</span>
        <h1 className={styles.title}>{event.title}</h1>
        {(preview.data?.shortDescription || event.shortDescription) && (
          <p className={styles.description}>{preview.data?.shortDescription || event.shortDescription}</p>
        )}
        {(fit.length > 0 || notFit.length > 0) && (
          <div className={styles.audience}>
            {fit.length > 0 && (
              <div className={styles.block}>
                <span className={styles.label}>Для кого подойдёт</span>
                <ul className={styles.list}>{fit.map((item) => <li key={item}>{item}</li>)}</ul>
              </div>
            )}
            {notFit.length > 0 && (
              <div className={styles.block}>
                <span className={styles.label}>Не подойдёт</span>
                <ul className={styles.list}>{notFit.map((item) => <li key={item}>{item}</li>)}</ul>
              </div>
            )}
          </div>
        )}
        <div className={styles.actions}>
          <Link ref={openButton} to={`/events/${event.id}`} className={styles.open}>
            Открыть событие
          </Link>
          <button
            type="button"
            className={styles.favorite}
            aria-pressed={Boolean(event.isFavorite)}
            disabled={!ready}
            onClick={() => toggle(event)}
          >
            {event.isFavorite ? '♥ В избранном' : '♡ В избранное'}
          </button>
        </div>
      </div>
    </div>
  )
}
