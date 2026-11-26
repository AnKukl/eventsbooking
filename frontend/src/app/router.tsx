import { Navigate, createBrowserRouter } from 'react-router-dom'
import { Layout } from './Layout'
import { routes } from './routes'
import { AuthPage } from '../pages/auth/AuthPage'
import { AccountLayout } from '../pages/account/AccountLayout'
import { BookingsTab } from '../pages/account/BookingsTab'
import { FavoritesPage } from '../pages/account/FavoritesPage'
import { ProfileTab } from '../pages/account/ProfileTab'
import { NotificationsTab } from '../pages/account/NotificationsTab'
import { OrganizerLayout } from '../pages/organizer/OrganizerLayout'
import { OrganizerPage } from '../pages/organizer/OrganizerPage'
import { EditorPage } from '../pages/organizer/EditorPage'
import { EventBookingsPage } from '../pages/organizer/EventBookingsPage'

export const router = createBrowserRouter([
  { element: <Layout />, children: routes },
  // the cabinet has its own header (logo + "Главная") and a tab per URL
  {
    path: '/account',
    element: <AccountLayout />,
    children: [
      { index: true, element: <Navigate to="/account/bookings" replace /> },
      { path: 'bookings', element: <BookingsTab /> },
      { path: 'favorites', element: <FavoritesPage /> },
      { path: 'profile', element: <ProfileTab /> },
      { path: 'notifications', element: <NotificationsTab /> },
    ],
  },
  // the organizer's cabinet: ORGANIZER only (others see 403)
  {
    path: '/organizer',
    element: <OrganizerLayout />,
    children: [
      { index: true, element: <OrganizerPage /> },
      { path: 'events/:id/edit', element: <EditorPage /> },
      { path: 'events/:id/bookings', element: <EventBookingsPage /> },
    ],
  },
  // S-auth is a screen of its own: no header
  { path: '/login', element: <AuthPage mode="login" /> },
  { path: '/register', element: <AuthPage mode="register" /> },
])
