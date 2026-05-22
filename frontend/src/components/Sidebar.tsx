import {
  MessageSquare,
  Code2,
  Database,
  BookOpen,
  FlaskConical,
  BarChart3,
  Plus,
  Settings,
} from 'lucide-react'
import { useChatStore } from '../stores/chatStore'
import type { TabName } from '../types'

const tabs: { id: TabName; label: string; icon: React.ReactNode }[] = [
  { id: 'chat', label: '对话', icon: <MessageSquare className="w-4 h-4" /> },
  { id: 'code-review', label: '代码审查', icon: <Code2 className="w-4 h-4" /> },
  { id: 'kb', label: '知识库', icon: <Database className="w-4 h-4" /> },
  { id: 'literature', label: '文献', icon: <BookOpen className="w-4 h-4" /> },
  { id: 'lab', label: '实验', icon: <FlaskConical className="w-4 h-4" /> },
  { id: 'admin', label: '管理', icon: <BarChart3 className="w-4 h-4" /> },
]

export default function Sidebar() {
  const { activeTab, setActiveTab, sessions, currentSessionId, loadMessages, newSession } =
    useChatStore()

  return (
    <div className="w-56 glass rounded-l-2xl flex flex-col">
      <div className="p-4 border-b border-white/5">
        <div className="flex items-center gap-3">
          <div className="w-8 h-8 rounded-lg bg-gradient-to-br from-blue-500 to-purple-600 flex items-center justify-center text-sm font-bold">
            AI
          </div>
          <div>
            <h1 className="text-sm font-semibold">Studio AI</h1>
            <p className="text-[10px] text-white/40">未来码实验室</p>
          </div>
        </div>
      </div>

      <nav className="flex-1 py-3 px-2 space-y-1 overflow-y-auto">
        {tabs.map((tab) => (
          <button
            key={tab.id}
            onClick={() => setActiveTab(tab.id)}
            className={`w-full flex items-center gap-3 px-3 py-2.5 rounded-xl text-sm transition-all ${
              activeTab === tab.id ? 'glass tab-active' : 'tab-inactive'
            }`}
          >
            {tab.icon}
            <span>{tab.label}</span>
          </button>
        ))}
      </nav>

      <div className="p-3 border-t border-white/5 space-y-2">
        {sessions.length > 0 && (
          <div className="space-y-1">
            <p className="text-[10px] text-white/30 px-2 uppercase tracking-wider">历史会话</p>
            {sessions.slice(0, 5).map((s) => (
              <button
                key={s.id}
                onClick={() => loadMessages(s.id)}
                className={`w-full text-left px-3 py-1.5 rounded-lg text-xs truncate transition-colors ${
                  currentSessionId === s.id
                    ? 'bg-white/10 text-blue-200'
                    : 'text-white/50 hover:bg-white/5 hover:text-white/70'
                }`}
              >
                {s.title}
              </button>
            ))}
          </div>
        )}

        <button
          onClick={newSession}
          className="w-full flex items-center gap-2 px-3 py-2 rounded-xl text-xs text-white/60 hover:text-white hover:bg-white/5 transition-all"
        >
          <Plus className="w-3.5 h-3.5" />
          新会话
        </button>

        <button className="w-full flex items-center gap-2 px-3 py-2 rounded-xl text-xs text-white/40 hover:text-white/60 hover:bg-white/5 transition-all">
          <Settings className="w-3.5 h-3.5" />
          设置
        </button>
      </div>
    </div>
  )
}
