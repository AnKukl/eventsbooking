import { Link, Navigate, NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom'
import { useMe } from '../../api/session'
import { Logo } from '../../components/Logo'
import { useToast } from '../../components/toast/Toast'
import { useCreateDraft } from '../../api/organizer'
import styles from './Organizer.module.css'

/** Access: guests sign in first; signed-in users without the ORGANIZER role get 403. */
export function OrganizerLayout() {
  const me = useMe()
  const location = useLocation()
  const navigate = useNavigate()
  const toast = useToast()
  const create = useCreateDraft()

  if (me.isPending) return null
  if (!me.data) return <Navigate to={`/login?returnTo=${encodeURIComponent(location.pathname)}`} replace />
  if (me.data.role !== 'ORGANIZER') return <Forbidden />

  const soon = (what: string) => () => toast.show({ message: `${what} — скоро` })
  const tab = ({ isActive }: { isActive: boolean }) => `${styles.tab} ${isActive ? styles.active : ''}`

  function newEvent() {
    // a draft needs a title, a start and a capacity; a week ahead at 18:00 is an editable starting point
    const start = new Date()
    start.setDate(start.getDate() + 7)
    start.setHours(18, 0, 0, 0)
    create.mutate(
      { title: 'Новое событие', startsAt: start.toISOString(), capacity: 20 },
      { onSuccess: (draft) => navigate(`/organizer/events/${draft.id}/edit`) },
    )
  }

  return (
    <div className="page">
      <header className={styles.header}>
        <Link to="/" className={styles.brand} aria-label="Events Booking, на главную">
          <Logo />
          <span className={styles.wordmark}>Events Booking</span>
          <span className={styles.badge}>Организатор</span>
        </Link>
        <div className={styles.headButtons}>
          <button type="button" className={styles.outline} onClick={soon('Выплаты')}>Выплаты</button>
          <button type="button" className={styles.primary} onClick={newEvent} disabled={create.isPending}>+ Создать событие</button>
        </div>
      </header>
      <div className={styles.layout}>
        <nav className={styles.nav} aria-label="Кабинет организатора">
          <NavLink to="/organizer" end className={tab}>Мероприятия</NavLink>
          <button type="button" className={styles.tab} onClick={soon('Сводный список броней')}>Брони</button>
          <button type="button" className={styles.tab} onClick={soon('Подробная аналитика')}>Аналитика</button>
          <button type="button" className={styles.tab} onClick={soon('Выплаты')}>Выплаты</button>
          <button type="button" className={`${styles.tab} ${styles.bottom}`} onClick={soon('Настройки')}>Настройки</button>
        </nav>
        <main className={styles.main}>
          <Outlet />
        </main>
      </div>
    </div>
  )
}

function Forbidden() {
  return (
    <div className="page">
      <section role="alert" className={styles.card}>
        <h1 className={styles.sectionTitle}>403 · Нет доступа</h1>
        <p>Кабинет организатора открыт только организаторам. Если вы проводите события, зарегистрируйтесь с кодом приглашения.</p>
        <Link to="/" className={styles.primary}>На главную</Link>
      </section>
    </div>
  )
}
