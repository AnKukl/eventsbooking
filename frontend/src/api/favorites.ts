import { type QueryClient, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useRef } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { api } from './client'
import type { EventItem, EventPage } from './events'
import { useMe } from './session'
import { useToast } from '../components/toast/Toast'

type Change = { id: number; favorite: boolean }
type Snapshot = [readonly unknown[], unknown][]

/** A cached list page or a single event, with the flag of one event changed. */
function withFlag(data: unknown, id: number, favorite: boolean): unknown {
  if (!data || typeof data !== 'object') return data
  const page = data as EventPage
  if (Array.isArray(page.content)) {
    return { ...page, content: page.content.map((event) => (event.id === id ? { ...event, isFavorite: favorite } : event)) }
  }
  const event = data as EventItem
  return event.id === id ? { ...event, isFavorite: favorite } : data
}

/** The favourites list without the event (it disappears from "Избранное" at once). */
function without(data: unknown, id: number): unknown {
  const page = data as EventPage | undefined
  if (!page || !Array.isArray(page.content)) return data
  return { ...page, content: page.content.filter((event) => event.id !== id) }
}

function snapshot(client: QueryClient): Snapshot {
  return [...client.getQueriesData({ queryKey: ['events'] }), ...client.getQueriesData({ queryKey: ['favorites'] })]
}

/**
 * ♥ everywhere: the heart changes at once (optimistic), and goes back with a message if the server refuses. After an
 * addition a toast offers "Отменить" for five seconds. A guest is sent to sign in and comes back (returnTo).
 */
export function useFavorite() {
  const client = useQueryClient()
  const toast = useToast()
  const me = useMe()
  const navigate = useNavigate()
  const location = useLocation()
  const undo = useRef<(change: Change) => void>(() => {})

  const mutation = useMutation({
    mutationFn: ({ id, favorite }: Change) => api<void>(`/api/favorites/${id}`, { method: favorite ? 'POST' : 'DELETE' }),
    onMutate: async ({ id, favorite }: Change) => {
      await Promise.all([client.cancelQueries({ queryKey: ['events'] }), client.cancelQueries({ queryKey: ['favorites'] })])
      const before = snapshot(client)
      client.setQueriesData({ queryKey: ['events'] }, (data) => withFlag(data, id, favorite))
      client.setQueriesData({ queryKey: ['favorites'] }, (data) => (favorite ? withFlag(data, id, true) : without(data, id)))
      return { before }
    },
    onError: (_error, _change, context) => {
      context?.before.forEach(([key, data]) => client.setQueryData(key, data))
      toast.show({ message: 'Не удалось обновить избранное. Попробуйте ещё раз.' })
    },
    onSuccess: (_data, { id, favorite }) => {
      if (favorite) {
        toast.show({ message: 'Сохранено в избранное', action: { label: 'Отменить', onClick: () => undo.current({ id, favorite: false }) } })
      }
    },
    onSettled: () => client.invalidateQueries({ queryKey: ['favorites'] }),
  })
  undo.current = mutation.mutate

  function toggle(event: EventItem) {
    if (me.isPending) return
    if (!me.data) {
      navigate(`/login?returnTo=${encodeURIComponent(location.pathname + location.search)}`)
      return
    }
    mutation.mutate({ id: event.id!, favorite: !event.isFavorite })
  }

  return { toggle, ready: !me.isPending }
}

/** My favourites, newest first. */
export function useFavorites() {
  return useQuery({
    queryKey: ['favorites'],
    queryFn: () => api<EventPage>('/api/favorites?page=0&size=50'),
  })
}
