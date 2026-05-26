import { create } from 'zustand'
import type { ChatMessage, ChatSession, ChatMode, TabName, AgentStep } from '../types'
import { api } from '../services/api'

const STEP_ICONS: Record<string, string> = {
  memory: '🧠',
  retrieval: '🔍',
  llm: '🤖',
  tool: '🛠',
}

const STEP_LABELS: Record<string, string> = {
  memory: '加载记忆',
  retrieval: '检索知识库',
  llm: '生成回答',
  tool: '调用工具',
}

interface ChatStore {
  messages: ChatMessage[]
  sessions: ChatSession[]
  currentSessionId: string | null
  isLoading: boolean
  mode: ChatMode
  activeTab: TabName
  error: string | null
  steps: AgentStep[]
  useStream: boolean

  setMode: (mode: ChatMode) => void
  setActiveTab: (tab: TabName) => void
  addMessage: (role: 'user' | 'assistant', content: string) => void
  sendMessage: (question: string) => Promise<void>
  sendMessageStream: (question: string) => Promise<void>
  loadSessions: () => Promise<void>
  loadMessages: (sessionId: string) => Promise<void>
  newSession: () => void
  setError: (error: string | null) => void
  setSteps: (steps: AgentStep[]) => void
  addStep: (step: AgentStep) => void
  clearSteps: () => void
}

export const useChatStore = create<ChatStore>((set, get) => ({
  messages: [],
  sessions: [],
  currentSessionId: null,
  isLoading: false,
  mode: 'auto',
  activeTab: 'chat',
  error: null,
  steps: [],
  useStream: true,

  setMode: (mode) => set({ mode }),
  setActiveTab: (tab) => set({ activeTab: tab }),
  setError: (error) => set({ error }),
  setSteps: (steps) => set({ steps }),
  clearSteps: () => set({ steps: [] }),

  addStep: (step) =>
    set((state) => {
      const existing = state.steps.findIndex((s) => s.step === step.step)
      if (existing >= 0) {
        const updated = [...state.steps]
        updated[existing] = step
        return { steps: updated }
      }
      return { steps: [...state.steps, step] }
    }),

  addMessage: (role, content) =>
    set((state) => ({
      messages: [
        ...state.messages,
        {
          id: crypto.randomUUID(),
          role,
          content,
          timestamp: Date.now(),
        },
      ],
    })),

  sendMessage: async (question) => {
    const { currentSessionId, mode, addMessage, useStream } = get()
    if (useStream) {
      return get().sendMessageStream(question)
    }
    set({ isLoading: true, error: null })
    addMessage('user', question)
    try {
      const response = await api.chat.send(currentSessionId, question, mode)
      addMessage('assistant', response.answer)
      if (!currentSessionId && response.sessionId) {
        set({ currentSessionId: response.sessionId })
        get().loadSessions()
      }
    } catch (err) {
      set({ error: err instanceof Error ? err.message : 'Unknown error' })
      addMessage('assistant', `Error: ${err instanceof Error ? err.message : 'Unknown error'}`)
    } finally {
      set({ isLoading: false })
    }
  },

  sendMessageStream: async (question) => {
    const { currentSessionId, mode, addMessage, clearSteps, addStep } = get()
    set({ isLoading: true, error: null, steps: [] })
    addMessage('user', question)
    clearSteps()

    const token = localStorage.getItem('token')
    try {
      const res = await fetch('/api/ai/chat/stream', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          ...(token ? { Authorization: `Bearer ${token}` } : {}),
        },
        body: JSON.stringify({ question, sessionId: currentSessionId, mode }),
      })

      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      const reader = res.body?.getReader()
      if (!reader) throw new Error('No response body')

      const decoder = new TextDecoder()
      let buffer = ''
      let eventName = ''

      while (true) {
        const { done, value } = await reader.read()
        if (done) break
        buffer += decoder.decode(value, { stream: true })

        const lines = buffer.split('\n')
        buffer = lines.pop() || ''

        for (const line of lines) {
          if (line.startsWith('event:')) {
            eventName = line.slice(6).trim()
            continue
          }
          if (line.startsWith('data:')) {
            try {
              const data = JSON.parse(line.slice(5).trim())

              if (eventName === 'session' && data.sessionId && !currentSessionId) {
                set({ currentSessionId: data.sessionId })
                get().loadSessions()
              } else if (eventName === 'step') {
                addStep({
                  step: data.step,
                  status: data.status,
                  detail: data.detail || '',
                  timestamp: Date.now(),
                })
              } else if (eventName === 'answer') {
                addMessage('assistant', data)
              }
              eventName = ''
            } catch {
              if (eventName === 'answer') {
                addMessage('assistant', line.slice(5).trim())
              }
              eventName = ''
            }
          }
        }
      }
    } catch (err) {
      set({ error: err instanceof Error ? err.message : 'Unknown error' })
      addMessage('assistant', `Error: ${err instanceof Error ? err.message : 'Unknown error'}`)
    } finally {
      set({ isLoading: false })
    }
  },

  loadSessions: async () => {
    try {
      const sessions = await api.chat.sessions()
      set({ sessions: sessions.map((s: string) => ({ id: s, title: s, lastActiveAt: Date.now() })) })
    } catch {}
  },

  loadMessages: async (sessionId) => {
    set({ currentSessionId: sessionId, messages: [] })
    try {
      const msgs = await api.chat.messages(sessionId)
      set({
        messages: msgs.map((m: { role: string; content: string; timestamp: number }) => ({
          id: crypto.randomUUID(),
          role: m.role as 'user' | 'assistant',
          content: m.content,
          timestamp: m.timestamp,
        })),
      })
    } catch {}
  },

  newSession: () => set({ currentSessionId: null, messages: [], steps: [] }),
}))
