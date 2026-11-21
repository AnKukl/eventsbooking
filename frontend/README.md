# Frontend

Vite + React + TypeScript, react-router, TanStack Query, zod. Тесты — Vitest и Testing Library.

## Команды

| Задача | Команда |
|---|---|
| Установить зависимости | `npm install` |
| Dev-сервер (http://localhost:5173) | `npm run dev` |
| Тесты | `npm test` |
| Проверка типов | `npm run typecheck` |
| Сборка | `npm run build` |
| Типы API из OpenAPI | `npm run api:types` (бэкенд должен работать на `localhost:8080`) |

В dev всё под `/api` проксируется на бэкенд (`BACKEND_URL`, по умолчанию `http://localhost:8080`), поэтому страница и
API живут на одном origin и CORS не участвует.

## Устройство

- `src/styles/tokens.css` — **единственное место с цветами** (а также шрифты, радиусы, отступы). Остальной CSS и SVG
  используют только переменные.
- `src/api/schema.d.ts` — сгенерирован `openapi-typescript`, руками не править; после изменения API — `npm run api:types`.
- `src/api/client.ts` — `fetch` к `/api`; ошибка API превращается в `ApiError` с `ProblemDetail` (RFC 9457).
- `src/components/Logo.tsx` — логотип 40×40 (цвета из токенов).
- `src/app/router.tsx` — маршруты: `/`, `/organizer`, остальное — 404.

## Макеты

Визуальный эталон — HTML-макеты в `design/` (карта страниц — `design/README.md`, токены — `design/tokens.css`, логотип —
`design/logo.svg`). Пока папки нет, токены и логотип сделаны по описанию этапа 16; когда макеты появятся, их сверяют
и переносят значения в `tokens.css` и `Logo.tsx`.

## Windows

`npm test` запускает Vitest через `scripts/vitest.mjs`: если терминал открыт как `c:\...` (так делает VS Code), Vitest
загружает свой модуль дважды (`c:` и `C:` для Node разные пути) и все тесты падают с `reading 'config'`. Скрипт
поднимает букву диска. При прямом вызове `npx vitest` запускайте из пути с `C:\`.
