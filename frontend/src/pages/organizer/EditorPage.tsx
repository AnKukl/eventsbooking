import { type DragEvent, useCallback, useEffect, useRef, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { z } from 'zod'
import { ApiError } from '../../api/client'
import { useBookingSettings } from '../../api/booking'
import { type OrganizerEvent, usePublish, useOrganizerEvent, useSaveEvent, useUploadPhotos } from '../../api/organizer'
import { useToast } from '../../components/toast/Toast'
import { when } from '../home/format'
import styles from './Organizer.module.css'

/** Autosave fires after this long without typing. */
export const AUTOSAVE_MS = 3000

type Form = {
  title: string; startsAt: string; venue: string; shortDescription: string; description: string
  price: string; capacity: string; audienceFit: string; audienceNotFit: string
  latitude: string; longitude: string; waitlistEnabled: boolean
}
type Field = keyof Form
type Errors = Partial<Record<Field | 'photos', string>>

const LABELS: Record<string, string> = {
  title: 'Название', startsAt: 'Дата и время', venue: 'Место', price: 'Цена', capacity: 'Всего мест',
  shortDescription: 'Короткое описание', description: 'Подробное описание', media: 'Фото', photos: 'Фото',
  audienceFit: 'Для кого подойдёт', audienceNotFit: 'Не подойдёт', latitude: 'Широта', longitude: 'Долгота', location: 'Точка на карте',
}

/** "2030-01-10T18:00" for <input type="datetime-local"> (local time) from an ISO instant, and back. */
function toLocalInput(iso?: string): string {
  if (!iso) return ''
  const d = new Date(iso)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`
}

function fromEvent(event: OrganizerEvent): Form {
  return {
    title: event.title ?? '', startsAt: toLocalInput(event.startsAt), venue: event.venue ?? '',
    shortDescription: event.shortDescription ?? '', description: event.description ?? '',
    price: event.price === undefined || event.price === null ? '' : String(event.price),
    capacity: String(event.capacity ?? ''), audienceFit: (event.audienceFit ?? []).join('\n'),
    audienceNotFit: (event.audienceNotFit ?? []).join('\n'),
    latitude: event.latitude === undefined || event.latitude === null ? '' : String(event.latitude),
    longitude: event.longitude === undefined || event.longitude === null ? '' : String(event.longitude),
    waitlistEnabled: event.waitlistEnabled ?? true,
  }
}

const lines = (text: string) => text.split('\n').map((line) => line.trim()).filter(Boolean)
const optionalNumber = (min: number, max: number, message: string) =>
  z.string().trim().refine((v) => v === '' || (!Number.isNaN(Number(v)) && Number(v) >= min && Number(v) <= max), message)

// the limits of OrganizerEventUpdateRequest on the server
const schema = z.object({
  title: z.string().trim().min(1, 'Введите название').max(200, 'Не длиннее 200 символов'),
  startsAt: z.string().min(1, 'Укажите дату и время'),
  venue: z.string().trim().max(200, 'Не длиннее 200 символов'),
  shortDescription: z.string().trim().max(160, 'Не длиннее 160 символов'),
  description: z.string().trim().max(2000, 'Не длиннее 2000 символов'),
  price: z.string().trim().refine((v) => v === '' || /^\d{1,8}([.,]\d{1,2})?$/.test(v), 'Цена: число, до двух знаков после запятой'),
  capacity: z.string().trim().refine((v) => /^\d+$/.test(v) && Number(v) > 0, 'Целое число больше нуля'),
  audienceFit: z.string().refine((v) => lines(v).length <= 4 && lines(v).every((l) => l.length <= 100), 'До 4 строк по 100 символов'),
  audienceNotFit: z.string().refine((v) => lines(v).length <= 3 && lines(v).every((l) => l.length <= 100), 'До 3 строк по 100 символов'),
  latitude: optionalNumber(-90, 90, 'От −90 до 90'),
  longitude: optionalNumber(-180, 180, 'От −180 до 180'),
  waitlistEnabled: z.boolean(),
}).refine((f) => (f.latitude.trim() === '') === (f.longitude.trim() === ''), { path: ['longitude'], message: 'Нужны обе координаты' })

function toUpdate(form: Form, version: number) {
  return {
    version,
    title: form.title.trim(),
    startsAt: new Date(form.startsAt).toISOString(),
    venue: form.venue.trim(),
    shortDescription: form.shortDescription.trim(),
    description: form.description.trim(),
    price: form.price.trim() === '' ? undefined : Number(form.price.replace(',', '.')),
    capacity: Number(form.capacity),
    audienceFit: lines(form.audienceFit),
    audienceNotFit: lines(form.audienceNotFit),
    latitude: form.latitude.trim() === '' ? undefined : Number(form.latitude),
    longitude: form.longitude.trim() === '' ? undefined : Number(form.longitude),
    waitlistEnabled: form.waitlistEnabled,
  }
}

const timeFormat = new Intl.DateTimeFormat('ru-RU', { hour: '2-digit', minute: '2-digit' })

/**
 * The event editor (design/pages/U-editor). The draft saves itself 3 s after the last change with the version it was
 * loaded with; if someone saved in between (409), nothing is overwritten: the editor stops and offers to reload.
 */
export function EditorPage() {
  const id = Number(useParams().id)
  const loaded = useOrganizerEvent(id)
  const settings = useBookingSettings()
  const save = useSaveEvent(id)
  const publish = usePublish(id)
  const upload = useUploadPhotos(id)
  const toast = useToast()

  const [form, setForm] = useState<Form | null>(null)
  const [version, setVersion] = useState(0)
  const [dirty, setDirty] = useState(false)
  const [errors, setErrors] = useState<Errors>({})
  const [conflict, setConflict] = useState(false)
  const [savedAt, setSavedAt] = useState<Date | null>(null)
  const [missing, setMissing] = useState<{ field: string; message: string }[]>([])
  const [dragging, setDragging] = useState(false)
  const timer = useRef<ReturnType<typeof setTimeout> | undefined>(undefined)
  const fileInput = useRef<HTMLInputElement>(null)

  // the form starts from the server copy (and again after "Обновить данные")
  useEffect(() => {
    if (loaded.data && (form === null || (!dirty && loaded.data.version !== version))) {
      setForm(fromEvent(loaded.data))
      setVersion(loaded.data.version ?? 0)
    }
  }, [loaded.data, form, dirty, version])

  const persist = useCallback(async (current: Form, onDone?: () => void) => {
    const parsed = schema.safeParse(current)
    if (!parsed.success) {
      const found: Errors = {}
      for (const issue of parsed.error.issues) found[issue.path[0] as Field] ??= issue.message
      setErrors(found)
      return false
    }
    setErrors({})
    try {
      const saved = await save.mutateAsync(toUpdate(current, version))
      setVersion(saved.version ?? version + 1)
      setDirty(false)
      setSavedAt(new Date())
      onDone?.()
      return true
    } catch (error) {
      if (error instanceof ApiError && error.status === 409) setConflict(true)
      else if (error instanceof ApiError && error.problem?.errors?.length) {
        const found: Errors = {}
        for (const violation of error.problem.errors) found[violation.field as Field] ??= violation.message
        setErrors(found)
      } else toast.show({ message: 'Не удалось сохранить черновик. Попробуйте ещё раз.' })
      return false
    }
  }, [save, version, toast])

  // autosave: 3 s of quiet after the last change; never while a conflict is open
  useEffect(() => {
    clearTimeout(timer.current)
    if (!dirty || conflict || !form) return
    timer.current = setTimeout(() => void persist(form), AUTOSAVE_MS)
    return () => clearTimeout(timer.current)
  }, [form, dirty, conflict, persist])

  if (loaded.isPending || !form) return <p className={styles.wait}>Загружаем событие…</p>
  if (loaded.isError) return <p className={styles.wait} role="alert">Не удалось загрузить событие.</p>

  const event = loaded.data

  function set<K extends Field>(key: K, value: Form[K]) {
    setForm((current) => (current ? { ...current, [key]: value } : current))
    setDirty(true)
    setMissing([])
  }

  async function reload() {
    setConflict(false)
    setDirty(false)
    const fresh = await loaded.refetch()
    if (fresh.data) {
      setForm(fromEvent(fresh.data))
      setVersion(fresh.data.version ?? 0)
    }
  }

  async function doPublish() {
    clearTimeout(timer.current)
    if (dirty && !(await persist(form!))) return
    publish.mutate(undefined, {
      onSuccess: () => {
        setMissing([])
        toast.show({ message: 'Событие опубликовано' })
      },
      onError: (error) => {
        if (error instanceof ApiError && error.status === 422) setMissing((error.problem?.errors ?? []).map((item) => ({ field: item.field ?? '', message: item.message ?? '' })))
        else toast.show({ message: 'Не удалось опубликовать. Попробуйте ещё раз.' })
      },
    })
  }

  function sendFiles(files: FileList | File[]) {
    const list = Array.from(files)
    if (list.length === 0) return
    upload.mutate(list, {
      onSuccess: () => setErrors((current) => ({ ...current, photos: undefined })),
      onError: (error) => setErrors((current) => ({
        ...current,
        photos: error instanceof ApiError
          ? error.status === 415 ? 'Только JPG, PNG или WebP.' : error.status === 413 ? 'Файл больше 8 МБ.'
            : error.status === 409 ? 'Не больше 6 фото.' : error.problem?.detail ?? 'Не удалось загрузить фото.'
          : 'Не удалось загрузить фото.',
      })),
    })
  }

  function onDrop(drop: DragEvent) {
    drop.preventDefault()
    setDragging(false)
    sendFiles(drop.dataTransfer.files)
  }

  const missingFields = new Set(missing.map((item) => item.field))
  const invalid = (field: Field) => Boolean(errors[field]) || missingFields.has(field)
  const media = event.media ?? []
  const statusText = event.status === 'DRAFT' ? 'Черновик' : event.status === 'SOLD_OUT' ? 'Распродано'
    : event.status === 'CANCELLED' ? 'Отменено' : 'Опубликовано'
  const saveState = save.isPending ? 'Сохраняем…' : conflict ? 'Не сохранено' : dirty ? 'Есть несохранённые изменения'
    : savedAt ? `Черновик сохранён в ${timeFormat.format(savedAt)}` : ''

  const field = (name: Field, label: string, input: React.ReactNode, hint?: string) => (
    <div>
      <label className={styles.label} htmlFor={`f-${name}`}>{label}</label>
      {input}
      {hint && !errors[name] && <span className={styles.hint}>{hint}</span>}
      {errors[name] && <span className={styles.error}>{errors[name]}</span>}
    </div>
  )

  return (
    <>
      <div className={styles.headButtons} style={{ justifyContent: 'flex-end' }}>
        <span className={styles.note} aria-live="polite">{saveState}</span>
        <button type="button" className={styles.outline} disabled={save.isPending || conflict}
          onClick={() => { clearTimeout(timer.current); void persist(form) }}>
          Сохранить черновик
        </button>
        <button type="button" className={styles.primary} disabled={publish.isPending || conflict || event.status !== 'DRAFT'}
          onClick={() => void doPublish()}>
          {event.status === 'DRAFT' ? 'Опубликовать' : 'Опубликовано'}
        </button>
      </div>

      {conflict && (
        <div className={styles.conflict} role="alert">
          <span>Событие изменили в другом окне или вкладке. Ваши последние правки не сохранены, чтобы не затереть чужие.</span>
          <button type="button" className={styles.outline} onClick={() => void reload()}>Обновить данные</button>
        </div>
      )}

      {missing.length > 0 && (
        <div className={styles.card} role="alert">
          <b>Чтобы опубликовать, заполните:</b>
          <ul className={styles.missing}>
            {missing.map((item) => <li key={item.field}>{LABELS[item.field] ?? item.field}: {item.message}</li>)}
          </ul>
        </div>
      )}

      <div className={styles.editor}>
        <section className={`${styles.card} ${styles.form}`} aria-label="Основное">
          <h1 className={styles.sectionTitle}>Основное</h1>
          {field('title', 'Название события', <input id="f-title" className={styles.input} value={form.title} aria-invalid={invalid('title')} onChange={(e) => set('title', e.target.value)} />)}
          <div className={styles.grid2}>
            {field('startsAt', 'Дата и время', <input id="f-startsAt" type="datetime-local" className={styles.input} value={form.startsAt} aria-invalid={invalid('startsAt')} onChange={(e) => set('startsAt', e.target.value)} />)}
            {field('venue', 'Место', <input id="f-venue" className={styles.input} value={form.venue} aria-invalid={invalid('venue')} onChange={(e) => set('venue', e.target.value)} />, 'Например: Grand Cafe, Sopot')}
          </div>
          {field('shortDescription', 'Короткое описание (видно при наведении)', <textarea id="f-shortDescription" rows={2} className={styles.input} value={form.shortDescription} onChange={(e) => set('shortDescription', e.target.value)} />, `${form.shortDescription.length} / 160`)}
          {field('description', 'Подробное описание', <textarea id="f-description" rows={4} className={styles.input} value={form.description} onChange={(e) => set('description', e.target.value)} />)}
          <div className={styles.grid3}>
            {field('price', 'Цена, PLN', <input id="f-price" inputMode="decimal" className={styles.input} value={form.price} aria-invalid={invalid('price')} onChange={(e) => set('price', e.target.value)} />, '0 — бесплатно')}
            {field('capacity', 'Всего мест', <input id="f-capacity" inputMode="numeric" className={styles.input} value={form.capacity} aria-invalid={invalid('capacity')} onChange={(e) => set('capacity', e.target.value)} />)}
            <div>
              <label className={styles.label} htmlFor="f-limit">Лимит на бронь</label>
              <input id="f-limit" className={styles.input} value={settings.data?.maxSeatsPerBooking ?? ''} readOnly />
              <span className={styles.hint}>Общий для всех событий</span>
            </div>
          </div>
          <div className={styles.grid2}>
            {field('audienceFit', 'Для кого подойдёт', <textarea id="f-audienceFit" rows={2} className={styles.input} value={form.audienceFit} onChange={(e) => set('audienceFit', e.target.value)} />, 'По одному пункту на строку, до 4')}
            {field('audienceNotFit', 'Не подойдёт', <textarea id="f-audienceNotFit" rows={2} className={styles.input} value={form.audienceNotFit} onChange={(e) => set('audienceNotFit', e.target.value)} />, 'По одному пункту на строку, до 3')}
          </div>
          <div className={styles.grid2}>
            {field('latitude', 'Широта (для карты)', <input id="f-latitude" inputMode="decimal" className={styles.input} value={form.latitude} onChange={(e) => set('latitude', e.target.value)} />, 'Например 54.4418')}
            {field('longitude', 'Долгота (для карты)', <input id="f-longitude" inputMode="decimal" className={styles.input} value={form.longitude} onChange={(e) => set('longitude', e.target.value)} />, 'Например 18.5601')}
          </div>
          <div>
            <span className={styles.label}>Фото события</span>
            <div className={`${styles.photos} ${dragging ? styles.dropActive : ''}`}
              onDragOver={(e) => { e.preventDefault(); setDragging(true) }} onDragLeave={() => setDragging(false)} onDrop={onDrop}
              data-testid="photo-drop">
              {media.map((item, index) => (
                <div key={item.url} className={styles.thumb}>
                  <img src={item.url} alt={item.alt ?? ''} />
                  {index === 0 && <span>Главное фото</span>}
                </div>
              ))}
              <button type="button" className={styles.drop} onClick={() => fileInput.current?.click()} disabled={upload.isPending}>
                {upload.isPending ? 'Загружаем…' : '+ Добавить фото'}
              </button>
              <span className={styles.note}>До 6 фото, первое — обложка. Можно перетащить сюда.</span>
              <input ref={fileInput} type="file" accept="image/jpeg,image/png,image/webp" multiple hidden
                aria-label="Выбрать фото" onChange={(e) => { if (e.target.files) sendFiles(e.target.files); e.target.value = '' }} />
            </div>
            {(errors.photos || missingFields.has('media')) && <span className={styles.error}>{errors.photos ?? 'Добавьте хотя бы одно фото'}</span>}
          </div>
          <div className={styles.waitlist}>
            <button type="button" role="switch" className={styles.switch} aria-checked={form.waitlistEnabled}
              aria-label="Разрешить лист ожидания, если мест нет" onClick={() => set('waitlistEnabled', !form.waitlistEnabled)} />
            Разрешить лист ожидания, если мест нет
          </div>
        </section>

        <aside className={styles.side}>
          <div className={`${styles.card} ${styles.sideCard}`} aria-label="Предпросмотр">
            <h2 className={styles.sideTitle}>Предпросмотр</h2>
            <div className={styles.previewPhoto}>
              {media[0] && <img src={media[0].url} alt="" />}
              {form.price.trim() !== '' && <span className={styles.previewChip}>{Number(form.price.replace(',', '.')) === 0 ? 'Бесплатно' : `${form.price} PLN`}</span>}
            </div>
            <span className={styles.previewTitle}>{form.title || 'Без названия'}</span>
            <span className={styles.previewMeta}>{[form.startsAt ? when(new Date(form.startsAt).toISOString()) : '', form.venue].filter(Boolean).join(' · ')}</span>
            {form.shortDescription && <span className={styles.previewText}>{form.shortDescription}</span>}
            {lines(form.audienceFit).length > 0 && <span className={styles.previewFit}>Для кого подойдёт: {lines(form.audienceFit).join(', ')}</span>}
          </div>
          <div className={`${styles.card} ${styles.sideCard}`}>
            <h2 className={styles.sideTitle}>Публикация</h2>
            <div className={styles.line}><span>Статус</span><span className={`${styles.status} ${event.status === 'DRAFT' ? styles.draft : styles.published}`}>{statusText}</span></div>
            <div className={styles.line}><span>Видно в каталоге</span><span>{event.status === 'DRAFT' ? 'После публикации' : 'Да'}</span></div>
            <span className={styles.note}>Для публикации нужны название, будущая дата, место, цена и хотя бы одно фото. Незаполненные поля подсветятся.</span>
          </div>
          <div className={`${styles.card} ${styles.sideCard}`}>
            <h2 className={styles.sideTitle}>Брони</h2>
            <div className={styles.line}>
              <span>{event.status === 'DRAFT' ? 'Нет броней — событие не опубликовано' : `Забронировано ${event.bookedCount} из ${event.capacity}`}</span>
            </div>
            <Link to={`/organizer/events/${id}/bookings`} className={styles.outline}>Открыть список броней</Link>
          </div>
        </aside>
      </div>
    </>
  )
}
