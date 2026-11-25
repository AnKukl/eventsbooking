import { createBrowserRouter } from 'react-router-dom'
import { Layout } from './Layout'
import { routes } from './routes'
import { AuthPage } from '../pages/auth/AuthPage'

export const router = createBrowserRouter([
  { element: <Layout />, children: routes },
  // S-auth is a screen of its own: no header
  { path: '/login', element: <AuthPage mode="login" /> },
  { path: '/register', element: <AuthPage mode="register" /> },
])
