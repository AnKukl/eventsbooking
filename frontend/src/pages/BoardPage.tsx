/**
 * A route that exists but whose screen is not built yet. It names the approved board it will be made from
 * (design/pages), so nothing is invented in the meantime.
 */
export function BoardPage({ title, board }: { title: string; board: string }) {
  return (
    <section>
      <h1>{title}</h1>
      <p>
        Экран по макету <code>design/pages/{board}.dc.html</code>, ещё не сверстан.
      </p>
    </section>
  )
}
