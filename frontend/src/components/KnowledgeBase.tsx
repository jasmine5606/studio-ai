import { useState } from 'react'
import { Upload, Search, Trash2, FileText } from 'lucide-react'
import { api } from '../services/api'
import type { KbFile, SearchHit } from '../types'

export default function KnowledgeBase() {
  const [files, setFiles] = useState<KbFile[]>([])
  const [searchResults, setSearchResults] = useState<SearchHit[]>([])
  const [query, setQuery] = useState('')
  const [loading, setLoading] = useState(false)

  const loadFiles = async () => {
    try {
      const data = await api.kb.list()
      setFiles(data)
    } catch {}
  }

  const handleUpload = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (!file) return
    setLoading(true)
    try {
      await api.kb.upload(file)
      await loadFiles()
    } catch {}
    setLoading(false)
  }

  const handleSearch = async () => {
    if (!query.trim()) return
    setLoading(true)
    try {
      const results = await api.kb.search(query)
      setSearchResults(results)
    } catch {}
    setLoading(false)
  }

  const handleDelete = async (id: string) => {
    try {
      await api.kb.delete(id)
      await loadFiles()
    } catch {}
  }

  return (
    <div className="p-6 space-y-6">
      <div className="flex items-center justify-between">
        <h2 className="text-lg font-semibold">知识库管理</h2>
        <label className="glass-button-primary cursor-pointer flex items-center gap-2">
          <Upload className="w-4 h-4" />
          上传文件
          <input type="file" className="hidden" onChange={handleUpload} accept=".txt,.md,.pdf,.docx,.pptx" />
        </label>
      </div>

      <div className="flex gap-3">
        <input
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          onKeyDown={(e) => e.key === 'Enter' && handleSearch()}
          placeholder="搜索知识库..."
          className="glass-input flex-1"
        />
        <button onClick={handleSearch} className="glass-button-primary flex items-center gap-2">
          <Search className="w-4 h-4" />
          搜索
        </button>
      </div>

      {searchResults.length > 0 && (
        <div className="space-y-3">
          <h3 className="text-sm font-medium text-white/60">
            搜索结果 ({searchResults.length})
          </h3>
          {searchResults.map((hit, i) => (
            <div key={i} className="glass p-4 space-y-2">
              <div className="flex items-center justify-between">
                <span className="text-xs text-blue-300">
                  Score: {(hit.score * 100).toFixed(1)}%
                </span>
                <span className="text-xs text-white/30">{hit.citationId}</span>
              </div>
              <p className="text-sm text-white/80 whitespace-pre-wrap">{hit.text}</p>
            </div>
          ))}
        </div>
      )}

      <div className="space-y-3">
        <div className="flex items-center justify-between">
          <h3 className="text-sm font-medium text-white/60">已上传文件</h3>
          <button onClick={loadFiles} className="text-xs text-blue-300 hover:text-blue-200">
            刷新
          </button>
        </div>
        {files.map((f) => (
          <div key={f.id} className="glass glass-hover p-4 flex items-center justify-between">
            <div className="flex items-center gap-3">
              <FileText className="w-5 h-5 text-white/40" />
              <div>
                <p className="text-sm">{f.fileName}</p>
                <p className="text-xs text-white/40">
                  {f.fileType} · {(f.fileSize / 1024).toFixed(1)}KB · {f.chunkCount} chunks · {f.ingestStatus}
                </p>
              </div>
            </div>
            <button
              onClick={() => handleDelete(f.id)}
              className="text-white/30 hover:text-red-400 transition-colors"
            >
              <Trash2 className="w-4 h-4" />
            </button>
          </div>
        ))}
        {files.length === 0 && (
          <p className="text-center text-white/30 text-sm py-8">暂无上传文件</p>
        )}
      </div>
    </div>
  )
}
