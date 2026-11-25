import type { EventItem } from '../../api/events'

const time = new Intl.DateTimeFormat('ru-RU', { hour: '2-digit', minute: '2-digit' })
const weekday = new Intl.DateTimeFormat('ru-RU', { weekday: 'long' })
const date = new Intl.DateTimeFormat('ru-RU', { day: 'numeric', month: 'long' })

function startOfDay(value: Date): number {
  return new Date(value.getFullYear(), value.getMonth(), value.getDate()).getTime()
}

/** "сегодня 18:00", "завтра 20:00", "суббота 11:00", "12 декабря 19:00" (the boards use this wording). */
export function when(startsAt: string, now: Date = new Date()): string {
  const start = new Date(startsAt)
  const days = Math.round((startOfDay(start) - startOfDay(now)) / 86_400_000)
  const day = days === 0 ? 'сегодня' : days === 1 ? 'завтра' : days > 1 && days < 7 ? weekday.format(start) : date.format(start)
  return `${day} ${time.format(start)}`
}

/** The same with a capital first letter, for the "В эту неделю" cards ("Завтра · 20:00"). */
export function whenTitle(startsAt: string, now: Date = new Date()): string {
  const text = when(startsAt, now).replace(/ (\d\d:\d\d)$/, ' · $1')
  return text.charAt(0).toUpperCase() + text.slice(1)
}

/** "45 PLN", "Бесплатно", or nothing when the price is unknown. */
export function price(event: EventItem): string | null {
  if (event.price === undefined || event.price === null) return null
  return Number(event.price) === 0 ? 'Бесплатно' : `${Number(event.price).toLocaleString('ru-RU')} PLN`
}

export function seatsLeft(event: EventItem): number {
  return Math.max(0, (event.capacity ?? 0) - (event.bookedCount ?? 0))
}

/** "Осталось 14 мест", "Осталось 1 место", "Осталось 3 места" */
export function seatsText(count: number): string {
  const tail = count % 10 === 1 && count % 100 !== 11 ? 'место'
    : [2, 3, 4].includes(count % 10) && ![12, 13, 14].includes(count % 100) ? 'места' : 'мест'
  return `${count} ${tail}`
}
