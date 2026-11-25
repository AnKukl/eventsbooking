import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ApiError, api, json } from './client'
import type { components } from './schema'

export type Me = components['schemas']['ProfileResponse']
export type Credentials = { email: string; password: string }

const ME = ['me'] as const

/** The signed-in user, or null for a guest (401 after a failed refresh). */
export function useMe() {
  return useQuery({
    queryKey: ME,
    queryFn: async () => {
      try {
        return await api<Me>('/api/me')
      } catch (error) {
        if (error instanceof ApiError && error.status === 401) return null
        throw error
      }
    },
    staleTime: 60_000,
  })
}

/** The letter for the header avatar: the first letter of the name, else of the email. */
export function initialOf(me: Me | null | undefined): string | undefined {
  return (me?.name?.trim() || me?.email)?.charAt(0).toUpperCase() || undefined
}

export function useLogin() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (credentials: Credentials) =>
      api<{ expiresIn: number }>('/api/auth/login', { method: 'POST', ...json(credentials) }),
    // everything cached so far belongs to the guest (isFavorite etc.)
    onSuccess: () => client.invalidateQueries(),
  })
}

/** Registration, then the same login, so the new user is signed in right away. */
export function useRegister() {
  const login = useLogin()
  return useMutation({
    mutationFn: async (credentials: Credentials) => {
      await api('/api/auth/register', { method: 'POST', ...json(credentials) })
      return login.mutateAsync(credentials)
    },
  })
}

export function useLogout() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: () => api<void>('/api/auth/logout', { method: 'POST' }),
    onSettled: () => {
      client.clear()
      client.setQueryData(ME, null)
    },
  })
}
