import { useState } from 'react'
import { Code2, GitBranch, FileCode } from 'lucide-react'

export default function CodeReview() {
  const [code, setCode] = useState('')
  const [result, setResult] = useState('')
  const [loading, setLoading] = useState(false)

  const handleReview = async () => {
    if (!code.trim()) return
    setLoading(true)
    try {
      const token = localStorage.getItem('token')
      const res = await fetch('/api/code-review/review', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          ...(token ? { Authorization: `Bearer ${token}` } : {}),
        },
        body: JSON.stringify({ code }),
      })
      const data = await res.json()
      setResult(data.result || data.review || JSON.stringify(data, null, 2))
    } catch (err) {
      setResult(`Error: ${err instanceof Error ? err.message : 'Unknown error'}`)
    }
    setLoading(false)
  }

  const handleUnitTest = async () => {
    if (!code.trim()) return
    setLoading(true)
    try {
      const token = localStorage.getItem('token')
      const res = await fetch('/api/code-review/unit-test', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          ...(token ? { Authorization: `Bearer ${token}` } : {}),
        },
        body: JSON.stringify({ code }),
      })
      const data = await res.json()
      setResult(data.result || data.test || JSON.stringify(data, null, 2))
    } catch (err) {
      setResult(`Error: ${err instanceof Error ? err.message : 'Unknown error'}`)
    }
    setLoading(false)
  }

  return (
    <div className="p-6 space-y-6">
      <div className="flex items-center gap-3">
        <Code2 className="w-5 h-5 text-blue-300" />
        <h2 className="text-lg font-semibold">代码审查</h2>
      </div>

      <div className="grid grid-cols-2 gap-6 h-[calc(100vh-200px)]">
        <div className="space-y-3">
          <textarea
            value={code}
            onChange={(e) => setCode(e.target.value)}
            placeholder="粘贴代码到此处..."
            className="w-full h-[80%] glass-input font-mono text-sm resize-none"
          />
          <div className="flex gap-3">
            <button
              onClick={handleReview}
              disabled={loading || !code.trim()}
              className="glass-button-primary flex items-center gap-2"
            >
              <FileCode className="w-4 h-4" />
              {loading ? '审查中...' : '代码审查'}
            </button>
            <button
              onClick={handleUnitTest}
              disabled={loading || !code.trim()}
              className="glass-button flex items-center gap-2"
            >
              <GitBranch className="w-4 h-4" />
              {loading ? '生成中...' : '生成单测'}
            </button>
          </div>
        </div>

        <div className="glass p-4 overflow-y-auto">
          {result ? (
            <pre className="text-sm text-white/80 whitespace-pre-wrap font-mono">{result}</pre>
          ) : (
            <div className="flex items-center justify-center h-full text-white/30 text-sm">
              审查结果会显示在这里
            </div>
          )}
        </div>
      </div>
    </div>
  )
}
