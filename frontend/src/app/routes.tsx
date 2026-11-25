import type { RouteObject } from 'react-router-dom'
import { BoardPage } from '../pages/BoardPage'
import { NotFoundPage } from '../pages/NotFoundPage'

/** The page map of design/README.md: route → approved board. */
export const pages: { path: string; title: string; board: string }[] = [
  { path: '/', title: 'Главная', board: 'D-bento' },
  { path: '/events', title: 'Все события', board: 'J-week' },
  { path: '/events/:id', title: 'Событие', board: 'O-event' },
  { path: '/events/:id/book', title: 'Бронирование', board: 'I-booking' },
  { path: '/search', title: 'Поиск', board: 'G-search' },
  { path: '/map', title: 'Карта', board: 'H-map' },
  { path: '/account', title: 'Кабинет', board: 'F-account' },
  { path: '/account/favorites', title: 'Избранное', board: 'K-favorites' },
  { path: '/account/profile', title: 'Профиль', board: 'L-profile' },
  { path: '/account/notifications', title: 'Уведомления', board: 'M-notifications' },
  { path: '/login', title: 'Вход', board: 'S-auth' },
  { path: '/register', title: 'Регистрация', board: 'S-auth' },
  { path: '/organizer', title: 'Кабинет организатора', board: 'T-organizer' },
  { path: '/organizer/events/:id/edit', title: 'Редактор события', board: 'U-editor' },
]

export const routes: RouteObject[] = [
  ...pages.map(({ path, title, board }) => ({ path, element: <BoardPage title={title} board={board} /> })),
  { path: '*', element: <NotFoundPage /> },
]
