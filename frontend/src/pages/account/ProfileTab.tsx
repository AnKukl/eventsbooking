import { type FormEvent, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { z } from 'zod'
import { ApiError } from '../../api/client'
import { useChangeEmail, useSaveProfile } from '../../api/account'
import { useMe } from '../../api/session'
import { useToast } from '../../components/toast/Toast'
import styles from './Tabs.module.css'

type Language = 'RU' | 'EN' | 'PL'
type Form = { name: string; city: string; phone: string; language: Language }
type Errors = Partial<Record<keyof Form | 'email', string>>

// the same rules as ProfileUpdateRequest on the server; an empty value clears the field
const schema = z.object({
  name: z.string().trim().max(100, 'Не длиннее 100 символов'),
  city: z.string().trim().max(100, 'Не длиннее 100 символов'),
  phone: z.string().trim().refine((value) => value === '' || /^\+[1-9]\d{7,14}$/.test(value), 'Формат +48123456789'),
  language: z.enum(['RU', 'EN', 'PL']),
})

const LANGUAGES: Language[] = ['RU', 'EN', 'PL']

/** "Профиль" (design/pages/L-profile). */
export function ProfileTab() {
  const me = useMe()
  const save = useSaveProfile()
  const changeEmail = useChangeEmail()
  const toast = useToast()
  const initial: Form = {
    name: me.data?.name ?? '', city: me.data?.city ?? '', phone: me.data?.phone ?? '',
    language: (me.data?.language as Language) ?? 'RU',
  }
  const [form, setForm] = useState<Form>(initial)
  const [errors, setErrors] = useState<Errors>({})
  const [newEmail, setNewEmail] = useState('')
  const [emailOpen, setEmailOpen] = useState(false)
  const loadedKey = JSON.stringify(initial)

  // when the profile (re)loads, the form starts from it
  useEffect(() => setForm(JSON.parse(loadedKey) as Form), [loadedKey])

  if (!me.data) return null
  const dirty = JSON.stringify(form) !== loadedKey

  function set<K extends keyof Form>(key: K, value: Form[K]) {
    setForm((current) => ({ ...current, [key]: value }))
  }

  function submit(event: FormEvent) {
    event.preventDefault()
    const parsed = schema.safeParse(form)
    if (!parsed.success) {
      const found: Errors = {}
      for (const issue of parsed.error.issues) found[issue.path[0] as keyof Form] ??= issue.message
      setErrors(found)
      return
    }
    setErrors({})
    save.mutate(parsed.data, {
      onSuccess: () => toast.show({ message: 'Профиль сохранён' }),
      onError: (error) => {
        if (error instanceof ApiError && error.problem?.errors?.length) {
          const found: Errors = {}
          for (const violation of error.problem.errors) found[violation.field as keyof Form] ??= violation.message
          setErrors(found)
        }
      },
    })
  }

  function requestEmail(event: FormEvent) {
    event.preventDefault()
    const parsed = z.string().trim().email().safeParse(newEmail)
    if (!parsed.success) {
      setErrors((current) => ({ ...current, email: 'Проверьте email' }))
      return
    }
    changeEmail.mutate(parsed.data, {
      onSuccess: () => {
        toast.show({ message: `Ссылка для подтверждения отправлена на ${parsed.data}` })
        setEmailOpen(false)
        setNewEmail('')
        setErrors((current) => ({ ...current, email: undefined }))
      },
      onError: (error) => setErrors((current) => ({
        ...current,
        email: error instanceof ApiError && error.status === 429
          ? `Слишком часто. Повторите через ${error.retryAfter ?? 60} с.`
          : error instanceof ApiError && error.status === 409 ? 'Этот email уже занят.' : 'Не удалось отправить. Попробуйте ещё раз.',
      })),
    })
  }

  const saveError = save.isError && !(save.error instanceof ApiError && save.error.problem?.errors?.length)

  return (
    <section className={styles.profile}>
      <form className={`${styles.card} ${styles.form}`} onSubmit={submit} noValidate>
        <h1 className={styles.cardTitle}>Личные данные</h1>
        <div className={styles.fields}>
          <label className={styles.field}>
            Имя
            <input className={styles.input} name="name" value={form.name} onChange={(e) => set('name', e.target.value)} aria-invalid={Boolean(errors.name)} />
            {errors.name && <span className={styles.fieldError}>{errors.name}</span>}
          </label>
          <label className={styles.field}>
            Город
            <input className={styles.input} name="city" value={form.city} onChange={(e) => set('city', e.target.value)} aria-invalid={Boolean(errors.city)} />
            {errors.city && <span className={styles.fieldError}>{errors.city}</span>}
          </label>
          <div className={`${styles.field} ${styles.wide}`}>
            <label htmlFor="profile-email">Email</label>
            <div className={styles.emailRow}>
              <input id="profile-email" className={styles.input} value={me.data.email ?? ''} readOnly />
              <span className={me.data.emailVerified ? styles.verified : styles.unverified}>
                {me.data.emailVerified ? 'Подтверждён' : 'Не подтверждён'}
              </span>
            </div>
            {!emailOpen ? (
              <button type="button" className={styles.small} style={{ alignSelf: 'flex-start' }} onClick={() => setEmailOpen(true)}>Сменить email</button>
            ) : (
              <div className={styles.inline}>
                <input className={styles.input} style={{ flex: 1 }} type="email" placeholder="Новый email" aria-label="Новый email"
                  value={newEmail} onChange={(e) => setNewEmail(e.target.value)} />
                <button type="button" className={styles.small} onClick={requestEmail} disabled={changeEmail.isPending}>Отправить ссылку</button>
              </div>
            )}
            {errors.email && <span className={styles.fieldError} role="alert">{errors.email}</span>}
          </div>
          <label className={styles.field}>
            Телефон
            <input className={styles.input} name="phone" inputMode="tel" placeholder="+48123456789" value={form.phone}
              onChange={(e) => set('phone', e.target.value)} aria-invalid={Boolean(errors.phone)} />
            {errors.phone && <span className={styles.fieldError}>{errors.phone}</span>}
          </label>
          <div className={styles.field}>
            Язык интерфейса
            <div className={styles.segmented} role="group" aria-label="Язык интерфейса">
              {LANGUAGES.map((language) => (
                <button key={language} type="button" className={styles.segment} aria-pressed={form.language === language}
                  onClick={() => set('language', language)}>{language}</button>
              ))}
            </div>
          </div>
        </div>
        <div className={styles.saveRow}>
          <span aria-live="polite">{saveError ? 'Не удалось сохранить. Попробуйте ещё раз.' : dirty ? 'Изменения не сохранены' : ''}</span>
          <button type="submit" className={styles.primary} disabled={save.isPending || !dirty}>Сохранить</button>
        </div>
      </form>

      <div className={styles.side}>
        <div className={`${styles.card} ${styles.form}`}>
          <h2 className={styles.smallTitle}>Пароль</h2>
          <span className={styles.muted}>Смена пароля появится скоро.</span>
          <button type="button" className={styles.outline} style={{ alignSelf: 'flex-start' }} disabled title="Скоро">Сменить пароль</button>
        </div>
        <div className={`${styles.card} ${styles.form}`}>
          <h2 className={styles.smallTitle}>Уведомления</h2>
          <span className={styles.muted}>Что и как присылать: письмо или push для каждого вида уведомлений.</span>
          <Link to="/account/notifications" className={styles.outline} style={{ alignSelf: 'flex-start' }}>Настроить</Link>
        </div>
        <div className={`${styles.card} ${styles.danger}`}>
          <span className={styles.dangerTitle}>Удалить аккаунт</span>
          <span className={styles.muted}>Скоро. Брони сохранятся в истории, но войти будет нельзя. Действие необратимо.</span>
        </div>
      </div>
    </section>
  )
}
