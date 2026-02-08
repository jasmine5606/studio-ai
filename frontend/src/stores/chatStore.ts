import { create } from 'zustand'
import type { ChatMessage, ChatSession, ChatMode, TabName } from '../types'
import { api } from '../services/api'

interface ChatStore {
  messages: ChatMessage[]
  sessions: ChatSession[]
  currentSessionId: string | null
  isLoading: boolean
  mode: ChatMode
  activeTab: TabName
  error: string | null

  setMode: (mode: ChatMode) => void
  setActiveTab: (tab: TabName) => void
  addMessage: (role: 'user' | 'assistant', content: string) => void
  sendMessage: (question: string) => Promise<void>
  loadSessions: () => Promise<void>
  loadMessages: (sessionId: string) => Promise<void>
  newSession: () => void
  setError: (error: string | null) => void
}

export const useChatStore = create<ChatStore>((set, get) => ({
  messages: [],
  sessions: [],
  currentSessionId: null,
  isLoading: false,
  mode: 'auto',
  activeTab: 'chat',
  error: null,

  setMode: (mode) => set({ mode }),
  setActiveTab: (tab) => set({ activeTab: tab }),
  setError: (error) => set({ error }),

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
    const { currentSessionId, mode, addMessage } = get()
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

  loadSessions: async () => {
    try {
      const sessions = await api.chat.sessions()
      set({ sessions: sessions.map((s: string) => ({ id: s, title: s, lastActiveAt: Date.now() })) })
    } catch {
      // silently fail
    }
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
    } catch {
      // silently fail
    }
  },

  newSession: () => set({ currentSessionId: null, messages: [] }),
}))
