import { afterEach, describe, expect, it, vi } from 'vitest'
import { act, fireEvent, render, screen } from '@testing-library/react'
import { TOAST_MS, ToastProvider, useToast } from './Toast'

afterEach(() => vi.useRealTimers())

function Trigger({ onUndo }: { onUndo: () => void }) {
  const toast = useToast()
  return (
    <button onClick={() => toast.show({ message: 'Сохранено в избранное', action: { label: 'Отменить', onClick: onUndo } })}>
      show
    </button>
  )
}

describe('Toast', () => {
  it('closes by itself after five seconds', () => {
    vi.useFakeTimers()
    render(<ToastProvider><Trigger onUndo={() => {}} /></ToastProvider>)

    fireEvent.click(screen.getByText('show'))
    expect(screen.getByRole('status')).toHaveTextContent('Сохранено в избранное')

    act(() => vi.advanceTimersByTime(TOAST_MS - 1))
    expect(screen.getByRole('status')).toBeInTheDocument()
    act(() => vi.advanceTimersByTime(1))
    expect(screen.queryByRole('status')).not.toBeInTheDocument()
    expect(TOAST_MS).toBe(5000)
  })

  it('"Отменить" runs the action and closes the toast', () => {
    const undo = vi.fn()
    render(<ToastProvider><Trigger onUndo={undo} /></ToastProvider>)

    fireEvent.click(screen.getByText('show'))
    fireEvent.click(screen.getByRole('button', { name: 'Отменить' }))

    expect(undo).toHaveBeenCalledOnce()
    expect(screen.queryByRole('status')).not.toBeInTheDocument()
  })
})
