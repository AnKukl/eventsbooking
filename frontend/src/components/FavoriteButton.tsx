import type { EventItem } from '../api/events'
import { useFavorite } from '../api/favorites'

/** ♡ / ♥ with aria-pressed and aria-label (design/README.md). Styling comes from the caller. */
export function FavoriteButton({ event, className }: { event: EventItem; className?: string }) {
  const { toggle, ready } = useFavorite()
  const pressed = Boolean(event.isFavorite)
  return (
    <button
      type="button"
      className={className}
      aria-label={pressed ? 'Убрать из избранного' : 'Добавить в избранное'}
      aria-pressed={pressed}
      disabled={!ready}
      onClick={() => toggle(event)}
    >
      {pressed ? '♥' : '♡'}
    </button>
  )
}
