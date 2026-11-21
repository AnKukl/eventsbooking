import { Link } from 'react-router-dom'

export function NotFoundPage() {
  return (
    <section style={{ padding: 'var(--space-7) 0' }}>
      <h1>Страница не найдена</h1>
      <p>
        <Link to="/">Вернуться к мероприятиям</Link>
      </p>
    </section>
  )
}
