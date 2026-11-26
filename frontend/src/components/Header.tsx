import { type FormEvent, useEffect, useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { z } from 'zod'
import { Logo } from './Logo'
import styles from './Header.module.css'

const searchSchema = z.string().trim().min(1).max(100)

/**
 * The header of the approved home page (design/pages/D-bento): logo with the wordmark, search, "Кабинет".
 * Search goes to /search?q=… on Enter, "Кабинет" to /account (E-buttons).
 *
 * @param initial the first letter of the signed-in user's name for the avatar; a guest gets an empty circle
 */
export function Header({ initial }: { initial?: string }) {
  const navigate = useNavigate()
  const location = useLocation()
  const [query, setQuery] = useState('')

  // on the search page the field shows the query of the URL (a shared link, the back button)
  useEffect(() => {
    if (location.pathname === '/search') setQuery(new URLSearchParams(location.search).get('q') ?? '')
  }, [location.pathname, location.search])

  function onSubmit(event: FormEvent) {
    event.preventDefault()
    const parsed = searchSchema.safeParse(query)
    if (!parsed.success) return
    // keep the other filters of the search page, change only the words
    const params = new URLSearchParams(location.pathname === '/search' ? location.search : '')
    params.set('q', parsed.data)
    navigate(`/search?${params}`)
  }

  return (
    <header className={styles.header}>
      <Link to="/" className={styles.brand} aria-label="Events Booking, на главную">
        <Logo />
        <span className={styles.wordmark}>Events Booking</span>
      </Link>
      <div className={styles.actions}>
        <form role="search" className={styles.searchForm} onSubmit={onSubmit}>
          <input
            className={styles.search}
            type="search"
            name="q"
            placeholder="Поиск: события, даты, настроение"
            aria-label="Поиск мероприятий"
            maxLength={100}
            value={query}
            onChange={(event) => setQuery(event.target.value)}
          />
        </form>
        <Link to="/account" className={styles.cabinet}>
          <span className={styles.avatar} aria-hidden="true">
            {initial?.charAt(0).toUpperCase()}
          </span>
          Кабинет
        </Link>
      </div>
    </header>
  )
}
