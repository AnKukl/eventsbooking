import type { RouteObject } from 'react-router-dom'
import { BoardPage } from '../pages/BoardPage'
import { NotFoundPage } from '../pages/NotFoundPage'
import { HomePage } from '../pages/home/HomePage'

/** The page map of design/README.md for screens not built yet: route → approved board. */
export const pages: { path: string; title: string; board: string }[] = [
  { path: '/events', title: 'Все события', board: 'J-week' },
  { path: '/events/:id', title: 'Событие', board: 'O-event' },
  { path: '/events/:id/book', title: 'Бронирование', board: 'I-booking' },
  { path: '/search', title: 'Поиск', board: 'G-search' },
  { path: '/map', title: 'Карта', board: 'H-map' },
  { path: '/account', title: 'Кабинет', board: 'F-account' },
  { path: '/account/favorites', title: 'Избранное', board: 'K-favorites' },
  { path: '/account/profile', title: 'Профиль', board: 'L-profile' },
  { path: '/account/notifications', title: 'Уведомления', board: 'M-notifications' },
  { path: '/organizer', title: 'Кабинет организатора', board: 'T-organizer' },
  { path: '/organizer/events/:id/edit', title: 'Редактор события', board: 'U-editor' },
]

/** Screens inside the common frame (header on top). */
export const routes: RouteObject[] = [
  { path: '/', element: <HomePage /> },
  ...pages.map(({ path, title, board }) => ({ path, element: <BoardPage title={title} board={board} /> })),
  { path: '*', element: <NotFoundPage /> },
]
