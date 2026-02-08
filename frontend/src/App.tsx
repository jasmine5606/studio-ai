import { useChatStore } from './stores/chatStore'
import Sidebar from './components/Sidebar'
import ChatPanel from './components/ChatPanel'
import CodeReview from './components/CodeReview'
import KnowledgeBase from './components/KnowledgeBase'

function Placeholder({ title }: { title: string }) {
  return (
    <div className="flex items-center justify-center h-full text-white/20">
      <div className="text-center space-y-3">
        <div className="text-5xl">🚧</div>
        <p className="text-xl">{title}</p>
        <p className="text-sm">即将上线</p>
      </div>
    </div>
  )
}

export default function App() {
  const { activeTab } = useChatStore()

  return (
    <div className="flex h-screen p-3 gap-3">
      <Sidebar />

      <main className="flex-1 glass rounded-2xl overflow-hidden">
        {activeTab === 'chat' && <ChatPanel />}
        {activeTab === 'code-review' && <CodeReview />}
        {activeTab === 'kb' && <KnowledgeBase />}
        {activeTab === 'literature' && <Placeholder title="文献工作台" />}
        {activeTab === 'lab' && <Placeholder title="实验记录" />}
        {activeTab === 'admin' && <Placeholder title="管理后台" />}
      </main>
    </div>
  )
}
