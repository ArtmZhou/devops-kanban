<template>
  <Teleport to="body">
    <div class="editor-overlay" @click.self="$emit('close')">
      <div class="editor-panel">
        <div class="editor-header">
          <span class="editor-title">编辑 - {{ taskTitle }}</span>
          <button class="editor-close" @click="$emit('close')">✕</button>
        </div>

        <div class="editor-body">
          <div class="editor-sidebar">
            <FileTree
              v-if="fileTree"
              :tree="fileTree"
              :selected-path="currentFile"
              @file-select="openFile"
            />
            <div v-else class="loading-tree">加载中...</div>
          </div>

          <div class="editor-main">
            <div v-if="!currentFile" class="editor-placeholder">
              选择左侧文件开始编辑
            </div>
            <div v-else class="editor-container">
              <div ref="editorRef" class="codemirror-wrapper"></div>
              <div class="editor-statusbar">
                <span class="status-file">{{ currentFile }}</span>
                <span class="status-position">行 {{ cursorLine }}, 列 {{ cursorCol }}</span>
                <span class="status-lang">{{ fileLanguage }}</span>
                <button
                  v-if="hasUnsavedChanges"
                  class="save-btn"
                  :disabled="saving"
                  @click="saveFile"
                >
                  保存 {{ saving ? '...' : '' }}
                </button>
                <span v-else class="status-saved">已保存</span>
              </div>
            </div>
          </div>
        </div>

        <div class="editor-footer">
          <span v-if="unsavedFiles.size > 0" class="unsaved-count">
            {{ unsavedFiles.size }} 个未保存变更
          </span>
          <span class="hint">Ctrl+S 保存当前文件</span>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { getFileTree, readFileContent, writeFileContent } from '../../api/git'
import FileTree from './FileTree.vue'
import { EditorView, keymap, lineNumbers, highlightActiveLineGutter } from '@codemirror/view'
import { EditorState } from '@codemirror/state'
import { javascript } from '@codemirror/lang-javascript'
import { python } from '@codemirror/lang-python'
import { html } from '@codemirror/lang-html'
import { css } from '@codemirror/lang-css'
import { json } from '@codemirror/lang-json'
import { defaultKeymap } from '@codemirror/commands'
import { bracketMatching } from '@codemirror/language'

const props = defineProps({
  projectId: { type: Number, required: true },
  taskId: { type: Number, required: true },
  taskTitle: { type: String, required: true },
})

defineEmits(['close'])

const fileTree = ref(null)
const currentFile = ref('')
const editorRef = ref(null)
const editorView = ref(null)
const saving = ref(false)
const cursorLine = ref(1)
const cursorCol = ref(1)
const unsavedFiles = ref(new Set())

const hasUnsavedChanges = computed(() => {
  if (!currentFile.value || !editorView.value) return false
  return unsavedFiles.value.has(currentFile.value)
})

const fileLanguage = computed(() => {
  if (!currentFile.value) return 'Plain Text'
  const ext = currentFile.value.split('.').pop().toLowerCase()
  const langMap = {
    js: 'JavaScript', ts: 'TypeScript', jsx: 'JavaScript', tsx: 'TypeScript',
    py: 'Python', html: 'HTML', css: 'CSS', json: 'JSON',
    vue: 'Vue', md: 'Markdown',
  }
  return langMap[ext] || 'Plain Text'
})

function getLanguageExtension() {
  if (!currentFile.value) return []
  const ext = currentFile.value.split('.').pop().toLowerCase()
  const langMap = {
    js: javascript, ts: javascript, jsx: javascript, tsx: javascript,
    py: python, html: html, css: css, json: json,
  }
  return langMap[ext] ? [langMap[ext]()] : []
}

function createEditor(content = '') {
  if (editorView.value) {
    editorView.value.destroy()
  }

  const state = EditorState.create({
    doc: content,
    extensions: [
      lineNumbers(),
      highlightActiveLineGutter(),
      bracketMatching(),
      ...getLanguageExtension(),
      keymap.of([
        ...defaultKeymap,
        {
          key: 'Mod-s',
          run: () => {
            saveFile()
            return true
          },
        },
      ]),
      EditorView.updateListener.of((update) => {
        if (update.docChanged && currentFile.value) {
          unsavedFiles.value.add(currentFile.value)
        }
        if (update.selectionSet) {
          const pos = update.state.selection.main.head
          const line = update.state.doc.lineAt(pos)
          cursorLine.value = line.number
          cursorCol.value = pos - line.from + 1
        }
      }),
      EditorView.theme({
        '&': { height: '100%', fontSize: '14px' },
        '.cm-scroller': { overflow: 'auto' },
      }),
    ],
  })

  editorView.value = new EditorView({ state, parent: editorRef.value })
}

async function loadFileTree() {
  try {
    const res = await getFileTree(props.projectId, props.taskId)
    if (res.success) {
      fileTree.value = res.data
    }
  } catch {
    ElMessage.error('Failed to load file tree')
  }
}

async function openFile(filePath) {
  currentFile.value = filePath
  try {
    const res = await readFileContent(props.projectId, props.taskId, filePath)
    if (res.success) {
      if (res.data.isBinary) {
        ElMessage.warning('Binary files cannot be edited')
        currentFile.value = ''
        return
      }
      await nextTick()
      createEditor(res.data.content)
      unsavedFiles.value.delete(filePath)
    }
  } catch {
    ElMessage.error('Failed to load file')
  }
}

async function saveFile() {
  if (!currentFile.value || !editorView.value || saving.value) return
  saving.value = true
  try {
    const content = editorView.value.state.doc.toString()
    const res = await writeFileContent(props.projectId, props.taskId, currentFile.value, content)
    if (res.success) {
      unsavedFiles.value.delete(currentFile.value)
      ElMessage.success('保存成功')
    }
  } catch {
    ElMessage.error('保存失败')
  } finally {
    saving.value = false
  }
}

function handleKeydown(e) {
  if ((e.ctrlKey || e.metaKey) && e.key === 's') {
    e.preventDefault()
    saveFile()
  }
}

onMounted(async () => {
  await loadFileTree()
  document.addEventListener('keydown', handleKeydown)
})

onUnmounted(() => {
  if (editorView.value) {
    editorView.value.destroy()
  }
  document.removeEventListener('keydown', handleKeydown)
})
</script>

<style scoped>
.editor-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.5);
  z-index: 2000;
  display: flex;
  align-items: center;
  justify-content: center;
}

.editor-panel {
  width: 95vw;
  height: 95vh;
  background: #fff;
  border-radius: 8px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.2);
}

.editor-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: 1px solid #e4e7ed;
  background: #f5f7fa;
}

.editor-title {
  font-size: 14px;
  font-weight: 600;
}

.editor-close {
  background: none;
  border: none;
  font-size: 18px;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 4px;
}

.editor-close:hover {
  background: rgba(0, 0, 0, 0.06);
}

.editor-body {
  display: flex;
  flex: 1;
  overflow: hidden;
}

.editor-sidebar {
  width: 260px;
  min-width: 200px;
  border-right: 1px solid #e4e7ed;
  overflow-y: auto;
  background: #fafafa;
}

.loading-tree {
  padding: 20px;
  text-align: center;
  color: #909399;
}

.editor-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.editor-placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: #909399;
  font-size: 14px;
}

.editor-container {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.codemirror-wrapper {
  flex: 1;
  overflow: auto;
}

.editor-statusbar {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 6px 12px;
  border-top: 1px solid #e4e7ed;
  background: #f5f7fa;
  font-size: 12px;
  color: #606266;
}

.save-btn {
  margin-left: auto;
  padding: 4px 12px;
  background: #409eff;
  color: #fff;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 12px;
}

.save-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.status-saved {
  margin-left: auto;
  color: #67c23a;
}

.editor-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 16px;
  border-top: 1px solid #e4e7ed;
  background: #f5f7fa;
  font-size: 12px;
  color: #909399;
}

.unsaved-count {
  color: #e6a23c;
  font-weight: 600;
}
</style>
