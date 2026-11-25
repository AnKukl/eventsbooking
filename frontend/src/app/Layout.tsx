import { Outlet } from 'react-router-dom'
import { Header } from '../components/Header'

/** The approved page frame: gradient background (body), 1280 px column, header on top (D-bento). */
export function Layout() {
  return (
    <div className="page">
      <Header />
      <main>
        <Outlet />
      </main>
    </div>
  )
}
