/**
 * Quotes for the sign-in screen (design/pages/S-auth, renderVals). Rodin's "I take a block of marble…" is left out:
 * design/README.md notes that no primary source was found for it. Add a replacement only with a verified source.
 */
export const QUOTES = [
  { text: 'Искусство — ложь, которая помогает нам увидеть правду.', author: 'Пабло Пикассо' },
  { text: 'Я вложил в работу сердце и душу и в процессе потерял рассудок.', author: 'Винсент ван Гог' },
  { text: 'Творчество требует смелости.', author: 'Анри Матисс' },
  {
    text: 'Живопись — это поэзия, которую видят, а не чувствуют; поэзия — это живопись, которую чувствуют, а не видят.',
    author: 'Леонардо да Винчи',
  },
] as const

export function randomQuote(random: () => number = Math.random) {
  return QUOTES[Math.floor(random() * QUOTES.length)]
}
