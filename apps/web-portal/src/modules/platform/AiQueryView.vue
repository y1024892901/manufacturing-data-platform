<script setup lang="ts">
import { nextTick, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Loading } from '@element-plus/icons-vue'

type Provider = { id: 'deepseek' | 'qwen'; name: string; model: string; configured: boolean }
type ToolTrace = { name: string; arguments: Record<string, unknown>; result_preview: string }
type ChatMessage = { role: 'user' | 'assistant'; content: string; toolCalls?: ToolTrace[] }

const providers = ref<Provider[]>([])
const selectedProvider = ref<'deepseek' | 'qwen'>('deepseek')
const messages = ref<ChatMessage[]>([
  {
    role: 'assistant',
    content: '你好，我可以通过 MCP 查询 MDM、CRM、ERP、PLM、SRM、WMS、MES、QMS、EAM 和能源系统。你可以直接用自然语言提问；遇到新问题，我会先发现相关表结构再查询。'
  }
])
const draft = ref('')
const loading = ref(false)
const chatBody = ref<HTMLElement>()

const examples = [
  'ERP 里最近创建的销售订单有哪些？',
  'MES 中各工单的当前状态是什么？',
  'WMS 最近有哪些库存变动？',
  'QMS 里有哪些未关闭的不合格记录？'
]

onMounted(async () => {
  try {
    const response = await fetch('/ai-api/api/ai/providers')
    if (!response.ok) throw new Error('AI 服务未启动')
    const data = await response.json() as { default_provider: string; providers: Provider[] }
    providers.value = data.providers || []
    const preferred = providers.value.find(item => item.id === data.default_provider && item.configured)
      || providers.value.find(item => item.configured)
    if (preferred) selectedProvider.value = preferred.id
    else if (data.default_provider === 'qwen' || data.default_provider === 'deepseek') selectedProvider.value = data.default_provider
  } catch (error) {
    ElMessage.warning(error instanceof Error ? error.message : '无法读取模型配置')
  }
})

function scrollToBottom() {
  nextTick(() => { if (chatBody.value) chatBody.value.scrollTop = chatBody.value.scrollHeight })
}

async function send(text = draft.value) {
  const question = text.trim()
  if (!question || loading.value) return
  const history = messages.value.slice(-12).map(({ role, content }) => ({ role, content }))
  messages.value.push({ role: 'user', content: question })
  draft.value = ''
  loading.value = true
  scrollToBottom()
  try {
    const response = await fetch('/ai-api/api/ai/chat', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ message: question, provider: selectedProvider.value, history })
    })
    const data = await response.json() as { answer?: string; tool_calls?: ToolTrace[]; detail?: string }
    if (!response.ok) throw new Error(data.detail || 'AI 查询失败')
    messages.value.push({ role: 'assistant', content: data.answer || '没有收到模型回答。', toolCalls: data.tool_calls || [] })
  } catch (error) {
    const reason = error instanceof Error ? error.message : '网络请求失败'
    messages.value.push({ role: 'assistant', content: `查询失败：${reason}` })
  } finally {
    loading.value = false
    scrollToBottom()
  }
}

function submit() {
  void send()
}
</script>

<template>
  <section class="ai-page">
    <header class="page-head">
      <div>
        <span class="eyebrow">MULTI-SYSTEM DATA ASSISTANT</span>
        <h1>AI 数据问答</h1>
        <p>用自然语言探索各业务系统数据，查询过程通过 MCP 工具执行。</p>
      </div>
      <div class="provider-picker">
        <span>模型</span>
        <el-select v-model="selectedProvider" aria-label="选择模型" style="width: 190px">
          <el-option v-for="item in providers" :key="item.id" :value="item.id" :disabled="!item.configured">
            <div class="provider-option"><b>{{ item.name }}</b><small>{{ item.configured ? item.model : '尚未配置 API Key' }}</small></div>
          </el-option>
          <el-option v-if="!providers.length" value="deepseek" label="DeepSeek" />
        </el-select>
      </div>
    </header>

    <div class="chat-card">
      <div ref="chatBody" class="chat-body">
        <article v-for="(message, index) in messages" :key="index" class="message-row" :class="message.role">
          <div class="avatar">{{ message.role === 'assistant' ? 'AI' : '我' }}</div>
          <div class="message-content">
            <div class="message-label">{{ message.role === 'assistant' ? '数据助手' : '你' }}</div>
            <div class="bubble">{{ message.content }}</div>
            <details v-if="message.toolCalls?.length" class="tool-details">
              <summary>本次查询调用了 {{ message.toolCalls.length }} 个 MCP 工具</summary>
              <div v-for="(tool, toolIndex) in message.toolCalls" :key="toolIndex" class="tool-call">
                <b>{{ tool.name }}</b>
                <pre>{{ JSON.stringify(tool.arguments, null, 2) }}</pre>
                <p>{{ tool.result_preview }}</p>
              </div>
            </details>
          </div>
        </article>
        <div v-if="loading" class="loading-row"><el-icon class="is-loading"><Loading /></el-icon><span>正在发现数据结构并查询…</span></div>
      </div>

      <div class="composer-area">
        <div class="examples">
          <button v-for="item in examples" :key="item" :disabled="loading" @click="send(item)">{{ item }}</button>
        </div>
        <div class="composer">
          <el-input
            v-model="draft"
            type="textarea"
            :autosize="{ minRows: 1, maxRows: 4 }"
            maxlength="4000"
            resize="none"
            placeholder="例如：ERP 里本月金额最高的 10 张订单是什么？"
            @keydown.ctrl.enter.prevent="submit"
          />
          <el-button type="primary" :loading="loading" :disabled="!draft.trim()" @click="submit">发送</el-button>
        </div>
        <div class="composer-hint">AI 会查询实际数据；当前每次只查询单个系统，不自动推断跨系统关联。Ctrl + Enter 发送</div>
      </div>
    </div>
  </section>
</template>

<style scoped>
.ai-page{max-width:1280px;height:calc(100vh - 118px);min-height:540px;margin:0 auto;display:flex;flex-direction:column;gap:18px;color:#233b59}
.page-head{display:flex;align-items:center;justify-content:space-between;gap:24px;padding:2px 2px 0}
.eyebrow{font-size:10px;letter-spacing:1.4px;color:#3d85d7;font-weight:800}
h1{margin:7px 0 4px;font-size:25px}.page-head p{margin:0;color:#8392a5;font-size:12px}
.provider-picker{display:flex;align-items:center;gap:10px;color:#78889b;font-size:12px}
.provider-option{display:flex;flex-direction:column;gap:2px}.provider-option small{color:#8e9aac;font-size:10px}
.chat-card{flex:1;min-height:0;display:flex;flex-direction:column;border:1px solid #e3eaf2;border-radius:15px;background:#fff;box-shadow:0 8px 26px rgba(37,67,104,.05);overflow:hidden}
.chat-body{flex:1;overflow-y:auto;padding:24px clamp(18px,4vw,54px);scroll-behavior:smooth}
.message-row{display:flex;align-items:flex-start;gap:12px;margin-bottom:22px}.message-row.user{flex-direction:row-reverse}
.avatar{width:32px;height:32px;flex:0 0 32px;display:grid;place-items:center;border-radius:10px;background:#e9f2ff;color:#327cc8;font-size:11px;font-weight:800}
.user .avatar{background:#e7f6ef;color:#229668}.message-content{max-width:min(82%,900px);min-width:100px}.user .message-content{text-align:right}
.message-label{margin:0 0 6px;color:#8291a4;font-size:10px}.bubble{display:inline-block;padding:12px 15px;border:1px solid #e7edf4;border-radius:4px 13px 13px 13px;background:#fbfcfe;color:#354b66;text-align:left;font-size:13px;line-height:1.75;white-space:pre-wrap;overflow-wrap:anywhere}
.user .bubble{border-color:#d7e9ff;border-radius:13px 4px 13px 13px;background:#edf6ff}
.tool-details{margin-top:9px;border:1px solid #e8edf3;border-radius:8px;color:#738399;text-align:left;font-size:11px}.tool-details summary{padding:8px 10px;cursor:pointer}.tool-call{padding:9px 10px;border-top:1px solid #edf1f5}.tool-call b{color:#42688e}.tool-call pre{max-height:140px;overflow:auto;padding:8px;background:#f6f8fb;border-radius:5px;color:#596b80;white-space:pre-wrap;overflow-wrap:anywhere}.tool-call p{max-height:180px;overflow:auto;margin:6px 0 0;white-space:pre-wrap;overflow-wrap:anywhere}
.loading-row{display:flex;align-items:center;gap:8px;margin-left:44px;color:#7e91a8;font-size:12px}
.composer-area{padding:12px 24px 15px;border-top:1px solid #edf1f5;background:#fff}.examples{display:flex;flex-wrap:wrap;gap:7px;margin-bottom:11px}.examples button{padding:6px 9px;border:1px solid #e1eaf4;border-radius:14px;background:#f8fbff;color:#6d8299;font-size:10px;cursor:pointer}.examples button:hover{border-color:#83b8ef;color:#347dc1}.examples button:disabled{cursor:not-allowed;opacity:.55}
.composer{display:flex;align-items:flex-end;gap:10px;padding:8px;border:1px solid #dfe7f0;border-radius:11px;background:#fff}.composer :deep(.el-textarea__inner){border:0;box-shadow:none;padding:4px 7px;line-height:1.6;color:#304762}.composer :deep(.el-textarea__inner:focus){box-shadow:none}.composer .el-button{height:36px;min-width:68px;border-radius:8px}
.composer-hint{margin:7px 3px 0;color:#9aa6b5;font-size:10px}
@media(max-width:760px){.ai-page{height:calc(100vh - 100px);min-height:460px}.page-head{align-items:flex-start;flex-direction:column;gap:12px}.chat-body{padding:18px 13px}.message-content{max-width:88%}.composer-area{padding:10px 12px}.examples{max-height:54px;overflow:auto}}
</style>
