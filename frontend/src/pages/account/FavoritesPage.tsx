import { Navigate, useLocation } from 'react-router-dom'
import { useFavorites } from '../../api/favorites'
import { useMe } from '../../api/session'
import { EventCard } from '../../components/EventCard'
import styles from './FavoritesPage.module.css'

/** "Избранное" (design/pages/K-favorites, the list itself; the cabinet menu comes with the account screens). */
export function FavoritesPage() {
  const me = useMe()
  const location = useLocation()
  const favorites = useFavorites()

  if (me.isPending) return null
  if (!me.data) return <Navigate to={`/login?returnTo=${encodeURIComponent(location.pathname)}`} replace />

  return (
    <section className={styles.main}>
      <h1 className={styles.title}>Избранное</h1>
      {favorites.isPending && <p className={styles.status}>Загружаем избранное…</p>}
      {favorites.isError && (
        <div className={styles.status} role="alert">
          Не удалось загрузить избранное.
          <button type="button" className="btn-primary" onClick={() => void favorites.refetch()}>
            Повторить
          </button>
        </div>
      )}
      {favorites.isSuccess && (
        <div className={styles.grid}>
          {(favorites.data.content ?? []).map((event) => (
            <EventCard key={event.id} event={event} />
          ))}
          <div className={styles.hint}>Сохраняйте события кнопкой ♥ на карточке, и они появятся здесь.</div>
        </div>
      )}
    </section>
  )
}
