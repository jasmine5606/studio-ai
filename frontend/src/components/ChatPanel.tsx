import { useState, useRef, useEffect } from 'react'
import { Send, Loader2 } from 'lucide-react'
import { useChatStore } from '../stores/chatStore'
import ReactMarkdown from 'react-markdown'
import { Prism as SyntaxHighlighter } from 'react-syntax-highlighter'
import { oneDark } from 'react-syntax-highlighter/dist/esm/styles/prism'

export default function ChatPanel() {
  const [input, setInput] = useState('')
  const messagesEndRef = useRef<HTMLDivElement>(null)
  const { messages, isLoading, mode, setMode, sendMessage, error } = useChatStore()

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages])

  const handleSend = async () => {
    if (!input.trim() || isLoading) return
    const question = input.trim()
    setInput('')
    await sendMessage(question)
  }

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      handleSend()
    }
  }

  return (
    <div className="flex flex-col h-full">
      <div className="flex items-center gap-3 px-4 py-3 border-b border-white/5">
        <div className="flex items-center gap-1 glass rounded-lg p-0.5">
          {(['auto', 'local', 'external'] as const).map((m) => (
            <button
              key={m}
              onClick={() => setMode(m)}
              className={`px-3 py-1 text-xs rounded-md transition-all ${
                mode === m ? 'bg-blue-500/30 text-blue-200' : 'text-white/40 hover:text-white/70'
              }`}
            >
              {m === 'auto' ? '自动' : m === 'local' ? '本地' : '外部'}
            </button>
          ))}
        </div>
        {error && (
          <span className="text-red-400 text-xs ml-auto">{error}</span>
        )}
      </div>

      <div className="flex-1 overflow-y-auto px-4 py-4 space-y-4">
        {messages.length === 0 && (
          <div className="flex items-center justify-center h-full text-white/30">
            <div className="text-center space-y-3">
              <div className="text-4xl">✨</div>
              <p className="text-lg">开始与 Studio AI 对话</p>
              <p className="text-sm">支持自动检索知识库、代码审查、文献分析</p>
            </div>
          </div>
        )}

        {messages.map((msg) => (
          <div
            key={msg.id}
            className={`flex ${msg.role === 'user' ? 'justify-end' : 'justify-start'} animate-slide-up`}
          >
            <div
              className={`max-w-[85%] px-4 py-3 ${
                msg.role === 'user' ? 'message-user' : 'message-assistant'
              }`}
            >
              {msg.role === 'user' ? (
                <p className="text-sm whitespace-pre-wrap">{msg.content}</p>
              ) : (
                <div className="text-sm prose prose-invert max-w-none prose-pre:bg-transparent prose-code:text-blue-200">
                  <ReactMarkdown
                    components={{
                      code({ className, children, ...props }) {
                        const match = /language-(\w+)/.exec(className || '')
                        const codeStr = String(children).replace(/\n$/, '')
                        return match ? (
                          <SyntaxHighlighter
                            style={oneDark}
                            language={match[1]}
                            PreTag="div"
                          >
                            {codeStr}
                          </SyntaxHighlighter>
                        ) : (
                          <code className={className} {...props}>
                            {children}
                          </code>
                        )
                      },
                    }}
                  >
                    {msg.content}
                  </ReactMarkdown>
                </div>
              )}
            </div>
          </div>
        ))}

        {isLoading && (
          <div className="flex justify-start animate-fade-in">
            <div className="message-assistant px-4 py-3 flex items-center gap-2">
              <Loader2 className="w-4 h-4 animate-spin text-blue-300" />
              <span className="text-sm text-white/50">思考中...</span>
            </div>
          </div>
        )}

        <div ref={messagesEndRef} />
      </div>

      <div className="px-4 py-3 border-t border-white/5">
        <div className="flex gap-3">
          <textarea
            value={input}
            onChange={(e) => setInput(e.target.value)}
            onKeyDown={handleKeyDown}
            placeholder="输入问题，按 Enter 发送，Shift+Enter 换行..."
            className="glass-input flex-1 resize-none min-h-[44px] max-h-[120px]"
            rows={1}
            disabled={isLoading}
          />
          <button
            onClick={handleSend}
            disabled={!input.trim() || isLoading}
            className="glass-button-primary self-end flex items-center gap-2"
          >
            {isLoading ? (
              <Loader2 className="w-4 h-4 animate-spin" />
            ) : (
              <Send className="w-4 h-4" />
            )}
          </button>
        </div>
      </div>
    </div>
  )
}
