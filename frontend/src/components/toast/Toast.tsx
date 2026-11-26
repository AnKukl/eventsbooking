import { createContext, type ReactNode, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react'
import styles from './Toast.module.css'

export const TOAST_MS = 5000

type ToastOptions = { message: string; action?: { label: string; onClick: () => void } }
type ToastApi = { show: (toast: ToastOptions) => void; hide: () => void }

const ToastContext = createContext<ToastApi | null>(null)

/** One toast at a time (a new one replaces the old); it closes by itself after five seconds. */
export function ToastProvider({ children }: { children: ReactNode }) {
  const [toast, setToast] = useState<(ToastOptions & { key: number }) | null>(null)
  const timer = useRef<ReturnType<typeof setTimeout> | undefined>(undefined)

  const hide = useCallback(() => {
    clearTimeout(timer.current)
    setToast(null)
  }, [])

  const show = useCallback((next: ToastOptions) => {
    clearTimeout(timer.current)
    setToast({ ...next, key: Date.now() })
    timer.current = setTimeout(() => setToast(null), TOAST_MS)
  }, [])

  useEffect(() => () => clearTimeout(timer.current), [])

  const api = useMemo(() => ({ show, hide }), [show, hide])

  return (
    <ToastContext.Provider value={api}>
      {children}
      <div aria-live="polite">
        {toast && (
          <div key={toast.key} className={styles.toast} role="status">
            {toast.message}
            {toast.action && (
              <button
                type="button"
                className={styles.action}
                onClick={() => {
                  hide()
                  toast.action!.onClick()
                }}
              >
                {toast.action.label}
              </button>
            )}
          </div>
        )}
      </div>
    </ToastContext.Provider>
  )
}

export function useToast(): ToastApi {
  const toast = useContext(ToastContext)
  if (!toast) throw new Error('useToast needs a ToastProvider')
  return toast
}
