type SSECallback = (event: string, data: unknown) => void

export function streamChat(
  question: string,
  sessionId: string | null,
  mode: string,
  onEvent: SSECallback,
  onError: (err: Error) => void,
  onComplete: () => void
): AbortController {
  const controller = new AbortController()
  const token = localStorage.getItem('token')

  fetch('/api/ai/chat/stream', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: JSON.stringify({ question, sessionId, mode }),
    signal: controller.signal,
  })
    .then(async (response) => {
      if (!response.ok) {
        const err = await response.json().catch(() => ({ message: response.statusText }))
        throw new Error(err.message || `HTTP ${response.status}`)
      }
      const reader = response.body?.getReader()
      if (!reader) throw new Error('No response body')

      const decoder = new TextDecoder()
      let buffer = ''

      while (true) {
        const { done, value } = await reader.read()
        if (done) {
          onComplete()
          break
        }

        buffer += decoder.decode(value, { stream: true })
        const lines = buffer.split('\n')
        buffer = lines.pop() || ''

        for (const line of lines) {
          if (line.startsWith('event:')) {
            // event line, next data line will have the event name
            continue
          }
          if (line.startsWith('data:')) {
            try {
              const data = JSON.parse(line.slice(5).trim())
              const eventName = data.sessionId ? 'session' :
                data.message ? 'error' : 'answer'
              onEvent(eventName, data)
            } catch {
              // non-JSON data (like plain text answer)
              const rawData = line.slice(5).trim()
              onEvent('token', rawData)
            }
          }
        }
      }
    })
    .catch((err) => {
      if (err.name !== 'AbortError') {
        onError(err)
      }
    })

  return controller
}
