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
| Типы API из OpenAPI | `npm run api:types` (из снимка `../docs/openapi.json`, бэкенд не нужен) |
| Типы API с живого бэкенда | `npm run api:types:live` (`localhost:8080`) |

В dev всё под `/api` проксируется на бэкенд (`BACKEND_URL`, по умолчанию `http://localhost:8080`), поэтому страница и
API живут на одном origin и CORS не участвует.

## Устройство

- `src/styles/tokens.css` — **единственное место с цветами**: дословная копия `design/tokens.css` плюс в конце значения,
  которые есть в макетах `design/pages`, но нет в `design/tokens.css`. Остальной CSS и SVG используют только переменные.
  `tokens.test.ts` проверяет и то, и другое.
- `src/api/schema.d.ts` — сгенерирован `openapi-typescript` из `docs/openapi.json` (снимок контракта, его проверяет
  `OpenApiSnapshotTest` в бэкенде), руками не править; после изменения API — `npm run api:types`.
- `src/api/client.ts` — `fetch` к `/api`; ошибка API превращается в `ApiError` с `ProblemDetail` (RFC 9457).
  Сессия — HttpOnly cookie `access_token` и `refresh_token`, браузер шлёт их сам, страница токен не видит
  (решение 017 в `docs/decisions.md`).
- `src/components/Logo.tsx` — знак из `design/logo.svg` без изменений; `Logo.test.tsx` сверяет каждую фигуру и цвет с файлом.
- `src/components/Header.tsx` — шапка главной `D-bento`, состояния кнопок из `E-buttons`.
- `src/app/routes.tsx` — карта страниц из `design/README.md`; пока экран не сверстан, страница называет свой макет.

## Макеты

Эталон — `../design` (правила и карта страниц — `design/README.md`). Файлы `design/pages/*.dc.html` работают только в
канвасе: из них берутся разметка, тексты и значения, экраны переписываются на React. Новые цвета, тени, скругления и
шрифты, которых нет в макетах, не добавлять.

## Windows

`npm test` запускает Vitest через `scripts/vitest.mjs`: если терминал открыт как `c:\...` (так делает VS Code), Vitest
загружает свой модуль дважды (`c:` и `C:` для Node разные пути) и все тесты падают с `reading 'config'`. Скрипт
поднимает букву диска. При прямом вызове `npx vitest` запускайте из пути с `C:\`.
