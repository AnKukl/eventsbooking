import { describe, expect, it } from 'vitest'
import { render } from '@testing-library/react'
import { Logo } from './Logo'
import { designFile, tokenValue } from '../test/design'

/** Every shape with its geometry and its fill, colours resolved through the tokens. */
function shapes(svg: Element): string[] {
  return Array.from(svg.querySelectorAll('rect, circle')).map((shape) => {
    const geometry = ['x', 'y', 'width', 'height', 'rx', 'cx', 'cy', 'r']
      .filter((name) => shape.hasAttribute(name))
      .map((name) => `${name}=${shape.getAttribute(name)}`)
    const fill = shape.getAttribute('fill') ?? ''
    const colour = fill.startsWith('var(') ? tokenValue(fill.slice(4, -1)) : fill
    return `${shape.tagName.toLowerCase()} ${geometry.join(' ')} fill=${colour.toUpperCase()}`
  })
}

describe('Logo', () => {
  it('is the mark of design/logo.svg: the same shapes, sizes and colours', () => {
    const approved = new DOMParser().parseFromString(designFile('logo.svg'), 'image/svg+xml').documentElement
    const { container } = render(<Logo />)
    const ours = container.querySelector('svg')!

    expect(ours.getAttribute('viewBox')).toBe(approved.getAttribute('viewBox'))
    expect(ours.getAttribute('width')).toBe('40')
    expect(ours.getAttribute('height')).toBe('40')
    expect(shapes(ours)).toEqual(shapes(approved))
  })

  it('two logos on one page do not share a mask id', () => {
    const { container } = render(
      <>
        <Logo />
        <Logo />
      </>,
    )

    const ids = Array.from(container.querySelectorAll('mask')).map((mask) => mask.id)
    expect(new Set(ids).size).toBe(2)
  })
})
