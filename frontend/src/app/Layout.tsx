import { Outlet } from 'react-router-dom'
import { Header } from '../components/Header'
import { initialOf, useMe } from '../api/session'

/** The approved page frame: gradient background (body), 1280 px column, header on top (D-bento). */
export function Layout() {
  const me = useMe()
  return (
    <div className="page">
      <Header initial={initialOf(me.data)} />
      <main>
        <Outlet />
      </main>
    </div>
  )
}
