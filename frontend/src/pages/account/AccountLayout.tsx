import { useState } from 'react'
import { Link, Navigate, NavLink, Outlet, useLocation } from 'react-router-dom'
import { useFavorites } from '../../api/favorites'
import { useMe } from '../../api/session'
import { useUnreadCount } from '../../api/account'
import { Logo } from '../../components/Logo'
import { LogoutModal } from './modals'
import styles from './AccountLayout.module.css'

/** The cabinet frame: logo, "Главная", the tabs (one URL each) and "Выйти". A guest signs in first. */
export function AccountLayout() {
  const me = useMe()
  const location = useLocation()
  const signedIn = Boolean(me.data)
  const favorites = useFavorites(signedIn)
  const unread = useUnreadCount(signedIn)
  const [leaving, setLeaving] = useState(false)

  if (me.isPending) return null
  if (!signedIn) return <Navigate to={`/login?returnTo=${encodeURIComponent(location.pathname)}`} replace />

  const favoriteCount = favorites.data?.page?.totalElements ?? favorites.data?.content?.length
  const unreadCount = unread.data?.count ?? 0
  const tab = ({ isActive }: { isActive: boolean }) => `${styles.tab} ${isActive ? styles.active : ''}`

  return (
    <div className="page">
      <header className={styles.header}>
        <Link to="/" className={styles.brand} aria-label="Events Booking, на главную">
          <Logo />
          <span className={styles.wordmark}>Events Booking</span>
        </Link>
        <Link to="/" className={styles.home}>
          <Logo soft size={30} />
          Главная
        </Link>
      </header>
      <div className={styles.layout}>
        <nav className={styles.nav} aria-label="Кабинет">
          <NavLink to="/account/bookings" className={tab}>Мои брони</NavLink>
          <NavLink to="/account/favorites" className={tab}>
            Избранное{favoriteCount ? ` · ${favoriteCount}` : ''}
          </NavLink>
          <NavLink to="/account/profile" className={tab}>Профиль</NavLink>
          <NavLink to="/account/notifications" className={tab}>
            Уведомления{unreadCount ? ` · ${unreadCount}` : ''}
          </NavLink>
          <button type="button" className={`${styles.tab} ${styles.logout}`} onClick={() => setLeaving(true)}>Выйти</button>
        </nav>
        <main className={styles.main}>
          <Outlet />
        </main>
      </div>
      {leaving && <LogoutModal onClose={() => setLeaving(false)} />}
    </div>
  )
}
