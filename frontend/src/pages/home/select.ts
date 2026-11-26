import type { EventItem } from '../../api/events'

/**
 * What goes where on the bento grid. The big tile is the nearest event that can still be booked (a sold-out event in
 * the most visible place helps nobody); then three tiles; the week strip shows this week's events that are not on a
 * tile yet, and "ещё N" counts this week's events shown nowhere.
 */
export function arrange(fromToday: EventItem[], week: EventItem[], weekTotal: number) {
  const big = fromToday.find((event) => event.seatStatus !== 'SOLD_OUT') ?? fromToday[0]
  const tiles = fromToday.filter((event) => event !== big).slice(0, 3)
  const onGrid = new Set([big, ...tiles].filter(Boolean).map((event) => event!.id))
  const weekCards = week.filter((event) => !onGrid.has(event.id)).slice(0, 3)
  const shownThisWeek = week.filter((event) => onGrid.has(event.id)).length + weekCards.length
  return { big, tiles, weekCards, more: Math.max(0, weekTotal - shownThisWeek) }
}
