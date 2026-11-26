import type { RouteObject } from 'react-router-dom'
import { NotFoundPage } from '../pages/NotFoundPage'
import { HomePage } from '../pages/home/HomePage'
import { EventPage } from '../pages/event/EventPage'
import { BookingPage } from '../pages/event/BookingPage'
import { SearchPage } from '../pages/search/SearchPage'
import { WeekPage } from '../pages/week/WeekPage'
import { MapPage } from '../pages/map/MapPage'

/** Screens inside the common frame (header on top). */
export const routes: RouteObject[] = [
  { path: '/', element: <HomePage /> },
  { path: '/events/:id', element: <EventPage /> },
  { path: '/events/:id/book', element: <BookingPage /> },
  { path: '/events', element: <WeekPage /> },
  { path: '/search', element: <SearchPage /> },
  { path: '/map', element: <MapPage /> },
  { path: '*', element: <NotFoundPage /> },
]
