export interface ChatMessage {
  id: string
  role: 'user' | 'assistant' | 'system'
  content: string
  timestamp: number
}

export interface ChatSession {
  id: string
  title: string
  lastActiveAt: number
}

export interface ChatResponse {
  sessionId: string
  answer: string
}

export interface KbFile {
  id: string
  fileName: string
  fileType: string
  fileSize: number
  ingestStatus: 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED'
  chunkCount: number
  createdAt: string
}

export interface LiteratureDoc {
  literatureId: string
  title: string
  source: string
  identifier: string
  projectTag: string
  chunkCount: number
  textPreview: string
  createdAt: string
}

export interface ExperimentRecord {
  recordId: string
  projectId: string
  projectName: string
  title: string
  conditions: string
  conclusion: string
  tags: string[]
  createdAt: string
}

export interface SearchHit {
  score: number
  text: string
  citationId: string
}

export interface EvalResult {
  score: number
  explanation: string
}

export interface AbTestResult {
  winner: string
  scoreA: number
  scoreB: number
  reason: string
}

export type ChatMode = 'auto' | 'local' | 'external'
export type TabName = 'chat' | 'code-review' | 'kb' | 'literature' | 'lab' | 'admin'
