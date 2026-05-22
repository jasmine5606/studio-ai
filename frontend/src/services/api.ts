import type { ChatResponse, KbFile, LiteratureDoc, ExperimentRecord, SearchHit, EvalResult, AbTestResult, ChatMode } from '../types'

const BASE = '/api'

async function request<T>(url: string, options?: RequestInit): Promise<T> {
  const token = localStorage.getItem('token')
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
    ...(options?.headers as Record<string, string> || {}),
  }

  const res = await fetch(`${BASE}${url}`, { ...options, headers })
  if (!res.ok) {
    const err = await res.json().catch(() => ({ message: res.statusText }))
    throw new Error(err.message || err.error || `HTTP ${res.status}`)
  }
  return res.json()
}

export const api = {
  auth: {
    login: (username: string, password: string) =>
      request<{ token: string; username: string; role: string }>('/auth/login', {
        method: 'POST',
        body: JSON.stringify({ username, password }),
      }),
    logout: () => request<void>('/auth/logout', { method: 'POST' }),
    me: () => request<{ username: string; role: string }>('/auth/me'),
    status: () => request<{ enabled: boolean }>('/auth/status'),
  },

  chat: {
    ask: (question: string) =>
      request<ChatResponse>('/ai/chat', {
        method: 'POST',
        body: JSON.stringify({ question }),
      }),
    send: (sessionId: string | null, question: string, mode: ChatMode = 'auto') =>
      request<ChatResponse>('/ai/chat', {
        method: 'POST',
        body: JSON.stringify({ sessionId, question, mode }),
      }),
    sessions: () => request<string[]>('/ai/sessions'),
    messages: (sessionId: string) =>
      request<Array<{ role: string; content: string; timestamp: number }>>(`/ai/sessions/${sessionId}/messages`),
    hydeRetrieve: (question: string) =>
      request<Array<{ score: number; text: string }>>('/ai/retrieve/hyde', {
        method: 'POST',
        body: JSON.stringify({ question, maxResults: 5 }),
      }),
    cacheLookup: (question: string) =>
      request<{ hit: boolean; similarQuestion?: string; answer?: string }>('/ai/cache/lookup', {
        method: 'POST',
        body: JSON.stringify({ question }),
      }),
  },

  kb: {
    upload: (file: File) => {
      const formData = new FormData()
      formData.append('file', file)
      const token = localStorage.getItem('token')
      return fetch(`${BASE}/kb/files`, {
        method: 'POST',
        headers: token ? { Authorization: `Bearer ${token}` } : {},
        body: formData,
      }).then(r => r.json())
    },
    list: () => request<KbFile[]>('/kb/files'),
    search: (q: string, mode = 'hybrid') =>
      request<SearchHit[]>(`/kb/search?q=${encodeURIComponent(q)}&mode=${mode}`),
    delete: (id: string) => request<void>(`/kb/files/${id}`, { method: 'DELETE' }),
  },

  literature: {
    list: () => request<LiteratureDoc[]>('/literature/docs'),
    search: (q: string) => request<SearchHit[]>(`/literature/search?q=${encodeURIComponent(q)}`),
    analyze: (literatureId: string, projectContext?: string) =>
      request<{ result: string }>('/literature/analyze', {
        method: 'POST',
        body: JSON.stringify({ literatureId, projectContext }),
      }),
  },

  lab: {
    projects: () => request<Array<{ projectId: string; projectName: string }>>('/lab/projects'),
    experiments: (projectId: string) =>
      request<ExperimentRecord[]>(`/lab/projects/${projectId}/experiments`),
  },

  evaluation: {
    faithfulness: (answer: string, context: string) =>
      request<EvalResult>('/evaluation/faithfulness', {
        method: 'POST',
        body: JSON.stringify({ answer, context }),
      }),
    relevance: (query: string, context: string) =>
      request<EvalResult>('/evaluation/relevance', {
        method: 'POST',
        body: JSON.stringify({ query, context }),
      }),
    abTest: (input: string, outputA: string, outputB: string) =>
      request<AbTestResult>('/evaluation/ab-test', {
        method: 'POST',
        body: JSON.stringify({ input, outputA, outputB }),
      }),
  },

  admin: {
    dashboard: () => request<Record<string, unknown>>('/admin/dashboard'),
    stats: () => request<Record<string, unknown>>('/admin/stats'),
  },
}
