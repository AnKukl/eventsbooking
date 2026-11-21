import { type FormEvent, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { z } from 'zod'
import { Logo } from './Logo'
import styles from './Header.module.css'

const searchSchema = z.string().trim().max(100)

export function Header() {
  const navigate = useNavigate()
  const [query, setQuery] = useState('')

  function onSubmit(event: FormEvent) {
    event.preventDefault()
    const parsed = searchSchema.safeParse(query)
    if (!parsed.success) return
    navigate(parsed.data ? `/?q=${encodeURIComponent(parsed.data)}` : '/')
  }

  return (
    <header className={styles.header}>
      <div className={`container ${styles.inner}`}>
        <Link to="/" className={styles.brand} aria-label="На главную">
          <Logo />
          <span className={styles.brandName}>Events Booking</span>
        </Link>
        <form role="search" className={styles.search} onSubmit={onSubmit}>
          <input
            className={styles.searchInput}
            type="search"
            name="q"
            placeholder="Найти мероприятие"
            aria-label="Поиск мероприятий"
            maxLength={100}
            value={query}
            onChange={(event) => setQuery(event.target.value)}
          />
        </form>
        <Link to="/organizer" className={styles.cabinet}>
          Кабинет
        </Link>
      </div>
    </header>
  )
}
