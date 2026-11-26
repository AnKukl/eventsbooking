import { Link, useSearchParams } from 'react-router-dom'
import { useCatalogue } from '../../api/catalogue'
import { EventCard } from '../../components/EventCard'
import { CITIES, type SearchState, readSearch, toFilter, writeSearch } from './filters'
import styles from './SearchPage.module.css'

/** "найдено 3", "найдено 1 событие" */
function found(count: number): string {
  return `найдено ${count}`
}

/** Search: design/pages/G-search. Every filter lives in the URL, so a link opens the same results. */
export function SearchPage() {
  const [params, setParams] = useSearchParams()
  const state = readSearch(params)
  const results = useCatalogue(toFilter(state))

  function update(change: Partial<SearchState>) {
    setParams(writeSearch({ ...state, ...change }), { replace: true })
  }

  const items = results.data?.content ?? []
  const total = results.data?.page?.totalElements ?? items.length
  const heading = state.q ? `«${state.q}» · ${found(total)}` : `Все события · ${found(total)}`
  const filtered = state.q || state.when !== 'all' || state.cheap || state.city

  return (
    <section>
      <div className={styles.head}>
        <div className={styles.titles}>
          <span className={styles.label}>Результаты</span>
          <h1 className={styles.title}>{results.isPending ? 'Ищем…' : heading}</h1>
        </div>
        <div className={styles.chips} role="group" aria-label="Фильтры">
          <button type="button" className={styles.chip} aria-pressed={state.when === 'all'} onClick={() => update({ when: 'all' })}>Все даты</button>
          <button type="button" className={styles.chip} aria-pressed={state.when === 'today'} onClick={() => update({ when: 'today' })}>Сегодня</button>
          <button type="button" className={styles.chip} aria-pressed={state.when === 'week'} onClick={() => update({ when: 'week' })}>Эта неделя</button>
          <button type="button" className={styles.chip} aria-pressed={state.cheap} onClick={() => update({ cheap: !state.cheap })}>До 30 PLN</button>
          {CITIES.map((city) => (
            <button key={city.key} type="button" className={styles.chip} aria-pressed={state.city === city.key}
              onClick={() => update({ city: state.city === city.key ? null : city.key })}>
              {city.label}
            </button>
          ))}
        </div>
      </div>

      {results.isError && (
        <div className={styles.status} role="alert">
          Не удалось выполнить поиск.{' '}
          <button type="button" className="btn-primary" onClick={() => void results.refetch()}>Повторить</button>
        </div>
      )}

      {results.isSuccess && (
        <div className={styles.grid}>
          {items.map((event) => <EventCard key={event.id} event={event} />)}
          <div className={styles.more} role={items.length === 0 ? 'status' : undefined}>
            <span>
              {items.length === 0 ? 'Ничего не нашли. ' : 'Ещё не нашли? '}
              {state.q ? 'Попробуйте другое слово, уберите фильтры' : 'Уберите фильтры'} или{' '}
              <Link to="/events?when=week" className={styles.moreLink}>посмотрите все события недели</Link>.
            </span>
            {filtered && (
              <button type="button" className={styles.reset} onClick={() => setParams(new URLSearchParams(), { replace: true })}>
                Сбросить фильтры
              </button>
            )}
          </div>
        </div>
      )}
    </section>
  )
}
