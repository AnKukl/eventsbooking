import { createBrowserRouter } from 'react-router-dom'
import { Layout } from './Layout'
import { HomePage } from '../pages/HomePage'
import { OrganizerPage } from '../pages/OrganizerPage'
import { NotFoundPage } from '../pages/NotFoundPage'

export const router = createBrowserRouter([
  {
    element: <Layout />,
    children: [
      { path: '/', element: <HomePage /> },
      { path: '/organizer', element: <OrganizerPage /> },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
])
