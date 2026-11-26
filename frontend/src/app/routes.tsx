import type { RouteObject } from 'react-router-dom'
import { BoardPage } from '../pages/BoardPage'
import { NotFoundPage } from '../pages/NotFoundPage'
import { HomePage } from '../pages/home/HomePage'
import { FavoritesPage } from '../pages/account/FavoritesPage'
import { EventPage } from '../pages/event/EventPage'
import { BookingPage } from '../pages/event/BookingPage'
import { SearchPage } from '../pages/search/SearchPage'
import { WeekPage } from '../pages/week/WeekPage'
import { MapPage } from '../pages/map/MapPage'

/** The page map of design/README.md for screens not built yet: route → approved board. */
export const pages: { path: string; title: string; board: string }[] = [
  { path: '/account', title: 'Кабинет', board: 'F-account' },
  { path: '/account/profile', title: 'Профиль', board: 'L-profile' },
  { path: '/account/notifications', title: 'Уведомления', board: 'M-notifications' },
  { path: '/organizer', title: 'Кабинет организатора', board: 'T-organizer' },
  { path: '/organizer/events/:id/edit', title: 'Редактор события', board: 'U-editor' },
]

/** Screens inside the common frame (header on top). */
export const routes: RouteObject[] = [
  { path: '/', element: <HomePage /> },
  { path: '/account/favorites', element: <FavoritesPage /> },
  { path: '/events/:id', element: <EventPage /> },
  { path: '/events/:id/book', element: <BookingPage /> },
  { path: '/events', element: <WeekPage /> },
  { path: '/search', element: <SearchPage /> },
  { path: '/map', element: <MapPage /> },
  ...pages.map(({ path, title, board }) => ({ path, element: <BoardPage title={title} board={board} /> })),
  { path: '*', element: <NotFoundPage /> },
]
