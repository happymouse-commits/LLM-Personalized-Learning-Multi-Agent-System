<template>
  <div class="monaco-wrapper">
    <div v-if="loadError" class="fallback-editor">
      <textarea
        :value="modelValue"
        @input="$emit('update:modelValue', $event.target.value)"
        class="fallback-textarea"
        :readonly="readonly"
        spellcheck="false"
        aria-label="代码编辑区"
      ></textarea>
      <div class="fallback-hint">Monaco 编辑器加载失败，使用简易编辑器</div>
    </div>
    <div v-else ref="editorContainer" class="editor-container" aria-label="代码编辑区"></div>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, watch, nextTick } from 'vue'

const props = defineProps({
  modelValue: { type: String, default: '' },
  language: { type: String, default: 'c' },
  theme: { type: String, default: 'vs-dark' },
  readonly: { type: Boolean, default: false },
  fontSize: { type: Number, default: 14 }
})

const emit = defineEmits(['update:modelValue'])

const editorContainer = ref(null)
const loadError = ref(false)
let editor = null
let monacoInstance = null
let isUpdating = false

onMounted(async () => {
  try {
    // 配置 Monaco Web Worker（Vite 环境必须）
    const editorWorker = (await import('monaco-editor/esm/vs/editor/editor.worker?worker')).default
    self.MonacoEnvironment = {
      getWorker(_, label) {
        return new editorWorker()
      }
    }

    const monaco = await import('monaco-editor')
    monacoInstance = monaco

    // 自定义 Catppuccin Mocha 主题
    monaco.editor.defineTheme('cryd-mocha', {
      base: 'vs-dark',
      inherit: true,
      rules: [
        { token: 'keyword', foreground: 'cba6f7', fontStyle: 'bold' },
        { token: 'keyword.type', foreground: 'cba6f7' },
        { token: 'keyword.control', foreground: 'cba6f7', fontStyle: 'bold' },
        { token: 'string', foreground: 'a6e3a1' },
        { token: 'string.escape', foreground: 'f5c2e7' },
        { token: 'comment', foreground: '6c7086', fontStyle: 'italic' },
        { token: 'number', foreground: 'fab387' },
        { token: 'predefined', foreground: '89b4fa' },
        { token: 'identifier', foreground: 'cdd6f4' },
        { token: 'type', foreground: 'f9e2af' },
        { token: 'delimiter', foreground: '9399b2' },
        { token: 'delimiter.parenthesis', foreground: '9399b2' },
        { token: 'delimiter.bracket', foreground: '9399b2' },
        { token: 'delimiter.square', foreground: '9399b2' },
        { token: 'meta.preprocessor', foreground: 'f38ba8' },
        { token: 'meta.preprocessor.directive', foreground: 'f38ba8' },
        { token: 'function', foreground: '89b4fa' },
      ],
      colors: {
        'editor.background': '#1e1e2e',
        'editor.foreground': '#cdd6f4',
        'editorLineNumber.foreground': '#45475a',
        'editorLineNumber.activeForeground': '#89b4fa',
        'editor.selectionBackground': '#585b7066',
        'editor.lineHighlightBackground': '#18182580',
        'editor.lineHighlightBorder': '#00000000',
        'editorCursor.foreground': '#f5e0dc',
        'editorWhitespace.foreground': '#45475a40',
        'editorIndentGuide.background1': '#45475a40',
        'editorIndentGuide.activeBackground1': '#585b7080',
        'editorBracketMatch.background': '#585b7040',
        'editorBracketMatch.border': '#89b4fa60',
        'editorGutter.background': '#181825',
        'editor.foldBackground': '#45475a40',
        'editorOverviewRuler.border': '#00000000',
        'editor.lineHighlight': '#18182580',
        'scrollbarSlider.background': '#585b7040',
        'scrollbarSlider.hoverBackground': '#585b7080',
        'scrollbarSlider.activeBackground': '#585b70a0',
        'minimapSlider.background': '#585b7030',
        'minimapSlider.hoverBackground': '#585b7050',
        'editorWidget.background': '#181825',
        'editorWidget.border': '#313244',
        'editorSuggestWidget.background': '#181825',
        'editorSuggestWidget.selectedBackground': '#313244',
        'editorSuggestWidget.highlightForeground': '#89b4fa',
        'editorHoverWidget.background': '#181825',
        'editorHoverWidget.border': '#313244',
        'input.background': '#313244',
        'input.border': '#45475a',
        'focusBorder': '#89b4fa40',
      }
    })

    editor = monaco.editor.create(editorContainer.value, {
      value: props.modelValue,
      language: props.language,
      theme: 'cryd-mocha',
      fontSize: props.fontSize,
      fontFamily: "'JetBrains Mono', 'Fira Code', 'Consolas', 'Courier New', monospace",
      fontLigatures: true,
      lineHeight: 24,
      letterSpacing: 0.3,
      lineNumbers: 'on',
      lineNumbersMinChars: 3,
      minimap: { enabled: false },
      scrollBeyondLastLine: false,
      renderWhitespace: 'selection',
      renderLineHighlight: 'all',
      bracketPairColorization: { enabled: true },
      guides: { bracketPairs: true, indentation: true },
      rulers: [80],
      tabSize: 4,
      insertSpaces: true,
      readOnly: props.readonly,
      automaticLayout: true,
      wordWrap: 'on',
      contextmenu: true,
      folding: true,
      foldingStrategy: 'indentation',
      showFoldingControls: 'mouseover',
      padding: { top: 12, bottom: 12 },
      smoothScrolling: true,
      cursorBlinking: 'smooth',
      cursorSmoothCaretAnimation: 'on',
      roundedSelection: true,
      scrollbar: {
        verticalScrollbarSize: 10,
        horizontalScrollbarSize: 10,
        useShadows: false,
        verticalHasArrows: false,
        horizontalHasArrows: false,
      },
      overviewRulerBorder: false,
      hideCursorInOverviewRuler: true,
    })

    editor.onDidChangeModelContent(() => {
      if (!isUpdating) {
        emit('update:modelValue', editor.getValue())
      }
    })
  } catch (e) {
    console.warn('Monaco Editor 加载失败，使用 textarea 回退:', e)
    loadError.value = true
  }
})

watch(() => props.modelValue, (newVal) => {
  if (editor && !isUpdating) {
    isUpdating = true
    const currentVal = editor.getValue()
    if (newVal !== currentVal) {
      editor.setValue(newVal || '')
    }
    nextTick(() => { isUpdating = false })
  }
})

watch(() => props.readonly, (newVal) => {
  if (editor) {
    editor.updateOptions({ readOnly: newVal })
  }
})

onBeforeUnmount(() => {
  if (editor) {
    editor.dispose()
    editor = null
  }
  monacoInstance = null
})
</script>

<style scoped>
.monaco-wrapper {
  width: 100%;
  height: 100%;
  position: relative;
}

.editor-container {
  width: 100%;
  height: 100%;
  min-height: 300px;
}

.fallback-editor {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
}

.fallback-textarea {
  flex: 1;
  width: 100%;
  min-height: 300px;
  background: #1e1e1e;
  color: #d4d4d4;
  font-family: 'JetBrains Mono', 'Fira Code', Consolas, monospace;
  font-size: 14px;
  line-height: 1.6;
  padding: 16px;
  border: 1px solid #3c3c3c;
  border-radius: 4px;
  resize: none;
  outline: none;
  tab-size: 4;
}

.fallback-textarea:focus {
  border-color: #007acc;
}

.fallback-hint {
  padding: 6px 12px;
  background: #2d2d2d;
  color: #888;
  font-size: 12px;
  text-align: center;
}
</style>
