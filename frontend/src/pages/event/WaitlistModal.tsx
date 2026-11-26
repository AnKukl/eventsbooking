import { useState } from 'react'
import { ApiError } from '../../api/client'
import type { EventItem } from '../../api/events'
import { type WaitlistChannel, useJoinWaitlist } from '../../api/booking'
import { Modal, modalStyles } from '../../components/modal/Modal'
import { useToast } from '../../components/toast/Toast'

const CHANNELS: { value: WaitlistChannel; label: string }[] = [
  { value: 'EMAIL', label: 'Письмо' },
  { value: 'PUSH', label: 'Push' },
]

/** "Мест нет" of design/pages/P-modals: choose a channel, join the waitlist (POST /api/events/{id}/waitlist). */
export function WaitlistModal({ event, onClose }: { event: EventItem; onClose: () => void }) {
  const [channel, setChannel] = useState<WaitlistChannel>('EMAIL')
  const join = useJoinWaitlist()
  const toast = useToast()

  function submit() {
    join.mutate(
      { eventId: event.id!, channel },
      {
        onSuccess: () => {
          toast.show({ message: 'Сообщим, когда появятся места' })
          onClose()
        },
      },
    )
  }

  const error = join.error instanceof ApiError ? waitlistError(join.error) : join.error ? 'Нет связи с сервером. Попробуйте ещё раз.' : null

  return (
    <Modal title="Сообщим, когда появятся места" label="Мест нет" narrow onClose={onClose}>
      <span className={modalStyles.text}>
        {event.title}. Пришлём {channel === 'EMAIL' ? 'письмо' : 'уведомление'}, как только освободится место.
      </span>
      <div className={modalStyles.chips} role="group" aria-label="Как сообщить">
        {CHANNELS.map((item) => (
          <button
            key={item.value}
            type="button"
            className={modalStyles.chip}
            aria-pressed={channel === item.value}
            onClick={() => setChannel(item.value)}
          >
            {item.label}
          </button>
        ))}
      </div>
      {error && <span className={modalStyles.error} role="alert">{error}</span>}
      <div className={modalStyles.actions}>
        <button type="button" className={modalStyles.secondary} onClick={onClose}>
          Не нужно
        </button>
        <button type="button" className={modalStyles.primary} onClick={submit} disabled={join.isPending}>
          Сообщить
        </button>
      </div>
    </Modal>
  )
}

function waitlistError(error: ApiError): string {
  switch (error.problem?.code) {
    case 'EVENT_HAS_FREE_SEATS': return 'Места уже появились — их можно забронировать.'
    case 'WAITLIST_DISABLED': return 'Организатор отключил лист ожидания для этого события.'
    case 'ALREADY_BOOKED': return 'У вас уже есть бронь на это событие.'
    case 'EVENT_ALREADY_STARTED': return 'Событие уже началось.'
    default: return error.status === 401 ? 'Войдите, чтобы встать в очередь.' : 'Не получилось. Попробуйте ещё раз.'
  }
}
