import { type FormEvent, useMemo, useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { z } from 'zod'
import landscape from '../../assets/auth-landscape.jpg'
import { ApiError } from '../../api/client'
import { useLogin, useRegister } from '../../api/session'
import { randomQuote } from './quotes'
import styles from './AuthPage.module.css'

type Mode = 'login' | 'register'

const loginSchema = z.object({
  email: z.string().trim().min(1, 'Введите email'),
  password: z.string().min(1, 'Введите пароль'),
})

// the same limits as RegisterRequest on the server
const registerSchema = z.object({
  email: z.string().trim().min(1, 'Введите email').email('Проверьте email').max(255, 'Слишком длинный email'),
  password: z.string().min(8, 'Пароль не короче 8 символов').max(72, 'Пароль не длиннее 72 символов'),
})

type FieldErrors = Partial<Record<'email' | 'password', string>>

const TEXT: Record<Mode, { title: string; submit: string }> = {
  login: { title: 'С возвращением', submit: 'Войти' },
  register: { title: 'Регистрация', submit: 'Зарегистрироваться' },
}

/** Where to go after signing in: the page the guest came from (state.from), else the home page. */
function target(state: unknown): string {
  const from = (state as { from?: string } | null)?.from
  return from && from.startsWith('/') && !from.startsWith('//') ? from : '/'
}

function problemMessage(error: unknown, mode: Mode): string {
  if (!(error instanceof ApiError)) return 'Нет связи с сервером. Попробуйте ещё раз.'
  if (error.status === 401) return 'Неверный email или пароль. Попробуйте ещё раз.'
  if (error.status === 409 && mode === 'register') return 'Этот email уже зарегистрирован. Войдите или укажите другой.'
  if (error.status === 429) return `Слишком много попыток. Повторите через ${error.retryAfter ?? 60} с.`
  if (error.status >= 500) return 'Сервис временно недоступен. Повторите через несколько секунд.'
  return error.problem?.detail ?? 'Не получилось. Проверьте данные и попробуйте ещё раз.'
}

/** design/pages/S-auth. "Запомнить меня", password recovery and Google sign-in are hidden: the API has none of them. */
export function AuthPage({ mode }: { mode: Mode }) {
  const navigate = useNavigate()
  const location = useLocation()
  const quote = useMemo(() => randomQuote(), [])
  const login = useLogin()
  const register = useRegister()
  const action = mode === 'login' ? login : register
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({})

  function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    const parsed = (mode === 'login' ? loginSchema : registerSchema).safeParse({
      email: form.get('email'),
      password: form.get('password'),
    })
    if (!parsed.success) {
      const errors: FieldErrors = {}
      for (const issue of parsed.error.issues) {
        const field = issue.path[0] as keyof FieldErrors
        errors[field] ??= issue.message
      }
      setFieldErrors(errors)
      return
    }
    setFieldErrors({})
    action.mutate(parsed.data, {
      onSuccess: () => navigate(target(location.state), { replace: true }),
      onError: (error) => {
        // 400 from the server: show its messages next to the fields
        if (error instanceof ApiError && error.status === 400 && error.problem?.errors?.length) {
          const errors: FieldErrors = {}
          for (const violation of error.problem.errors) {
            if (violation.field === 'email' || violation.field === 'password') errors[violation.field] ??= violation.message
          }
          setFieldErrors(errors)
        }
      },
    })
  }

  const serverError =
    action.isError && !(action.error instanceof ApiError && action.error.status === 400 && action.error.problem?.errors?.length)
      ? problemMessage(action.error, mode)
      : null

  return (
    <div className={styles.screen}>
      <section className={styles.landscape}>
        <img className={styles.photo} src={landscape} alt="Гора Фудзи, озеро и цветущая сакура" />
        <figure role="note" className={styles.quote}>
          <blockquote>«{quote.text}»</blockquote>
          <figcaption>{quote.author}</figcaption>
        </figure>
      </section>

      <section className={styles.panel}>
        <Link to="/" className={styles.brand}>
          Events Booking
        </Link>
        <nav className={styles.tabs} aria-label="Вход или регистрация">
          <Link
            to="/login"
            state={location.state}
            replace
            className={`${styles.tab} ${mode === 'login' ? styles.tabActive : ''}`}
            aria-current={mode === 'login' ? 'page' : undefined}
          >
            Вход
          </Link>
          <Link
            to="/register"
            state={location.state}
            replace
            className={`${styles.tab} ${mode === 'register' ? styles.tabActive : ''}`}
            aria-current={mode === 'register' ? 'page' : undefined}
          >
            Регистрация
          </Link>
        </nav>
        <h1 className={styles.title}>{TEXT[mode].title}</h1>

        <form className={styles.form} onSubmit={onSubmit} noValidate>
          <label className={styles.label}>
            Email
            <input
              className={styles.input}
              name="email"
              type="email"
              autoComplete="email"
              aria-invalid={Boolean(fieldErrors.email)}
            />
            {fieldErrors.email && <span className={styles.fieldError}>{fieldErrors.email}</span>}
          </label>
          <label className={styles.label}>
            Пароль
            <input
              className={styles.input}
              name="password"
              type="password"
              autoComplete={mode === 'login' ? 'current-password' : 'new-password'}
              aria-invalid={Boolean(fieldErrors.password)}
            />
            {fieldErrors.password && <span className={styles.fieldError}>{fieldErrors.password}</span>}
          </label>
          <button className={styles.submit} type="submit" disabled={action.isPending}>
            {TEXT[mode].submit}
          </button>
          {serverError && (
            <span className={styles.error} role="alert">
              {serverError}
            </span>
          )}
        </form>

        <span className={styles.legal}>
          Нажимая «{TEXT[mode].submit}», вы принимаете условия использования и политику конфиденциальности.
        </span>
      </section>
    </div>
  )
}
