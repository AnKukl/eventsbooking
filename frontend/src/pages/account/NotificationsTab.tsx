import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { type Notification, type Preference, useMarkAllRead, useNotifications, usePreferences, useSavePreferences } from '../../api/account'
import { startOfToday } from '../../api/events'
import { useToast } from '../../components/toast/Toast'
import styles from './Tabs.module.css'

const time = new Intl.DateTimeFormat('ru-RU', { hour: '2-digit', minute: '2-digit' })
const date = new Intl.DateTimeFormat('ru-RU', { day: 'numeric', month: 'long' })

const TYPE_LABELS: Record<string, string> = {
  BOOKING_CONFIRMED: 'Подтверждение брони',
  SEAT_AVAILABLE: 'Освободилось место',
  REMINDER_24H: 'Напоминание за сутки',
  REMINDER_2H: 'Напоминание за 2 часа',
}

/** "Сегодня", "Вчера", "Ранее" — the groups of the board. */
export function groupByDay(items: Notification[], now: Date = new Date()): { label: string; items: Notification[] }[] {
  const today = startOfToday(now).getTime()
  const yesterday = today - 86_400_000
  const groups: Record<string, Notification[]> = { Сегодня: [], Вчера: [], Ранее: [] }
  for (const item of items) {
    const at = new Date(item.createdAt!).getTime()
    groups[at >= today ? 'Сегодня' : at >= yesterday ? 'Вчера' : 'Ранее'].push(item)
  }
  return Object.entries(groups).filter(([, list]) => list.length > 0).map(([label, list]) => ({ label, items: list }))
}

/** "Уведомления" (design/pages/M-notifications). Opening the tab marks everything read; the dots still show what was new. */
export function NotificationsTab() {
  const feed = useNotifications()
  const markAll = useMarkAllRead()
  const preferences = usePreferences()
  const savePreferences = useSavePreferences()
  const toast = useToast()
  const [seenUnread, setSeenUnread] = useState<Set<number> | null>(null)
  const marked = useRef(false)

  // remember which ones were unread when the feed arrived, then mark them read on the server once
  useEffect(() => {
    if (!feed.data || marked.current) return
    const unread = new Set((feed.data.content ?? []).filter((item) => !item.readAt).map((item) => item.id!))
    setSeenUnread(unread)
    marked.current = true
    if (unread.size > 0) markAll.mutate()
  }, [feed.data, markAll])

  function toggle(type: Preference['type'], channel: 'email' | 'push') {
    const current = preferences.data?.preferences ?? []
    const next = current.map((item) => (item.type === type ? { ...item, [channel]: !item[channel] } : item))
    savePreferences.mutate(next, { onError: () => toast.show({ message: 'Не удалось сохранить настройки. Попробуйте ещё раз.' }) })
  }

  const items = feed.data?.content ?? []

  return (
    <section className={styles.feed}>
      <div className={`${styles.card} ${styles.feedCard}`}>
        <div className={styles.feedHead}>
          <h1 className={styles.feedTitle}>Уведомления</h1>
          <button type="button" className={styles.small} disabled={markAll.isPending}
            onClick={() => { setSeenUnread(new Set()); markAll.mutate() }}>
            Отметить все прочитанными
          </button>
        </div>
        {feed.isPending && <p className={styles.status}>Загружаем уведомления…</p>}
        {feed.isError && (
          <div className={styles.status} role="alert">
            Не удалось загрузить уведомления.{' '}
            <button type="button" className={styles.small} onClick={() => void feed.refetch()}>Повторить</button>
          </div>
        )}
        {feed.isSuccess && items.length === 0 && <p className={styles.empty}>Уведомлений пока нет.</p>}
        {groupByDay(items).map((group) => (
          <div key={group.label} role="group" aria-label={group.label}>
            <span className={styles.group}>{group.label}</span>
            {group.items.map((item) => {
              const unread = seenUnread?.has(item.id!) ?? !item.readAt
              const content = (
                <>
                  <span className={`${styles.dot} ${unread ? '' : styles.dotRead}`} aria-label={unread ? 'Новое' : 'Прочитано'} />
                  <span>
                    <span className={styles.noteTitle}>{item.title}</span>
                    <br />
                    <span className={styles.noteBody}>{item.body}</span>
                  </span>
                  <span className={styles.time}>
                    {group.label === 'Ранее' ? date.format(new Date(item.createdAt!)) : time.format(new Date(item.createdAt!))}
                  </span>
                </>
              )
              return item.targetEventId
                ? <Link key={item.id} to={`/events/${item.targetEventId}`} className={styles.note}>{content}</Link>
                : <div key={item.id} className={styles.note}>{content}</div>
            })}
          </div>
        ))}
      </div>

      <aside className={`${styles.card} ${styles.prefs}`} aria-label="Что присылать">
        <h2 className={styles.smallTitle}>Что присылать</h2>
        <div className={styles.prefRow}>
          <span />
          <span className={styles.prefHead}>Письмо</span>
          <span className={styles.prefHead}>Push</span>
        </div>
        {(preferences.data?.preferences ?? []).map((item) => (
          <div key={item.type} className={styles.prefRow}>
            <span>{TYPE_LABELS[item.type!] ?? item.type}</span>
            {(['email', 'push'] as const).map((channel) => (
              <button key={channel} type="button" role="switch" className={styles.switch}
                aria-checked={Boolean(item[channel])}
                aria-label={`${TYPE_LABELS[item.type!] ?? item.type}: ${channel === 'email' ? 'письмо' : 'push'}`}
                disabled={savePreferences.isPending}
                onClick={() => toggle(item.type, channel)} />
            ))}
          </div>
        ))}
        {preferences.isError && <span className={styles.fieldError} role="alert">Не удалось загрузить настройки.</span>}
      </aside>
    </section>
  )
}
