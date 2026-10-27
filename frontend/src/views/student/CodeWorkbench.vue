<template>
  <div class="code-workbench">
    <!-- 顶栏：仅保留返回按钮 -->
    <header class="workbench-header">
      <button class="back-btn" @click="goBack">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" width="18" height="18">
          <path d="M19 12H5M12 19l-7-7 7-7" stroke-linecap="round" stroke-linejoin="round"/>
        </svg>
        返回
      </button>
    </header>

    <!-- gcc 不可用提示 -->
    <div v-if="gccUnavailable" class="gcc-warning">
      <span class="warn-icon">⚠️</span>
      当前服务器未安装 gcc，仅支持编辑参考代码，无法运行/判题
    </div>

    <!-- 判题状态条：渐变高亮 -->
    <div v-if="judgeResult" :class="['judge-bar', judgeResult.pass ? 'pass' : 'fail']">
      <template v-if="judgeResult.isSelfCheck">
        <template v-if="judgeResult.pass">
          <span class="judge-icon">✅</span>
          自检通过 · 代码可编译并正常运行
        </template>
        <template v-else>
          <span class="judge-icon">⚠️</span>
          自检未通过 · 请查看下方详情
        </template>
      </template>
      <template v-else>
        <template v-if="judgeResult.pass">
          <span class="judge-icon">🎉</span>
          所有样例通过（<strong>{{ judgeResult.passCount }}/{{ judgeResult.totalCount }}</strong>）· 恭喜！
        </template>
        <template v-else>
          <span class="judge-icon">💪</span>
          第<strong>{{ judgeResult.failedIndex + 1 }}</strong>组样例未通过（{{ judgeResult.passCount }}/{{ judgeResult.totalCount }}）· 加油！
        </template>
      </template>
    </div>

    <!-- 主体 -->
    <div class="workbench-body">
      <!-- 左栏：题目区 蓝桥杯极简高对比度风格 + 三Tab内容区 -->
      <aside class="problem-panel">
        <!-- 蓝桥杯左侧导航：题目 / 题解 / 学习助手 -->
        <div class="side-nav">
          <div :class="['nav-item', { active: activeTab === 'question' }]" @click="activeTab = 'question'" title="题目">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="18" height="18">
              <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" stroke-linecap="round" stroke-linejoin="round"/>
              <path d="M14 2v6h6M16 13H8M16 17H8M10 9H8" stroke-linecap="round"/>
            </svg>
          </div>
          <div :class="['nav-item', { active: activeTab === 'solution' }]" @click="activeTab = 'solution'; ensureSolutionLoaded()" title="题解">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="18" height="18">
              <path d="M2 3h6a4 4 0 0 1 4 4v14a3 3 0 0 0-3-3H2z" stroke-linecap="round" stroke-linejoin="round"/>
              <path d="M22 3h-6a4 4 0 0 0-4 4v14a3 3 0 0 1 3-3h7z" stroke-linecap="round" stroke-linejoin="round"/>
            </svg>
          </div>
          <div :class="['nav-item', { active: activeTab === 'tutor' }]" @click="activeTab = 'tutor'" title="AI 学习助手">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="18" height="18">
              <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" stroke-linecap="round" stroke-linejoin="round"/>
            </svg>
          </div>
        </div>

        <div class="problem-main">
          <!-- ========== Tab 1：题目 ========== -->
          <div v-show="activeTab === 'question'" class="tab-pane">
            <div class="problem-scroll">
              <template v-if="!parsed.fallback">
                <section>
                  <h3 class="section-h">问题描述</h3>
                  <div class="section-content" v-html="renderMarkdown(parsed.description)"></div>
                </section>
                <section v-if="parsed.inputFormat">
                  <h3 class="section-h">输入格式</h3>
                  <div class="section-content" v-html="renderMarkdown(parsed.inputFormat)"></div>
                </section>
                <section v-if="parsed.outputFormat">
                  <h3 class="section-h">输出格式</h3>
                  <div class="section-content" v-html="renderMarkdown(parsed.outputFormat)"></div>
                </section>
                <template v-for="(sample, idx) in parsed.samples" :key="idx">
                  <section>
                    <h3 class="section-h">样例输入 {{ idx + 1 }}</h3>
                    <div class="sample-block">
                      <button class="copy-btn" @click="copyToClipboard(sample.input)">复制</button>
                      <pre class="sample-code">{{ sample.input || '(空)' }}</pre>
                    </div>
                  </section>
                  <section>
                    <h3 class="section-h">样例输出 {{ idx + 1 }}</h3>
                    <div class="sample-block sample-out">
                      <button class="copy-btn" @click="copyToClipboard(sample.output)">复制</button>
                      <pre class="sample-code">{{ sample.output || '(空)' }}</pre>
                    </div>
                  </section>
                </template>
                <section v-if="parsed.constraints">
                  <h3 class="section-h">评测用例规模与约定</h3>
                  <div class="section-content" v-html="renderMarkdown(parsed.constraints)"></div>
                </section>
              </template>
              <template v-else>
                <section>
                  <div class="section-content fallback-md" v-html="renderMarkdown(parsed.rawMarkdown)"></div>
                </section>
              </template>
            </div>
          </div>

          <!-- ========== Tab 2：题解（体检） ========== -->
          <div v-show="activeTab === 'solution'" class="tab-pane solution-pane">
            <div class="problem-scroll" ref="solutionScroll">
              <!-- 题解标题栏 -->
              <div class="solution-simple-header">
                <h3 class="ssh-title">📚 官方题解</h3>
              </div>

              <!-- AI 增强指示器（有基础数据时，内容已可见，仅显示顶部细条） -->
              <div v-if="solutionEnhancing" class="solution-enhancing-bar">
                <span class="seb-spinner"></span>
                <span class="seb-text">AI 正在增强题解内容…</span>
              </div>

              <!-- Loading（仅在无任何基础数据时显示） -->
              <div v-if="solutionLoading" class="solution-loading-simple">
                <div class="sls-spinner"></div>
                <p class="sls-text">AI 正在生成官方题解…</p>
                <div class="sls-steps">
                  <span v-for="n in 2" :key="n" :class="['sls-step', { active: solutionStep >= n, done: solutionStep > n }]">
                    <span class="sls-check" v-if="solutionStep > n">✓</span>
                    <span class="sls-num" v-else>{{ n }}</span>
                    <span class="sls-label">{{ ['思路点拨','参考代码'][n-1] }}</span>
                  </span>
                </div>
              </div>

              <!-- 题解内容（有基础数据时立即可见，不等待 AI） -->
              <template v-else-if="!solution.error">
                <!-- 一、思路点拨 -->
                <section v-if="solution.thought" class="solution-section-v2" id="sol-0" :style="{ animationDelay: '0.05s' }">
                  <div class="ssv2-hbar">
                    <span class="ssv2-index">01</span>
                    <h3 class="solution-h ssv2-h"><span class="sh-emoji">💡</span>思路点拨</h3>
                  </div>
                  <div class="solution-body ssv2-body ssv2-card thought-card">
                    <div class="thought-steps" v-html="renderMarkdown(solution.thought)"></div>
                  </div>
                </section>

                <!-- 二、参考代码 -->
                <section v-if="solution.referenceCode" class="solution-section-v2" id="sol-1" :style="{ animationDelay: '0.12s' }">
                  <div class="ssv2-hbar">
                    <h3 class="solution-h ssv2-h">
                      <span class="sh-emoji">💻</span>参考代码
                      <button class="copy-ref-btn" @click="copyToClipboard(solution.referenceCode)">
                        <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2"><rect x="9" y="9" width="13" height="13" rx="2"/><path d="M5 15H4a2 2 0 01-2-2V4a2 2 0 012-2h9a2 2 0 012 2v1"/></svg>
                        复制代码
                      </button>
                    </h3>
                  </div>
                  <div class="ref-code-wrap-v2">
                    <div class="rcv2-window">
                      <div class="rcv2-traffic">
                        <span class="rct r" @click="applyReferenceCode" title="用参考代码替换编辑器"></span>
                        <span class="rct y"></span>
                        <span class="rct g"></span>
                      </div>
                      <div class="rcv2-filename">
                        <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2"><path d="M14 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V8z"/><path d="M14 2v6h6"/></svg>
                        main.c
                      </div>
                      <div class="rcv2-actions">
                        <button class="rcv2-btn" @click="applyReferenceCode">
                          <svg viewBox="0 0 24 24" width="13" height="13" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 2v6h-6M3 12a9 9 0 0115-6.7L21 8M3 22v-6h6M21 12a9 9 0 01-15 6.7L3 16"/></svg>
                          应用到编辑器
                        </button>
                      </div>
                    </div>
                    <pre class="ref-code"><code>{{ solution.referenceCode }}</code></pre>
                  </div>
                </section>

                <div v-if="!solution.thought && !solution.referenceCode" class="solution-empty-simple">
                  <div class="ses-icon">📚</div>
                  <p class="ses-title">本题暂无官方题解</p>
                  <p class="ses-sub">一键生成结构化题解，含解题思路与可运行参考代码</p>
                  <button class="se-btn big" @click="generateSolution()">
                    ✨ AI 生成题解
                  </button>
                </div>
              </template>

              <template v-else>
                <div class="solution-error-simple">
                  <div class="ses-icon error">❌</div>
                  <p class="ses-title">题解加载失败</p>
                  <p class="ses-sub">{{ solution.error }}</p>
                  <button class="se-btn big" @click="generateSolution()">🔄 重新生成</button>
                </div>
              </template>
            </div>
          </div>

          <!-- ========== Tab 3：AI 学习助手 ========== -->
          <div v-show="activeTab === 'tutor'" class="tab-pane tutor-pane">
            <!-- 极简标题栏 + 工具按钮 -->
            <div class="tutor-minimal-header">
              <span class="tmh-icon">🤖</span>
              <span class="tmh-title">AI 学习助手</span>
              <span class="tmh-hint">基于本题题意 + 官方题解，专注知识传授</span>
            </div>

            <!-- 消息区（头像 + 气泡，结构迁移自 AIGuide） -->
            <div class="chat-messages" ref="chatBox">
              <div v-if="tutorMessages.length === 0" class="chat-empty">
                <span class="ce-icon">💡</span>
                <p class="ce-text">问我任何关于这道题的问题</p>
                <span class="ce-hint">我会结合题意和官方题解，用 3-5 个要点引导你思考</span>
              </div>

              <div v-for="(m, idx) in tutorMessages" :key="m.id || idx"
                   :class="['msg', m.role === 'user' ? 'msg-user' : 'msg-ai']">
                <!-- AI 头像 -->
                <div v-if="m.role === 'assistant'" class="msg-avatar msg-avatar-img">
                  <img src="/images/avatar.png" alt="AI" />
                </div>
                <!-- 气泡 -->
                <div class="msg-bubble">
                  <div v-if="m.role === 'assistant'" class="agent-tag">AI 导师</div>
                  <div class="msg-content-parsed" v-html="renderMarkdown(m.content)"></div>
                  <!-- TTS 朗读按钮 -->
                  <button v-if="m.role === 'assistant' && m.content" class="tts-btn"
                          :class="{ playing: readingMap[m.id] }" @click="toggleRead(m)">
                    {{ readingMap[m.id] ? '⏹ 停止朗读' : '🔊 朗读' }}
                  </button>
                </div>
                <!-- 用户头像 -->
                <div v-if="m.role === 'user'" class="msg-avatar user-avatar">👤</div>
              </div>

              <!-- 思考中 -->
              <div v-if="tutorLoading && !ragRegenerating" class="msg msg-ai">
                <div class="msg-avatar msg-avatar-img"><img src="/images/avatar.png" alt="AI" /></div>
                <div class="msg-bubble thinking">
                  <span class="agent-tag">正在深度思考</span>
                  <span class="thinking-spinner"></span>
                </div>
              </div>
            </div>

            <!-- RAG 知识库检索结果折叠栏（聚焦：幻觉率 + 可靠来源） -->
            <transition name="rag-slide">
              <div v-if="ragResults.length > 0" class="rag-panel-chat" :class="hallucinationLevelClass">
                <div class="rag-panel-header" @click="ragExpanded = !ragExpanded">
                  <div class="rag-panel-left">
                    <span class="rag-panel-icon">📚</span>
                    <span class="rag-panel-title">RAG 知识库检索</span>
                    <span class="rag-panel-count">{{ ragResults.length }} 个可靠来源</span>
                  </div>
                  <div class="rag-panel-right">
                    <span class="rag-hallucination-badge" :class="hallucinationLevelClass">
                      <span class="hallucination-dot"></span>
                      幻觉率 {{ hallucinationPercent }}% · {{ hallucinationStatusText }}
                    </span>
                    <span :class="['rag-toggle-icon', { open: ragExpanded }]">▴</span>
                  </div>
                </div>
                <transition name="rag-expand">
                  <div v-if="ragExpanded" class="rag-panel-body">
                    <!-- 核心指标：AI 幻觉率 -->
                    <div class="rag-metrics-section">
                      <div class="rag-metric-circle">
                        <div class="circle-progress" :class="hallucinationLevelClass">
                          <svg viewBox="0 0 100 100" class="circle-svg">
                            <circle cx="50" cy="50" r="42" fill="none" stroke="#f0f2f5" stroke-width="8"/>
                            <circle cx="50" cy="50" r="42" fill="none" stroke="currentColor" stroke-width="8"
                              stroke-linecap="round"
                              :stroke-dasharray="`${(100 - hallucinationPercent) * 2.64} 264`"
                              transform="rotate(-90 50 50)"/>
                          </svg>
                          <div class="circle-content">
                            <div class="circle-value" :class="hallucinationLevelClass">{{ hallucinationPercent }}%</div>
                            <div class="circle-label">幻觉率</div>
                          </div>
                        </div>
                      </div>
                      <div class="rag-credibility-summary">
                        <div class="credibility-status-row">
                          <span class="credibility-status-tag" :class="hallucinationLevelClass">{{ hallucinationStatusText }}</span>
                          <span class="credibility-hint" :class="hallucinationLevelClass">{{ credibilityHint }}</span>
                        </div>
                        <!-- 幻觉率检测（自动重生成已禁用） -->
                        <div class="rag-guard">
                          <span class="guard-icon">🛡</span>
                          <span class="guard-text">幻觉率检测已启用<span class="guard-threshold">（阈值 30%）</span></span>
                        </div>
                        <div class="credibility-bar-wrap">
                          <div class="credibility-bar-fill" :class="hallucinationLevelClass"
                               :style="{ width: (100 - hallucinationPercent) + '%' }"></div>
                        </div>
                        <div class="credibility-meta">
                          <span v-if="ragMeta?.factCheckPassed" class="check-pass">✅ 事实一致性校验通过</span>
                          <span v-else class="check-warn">⚠️ 部分内容未经知识库验证</span>
                          <span class="credibility-divider">·</span>
                          <span>可信度 {{ reliabilityPercent }}%</span>
                        </div>
                      </div>
                    </div>
                    <!-- 可靠来源：知识库片段 -->
                    <div class="rag-sources-section">
                      <div class="rag-chunks-header">
                        <span class="rag-chunks-title">📖 知识来源片段</span>
                        <span class="rag-chunks-hint">点击展开完整内容</span>
                      </div>
                      <div v-for="(chunk, idx) in ragResults" :key="idx"
                           class="rag-chunk-item"
                           :class="{ expanded: chunk._expanded }"
                           @click="chunk._expanded = !chunk._expanded">
                        <div class="rag-chunk-top">
                          <div class="rag-chunk-top-left">
                            <span class="rag-chunk-idx">#{{ idx + 1 }}</span>
                            <span class="rag-chunk-source">{{ chunk.source || '未注明来源' }}</span>
                          </div>
                          <span class="rag-chunk-score" :class="chunkScoreClass(chunk.score)">
                            匹配度 {{ (chunk.score * 100).toFixed(1) }}%
                          </span>
                        </div>
                        <div class="rag-chunk-text" :class="{ 'full-text': chunk._expanded }">
                          {{ chunk.content }}
                        </div>
                        <div class="rag-chunk-bottom">
                          <div class="rag-chunk-kp" v-if="chunk.knowledgePoint">
                            📌 {{ chunk.knowledgePoint }}
                          </div>
                          <span class="rag-chunk-expand-hint">
                            {{ chunk._expanded ? '收起 ▲' : '展开 ▼' }}
                          </span>
                        </div>
                      </div>
                    </div>
                  </div>
                </transition>
              </div>
            </transition>

            <!-- 快捷提问 chips -->
            <div class="tutor-quick-row">
              <button v-for="q in quickQuestions" :key="q.id" class="tqr-chip" @click="sendQuick(q.text)">{{ q.text }}</button>
            </div>

            <!-- 输入区（结构迁移自 AIGuide：input-row + input-wrapper + send-btn + voice-btn） -->
            <div class="chat-input-area">
              <div class="input-row">
                <div class="input-wrapper">
                  <textarea v-model="tutorInput" rows="1" class="chat-input-native"
                    placeholder="问我思路、给你线索、引导你自己解出这道题（Enter 发送，Shift+Enter 换行）"
                    @keydown.enter.exact.prevent="sendTutor()" :disabled="tutorLoading"></textarea>
                </div>
                <button class="send-btn" @click="sendTutor()" :disabled="tutorLoading || !tutorInput.trim()">
                  <div class="svg-wrapper-1"><div class="svg-wrapper">
                    <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="20" height="20">
                      <path fill="none" d="M0 0h24v24H0z"></path>
                      <path fill="currentColor" d="M1.946 9.315c-.522-.174-.527-.455.01-.634l19.087-6.362c.529-.176.832.12.684.638l-5.454 19.086c-.15.529-.455.547-.679.045L12 14l6-8-8 6-8.054-2.685z"></path>
                    </svg>
                  </div></div>
                  <span>发送</span>
                </button>
                <!-- 语音按钮（讯飞 ASR，按住说话） -->
                <button class="voice-btn"
                        :class="{ recording: cwVoiceState === 'recording', listening: cwVoiceState === 'listening' }"
                        :disabled="cwVoiceState === 'listening'"
                        @mousedown.prevent="cwStartRecording"
                        @touchstart.prevent="cwStartRecording" @touchend="cwStopRecording" @touchcancel="cwStopRecording"
                        title="按住说话">
                  <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="18" height="18" fill="currentColor">
                    <path d="M12 14c1.66 0 3-1.34 3-3V5c0-1.66-1.34-3-3-3S9 3.34 9 5v6c0 1.66 1.34 3 3 3z"/>
                    <path d="M17 11c0 2.76-2.24 5-5 5s-5-2.24-5-5H5c0 3.53 2.61 6.43 6 6.92V21h2v-3.08c3.39-.49 6-3.39 6-6.92h-2z"/>
                  </svg>
                </button>
              </div>
              <!-- 语音状态提示 -->
              <div v-if="cwVoiceState === 'recording'" class="voice-status recording-status">
                <span class="voice-pulse"></span>
                正在聆听...松手自动发送
              </div>
              <div v-else-if="cwVoiceState === 'listening'" class="voice-status listening-status">
                <span class="voice-spinner"></span>
                AI 正在识别语音...
              </div>
            </div>
          </div>
        </div>
      </aside>

      <!-- 分隔条 -->
      <div class="divider-bar" aria-hidden="true">
        <div class="divider-dots"></div>
      </div>

      <!-- 右栏：编辑器 + 控制台 -->
      <main class="editor-panel">
        <div class="editor-toolbar">
          <div class="editor-tabs">
            <div class="editor-tab active">
              <span class="file-icon">📄</span>
              <span class="file-name">main.c</span>
              <span class="tab-close">×</span>
            </div>
          </div>
          <div class="toolbar-spacer"></div>
          <div class="toolbar-info">
            <span class="info-dot info-lang">C</span>
            <span class="info-text">C99 · GCC</span>
          </div>
        </div>

        <div class="editor-wrapper">
          <MonacoEditor v-model="code" language="c" theme="vs-dark" :readonly="false" :font-size="14" />
        </div>

        <div class="console-area">
          <div class="console-header">
            <div class="console-tabs">
              <button :class="['console-tab', { active: consoleTab === 'output' }]" @click="consoleTab = 'output'">
                <span class="ctab-icon">▶</span>
                运行日志
              </button>
              <button :class="['console-tab', { active: consoleTab === 'judge' }]" @click="consoleTab = 'judge'">
                <span class="ctab-icon">✓</span>
                判题结果
                <span v-if="judgeResult" class="ctab-count">{{ judgeResult.passCount }}/{{ judgeResult.totalCount }}</span>
              </button>
            </div>
            <div class="console-actions" v-if="runResult || judgeResult">
              <button class="clear-btn" @click="runResult = null; judgeResult = null;">🗑 清空</button>
            </div>
          </div>
          <div class="console-content">
            <div v-if="consoleTab === 'output'" class="console-output">
              <div v-if="running" class="console-running">
                <span class="spinner"></span>
                <span class="running-text">代码正在编译运行中...</span>
                <span class="running-hint">通常耗时 1-3 秒</span>
              </div>
              <div v-else-if="runResult" class="run-result">
                <div class="run-status-card" :class="{ ok: runResult.compileOk, fail: !runResult.compileOk, timeout: runResult.timedOut }">
                  <div class="run-status-icon">
                    <span v-if="runResult.timedOut">⏱️</span>
                    <span v-else-if="!runResult.compileOk">❌</span>
                    <span v-else>✅</span>
                  </div>
                  <div class="run-status-info">
                    <div class="run-status-title" v-if="runResult.timedOut">运行超时</div>
                    <div class="run-status-title" v-else-if="!runResult.compileOk">编译失败</div>
                    <div class="run-status-title" v-else>运行成功</div>
                    <div class="run-status-sub">
                      <span v-if="!runResult.timedOut">耗时 <strong>{{ runResult.durationMs }}ms</strong></span>
                      <span v-if="runResult.compileOk && !runResult.timedOut"> · 退出码 <strong>{{ runResult.exitCode }}</strong></span>
                    </div>
                  </div>
                </div>
                <div v-if="runResult.compileError && !runResult.compileOk" class="console-section">
                  <div class="cs-label"><span class="cs-dot fail"></span>编译错误 compile error</div>
                  <pre class="compile-error">{{ runResult.compileError }}</pre>
                </div>
                <div v-if="runResult.stdout" class="console-section">
                  <div class="cs-label"><span class="cs-dot ok"></span>标准输出 stdout</div>
                  <pre class="stdout-block">{{ runResult.stdout }}</pre>
                </div>
                <div v-if="runResult.stderr" class="console-section">
                  <div class="cs-label"><span class="cs-dot warn"></span>标准错误 stderr</div>
                  <pre class="stderr-block">{{ runResult.stderr }}</pre>
                </div>
                <div v-if="!runResult.stdout && !runResult.stderr && runResult.compileOk && !runResult.timedOut" class="empty-output">
                  <span class="empty-icon">📭</span>程序没有输出任何内容
                </div>
              </div>
              <div v-else class="console-empty">
                <span class="empty-icon-big">▶️</span>
                <p>点击下方「🔧 调试」按钮编译运行</p>
                <span>输出结果将在此处显示</span>
              </div>
            </div>

            <div v-if="consoleTab === 'judge'" class="judge-detail">
              <div v-if="!judgeResult" class="console-empty">
                <span class="empty-icon-big">📝</span>
                <p>点击下方「🚀 提交检测」按钮判题</p>
                <span>自动对比所有样例的期望输出与实际输出</span>
              </div>
              <template v-else>
                <div class="judge-summary" :class="{ pass: judgeResult.pass, fail: !judgeResult.pass }">
                  <div class="js-score">
                    <template v-if="!judgeResult.isSelfCheck">
                      <span class="js-num">{{ judgeResult.passCount }}</span>
                      <span class="js-sep">/</span>
                      <span class="js-total">{{ judgeResult.totalCount }}</span>
                    </template>
                    <template v-else>
                      <span class="js-num" style="font-size:22px; letter-spacing:0;">{{ judgeResult.pass ? '✓' : '✗' }}</span>
                    </template>
                  </div>
                  <div class="js-text">
                    <div class="js-title">{{ judgeResult.isSelfCheck ? (judgeResult.pass ? '编译 &amp; 运行自检通过' : '自检未通过') : (judgeResult.pass ? '全部通过！' : '有样例未通过') }}</div>
                    <div class="js-sub">{{ judgeResult.isSelfCheck ? (judgeResult.pass ? '代码语法正确，可正常运行 🚀' : '查看下方详情，修复编译错误或运行问题 🛠') : (judgeResult.pass ? '很棒，继续下一题吧 🎉' : '查看下方差异分析，再试一次 💪') }}</div>
                  </div>
                  <div class="js-progress">
                    <div class="js-progress-fill" :style="{ width: (judgeResult.isSelfCheck ? (judgeResult.pass ? 100 : 5) : (judgeResult.passCount / judgeResult.totalCount * 100)) + '%' }"></div>
                  </div>
                </div>
                <div v-for="(r, idx) in judgeResult.results" :key="idx" class="judge-case" :class="{ pass: r.pass, fail: !r.pass }">
                  <div class="case-header">
                    <span class="case-icon">{{ r.pass ? '✅' : '❌' }}</span>
                    <span class="case-title">{{ judgeResult.isSelfCheck ? '编译 &amp; 运行自检' : ('样例 ' + (idx + 1)) }}</span>
                    <span class="case-status">{{ r.pass ? (judgeResult.isSelfCheck ? '通过' : '通过 Accept') : (judgeResult.isSelfCheck ? '未通过' : '未通过 Wrong Answer') }}</span>
                  </div>
                  <div v-if="!r.pass" class="case-diff">
                    <div class="diff-side">
                      <div class="diff-label">期望输出 Expected</div>
                      <pre class="diff-expected">{{ r.expected }}</pre>
                    </div>
                    <div class="diff-arrow">→</div>
                    <div class="diff-side">
                      <div class="diff-label">实际输出 Actual</div>
                      <pre class="diff-actual mismatch">{{ r.actual }}</pre>
                    </div>
                  </div>
                </div>
              </template>
            </div>
          </div>
        </div>

        <div class="action-bar">
          <div class="action-hint" v-if="parsed.samples.length > 0">
            <span class="hint-icon">💡</span>
            共 <strong>{{ parsed.samples.length }}</strong> 组样例，调试只跑第1组，提交检测全量判题
          </div>
          <div class="action-hint" v-else>
            <span class="hint-icon">⚠️</span>
            本题暂无样例，提交检测将执行一次编译 &amp; 运行自检
          </div>
          <div class="action-spacer"></div>
          <button class="action-btn btn-reset" @click="resetCode" :disabled="running">
            重置代码
          </button>
          <button class="action-btn btn-debug" @click="runDebug" :disabled="running || gccUnavailable">
            <span v-if="running" class="btn-spinner"></span>
            <template v-else>调试</template>
          </button>
          <button class="action-btn btn-submit" @click="runJudge" :disabled="running || gccUnavailable">
            提交检测
          </button>
        </div>
      </main>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch, nextTick, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import api, { resourceApi } from '../../api'
import MonacoEditor from '../../components/MonacoEditor.vue'
import { parseCodeResourceContent, judgeOutput } from '../../utils/problemParser'
import { marked } from 'marked'
import DOMPurify from 'dompurify'
import { speakWithTts, stopTts, isTtsPlaying } from '../../utils/tts'

const route = useRoute()
const router = useRouter()

/* ============ 基础状态 ============ */
const resource = ref({})
const code = ref('')
const originalCode = ref('')
const parsed = ref({ fallback: true, samples: [], description: '', rawMarkdown: '' })
const activeTab = ref('question') // question | solution | tutor
const consoleTab = ref('output')
const running = ref(false)
const runResult = ref(null)
const judgeResult = ref(null)
const gccUnavailable = ref(false)

/* ============ 题解状态 ============ */
const solution = ref({
  summary: '',
  thought: '',
  complexity: { time: '', space: '', level: 'medium' },
  pitfalls: [],
  referenceCode: '',
  expansion: '',
  tags: [],
  updatedAt: '',
  error: ''
})
const solutionLoading = ref(false)
const solutionStep = ref(0)
const solutionEnhancing = ref(false)
const openTagInput = ref(false)
const newTagName = ref('')
const tagInputRef = ref(null)
const solutionLoaded = ref(false)
const TAG_COLORS = ['#6366f1','#8b5cf6','#ec4899','#ef4444','#f59e0b','#10b981','#06b6d4','#3b82f6','#14b8a6','#f97316','#84cc16','#a855f7']
let slTimer = null
/* ============ 题解 Tab v2 新增状态 / 计算 / 目录 ============ */
const solutionScroll = ref(null)
const dotsNav = ref(null)
const activeDot = ref(0)
const solutionDots = [
  { id: 'sol-0', title: '思路点拨', short: '思路' },
  { id: 'sol-1', title: '参考代码', short: '代码' }
]
/* 健康度评分：仅基于思路点拨 + 参考代码 2 大板块 综合估算 */
const solutionHealth = computed(() => {
  const s = solution.value
  let score = 0
  if (s.thought && s.thought.length > 30) score += 45
  if (s.referenceCode && s.referenceCode.length > 30) {
    score += 45
    const lines = s.referenceCode.split('\n').length
    if (lines >= 15 && lines <= 80) score += 10
  }
  return Math.min(100, score)
})
/* 2 项细粒度指标 */
const solutionMetrics = computed(() => {
  const s = solution.value
  return [
    { name: '思路完整度', value: s.thought && s.thought.length > 50 ? Math.min(100, 55 + s.thought.length / 10) : 25, color: '#6366f1' },
    { name: '代码质量', value: s.referenceCode && s.referenceCode.length > 40 ? Math.min(98, 50 + s.referenceCode.split('\n').length * 2) : 20, color: '#10b981' }
  ]
})

/* ============ AI 学习助手状态 ============ */
const quickQuestions = [
  { id: 1, text: '这道题的思路是什么？先给我点线索' },
  { id: 2, text: '我的代码卡在某个样例，帮我定位问题' },
  { id: 3, text: '这道题涉及哪些 C 语言知识点？' }
]
const tutorMessages = ref([])
const tutorInput = ref('')
const tutorLoading = ref(false)
const chatBox = ref(null)
let evtSource = null

/* ---- RAG 知识库检索结果折叠栏 ---- */
const ragMeta = ref(null)
const ragExpanded = ref(false)
const ragResults = computed(() => ragMeta.value?.fragments || [])
const avgScore = computed(() => {
  if (ragMeta.value?.avgSimilarity != null) return ragMeta.value.avgSimilarity
  if (!ragResults.value.length) return 0
  const sum = ragResults.value.reduce((acc, c) => acc + (c.score || 0), 0)
  return Math.round(sum / ragResults.value.length * 1000) / 10
})
const reliabilityPercent = computed(() => {
  if (ragMeta.value?.reliability != null) return ragMeta.value.reliability
  return Number(avgScore.value)
})
const hallucinationPercent = computed(() => {
  if (ragMeta.value?.hallucinationRate != null) return ragMeta.value.hallucinationRate
  return 0
})
const hallucinationLevelClass = computed(() => {
  const h = Number(hallucinationPercent.value)
  if (h <= 10) return 'level-low'
  if (h <= 30) return 'level-medium'
  return 'level-high'
})
// 幻觉率状态文字（与后端 30% 阈值对齐）
const hallucinationStatusText = computed(() => {
  const h = Number(hallucinationPercent.value)
  if (h <= 10) return '低风险'
  if (h <= 30) return '中风险'
  return '已超标'
})
const credibilityHint = computed(() => {
  const h = Number(hallucinationPercent.value)
  if (h <= 10) return '回答可靠，可放心参考'
  if (h <= 30) return '回答基本可靠，建议核对关键信息'
  return '回答可能存在不准确之处，请谨慎参考'
})
const ragRegenerating = ref(false)   // 正在重生成（动画态）
const ragRegenerated = ref(false)    // 本次回答已触发过自动重生成（历史态）
function chunkScoreClass(score) {
  if (score >= 0.9) return 'score-high'
  if (score >= 0.7) return 'score-medium'
  return 'score-low'
}
/* ---- AIGuide 迁移：打字机、TTS、语音输入、消息编辑、RAG ---- */
const typingMap = ref({})      // key:msg.id => 正在打字中
const typewriterTimers = {}    // key:msg.id => timer handle

/* ---- TTS 朗读：使用 speakWithTts（三级降级：VoiceClone → MiMo TTS → 浏览器） ---- */
const ttsReadingId = ref(null)
const readingMap = ref({})  // key: msgId => true/false
function _ttsSpeak(msgId, content) {
  // 停止之前的朗读
  stopRead()
  // 去除 Markdown 标记
  const cleanText = content
    .replace(/```[\s\S]*?```/g, '（代码块）')
    .replace(/`([^`]+)`/g, '$1')
    .replace(/!\[([^\]]*)\]\([^)]+\)/g, '$1')
    .replace(/\[([^\]]*)\]\([^)]+\)/g, '$1')
    .replace(/^#{1,6}\s+/gm, '')
    .replace(/[*_~]{1,3}([^*_~]+)[*_~]{1,3}/g, '$1')
    .replace(/^>\s+/gm, '')
    .replace(/^[\s]*[-*+]\s+/gm, '')
    .replace(/^[\s]*\d+\.\s+/gm, '')
    .replace(/\s+/g, ' ')
    .trim()
  if (!cleanText) return
  ttsReadingId.value = msgId
  readingMap.value[msgId] = true
  speakWithTts(cleanText, {
    onStart: () => {},
    onEnd: () => {
      readingMap.value[msgId] = false
      if (ttsReadingId.value === msgId) ttsReadingId.value = null
    },
    onError: () => {
      readingMap.value[msgId] = false
      if (ttsReadingId.value === msgId) ttsReadingId.value = null
    }
  })
}
function stopRead() {
  stopTts()
  if (ttsReadingId.value) {
    readingMap.value[ttsReadingId.value] = false
  }
  ttsReadingId.value = null
}

// 兼容旧接口
function startRead(m) {
  _ttsSpeak(m.id, m.content)
}
function toggleRead(m) {
  if (readingMap.value[m.id]) {
    stopRead()
  } else {
    _ttsSpeak(m.id, m.content)
  }
}
const useVoiceInput = ref(true)  // 始终启用语音输入（讯飞 ASR）
const recognizing = ref(false)
let recog = null

// ===== 讯飞 ASR 语音识别（按住说话模式） =====
const cwVoiceState = ref('idle') // idle, recording, listening
let cwAudioContext = null
let cwMediaStreamSource = null
let cwScriptProcessor = null
let cwMediaStream = null
let cwPcmChunks = []
let cwIsStopping = false

// Float32 → Int16（16bit PCM）
function cwFloat32ToInt16(float32) {
  const int16 = new Int16Array(float32.length)
  for (let i = 0; i < float32.length; i++) {
    const s = Math.max(-1, Math.min(1, float32[i]))
    int16[i] = s < 0 ? s * 0x8000 : s * 0x7FFF
  }
  return int16
}

// 线性降采样到 16kHz
function cwDownsampleTo16k(buffer, fromRate) {
  if (fromRate === 16000) return buffer
  const ratio = fromRate / 16000
  const newLength = Math.round(buffer.length / ratio)
  const result = new Float32Array(newLength)
  let offsetResult = 0, offsetBuffer = 0
  while (offsetResult < newLength) {
    const nextOffsetBuffer = Math.round((offsetResult + 1) * ratio)
    let accum = 0, count = 0
    for (let i = offsetBuffer; i < nextOffsetBuffer && i < buffer.length; i++) { accum += buffer[i]; count++ }
    result[offsetResult] = count > 0 ? accum / count : 0
    offsetResult++; offsetBuffer = nextOffsetBuffer
  }
  return result
}

async function cwStartRecording() {
  if (cwVoiceState.value !== 'idle') return
  try {
    cwMediaStream = await navigator.mediaDevices.getUserMedia({
      audio: { channelCount: 1, sampleRate: 16000, echoCancellation: true, noiseSuppression: true }
    })
    cwAudioContext = new (window.AudioContext || window.webkitAudioContext)()
    const sourceRate = cwAudioContext.sampleRate
    cwMediaStreamSource = cwAudioContext.createMediaStreamSource(cwMediaStream)
    const workletCode = `
      class PCMCollectorProcessor extends AudioWorkletProcessor {
        process(inputs) {
          const input = inputs[0]
          if (input && input[0]) {
            const chunk = new Float32Array(input[0])
            this.port.postMessage(chunk, [chunk.buffer])
          }
          return true
        }
      }
      registerProcessor('pcm-collector-processor', PCMCollectorProcessor)
    `
    const workletUrl = URL.createObjectURL(new Blob([workletCode], { type: 'application/javascript' }))
    await cwAudioContext.audioWorklet.addModule(workletUrl)
    URL.revokeObjectURL(workletUrl)
    cwScriptProcessor = new AudioWorkletNode(cwAudioContext, 'pcm-collector-processor', {
      numberOfInputs: 1, numberOfOutputs: 1, outputChannelCount: [1]
    })
    cwPcmChunks = []
    cwScriptProcessor.port.onmessage = (e) => {
      const resampled = cwDownsampleTo16k(e.data, sourceRate)
      cwPcmChunks.push(cwFloat32ToInt16(resampled))
    }
    const zeroGain = cwAudioContext.createGain()
    zeroGain.gain.value = 0
    cwMediaStreamSource.connect(cwScriptProcessor)
    cwScriptProcessor.connect(zeroGain)
    zeroGain.connect(cwAudioContext.destination)
    cwVoiceState.value = 'recording'
    document.addEventListener('mouseup', cwStopRecording, { once: true })
  } catch (e) {
    console.warn('麦克风访问失败:', e)
    ElMessage.warning('无法访问麦克风，请检查浏览器权限设置')
  }
}

function cwStopRecording() {
  if (cwIsStopping || cwVoiceState.value !== 'recording') return
  cwIsStopping = true
  cwVoiceState.value = 'idle'
  document.removeEventListener('mouseup', cwStopRecording)
  if (cwMediaStream) { cwMediaStream.getTracks().forEach(t => t.stop()); cwMediaStream = null }
  if (cwScriptProcessor) { cwScriptProcessor.disconnect(); cwScriptProcessor = null }
  if (cwMediaStreamSource) { cwMediaStreamSource.disconnect(); cwMediaStreamSource = null }
  const ctx = cwAudioContext; cwAudioContext = null
  if (ctx) { setTimeout(() => ctx.close().catch(() => {}), 200) }
  if (cwPcmChunks.length === 0) { cwIsStopping = false; return }
  cwVoiceState.value = 'listening'
  try {
    const totalLength = cwPcmChunks.reduce((sum, c) => sum + c.length, 0)
    const pcmData = new Int16Array(totalLength)
    let off = 0
    for (const c of cwPcmChunks) { pcmData.set(c, off); off += c.length }
    cwPcmChunks = []
    const bytes = new Uint8Array(pcmData.buffer)
    let binary = ''
    for (let i = 0; i < bytes.length; i += 0x8000) {
      binary += String.fromCharCode.apply(null, bytes.subarray(i, i + 0x8000))
    }
    const base64 = btoa(binary)
    api.post('/voice/recognize', { audio: base64 }).then(res => {
      const text = res?.data?.text || ''
      cwIsStopping = false
      cwVoiceState.value = 'idle'
      if (!text) { ElMessage.info('没有听清，请重试'); return }
      tutorInput.value = text
      nextTick(() => sendTutor())
    }).catch(e => {
      console.warn('语音识别失败:', e)
      cwIsStopping = false
      cwVoiceState.value = 'idle'
      ElMessage.warning('语音识别失败，请重试')
    })
  } catch (e) {
    console.warn('语音识别失败:', e)
    cwIsStopping = false
    cwVoiceState.value = 'idle'
    ElMessage.warning('语音识别失败，请重试')
  }
}
const editId = ref(null)
const editContentMap = ref({})

/* RAG 侧边栏（对应原 showNote） */
const showNote = ref(false)
const ragTab = ref('problem') // problem | code | result | samples
const ragText = ref('')
const ragBox = ref(null)

/* ============ 工具函数 ============ */
function renderMarkdown(md) {
  if (!md) return ''
  try { return DOMPurify.sanitize(marked(md)) } catch { return md }
}
function renderMarkdownInline(md) {
  const html = renderMarkdown(md)
  return html.replace(/^<p>/, '').replace(/<\/p>$/, '')
}
function goBack() {
  try {
    // 如果有历史记录，返回上一页；否则回退到学习中心列表
    if (window.history.length > 2) { router.back() }
    else {
      const name = resource.value?.knowledgePoint || ''
      router.push({ path: '/student/learn', query: name ? { name } : {} })
    }
  } catch (e) {
    router.push('/student/learn')
  }
}
function resetCode() {
  code.value = originalCode.value
  runResult.value = null
  judgeResult.value = null
  ElMessage.info('已重置为初始代码')
}
async function copyToClipboard(text) {
  try { await navigator.clipboard.writeText(text); ElMessage.success('已复制') }
  catch { ElMessage.warning('复制失败') }
}
function scrollChatToBottom() {
  nextTick(() => { if (chatBox.value) chatBox.value.scrollTop = chatBox.value.scrollHeight })
}

/* ============ 标准 C 语言初始模板（4 空格缩进） ============ */
const STANDARD_C_TEMPLATE = `#include <stdio.h>
#include <stdlib.h>

int main(int argc, char *argv[])
{
    // 请在此输入您的代码
    return 0;
}`

/* ---------- 编辑器清空自动回填模板 ----------
 * 当用户把编辑器内容全删为空时，自动填回标准模板。
 * 使用 _fillingTemplate 作轻量防护，避免在 nextTick 回填过程中因 watch 重入
 * 触发重复逻辑；事实上模板 .trim() 后非空，第二层本身也会 early return。
 */
let _fillingTemplate = false
watch(code, (newVal) => {
  if (_fillingTemplate) return
  if (newVal && newVal.trim()) return
  _fillingTemplate = true
  nextTick(() => {
    code.value = STANDARD_C_TEMPLATE
    nextTick(() => { _fillingTemplate = false })
  })
})

/* ============ 资源加载 ============ */
async function loadResource() {
  const resourceId = route.params.resourceId

  // 编程题形式参考：从 sessionStorage 加载章节练习页传入的参考题内容
  if (resourceId === 'practice-ref' || route.query.from === 'chapter-practice-ref') {
    try {
      const raw = sessionStorage.getItem('cryd_practice_ref_content')
      if (raw) {
        const ref = JSON.parse(raw)
        resource.value = {
          title: ref.title || '编程题形式参考',
          content: ref.content || '',
          knowledgePoint: ref.knowledgePoint || '',
          difficulty: ref.difficulty || 'medium'
        }
        parsed.value = parseCodeResourceContent(ref.content || '')
        code.value = parsed.value.codeSnippet?.trim() || STANDARD_C_TEMPLATE
        originalCode.value = code.value
        // 用后即清，避免下次进入仍残留
        sessionStorage.removeItem('cryd_practice_ref_content')
        return
      }
    } catch (e) { console.warn('加载编程题参考失败:', e) }
  }

  const isDemo = localStorage.getItem('cryd_demo_mode') === 'true'
  // 优先从 query 读取 ResourceDetail 跳转传入的元信息
  const qTitle = route.query.title
  const qKp = route.query.knowledgePoint
  const qDiff = route.query.difficulty

  // ---------- 默认资源兜底（与 ResourceDetail.loadDefaultResource 对齐） ----------
  const loadDefaultsBySoftId = (softId) => {
    const defaultMap = {
      default_1: { title: '控制结构与流程控制', type: 'video', content: '', kp: '控制结构' },
      default_2: { title: '数组与指针的关系详解', type: 'article', content: '', kp: '数组' },
      default_3: {
        title: '指针与内存地址', type: 'code', kp: '指针',
        content: `## 问题描述\n\n请编写一个 C 程序，演示指针的基本用法：声明一个整型变量，声明一个指向它的指针，通过指针修改变量的值，并打印修改前后的值，帮助理解「指针保存的是地址」与「解引用修改原变量」这两个核心概念。\n\n## 输入格式\n\n无输入，程序内置整型初始值 42，通过指针修改为 99，直接运行即可。\n\n## 输出格式\n\n依次输出以下 3 行：\n\n1. 初始值：通过 \`*ptr\` 读取 num 的值（修改前）\n2. 修改后值：通过指针修改 num = 99 后，直接输出 num\n3. num 的地址：使用 %p 打印 &num（指针保存的地址与其一致）\n\n即：\n\`\`\`plaintext\n修改前：num = 42\n修改后：num = 99\nnum 的地址 = 0x...\n\`\`\`\n\n## 样例输入 1\n\n\`\`\`plaintext\n（无输入）\n\`\`\`\n\n## 样例输出 1\n\n\`\`\`plaintext\n修改前：num = 42\n修改后：num = 99\nnum 的地址 = 0x7ffd...\n\`\`\`\n\n## 代码解析\n\n本程序覆盖指针入门最关键的 3 个知识点：\n\n1. **取址运算符 \`&\`**：\`int *ptr = &num;\` 把变量 num 的**内存起始地址**赋值给指针变量 ptr，此时称「ptr 指向 num」。\n2. **解引用运算符 \`*\`**：\`*ptr\` 读作「访问 ptr 指向的内存」，既可以读（\`printf(\"%d\", *ptr)\`）也可以写（\`*ptr = 99;\`）。写操作会直接修改 num 本身。\n3. **指针与原变量的等价性**：修改后直接 \`printf(\"%d\", num)\` 会打印 99，证明 *ptr = 99 确实修改了 num 的原始内存，而不是修改一个副本。\n\n\`\`\`c\n#include <stdio.h>\n\nint main() {\n    int num = 42;\n    int *ptr = &num;\n    printf(\"修改前：num = %d\\n\", *ptr);\n    *ptr = 99;\n    printf(\"修改后：num = %d\\n\", num);\n    printf(\"num 的地址 = %p\\n\", (void*)&num);\n    return 0;\n}\n\`\`\`\n\n## 改进建议\n\n- \`%p\` 标准要求传 \`void*\`，代码中显式 \`(void*)&num\` 强转，保证跨编译器可移植。\n- 永远不要解引用**未初始化的指针**：\`int *q; *q = 10;\` 是典型的野指针写入，会导致段错误或静默数据污染。\n- 需要在函数内部修改实参时，必须传指针（地址传递），不要期望值传递能修改调用者的变量。`
      },
      default_4: { title: '结构体知识图谱', type: 'mindmap', content: '', kp: '结构体' },
      default_5: {
        title: '动态内存分配与管理', type: 'code', kp: '内存管理',
        content: `## 问题描述\n\n演示堆内存分配 \`malloc\` 与释放 \`free\` 的完整安全流程：申请长度为 5 的 int 数组、检查返回值（判 NULL）、写入 1~5、遍历打印、最后释放内存并将指针置 NULL，帮助建立「申请→判空→使用→释放→置空」的安全习惯。\n\n## 输入格式\n\n无输入，程序内置数组长度 n = 5，填充顺序为 arr[i] = i+1，直接运行即可。\n\n## 输出格式\n\n按顺序输出 5 个元素：\n\n\`\`\`plaintext\n1 2 3 4 5 \n\`\`\`\n\n（末尾空格后换行）。若内存不足，仅输出 1 行「内存分配失败」并以退出码 1 结束。\n\n## 样例输入 1\n\n\`\`\`plaintext\n（无输入）\n\`\`\`\n\n## 样例输出 1\n\n\`\`\`plaintext\n1 2 3 4 5 \n\`\`\`\n\n## 代码解析\n\n动态内存的「安全四步曲」：\n\n1. **malloc 分配**：\`malloc(5 * sizeof(int))\` 按实际字节数申请。建议永远写 \`sizeof(基类型)\` 而非硬编码 4，保证跨平台（不同机器 int 大小可能为 4 或 8）。\n2. **NULL 判空**：内存不足或超出进程限制时 malloc 会返回 NULL，不判空直接 \`arr[i] = x\` 会触发段错误，代码中 \`if (arr == NULL) return 1;\` 就是典型防御性写法。\n3. **使用与遍历**：\`arr[i]\` 下标访问等价于 \`*(arr+i)\`，malloc 地址会对齐到任意类型的对齐要求，因此可直接用于任何类型。\n4. **free 与置空**：每个 malloc 必须配套一个 free，否则内存泄漏；free 后指针仍指向旧地址（悬空指针），最佳实践是立即 \`arr = NULL\`，避免二次 free 或误访问。\n\n\`\`\`c\n#include <stdio.h>\n#include <stdlib.h>\n\nint main() {\n    int n = 5;\n    int *arr = (int *)malloc(n * sizeof(int));\n    if (arr == NULL) {\n        printf(\"内存分配失败\\n\");\n        return 1;\n    }\n    for (int i = 0; i < n; i++) {\n        arr[i] = i + 1;\n    }\n    for (int i = 0; i < n; i++) {\n        printf(\"%d \", arr[i]);\n    }\n    printf(\"\\n\");\n    free(arr);\n    arr = NULL;\n    return 0;\n}\n\`\`\`\n\n## 改进建议\n\n- 分配失败时推荐用 \`perror(\"malloc failed\")\` 或 \`strerror(errno)\` 打印具体错误码，便于定位原因（需额外 include <errno.h> 与 <string.h>）。\n- 需要批量清零的场景可改用 \`calloc(n, sizeof(int))\`，它会自动将分配的内存全部置 0，避免读到未初始化的垃圾值。\n- 长生命周期程序建议使用 AddressSanitizer（gcc/clang 的 \`-fsanitize=address\`）检测泄漏、越界、use-after-free 等动态内存问题。`
      },
      default_6: { title: 'C语言学习总结', type: 'summary', content: '', kp: 'C语言' },
    }
    const d = defaultMap[softId]
    if (!d) return null
    return {
      id: softId,
      title: d.title,
      type: d.type,
      knowledgePoint: qKp || d.kp,
      difficulty: qDiff || '基础',
      content: d.content || '',
    }
  }

  if (isDemo || resourceId === 'demo') {
    try {
      const { demoResources } = await import('../../mock/demoData')
      // 1) 先按 ID 精确查找（用户从 ResourceDetail 点击跳转时传入的真实/软ID）
      let codeRes = (demoResources || []).find(r => String(r.id) === String(resourceId))
      // 2) 再尝试软ID默认映射（default_1 .. default_6）
      if (!codeRes && String(resourceId).startsWith('default_')) {
        codeRes = loadDefaultsBySoftId(resourceId)
      }
      // 3) 最后兜底：任意一条代码资源
      if (!codeRes) {
        codeRes = (demoResources || []).find(r => r.type === 'code')
      }
      if (codeRes) {
        resource.value = {
          ...codeRes,
          // 用跳转传入的 query 覆盖展示信息，保证界面一致性
          title: qTitle || codeRes.title || '编程练习',
          knowledgePoint: qKp || codeRes.knowledgePoint || codeRes.chapter || 'C语言',
          difficulty: qDiff || codeRes.difficulty || '基础',
        }
        parsed.value = parseCodeResourceContent(resource.value.content || '')
        // 初始不直接把参考代码塞进编辑器——给学生空模板让其自己写；
        // 参考代码会在题解 Tab 通过 solution.referenceCode 展示并可"应用到编辑器"
        code.value = STANDARD_C_TEMPLATE
        originalCode.value = STANDARD_C_TEMPLATE
        return
      }
    } catch (e) { console.warn('加载演示资源失败:', e) }
  }

  // 非数字软ID：演示模式默认映射已尝试过，非演示模式走默认资源
  if (resourceId && !/^\d+$/.test(String(resourceId))) {
    const fallback = loadDefaultsBySoftId(resourceId)
    if (fallback) {
      resource.value = {
        ...fallback,
        title: qTitle || fallback.title,
        knowledgePoint: qKp || fallback.knowledgePoint,
        difficulty: qDiff || fallback.difficulty,
      }
      parsed.value = parseCodeResourceContent(fallback.content || '')
      code.value = STANDARD_C_TEMPLATE
      originalCode.value = STANDARD_C_TEMPLATE
      return
    }
  }

  if (!resourceId || resourceId === 'demo' || !/^\d+$/.test(String(resourceId))) {
    // 所有匹配都失败：给一个空题 + 标准模板，避免页面白屏
    resource.value = {
      id: resourceId || 'empty',
      title: qTitle || '编程练习',
      type: 'code',
      knowledgePoint: qKp || 'C语言',
      difficulty: qDiff || '基础',
      content: '',
    }
    parsed.value = parseCodeResourceContent('')
    code.value = STANDARD_C_TEMPLATE
    originalCode.value = STANDARD_C_TEMPLATE
    return
  }

  try {
    const res = await resourceApi.getById(resourceId)
    if (res.data) {
      resource.value = {
        ...res.data,
        title: qTitle || res.data.title,
        knowledgePoint: qKp || (res.data.knowledgePoint || res.data.chapter),
        difficulty: qDiff || res.data.difficulty,
      }
      parsed.value = parseCodeResourceContent(res.data.content || '')
      code.value = STANDARD_C_TEMPLATE
      originalCode.value = STANDARD_C_TEMPLATE
    }
  } catch (e) { console.error('加载资源失败:', e); ElMessage.error('加载资源失败') }
}

/* ============ 调试 / 判题 ============ */

// 演示模式本地模拟运行：后端 /api/code/run 需登录，demo_token 无效会返回 403
// 用样例期望输出模拟运行结果，保证演示流程不中断（不影响真实模式）
function demoRunCode(code, expectedOutput) {
  const openBraces = (code.match(/{/g) || []).length
  const closeBraces = (code.match(/}/g) || []).length
  const hasMain = /int\s+main\s*\(/.test(code)
  if (!hasMain || openBraces !== closeBraces) {
    return { ok: false, compileOk: false, compileError: '演示模式：代码缺少 main 函数或大括号不匹配', exitCode: -1, stdout: '', stderr: '', durationMs: 0, timedOut: false }
  }
  return { ok: true, compileOk: true, compileError: '', exitCode: 0, stdout: expectedOutput || '', stderr: '', durationMs: Math.floor(Math.random() * 80) + 10, timedOut: false }
}

async function runDebug() {
  if (!code.value.trim()) { ElMessage.warning('代码不能为空'); return }
  running.value = true; runResult.value = null; consoleTab.value = 'output'
  try {
    const isDemo = localStorage.getItem('cryd_demo_mode') === 'true'
    const sampleInput = parsed.value.samples[0]?.input || ''
    const sampleOutput = parsed.value.samples[0]?.output || ''
    let res
    if (isDemo) {
      // 演示模式：后端 /api/code/run 需登录，demo_token 被拒（403），改用本地模拟
      await new Promise(r => setTimeout(r, 400))
      res = { code: 200, data: demoRunCode(code.value, sampleOutput) }
    } else {
      res = await api.post('/code/run', { code: code.value, stdin: sampleInput, timeoutSeconds: 5 })
    }
    if (res.code === 200 && res.data) {
      runResult.value = res.data
      if (res.data.compileError && res.data.compileError.includes('gcc not available')) { gccUnavailable.value = true; ElMessage.warning('服务器未安装 gcc') }
    } else ElMessage.error(res.message || '运行失败')
  } catch (e) { ElMessage.error('运行异常: ' + (e.message || '')) }
  finally { running.value = false }
}
async function runJudge() {
  if (!code.value.trim()) { ElMessage.warning('代码不能为空'); return }
  running.value = true; judgeResult.value = null; consoleTab.value = 'judge'
  try {
    const isDemo = localStorage.getItem('cryd_demo_mode') === 'true'
    const sampleCount = parsed.value.samples.length

    // -------- 无样例：执行一次编译 + 运行自检（空 stdin） --------
    if (sampleCount === 0) {
      let res
      if (isDemo) {
        await new Promise(r => setTimeout(r, 350))
        res = { code: 200, data: demoRunCode(code.value, '') }
      } else {
        res = await api.post('/code/run', { code: code.value, stdin: '', timeoutSeconds: 5 })
      }
      const selfResults = []
      if (res.code === 200 && res.data) {
        const d = res.data
        // 自检通过条件：无编译错误 + 未超时 + 退出码为 0（演示模式下 ok=true 直接用）
        const ok = isDemo
          ? !!d.ok
          : (!d.compileError && !d.timedOut && (d.exitCode === 0 || d.exitCode == null))
        let actualText = ''
        if (d.compileError) {
          actualText = '【编译错误】\n' + d.compileError + (d.stderr ? '\n---\n' + d.stderr : '')
        } else if (d.timedOut) {
          actualText = '【运行超时】\n' + (d.stdout || '(无输出)') + (d.stderr ? '\n--- stderr ---\n' + d.stderr : '')
        } else {
          actualText = (d.stdout || '(无输出)') + (d.stderr ? '\n--- stderr ---\n' + d.stderr : '')
        }
        selfResults.push({
          pass: ok,
          expected: '(自检模式不校验输出内容)',
          actual: actualText,
          diffLines: [],
          isSelfCheck: true,
        })
        judgeResult.value = {
          pass: ok, passCount: ok ? 1 : 0, totalCount: 1,
          failedIndex: ok ? -1 : 0, results: selfResults, isSelfCheck: true,
        }
        if (ok) ElMessage.success('自检通过：代码可编译并正常运行 ✓')
        else if (d.compileError) ElMessage.warning('编译未通过，请查看详情')
        else ElMessage.warning('自检未通过，请查看详情')
      } else {
        selfResults.push({
          pass: false,
          expected: '-',
          actual: '(运行失败: ' + (res.message || '未知错误') + ')',
          diffLines: [],
          isSelfCheck: true,
        })
        judgeResult.value = {
          pass: false, passCount: 0, totalCount: 1, failedIndex: 0,
          results: selfResults, isSelfCheck: true,
        }
        ElMessage.error('自检失败')
      }
      return
    }

    // -------- 有样例：正常全量判题 --------
    const results = []
    let passCount = 0, failedIndex = -1
    for (let i = 0; i < sampleCount; i++) {
      const sample = parsed.value.samples[i]
      let res
      if (isDemo) {
        await new Promise(r => setTimeout(r, 300))
        res = { code: 200, data: demoRunCode(code.value, sample.output || '') }
      } else {
        res = await api.post('/code/run', { code: code.value, stdin: sample.input || '', timeoutSeconds: 5 })
      }
      if (res.code === 200 && res.data) {
        const actual = (res.data.stdout || '').trim()
        const expected = (sample.output || '').trim()
        const judge = judgeOutput(expected, actual)
        results.push({ pass: judge.pass, expected: sample.output || '', actual: res.data.stdout || '(无输出)', diffLines: judge.diffLines })
        if (judge.pass) passCount++; else if (failedIndex < 0) failedIndex = i
      } else {
        results.push({ pass: false, expected: sample.output || '', actual: '(运行失败)', diffLines: [] })
        if (failedIndex < 0) failedIndex = i
      }
    }
    const allPass = passCount === sampleCount
    judgeResult.value = { pass: allPass, passCount, totalCount: sampleCount, failedIndex, results, isSelfCheck: false }
    ElMessage[allPass ? 'success' : 'error'](allPass ? '所有样例通过！' : '有样例未通过')
  } catch (e) { ElMessage.error('判题异常: ' + (e.message || '')) }
  finally { running.value = false }
}

/* ============ Tab2 题解：生成/加载 + 标签 ============ */
async function ensureSolutionLoaded() {
  if (solutionLoaded.value || solutionLoading.value) return
  await generateSolution()
}
async function generateSolution() {
  solution.value.error = ''
  solutionStep.value = 0

  // 先用解析器里已有的 explanation / improvement / codeSnippet 快速填充
  const p = parsed.value
  // 演示模式下若题目无解析文本，生成基于知识点的引导式分析（AI 不可用时的兜底）
  const isDemoMode = localStorage.getItem('cryd_demo_mode') === 'true'
  const demoThought = (isDemoMode && !(p.thought || p.explanation))
    ? `本题考察 **${resource.value.knowledgePoint || resource.value.title || 'C 语言'}** 相关知识。\n\n**解题思路：**\n1. 通读题目，明确输入输出格式与数据范围\n2. 设计算法逻辑，注意边界条件与特殊情况处理\n3. 参考下方代码实现，关注关键语法、函数用法与内存管理\n4. 可点击「应用到编辑器」将参考代码载入后运行调试\n\n> 💡 建议：先尝试自己编写，遇到困难再参考代码思路。`
    : ''
  solution.value = {
    summary: '',
    thought: p.thought || p.explanation || demoThought,
    complexity: { time: '-', space: '-', level: resource.value.difficulty || 'medium' },
    pitfalls: [],
    referenceCode: p.codeSnippet || '',
    expansion: p.improvement || '',
    tags: resource.value.tags ? resource.value.tags.map((n, i) => ({ name: n, color: TAG_COLORS[i % TAG_COLORS.length] })) : [],
    updatedAt: new Date().toLocaleString(),
    error: ''
  }

  // 有基础数据则立即展示，仅显示"AI增强中"指示器；无基础数据才显示全屏 loading
  const hasBasic = !!(p.thought || p.codeSnippet || p.improvement)
  if (hasBasic) {
    solutionLoading.value = false
    solutionEnhancing.value = true
  } else {
    solutionLoading.value = true
    slTimer = setInterval(() => { if (solutionStep.value < 2) solutionStep.value++ }, 700)
  }

  // 后台调用 AI 生成更丰富的题解（使用 /chat/send，与问卷导学一致）
  try {
    const prompt = buildSolutionPrompt()
    const res = await api.post('/chat/send', { studentId: _getStudentId(), message: prompt, mode: 'solution' })
    const reply = res?.data?.message || res?.data?.data?.message || ''
    if (reply) tryParseSolutionFromAI(reply)
  } catch (e) {
    // 降级不报错，用已有的基础题解
  } finally {
    if (slTimer) { clearInterval(slTimer); slTimer = null }
    solutionStep.value = 2
    solutionEnhancing.value = false
    setTimeout(() => { solutionLoading.value = false; solutionLoaded.value = true }, 300)
  }
}
function _getStudentId() {
  try { return JSON.parse(localStorage.getItem('cryd_user') || '{}').id || 1 } catch { return 1 }
}
function buildSolutionPrompt() {
  const p = parsed.value
  return `请为下面这道 C 语言编程题写高质量的结构化题解，用 Markdown 组织，**严格**按以下 6 个## 标题输出：
## 题目大意
（用更清晰、更简单的语言重述题目，3-6 句）

## 思路点拨
（分 1~4 步写清楚解题思路，引导思考，不要直接甩公式）

## 复杂度分析
- 时间复杂度：大 O 表示法 + 为什么
- 空间复杂度：大 O 表示法 + 为什么
- 难度级别：easy / medium / hard 三选一

## 坑点 & 易错点
（3~5 个，按重要性排序，每点一句话）

## 参考代码
用 \`\`\`c ... \`\`\` 代码块输出完整可编译的 main.c 代码

## 延伸与举一反三
（2~4 句：这个解法还能怎么优化？能迁移到什么类似题？）

---
【原题信息】
标题：${resource.value.title || ''}
问题描述：${p.description || p.rawMarkdown?.substring(0, 600) || ''}
样例数量：${p.samples.length}
${p.samples[0] ? '样例1输入:' + p.samples[0].input + '，样例1输出:' + p.samples[0].output : ''}`
}
function tryParseSolutionFromAI(md) {
  if (!md) return
  const g = (rx) => { const m = md.match(rx); return m ? m[1].trim() : '' }
  const s1 = g(/## 题目大意\n([\s\S]*?)(?=\n## 思路点拨|$)/)
  const s2 = g(/## 思路点拨\n([\s\S]*?)(?=\n## 复杂度分析|$)/)
  const s3 = g(/## 复杂度分析\n([\s\S]*?)(?=\n## 坑点|$)/)
  const s4 = g(/## 坑点 & 易错点\n([\s\S]*?)(?=\n## 参考代码|$)/)
  const s5Raw = g(/## 参考代码\n([\s\S]*?)(?=\n## 延伸|$)/)
  const s5 = (s5Raw.match(/```c\s*([\s\S]*?)\s*```/) || [])[1]?.trim() || s5Raw.trim()
  const s6 = g(/## 延伸与举一反三\n([\s\S]*?)$/)

  if (s1) solution.value.summary = s1
  if (s2) solution.value.thought = s2
  if (s3) {
    const t = s3.match(/时间复杂度[：: ]*([^\n]+)/)
    const sp = s3.match(/空间复杂度[：: ]*([^\n]+)/)
    const lv = s3.match(/难度级别[：: ]*(\w+)/)
    if (t) solution.value.complexity.time = t[1].trim()
    if (sp) solution.value.complexity.space = sp[1].trim()
    if (lv && ['easy','medium','hard'].includes(lv[1].trim())) solution.value.complexity.level = lv[1].trim()
  }
  if (s4) {
    const items = s4.split(/\n\s*\d+\.|\n[-*] /).map(x => x.trim()).filter(Boolean)
    if (items.length) solution.value.pitfalls = items
  }
  if (s5) solution.value.referenceCode = s5
  if (s6) solution.value.expansion = s6
  solution.value.updatedAt = new Date().toLocaleString()
}
function addTag() {
  const name = newTagName.value.trim()
  if (!name) return
  const exists = solution.value.tags.some(t => t.name === name)
  if (exists) { ElMessage.warning('标签已存在'); return }
  const color = TAG_COLORS[solution.value.tags.length % TAG_COLORS.length]
  solution.value.tags.push({ name, color })
  newTagName.value = ''
  openTagInput.value = false
  ElMessage.success(`标签「${name}」已添加`)
}
function removeTag(idx) {
  const t = solution.value.tags[idx]
  solution.value.tags.splice(idx, 1)
  if (t) ElMessage.info(`已移除标签「${t.name}」`)
}

/* ============ Tab2 题解 v2：滚动导航 / 复制 / 导出 / 应用代码 ============ */
function scrollSolutionToSection(idx) {
  if (!solutionScroll.value) return
  const target = solutionScroll.value.querySelector(`#${solutionDots[idx].id}`)
  if (target) target.scrollIntoView({ behavior: 'smooth', block: 'start' })
  activeDot.value = idx
}
function onSolutionScroll() {
  if (!solutionScroll.value) return
  const box = solutionScroll.value
  for (let i = solutionDots.length - 1; i >= 0; i--) {
    const el = box.querySelector(`#${solutionDots[i].id}`)
    if (el && el.offsetTop - box.offsetTop - 120 <= box.scrollTop) { activeDot.value = i; break }
  }
}
function applyReferenceCode() {
  if (!solution.value.referenceCode) { ElMessage.warning('暂无参考代码'); return }
  code.value = solution.value.referenceCode
  runResult.value = null
  judgeResult.value = null
  ElMessage.success('已应用参考代码到编辑器')
}

/* ============ Tab3 AI 学习助手：完整迁移 AIGuide ============ */
function sendQuick(text) {
  tutorInput.value = text
  sendTutor()
}
function clearTutorChat() {
  typewriterStopAll()
  stopRead()
  tutorMessages.value = []
  ElMessage.info('对话已清空')
}
function exportTutorChat() {
  if (!tutorMessages.value.length) { ElMessage.warning('暂无可导出的对话'); return }
  const md = tutorMessages.value.map(m => `### ${m.role === 'user' ? '我' : 'AI导师'}\n\n${m.content}\n`).join('\n---\n\n')
  const blob = new Blob([md], { type: 'text/markdown' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url; a.download = `导师对话-${(resource.value.title||'题').slice(0,12)}-${Date.now()}.md`
  document.body.appendChild(a); a.click(); document.body.removeChild(a); URL.revokeObjectURL(url)
  ElMessage.success('已导出 Markdown 文件')
}
async function sendTutor() {
  const text = tutorInput.value.trim()
  if (!text) return
  if (tutorLoading.value) return
  // 发送前确保题解已加载（未加载则后台触发）
  if (!solutionLoaded.value && !solutionLoading.value && !solutionEnhancing.value) {
    ensureSolutionLoaded()
  }
  // 发送新消息时停止上一条朗读
  stopRead()
  tutorMessages.value.push({ id: _genId(), role: 'user', content: text, createdAt: Date.now() })
  tutorInput.value = ''
  tutorLoading.value = true
  ragMeta.value = null
  ragExpanded.value = false
  ragRegenerated.value = false
  scrollChatToBottom()

  const context = buildTutorContext()
  try {
    const res = await api.post('/chat/send', {
      studentId: _getStudentId(),
      message: text,
      mode: 'auto',
      extraContext: context,
      systemPrompt: `你是一位C语言编程导师，正在辅导学生完成一道编程题。你已掌握当前题目的完整题意、官方题解和学生当前代码。

【回答策略——根据学生问题类型自适应选择，苏格拉底式问答仅在适合时使用】
1. 学生问"怎么做/思路/不会/卡住了" → 苏格拉底式引导：给出2-3个思考方向，引导学生自己推导，不直接给完整代码或答案
2. 学生问"什么是/为什么/原理/区别" → 直接完整解释概念，可附简短示例
3. 学生报告编译错误/运行错误/答案不对 → 直接帮助定位问题，给出具体修改建议
4. 学生问语法/用法/格式 → 直接给出说明和示例
5. 学生分享代码请求点评 → 直接点评，指出问题并给改进建议
6. 日常问候/闲聊 → 友好简短回应，自然引导回学习

【通用要求】
- 回答简洁，控制在200字以内
- 用Markdown格式排版
- 可引用当前题目的具体内容（样例、约束等）来回答
- 不要每次回复都用苏格拉底式，只在学生寻求思路引导时使用`,
      history: tutorMessages.value.slice(-10)
    })
    const reply = res?.data?.message || res?.data?.data?.message || ''
    if (!reply) throw new Error('AI 返回空内容')
    // 打字机效果逐字显示
    const aiMsg = { id: _genId(), role: 'assistant', content: '', createdAt: Date.now() }
    tutorMessages.value.push(aiMsg)
    let idx = 0
    const typeTimer = setInterval(() => {
      if (idx < reply.length) {
        const chunk = reply.substring(idx, idx + 4)
        idx += 4
        aiMsg.content = reply.substring(0, idx)
        scrollChatToBottom()
      } else {
        aiMsg.content = reply
        clearInterval(typeTimer)
        tutorLoading.value = false
        scrollChatToBottom()
      }
    }, 16)
  } catch (e) {
    console.warn('AI对话失败:', e)
    // 降级：直接调 demoStore 或本地的提示
    const demo = _mockSocraticReply(text)
    const fallbackMsg = { id: _genId(), role: 'assistant', content: demo, createdAt: Date.now() }
    tutorMessages.value.push(fallbackMsg)
    tutorLoading.value = false
    scrollChatToBottom()
  }
}
function buildTutorContext() {
  const p = parsed.value, jr = judgeResult.value, rr = runResult.value, s = solution.value
  const ctx = []
  ctx.push(`【当前题目】标题:${resource.value.title || '未命名'}  知识点:${resource.value.knowledgePoint || '未标注'}  难度:${resource.value.difficulty || 'medium'}`)
  ctx.push(`【题目描述】${p.description || p.rawMarkdown?.substring(0, 1500) || '（暂无题目描述）'}`)
  if (p.inputFormat) ctx.push(`【输入格式】${p.inputFormat}`)
  if (p.outputFormat) ctx.push(`【输出格式】${p.outputFormat}`)
  if (p.constraints) ctx.push(`【评测用例规模与约定】${p.constraints}`)
  if (p.samples.length) {
    p.samples.forEach((sm, i) => {
      ctx.push(`【样例 ${i + 1} 输入】${sm.input || '(空)'}`)
      ctx.push(`【样例 ${i + 1} 输出】${sm.output || '(空)'}`)
    })
  }
  ctx.push(`——— 官方题解 ———`)
  ctx.push(`【题解-题目大意】${s.summary || '（暂未生成，请稍等片刻再问，题解正在后台生成中）'}`)
  ctx.push(`【题解-思路点拨】${s.thought || '（暂未生成，请稍等片刻再问，题解正在后台生成中）'}`)
  if (s.complexity?.time || s.complexity?.space || s.complexity?.level) {
    ctx.push(`【题解-复杂度/难度】时间复杂度:${s.complexity.time || '-'}  空间复杂度:${s.complexity.space || '-'}  难度评估:${s.complexity.level || '-'}`)
  }
  if (s.pitfalls?.length) ctx.push(`【题解-坑点/易错点】${s.pitfalls.map((p, i) => `${i + 1}.${p}`).join('  ')}`)
  if (s.referenceCode) ctx.push(`【题解-参考代码（C99）】\n${s.referenceCode.slice(0, 800)}${s.referenceCode.length > 800 ? '\n（代码过长，已截断）' : ''}`)
  if (s.expansion) ctx.push(`【题解-延伸拓展/举一反三】${s.expansion}`)
  ctx.push(`——— 学生现场状态 ———`)
  if (rr) ctx.push(`【最近一次运行结果】exit=${rr.exitCode} stdout=${(rr.stdout||'').slice(0,200)} error=${(rr.compileError||rr.stderr||'无').slice(0,200)}`)
  if (jr) {
    ctx.push(`【最近一次判题结果】通过=${jr.passCount}/${jr.totalCount}`)
    if (jr.failedIndex >= 0) {
      const fr = jr.results[jr.failedIndex]
      ctx.push(`【判题-失败样例#${jr.failedIndex + 1}】期望=${(fr.expected||'').slice(0,120)}  实际=${(fr.actual||'').slice(0,120)}`)
    }
  }
  ctx.push(`【学生当前代码（完整前800字符）】${code.value.slice(0, 800)}${code.value.length > 800 ? '\n（代码过长，已截断）' : ''}`)
  if (showNote.value && ragText.value) ctx.push(`【学生正在笔记中写的草稿】${ragText.value.slice(0, 300)}`)
  return ctx.filter(Boolean).join('\n')
}
function _mockSocraticReply(question) {
  // 离线 / 接口失败时的备用回复，根据问题类型自适应（苏格拉底式仅用于思路求助）
  const q = question.toLowerCase()

  // ---- 直接答疑类：报错/调试/语法 ----
  if (q.includes('报错') || q.includes('编译错误') || q.includes('编译失败')) {
    return `编译错误通常是语法问题，常见原因：\n\n1. **缺少分号** — 每条语句末尾记得加 \`;\`\n2. **括号不匹配** — 检查 \`{}\` 和 \`()\` 是否成对\n3. **未声明变量** — 使用前先 \`int\`/\`char\` 等声明\n4. **头文件缺失** — \`#include <stdio.h>\` 等是否包含\n\n把具体的编译错误信息贴给我，我帮你精确定位 🔍`
  }
  if (q.includes('运行错误') || q.includes('答案不对') || q.includes('wa') || q.includes('为什么错')) {
    return `答案不对时，先排查这几个方向：\n\n1. **输出格式** — 多了空格/换行？少了换行？\n2. **边界值** — n=0？n=1？最大值溢出？\n3. **循环边界** — \`<\` 写成了 \`<=\`？\n4. **数据类型** — \`int\` 溢出？该用 \`long long\`？\n\n把你的代码和错误样例贴给我，我帮你逐行排查 🐞`
  }
  if (q.includes('怎么用') || q.includes('语法') || q.includes('格式') || q.includes('printf') || q.includes('scanf')) {
    return `这是 C 语言基础语法问题，直接说明：\n\n- \`printf(\"格式串\", 变量)\` — 输出，如 \`printf(\"%d\\n\", x)\`\n- \`scanf(\"格式串\", &变量)\` — 输入，如 \`scanf(\"%d\", &n)\`\n- 常用格式符：\`%d\`整数、\`%f\`浮点、\`%c\`字符、\`%s\`字符串\n\n有具体场景的话告诉我，我给完整示例 📝`
  }

  // ---- 苏格拉底式引导类：思路求助 ----
  if (q.includes('思路') || q.includes('怎么做') || q.includes('不会做') || q.includes('没思路')) {
    return `好问题！我先不告诉你怎么做，先一步步思考：\n\n1. **输入是什么？输出又需要什么格式？** 先试着用笔 + 纸，把样例 1 自己跑一遍。\n2. 你觉得这题主要考哪 **2-3 个 C 语言知识点**？先列出来。\n3. 如果让你用**最朴素的方法**（不考虑优化）先拿到部分分，你会先写个什么版本的循环？\n\n等你回答这三个小问题，我再继续给你点拨`
  }
  if (q.includes('卡住') || q.includes('卡住了') || q.includes('不知道怎么开始')) {
    return `没关系，我们从最基础的开始：\n\n**如果不考虑性能，只追求"做对"**，你能用哪 3 步描述这道题的完整流程？例如：\n ① 先读入 n 个数字  ② 对每个数字做某事  ③ 按序打印结果\n\n你先写一个"最暴力但正确"的版本，我们再在它基础上做优化`
  }
  if (q.includes('复杂度') || q.includes('优化')) {
    return `很好！你已经在思考"更优解"了。先回答我：\n\n1. 你现在写的版本，**最坏情况**做了几次循环？大概是 O(n?)？\n2. 有重复计算的子问题吗？如果有，能不能 **用变量存起来避免重复算**？\n3. 能不能先**排个序**，或者用个数组"计数"替代嵌套循环？\n\n你先评估你现在版本的时间复杂度，然后我们一起降它`
  }

  // ---- 默认：友好回应 ----
  return `我收到了你的问题～由于AI服务暂时不可用，这里是通用建议：\n\n- 如果是**思路问题**：先手算样例1，把每一步写下来\n- 如果是**代码问题**：贴出代码和报错信息，我帮你看\n- 如果是**概念问题**：告诉我具体是哪个知识点不懂\n\n稍后AI服务恢复后会给你更详细的回答 💪`
}

/* ============ AIGuide 迁移：打字机 / TTS / 语音 / 消息操作 / RAG ============ */
function _genId() { return 'm_' + Date.now() + '_' + Math.floor(Math.random() * 1000) }
function _stripMd(text) { return (text || '').replace(/```[\s\S]*?```/g, '(代码块)').replace(/[#>*_`~\-]/g, '').replace(/\s+/g, ' ').trim() }
function typewriterStart(msg, finalText) {
  typingMap.value[msg.id] = true
  msg.content = ''
  let i = 0
  typewriterTimers[msg.id] = setInterval(() => {
    const chunk = Math.max(1, Math.min(5, (finalText.length - i) / 15 | 0 || 2))
    msg.content = finalText.slice(0, Math.min(finalText.length, i + chunk))
    i += chunk
    if (i >= finalText.length) { msg.content = finalText; typewriterStop(msg.id) }
    scrollChatToBottom()
  }, 18)
}
function typewriterStop(id) {
  typingMap.value[id] = false
  if (typewriterTimers[id]) { clearInterval(typewriterTimers[id]); delete typewriterTimers[id] }
}
function typewriterStopAll() { Object.keys(typewriterTimers).forEach(k => typewriterStop(k)) }
function copyMsg(m) { copyToClipboard(m.content) }
function editMsg(m) { editId.value = m.id; editContentMap.value[m.id] = m.content; nextTick(() => { const ta = document.querySelector(`.edit-ta-${m.id}`); if (ta) { ta.focus(); ta.setSelectionRange(ta.value.length, ta.value.length) } }) }
function cancelEdit(m) { editId.value = null; delete editContentMap.value[m.id] }
function saveEdit(m) { const n = (editContentMap.value[m.id] || '').trim(); if (!n) { ElMessage.warning('内容不能为空'); return } m.content = n; m.edited = true; m.updatedAt = Date.now(); editId.value = null; delete editContentMap.value[m.id]; ElMessage.success('已更新') }
function deleteMsg(idx) { const m = tutorMessages.value[idx]; if (!m) return; typewriterStop(m.id); if (readingMap.value[m.id]) stopRead(); tutorMessages.value.splice(idx, 1); ElMessage.info('已删除') }
function regenerateMsg(idx) {
  const msgs = tutorMessages.value; let q = ''
  for (let i = idx - 1; i >= 0; i--) { if (msgs[i].role === 'user') { q = msgs[i].content; break } }
  typewriterStop(msgs[idx].id); if (readingMap.value[msgs[idx].id]) stopRead(); msgs.splice(idx, 1)
  if (q) { tutorInput.value = q; sendTutor(true) } else ElMessage.warning('无法找到上下文')
}
function addRAG() { const t = ragText.value.trim(); if (!t) return; ragText.value = ''; ElMessage.success('已追加到上下文'); tutorMessages.value.push({ id: _genId(), role: 'system', content: `📝 学生追加的笔记：\n\n> ${t}`, createdAt: Date.now(), _isNote: true }); scrollChatToBottom() }
function askWithRAG() { const t = ragText.value.trim(); if (!t) { ElMessage.warning('请先写点内容'); return } tutorInput.value = t; ragText.value = ''; sendTutor() }
function focusRAGInput() { nextTick(() => { const ta = document.querySelector('.rag-input'); if (ta) ta.focus() }) }

/* ============ 快捷键绑定 ============ */
function _handleKey(e) {
  const ctrl = e.ctrlKey || e.metaKey
  if (!ctrl) return
  if (e.key === '1') { e.preventDefault(); activeTab.value = 'question' }
  else if (e.key === '2') { e.preventDefault(); activeTab.value = 'solution'; ensureSolutionLoaded() }
  else if (e.key === '3') { e.preventDefault(); activeTab.value = 'tutor' }
  else if (e.key === 'Enter') { e.preventDefault(); runDebug() }
  else if (e.key === 's' || e.key === 'S') { e.preventDefault(); runJudge() }
}
onMounted(() => {
  loadResource().then(() => {
    // 预加载题解：资源加载完成后立即后台生成，用户切到题解Tab时已完成
    ensureSolutionLoaded()
  })
  window.addEventListener('keydown', _handleKey)
  // 切换 tab 时，如果是题解/AI 助手的滚动条，下次打开保持
  watch(activeTab, (t) => {
    if (t === 'solution' && openTagInput.value) nextTick(() => tagInputRef.value?.focus())
    if (t === 'tutor') {
      // 切到 AI 学习助手时，确保题解已预加载（后台生成，不阻塞）
      if (!solutionLoaded.value && !solutionLoading.value && !solutionEnhancing.value) {
        ensureSolutionLoaded()
      }
      scrollChatToBottom()
    }
  })
})
onBeforeUnmount(() => {
  window.removeEventListener('keydown', _handleKey)
  if (evtSource) { try { evtSource.close() } catch {} }
  if (slTimer) clearInterval(slTimer)
  // 清理打字机
  typewriterStopAll()
  // 清理 TTS
  stopRead()
  // 清理讯飞 ASR 语音录音资源
  document.removeEventListener('mouseup', cwStopRecording)
  if (cwScriptProcessor) { try { cwScriptProcessor.disconnect() } catch {} ; cwScriptProcessor = null }
  if (cwMediaStreamSource) { try { cwMediaStreamSource.disconnect() } catch {} ; cwMediaStreamSource = null }
  if (cwMediaStream) { cwMediaStream.getTracks().forEach(t => t.stop()); cwMediaStream = null }
  if (cwAudioContext) { try { cwAudioContext.close() } catch {} ; cwAudioContext = null }
})
</script>

<style scoped>
/* ========== 全局变量 ========== */
.code-workbench {
  --brand: #6366f1;
  --brand-2: #8b5cf6;
  --ok: #10b981;
  --warn: #f59e0b;
  --fail: #ef4444;
  --bg-panel: #f7f8fc;
  --bg-card: #ffffff;
  --bg-editor: #1e1e2e;
  --bg-toolbar: #181825;
  --bg-console: #11111b;
  --text-primary: #1e293b;
  --text-secondary: #64748b;
  --text-dark: #cdd6f4;
  --text-dark-sec: #6c7086;
  --border: #e2e8f0;
  --border-dark: #313244;
  --shadow-sm: 0 1px 2px rgba(0,0,0,0.05);
  --shadow-md: 0 4px 16px rgba(0,0,0,0.08);
  --shadow-lg: 0 10px 40px rgba(99,102,241,0.15);
  display: flex;
  flex-direction: column;
  height: 100vh;
  background:
    radial-gradient(1200px 600px at 10% -10%, rgba(99,102,241,0.12), transparent 60%),
    radial-gradient(800px 500px at 100% 0%, rgba(139,92,246,0.10), transparent 60%),
    #f0f1f5;
  overflow: hidden;
  color: var(--text-primary);
}

/* ========== 顶栏：渐变毛玻璃 ========== */
.workbench-header {
  display: flex;
  align-items: flex-start;
  gap: 18px;
  padding: 14px 28px;
  background: linear-gradient(135deg, rgba(30,41,59,0.96) 0%, rgba(49,46,129,0.95) 50%, rgba(76,29,149,0.95) 100%);
  backdrop-filter: blur(20px) saturate(180%);
  -webkit-backdrop-filter: blur(20px) saturate(180%);
  border-bottom: 1px solid rgba(255,255,255,0.08);
  box-shadow: 0 4px 20px rgba(0,0,0,0.25);
  flex-shrink: 0;
  position: relative;
  z-index: 10;
}
.workbench-header::after {
  content: '';
  position: absolute; left: 0; right: 0; bottom: 0; height: 1px;
  background: linear-gradient(90deg, transparent, rgba(255,255,255,0.25), transparent);
}
.back-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 9px 18px;
  background: rgba(255,255,255,0.08);
  color: #e2e8f0;
  border: 1px solid rgba(255,255,255,0.12);
  border-radius: 10px;
  cursor: pointer;
  font-size: 14px;
  font-weight: 500;
  transition: all 0.25s cubic-bezier(0.4,0,0.2,1);
}
.back-btn:hover { background: rgba(255,255,255,0.16); border-color: rgba(255,255,255,0.22); transform: translateX(-2px); box-shadow: 0 4px 12px rgba(0,0,0,0.2); }

/* gcc 警告 */
.gcc-warning {
  display: flex; align-items: center; justify-content: center; gap: 8px;
  padding: 10px 24px;
  background: linear-gradient(90deg, rgba(245,158,11,0.10), rgba(245,158,11,0.20), rgba(245,158,11,0.10));
  color: #92400e; font-size: 13px; font-weight: 500;
  border-bottom: 1px solid rgba(245,158,11,0.30);
  flex-shrink: 0;
}
.warn-icon { font-size: 15px; }

/* 判题状态条：渐变高亮 */
.judge-bar {
  display: flex; align-items: center; justify-content: center; gap: 10px;
  padding: 12px 24px; font-size: 14px; font-weight: 600;
  flex-shrink: 0; animation: slideDown 0.4s cubic-bezier(0.4,0,0.2,1);
}
@keyframes slideDown { from { transform: translateY(-100%); opacity: 0; } to { transform: translateY(0); opacity: 1; } }
.judge-bar strong { font-size: 16px; font-weight: 800; }
.judge-icon { font-size: 20px; filter: drop-shadow(0 2px 6px rgba(0,0,0,0.15)); }
.judge-bar.pass {
  background: linear-gradient(90deg, rgba(16,185,129,0.15), rgba(16,185,129,0.28), rgba(16,185,129,0.15));
  color: #065f46; border-bottom: 2px solid rgba(16,185,129,0.5);
}
.judge-bar.fail {
  background: linear-gradient(90deg, rgba(239,68,68,0.15), rgba(239,68,68,0.28), rgba(239,68,68,0.15));
  color: #991b1b; border-bottom: 2px solid rgba(239,68,68,0.5);
}

/* ========== 主体 ========== */
.workbench-body { display: flex; flex: 1; overflow: hidden; padding: 14px; gap: 14px; }

/* ========== 左栏：蓝桥杯极简高对比度 + 3 Tab ========== */
.problem-panel {
  width: 45%;
  min-width: 380px;
  background: #ffffff;
  border-radius: 16px;
  overflow: hidden;
  display: flex;
  box-shadow: 0 2px 12px rgba(0,0,0,0.06), 0 0 0 1px rgba(0,0,0,0.04);
  flex-shrink: 0;
}
.side-nav {
  width: 58px;
  background: linear-gradient(180deg, #fafbfc 0%, #f1f5f9 100%);
  border-right: 1px solid #ebeef5;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 16px 0;
  gap: 8px;
  flex-shrink: 0;
}
.nav-item {
  width: 42px;
  height: 42px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: #8a94a6;
  border-radius: 11px;
  cursor: pointer;
  transition: all 0.18s ease;
  position: relative;
}
.nav-item:hover { background: #eef1f6; color: #1f2937; }
.nav-item.active {
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  color: #fff;
  box-shadow: 0 5px 14px rgba(99,102,241,0.4), inset 0 1px 0 rgba(255,255,255,0.2);
}
.nav-item.active::before {
  content: '';
  position: absolute;
  left: -16px; top: 50%; transform: translateY(-50%);
  width: 4px; height: 26px; border-radius: 0 4px 4px 0;
  background: linear-gradient(180deg, #6366f1, #8b5cf6);
}
.problem-main { flex: 1; display: flex; flex-direction: column; min-width: 0; }
.tab-pane { display: flex; flex-direction: column; height: 100%; overflow: hidden; }

/* 题目滚动 + 题解滚动 公用 */
.problem-scroll {
  flex: 1; overflow-y: auto; padding: 24px 30px 18px; scroll-behavior: smooth;
}
.problem-scroll::-webkit-scrollbar { width: 8px; }
.problem-scroll::-webkit-scrollbar-track { background: transparent; }
.problem-scroll::-webkit-scrollbar-thumb { background: rgba(180,188,200,0.45); border-radius: 4px; }
.problem-scroll::-webkit-scrollbar-thumb:hover { background: rgba(150,160,180,0.7); }

.problem-scroll > section { margin-bottom: 24px; }
.section-h {
  font-size: 16px; font-weight: 700; color: #1f2937;
  margin: 0 0 12px; padding-bottom: 8px;
  border-bottom: 1.5px solid #e5e7eb;
  letter-spacing: 0.2px;
}
.section-content {
  font-size: 14.5px; line-height: 1.9; color: #2c3446;
}
.section-content :deep(p) { margin: 10px 0; }
.section-content :deep(ul), .section-content :deep(ol) { padding-left: 24px; margin: 10px 0; }
.section-content :deep(li) { margin: 5px 0; }
.section-content :deep(code) {
  background: #f5f7fa; padding: 2px 8px; border-radius: 5px;
  font-family: 'JetBrains Mono', Consolas, monospace;
  font-size: 13px; color: #d63384; border: 1px solid #e9ecef; font-weight: 500;
}
.section-content :deep(pre) {
  background: #ffffff !important;
  border: 1px solid #e5e7eb !important;
  border-radius: 8px !important;
  padding: 14px 18px !important;
  margin: 10px 0 !important;
  overflow-x: auto !important;
  box-shadow: 0 1px 3px rgba(0,0,0,0.03) !important;
}
.section-content :deep(pre code) {
  background: transparent !important; border: none !important; padding: 0 !important;
  color: #1f2937 !important; font-size: 13.5px !important; line-height: 1.75 !important; font-weight: 500 !important;
}

/* 样例：蓝桥杯内嵌深灰框 */
.sample-block {
  position: relative;
  background: #1e2328;
  border-radius: 8px;
  padding: 14px 16px;
  margin: 4px 0 0;
  border: 1px solid #2c3238;
  box-shadow: inset 0 1px 0 rgba(255,255,255,0.03);
}
.sample-block.sample-out { background: #1e2328; border-color: #2c3238; }
.sample-code {
  color: #eef0f3;
  font-family: 'JetBrains Mono', Consolas, monospace;
  font-size: 13.5px; line-height: 1.75; margin: 0;
  white-space: pre-wrap; word-break: break-all;
  min-height: 22px; max-height: 260px; overflow-y: auto;
  font-weight: 500; letter-spacing: 0.1px;
}
.sample-code::-webkit-scrollbar { width: 6px; }
.sample-code::-webkit-scrollbar-thumb { background: rgba(120,130,150,0.35); border-radius: 3px; }
.copy-btn {
  position: absolute; top: 9px; right: 10px;
  padding: 4px 12px;
  background: rgba(255,255,255,0.06);
  color: #c3cbd8;
  border: 1px solid rgba(255,255,255,0.08);
  border-radius: 5px;
  font-size: 11.5px; font-weight: 500; cursor: pointer; transition: all 0.2s ease;
}
.copy-btn:hover { background: rgba(255,255,255,0.14); color: #fff; border-color: rgba(255,255,255,0.2); }

/* 分析 Tab */
.analysis-card {
  background: #fff; border: 1px solid #e5e7eb; border-radius: 12px;
  overflow: hidden; margin-top: 4px; box-shadow: 0 1px 4px rgba(0,0,0,0.03);
}
.analysis-tabbar { display: flex; background: #fafbfc; border-bottom: 1px solid #e5e7eb; padding: 0 8px; }
.tab-btn {
  padding: 11px 20px; background: none; border: none;
  color: #6b7280; font-size: 13px; font-weight: 600;
  cursor: pointer; position: relative; transition: all 0.15s;
}
.tab-btn:hover { color: #1f2937; }
.tab-btn.active { color: #2b85e4; background: #fff; }
.tab-btn.active::after {
  content: ''; position: absolute; left: 18px; right: 18px; bottom: -1px;
  height: 2px; background: #2b85e4; border-radius: 1px 1px 0 0;
}
.tab-content {
  padding: 16px 20px; font-size: 14.5px; line-height: 1.9; color: #2c3446;
}
.tab-content :deep(code) {
  background: #f5f7fa; padding: 2px 8px; border-radius: 5px;
  font-family: 'JetBrains Mono', monospace; font-size: 13px;
  color: #d63384; border: 1px solid #e9ecef;
}
.tab-content :deep(pre) {
  background: #ffffff !important;
  border: 1px solid #e5e7eb !important;
  border-radius: 8px !important;
  padding: 14px 18px !important;
  margin: 10px 0 !important;
  overflow-x: auto !important;
  box-shadow: 0 1px 3px rgba(0,0,0,0.03) !important;
}
.tab-content :deep(pre code) {
  background: transparent !important; border: none !important; padding: 0 !important;
  color: #1f2937 !important; font-size: 13.5px !important; line-height: 1.75 !important; font-weight: 500 !important;
}

.fallback-md :deep(pre) {
  background: #1e2328; color: #eef0f3; padding: 16px;
  border-radius: 8px; overflow-x: auto; border: 1px solid #2c3238;
}
.fallback-md :deep(code) { font-family: 'JetBrains Mono', monospace; font-size: 13.5px; }

/* ============ Tab2: 题解（体检） ============ */
.solution-hero {
  background: linear-gradient(135deg, rgba(99,102,241,0.08) 0%, rgba(139,92,246,0.08) 50%, rgba(59,130,246,0.06) 100%);
  border: 1px solid rgba(99,102,241,0.15);
  border-radius: 14px;
  padding: 18px 20px;
  margin-bottom: 22px;
  position: relative;
  overflow: hidden;
}
.solution-hero::before {
  content: '';
  position: absolute;
  top: -60%; right: -15%;
  width: 260px; height: 260px;
  background: radial-gradient(circle, rgba(139,92,246,0.35), transparent 70%);
  border-radius: 50%;
  opacity: 0.3;
  pointer-events: none;
}
.hero-row {
  display: flex; align-items: center; justify-content: space-between; margin-bottom: 14px;
  position: relative; z-index: 1;
}
.hero-badge {
  display: inline-flex; align-items: center; gap: 8px;
  padding: 6px 14px;
  background: #fff;
  border: 1px solid rgba(99,102,241,0.2);
  border-radius: 999px;
  box-shadow: 0 2px 8px rgba(99,102,241,0.08);
}
.hb-icon { font-size: 15px; }
.hb-text {
  font-size: 13px; font-weight: 700;
  background: linear-gradient(90deg, #6366f1, #8b5cf6);
  -webkit-background-clip: text; background-clip: text; -webkit-text-fill-color: transparent;
}
.hero-updated {
  font-size: 11.5px; color: #6b7280;
  background: rgba(255,255,255,0.6);
  padding: 3px 10px; border-radius: 6px;
}
.hero-tags {
  display: flex; flex-wrap: wrap; align-items: center;
  gap: 8px; position: relative; z-index: 1;
}
.tag-chip {
  display: inline-flex; align-items: center;
  padding: 4px 12px;
  border: 1px solid;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
  font-family: 'JetBrains Mono', monospace;
  transition: all 0.2s;
  cursor: pointer;
}
.tag-chip:hover { transform: translateY(-1px); filter: brightness(1.1); box-shadow: 0 2px 8px currentColor; }
.tag-add-btn {
  width: 26px; height: 26px;
  display: inline-flex; align-items: center; justify-content: center;
  background: #fff; border: 1px dashed #cbd5e1;
  color: #64748b; border-radius: 50%;
  font-size: 16px; cursor: pointer; font-weight: 700;
  transition: all 0.2s;
}
.tag-add-btn:hover { border-color: #6366f1; color: #6366f1; background: #eef2ff; }
.tag-add-btn.big { width: auto; height: auto; padding: 6px 16px; border-radius: 999px; font-size: 12.5px; gap: 4px; }
.tag-input {
  border: 1px solid #c7d2fe;
  background: #fff;
  padding: 5px 12px;
  border-radius: 8px;
  font-size: 12.5px;
  outline: none;
  width: 240px;
  color: #1f2937;
  box-shadow: 0 0 0 3px rgba(99,102,241,0.1);
}
.tag-input:focus { border-color: #6366f1; box-shadow: 0 0 0 4px rgba(99,102,241,0.15); }
.hero-empty-tags { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; position: relative; z-index: 1; }

.solution-loading {
  display: flex;
  gap: 18px;
  padding: 26px 22px;
  background: linear-gradient(135deg, #f8fafc, #eef2ff);
  border: 1px solid #e0e7ff;
  border-radius: 14px;
  margin-bottom: 22px;
}
.solution-loading .spinner {
  width: 28px; height: 28px;
  border: 3px solid rgba(99,102,241,0.15);
  border-top-color: #6366f1;
  border-radius: 50%;
  animation: spin 0.9s linear infinite;
  flex-shrink: 0;
  margin-top: 2px;
}
@keyframes spin { to { transform: rotate(360deg); } }
.sl-text { flex: 1; }
.sl-title { font-size: 14px; font-weight: 700; color: #1e293b; margin: 0 0 4px; }
.sl-sub { font-size: 12.5px; color: #64748b; margin: 0 0 14px; }
.sl-steps { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.sl-step {
  display: inline-flex; align-items: center; gap: 6px;
  padding: 5px 12px;
  background: #fff;
  border: 1px solid #e2e8f0;
  color: #94a3b8;
  border-radius: 999px;
  font-size: 11.5px;
  font-weight: 600;
  transition: all 0.3s;
}
.sl-step.active {
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  color: #fff;
  border-color: transparent;
  box-shadow: 0 3px 10px rgba(99,102,241,0.35);
}
.sl-dot {
  width: 6px; height: 6px; border-radius: 50%; background: currentColor; opacity: 0.6;
}

.solution-section {
  margin-bottom: 24px;
}
.solution-h {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 16px;
  font-weight: 800;
  color: #0f172a;
  margin: 0 0 12px;
  padding: 10px 14px;
  background: linear-gradient(90deg, rgba(99,102,241,0.08), transparent 75%);
  border-left: 4px solid #6366f1;
  border-radius: 0 8px 8px 0;
}
.sh-emoji {
  width: 28px; height: 28px;
  display: inline-flex; align-items: center; justify-content: center;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 2px 6px rgba(99,102,241,0.15);
  font-size: 14px;
  flex-shrink: 0;
}
.copy-ref-btn {
  margin-left: auto;
  font-size: 11.5px;
  padding: 4px 12px;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  color: #64748b;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
}
.copy-ref-btn:hover { border-color: #10b981; color: #059669; background: #ecfdf5; }
.solution-body {
  font-size: 14.5px; line-height: 1.95; color: #334155;
  padding: 0 4px;
}
.solution-body :deep(p) { margin: 12px 0; }
.solution-body :deep(p:first-child) { margin-top: 0; }
.solution-body :deep(p:last-child) { margin-bottom: 0; }
.solution-body :deep(ul), .solution-body :deep(ol) { padding-left: 24px; margin: 12px 0; }
.solution-body :deep(li) { margin: 6px 0; }
.solution-body :deep(li::marker) { color: #6366f1; font-weight: 700; }
.solution-body :deep(code) {
  background: #fef2f8; color: #be185d; padding: 2px 8px;
  border-radius: 5px; font-family: 'JetBrains Mono', monospace; font-size: 13px;
  border: 1px solid #fbcfe8; font-weight: 500;
}
/* 重点高亮：strong 标签带底色 */
.solution-body :deep(strong) {
  color: #4338ca; font-weight: 700;
  background: linear-gradient(180deg, transparent 60%, rgba(99,102,241,0.12) 60%);
  padding: 0 2px;
}
/* 引用块：左侧紫色竖线 */
.solution-body :deep(blockquote) {
  margin: 12px 0; padding: 8px 16px;
  border-left: 3px solid #6366f1;
  background: rgba(99,102,241,0.04);
  border-radius: 0 8px 8px 0;
  color: #475569; font-size: 13.5px;
}
/* 标题层级 */
.solution-body :deep(h4) { font-size: 14px; font-weight: 700; color: #0f172a; margin: 14px 0 8px; }
.solution-body :deep(h5) { font-size: 13.5px; font-weight: 700; color: #1e293b; margin: 12px 0 6px; }
/* 代码块（非行内） */
.solution-body :deep(pre) {
  margin: 12px 0; background: #0f172a; padding: 14px 16px;
  border-radius: 10px; overflow-x: auto;
  font-size: 13px; line-height: 1.7;
}
.solution-body :deep(pre code) {
  background: transparent; border: none; color: #e2e8f0;
  padding: 0; font-weight: 400;
}

.complexity-card {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  background: linear-gradient(135deg, #fafbff 0%, #f5f3ff 100%);
  border: 1px solid #e0e7ff;
  border-radius: 14px;
  padding: 16px;
  margin-bottom: 4px;
}
.cc-item {
  display: flex; flex-direction: column; gap: 5px;
  padding: 10px 12px;
  background: #fff;
  border-radius: 10px;
  border: 1px solid #eef2ff;
}
.cc-label {
  font-size: 11.5px;
  color: #64748b;
  font-weight: 600;
  letter-spacing: 0.2px;
}
.cc-value {
  font-family: 'JetBrains Mono', monospace;
  font-size: 15px;
  font-weight: 700;
  color: #1e293b;
}
.cc-value.diff.easy { color: #059669; }
.cc-value.diff.medium { color: #d97706; }
.cc-value.diff.hard { color: #dc2626; }

.pitfall-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.pitfall-list > li {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  padding: 12px 14px;
  background: linear-gradient(90deg, #fff7ed, #ffffff);
  border: 1px solid #fed7aa;
  border-left: 4px solid #f97316;
  border-radius: 10px;
  font-size: 14px;
  line-height: 1.8;
  color: #92400e;
}
.p-num {
  width: 22px; height: 22px;
  display: inline-flex; align-items: center; justify-content: center;
  font-family: 'JetBrains Mono', monospace;
  font-size: 11px; font-weight: 800;
  background: linear-gradient(135deg, #f97316, #ea580c);
  color: #fff;
  border-radius: 7px;
  flex-shrink: 0;
  margin-top: 2px;
  box-shadow: 0 2px 6px rgba(249,115,22,0.3);
}

.ref-code-wrap {
  background: #0f172a;
  border-radius: 12px;
  overflow: hidden;
  border: 1px solid #1e293b;
  box-shadow: 0 8px 24px rgba(15,23,42,0.3);
}
.ref-code-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 16px;
  background: #1e293b;
  border-bottom: 1px solid #334155;
}
.ref-file {
  font-family: 'JetBrains Mono', monospace;
  font-size: 12px;
  color: #94a3b8;
  font-weight: 600;
}
.ref-lang {
  font-family: 'JetBrains Mono', monospace;
  font-size: 11px;
  color: #38bdf8;
  padding: 2px 10px;
  background: rgba(56,189,248,0.1);
  border: 1px solid rgba(56,189,248,0.25);
  border-radius: 999px;
  font-weight: 700;
}
.ref-code {
  margin: 0;
  padding: 18px 20px;
  color: #e2e8f0;
  font-family: 'JetBrains Mono', Consolas, monospace;
  font-size: 13.5px;
  line-height: 1.8;
  font-weight: 500;
  letter-spacing: 0.1px;
  overflow-x: auto;
  max-height: 520px;
}
.ref-code::-webkit-scrollbar { width: 10px; height: 10px; }
.ref-code::-webkit-scrollbar-thumb { background: rgba(100,116,139,0.35); border-radius: 5px; }

.solution-empty, .solution-error {
  padding: 40px 20px;
  text-align: center;
  background: linear-gradient(180deg, #fafbfc, #f8fafc);
  border: 1px dashed #cbd5e1;
  border-radius: 14px;
}
.solution-empty .se-icon, .solution-error .se-icon {
  font-size: 42px;
  display: block;
  margin-bottom: 12px;
  filter: drop-shadow(0 4px 12px rgba(99,102,241,0.2));
}
.solution-empty p, .solution-error p {
  margin: 0 0 16px;
  font-size: 14.5px;
  font-weight: 600;
  color: #475569;
}
.solution-error p { color: #b91c1c; }
.se-btn {
  padding: 10px 22px;
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  color: #fff;
  border: none;
  border-radius: 10px;
  font-size: 13.5px;
  font-weight: 600;
  cursor: pointer;
  box-shadow: 0 4px 16px rgba(99,102,241,0.35);
  transition: all 0.25s;
}
.se-btn:hover { transform: translateY(-1.5px); box-shadow: 0 6px 22px rgba(99,102,241,0.5); filter: brightness(1.08); }

/* ============ Tab3: AI 学习助手（结构迁移自 AIGuide） ============ */
.tutor-pane {
  overflow: hidden;
  display: flex;
  flex-direction: column;
  background: rgba(255,255,255,0.5);
}

/* ===== 极简标题栏 + 工具按钮 ===== */
.tutor-minimal-header {
  display: flex; align-items: center; gap: 8px;
  padding: 10px 16px;
  border-bottom: 1px solid #e2e8f0;
  background: #fff;
  flex-shrink: 0;
}
.tmh-icon { font-size: 16px; }
.tmh-title { font-size: 14px; font-weight: 800; color: #0f172a; }
.tmh-hint { font-size: 11.5px; color: #94a3b8; margin-left: auto; }

/* ===== 消息区（chat-messages） ===== */
.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
  scroll-behavior: smooth;
  display: flex;
  flex-direction: column;
}
.chat-messages::-webkit-scrollbar { width: 5px; }
.chat-messages::-webkit-scrollbar-thumb { background: #d9d9d9; border-radius: 3px; }

.chat-empty {
  margin: auto; text-align: center; padding: 30px 16px;
  display: flex; flex-direction: column; align-items: center; gap: 6px;
}
.ce-icon { font-size: 48px; opacity: 0.5; filter: drop-shadow(0 4px 12px rgba(99,102,241,0.2)); }
.ce-text { font-size: 14px; font-weight: 700; color: #475569; margin: 4px 0 0; }
.ce-hint { font-size: 12.5px; color: #94a3b8; }

/* ===== 消息行（msg / msg-ai / msg-user） ===== */
.msg { display: flex; align-items: flex-start; gap: 10px; margin-bottom: 18px; animation: msgIn 0.3s ease; }
@keyframes msgIn { from { opacity: 0; transform: translateY(8px); } to { opacity: 1; transform: translateY(0); } }
.msg-user { flex-direction: row; justify-content: flex-end; }
.msg-ai { flex-direction: row; }

/* ===== 头像（迁移自 AIGuide：AI 头像为图片，用户头像为 emoji） ===== */
.msg-avatar {
  width: 36px; height: 36px; border-radius: 12px;
  display: flex; align-items: center; justify-content: center;
  font-size: 20px; flex-shrink: 0;
  background: #e8f4fd;
}
.msg-avatar.msg-avatar-img {
  background: none; padding: 0; overflow: hidden; border-radius: 8px;
}
.msg-avatar.msg-avatar-img img {
  width: 100%; height: 100%; object-fit: cover; object-position: 20% 15%; display: block;
}
.msg-avatar.user-avatar { background: #e6f7ff; order: 2; }
.msg-user .msg-avatar { order: 2; }

/* ===== 气泡（迁移自 AIGuide） ===== */
.msg-bubble {
  max-width: 75%;
  padding: 14px 18px;
  border-radius: 18px;
  font-size: 14px;
  line-height: 1.7;
  word-break: break-word;
}
.msg-user .msg-bubble {
  background: linear-gradient(135deg, #3b82f6, #6366f1);
  color: #fff;
  border-bottom-right-radius: 4px;
  box-shadow: 0 2px 12px rgba(59,130,246,0.25);
}
.msg-ai .msg-bubble {
  background: #fff;
  color: #303133;
  border-bottom-left-radius: 4px;
  box-shadow: 0 2px 12px rgba(0,0,0,0.06);
  border: 1px solid #f1f5f9;
}
.agent-tag { font-size: 11px; color: #409EFF; margin-bottom: 4px; font-weight: 600; }
.cursor { color: #409EFF; animation: blink 0.8s infinite; }
@keyframes blink { 0%,100%{opacity:1} 50%{opacity:0} }

/* 思考中气泡（迁移自 AIGuide） */
.msg-bubble.thinking {
  display: flex; align-items: center; gap: 8px;
  padding: 14px 18px;
}
.thinking-spinner {
  width: 16px; height: 16px;
  border: 2px solid #e0e0e0; border-top-color: #409EFF;
  border-radius: 50%; animation: spin 0.8s linear infinite;
}
@keyframes spin { to { transform: rotate(360deg); } }

/* ===== TTS 朗读按钮（迁移自 AIGuide） ===== */
.tts-btn {
  margin-top: 8px;
  padding: 4px 10px;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  background: #f8fafc;
  color: #64748b;
  font-size: 11px;
  cursor: pointer;
  transition: all 0.2s;
  font-family: inherit;
}
.tts-btn:hover { background: #eff6ff; color: #3b82f6; border-color: #bfdbfe; }
.tts-btn.playing { background: #eff6ff; color: #2563eb; border-color: #93c5fd; }

/* ===== 消息内容 markdown 排版（迁移自 AIGuide） ===== */
.msg-text, .msg-content-parsed {
  line-height: 1.7;
  font-size: 14px;
  color: #1a1a2e;
  word-break: break-word;
}
.msg-text :deep(h1), .msg-content-parsed :deep(h1) { font-size: 1.3em; margin: 16px 0 8px; font-weight: 700; color: #0f172a; border-bottom: 1px solid #e2e8f0; padding-bottom: 4px; }
.msg-text :deep(h2), .msg-content-parsed :deep(h2) { font-size: 1.15em; margin: 14px 0 6px; font-weight: 700; color: #1e293b; }
.msg-text :deep(h3), .msg-content-parsed :deep(h3) { font-size: 1.05em; margin: 12px 0 4px; font-weight: 600; color: #334155; }
.msg-text :deep(h4), .msg-text :deep(h5), .msg-text :deep(h6),
.msg-content-parsed :deep(h4), .msg-content-parsed :deep(h5), .msg-content-parsed :deep(h6) { font-size: 1em; margin: 10px 0 4px; font-weight: 600; color: #475569; }
.msg-text :deep(p), .msg-content-parsed :deep(p) { margin: 0 0 10px; }
.msg-text :deep(p:last-child), .msg-content-parsed :deep(p:last-child) { margin-bottom: 0; }
.msg-text :deep(ul), .msg-text :deep(ol),
.msg-content-parsed :deep(ul), .msg-content-parsed :deep(ol) { margin: 6px 0; padding-left: 22px; }
.msg-text :deep(li), .msg-content-parsed :deep(li) { margin-bottom: 4px; line-height: 1.65; }
.msg-text :deep(li::marker), .msg-content-parsed :deep(li::marker) { color: #3b82f6; }
.msg-text :deep(code), .msg-content-parsed :deep(code) {
  font-family: 'SF Mono', 'Fira Code', Consolas, monospace;
  font-size: 12.5px;
  background: #f1f5f9;
  color: #e11d48;
  padding: 1px 5px;
  border-radius: 4px;
  border: 1px solid #e2e8f0;
}
.msg-text :deep(pre), .msg-content-parsed :deep(pre) {
  background: #1e293b;
  padding: 14px 16px;
  border-radius: 10px;
  overflow-x: auto;
  margin: 10px 0;
  border: 1px solid #334155;
}
.msg-text :deep(pre code), .msg-content-parsed :deep(pre code) {
  background: none;
  color: #e2e8f0;
  padding: 0;
  border: none;
  font-size: 13px;
  line-height: 1.6;
}
.msg-text :deep(blockquote), .msg-content-parsed :deep(blockquote) {
  border-left: 3px solid #3b82f6;
  padding: 8px 14px;
  margin: 10px 0;
  background: linear-gradient(135deg, #eff6ff, #f0f9ff);
  border-radius: 0 8px 8px 0;
  color: #475569;
  font-size: 13.5px;
}
.msg-text :deep(table), .msg-content-parsed :deep(table) {
  border-collapse: collapse; width: 100%; margin: 10px 0; font-size: 13px;
}
.msg-text :deep(th), .msg-text :deep(td),
.msg-content-parsed :deep(th), .msg-content-parsed :deep(td) {
  border: 1px solid #e2e8f0; padding: 7px 12px; text-align: left;
}
.msg-text :deep(th), .msg-content-parsed :deep(th) {
  background: #f8fafc; font-weight: 600; color: #1e293b;
}
.msg-text :deep(tr:nth-child(even)), .msg-content-parsed :deep(tr:nth-child(even)) { background: #f8fafc; }
.msg-text :deep(hr), .msg-content-parsed :deep(hr) { border: none; border-top: 1px solid #e2e8f0; margin: 14px 0; }
.msg-text :deep(strong), .msg-content-parsed :deep(strong) { color: #0f172a; font-weight: 600; }
.msg-text :deep(em), .msg-content-parsed :deep(em) { color: #64748b; }
.msg-text :deep(a), .msg-content-parsed :deep(a) { color: #2563eb; text-decoration: none; border-bottom: 1px dashed #2563eb; }
.msg-text :deep(a:hover), .msg-content-parsed :deep(a:hover) { border-bottom-style: solid; }

/* ===== 快捷提问 chips（迁移自 AIGuide option-chip 风格） ===== */
.tutor-quick-row {
  display: flex; flex-wrap: wrap; gap: 8px;
  padding: 10px 16px;
  overflow-x: auto;
  border-top: 1px solid #f1f5f9;
  background: #fafbff;
  flex-shrink: 0;
}
.tutor-quick-row::-webkit-scrollbar { height: 4px; }
.tutor-quick-row::-webkit-scrollbar-thumb { background: #cbd5e1; border-radius: 2px; }
.tqr-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
  padding: 8px 16px;
  background: linear-gradient(135deg, #f8fafc, #f1f5f9);
  border: 1.5px solid #e2e8f0;
  border-radius: 20px;
  font-size: 13px;
  color: #475569;
  cursor: pointer;
  transition: all 0.25s;
  text-align: left;
  white-space: nowrap;
  font-family: inherit;
}
.tqr-chip:hover {
  border-color: #3b82f6;
  background: linear-gradient(135deg, #eff6ff, #dbeafe);
  color: #2563eb;
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(59, 130, 246, 0.2);
}
.tqr-chip::before { content: '💡'; font-size: 12px; }

/* ===== 输入区（迁移自 AIGuide chat-input-area） ===== */
.chat-input-area {
  padding: 12px 20px 20px;
  background: linear-gradient(180deg, rgba(255,255,255,0) 0%, rgba(255,255,255,0.9) 15%, #fff 100%);
  border-top: 1px solid rgba(0,0,0,0.04);
  flex-shrink: 0;
}
.input-row { display: flex; align-items: center; gap: 10px; }
.input-wrapper { flex: 1; min-width: 0; }
.chat-input-native {
  width: 100%;
  min-height: 48px;
  max-height: 140px;
  padding: 12px 20px;
  border: 2px solid #e2e8f0;
  border-radius: 28px;
  font-size: 14px;
  line-height: 1.5;
  color: #1e293b;
  background: #f8fafc;
  outline: none;
  transition: all 0.3s;
  font-family: inherit;
  box-sizing: border-box;
  resize: none;
}
.chat-input-native::placeholder { color: #94a3b8; font-size: 13px; }
.chat-input-native:focus { border-color: #3b82f6; background: #fff; box-shadow: 0 0 0 4px rgba(59,130,246,0.08), 0 2px 12px rgba(0,0,0,0.04); }
.chat-input-native:disabled { background: #f1f5f9; color: #94a3b8; cursor: not-allowed; }

.send-btn {
  font-family: inherit;
  font-size: 15px;
  font-weight: 600;
  background: linear-gradient(135deg, #3b82f6, #6366f1);
  color: white;
  padding: 0 20px 0 14px;
  height: 48px;
  display: flex;
  align-items: center;
  gap: 6px;
  border: none;
  border-radius: 26px;
  cursor: pointer;
  flex-shrink: 0;
  box-shadow: 0 2px 8px rgba(59,130,246,0.3);
  transition: all 0.25s;
}
.send-btn:hover { background: linear-gradient(135deg, #2563eb, #4f46e5); box-shadow: 0 6px 24px rgba(59,130,246,0.45); transform: translateY(-1px); }
.send-btn:active { transform: scale(0.95); }
.send-btn:disabled { background: #cbd5e1; color: #94a3b8; box-shadow: none; cursor: not-allowed; transform: none; }
.svg-wrapper-1 { display: inline-flex; }
.svg-wrapper { display: inline-flex; align-items: center; justify-content: center; }

/* 语音按钮（迁移自 AIGuide voice-btn） */
.voice-btn {
  width: 48px; height: 48px;
  border-radius: 50%;
  flex-shrink: 0;
  background: #f1f5f9;
  color: #64748b;
  border: 2px solid #e5e7eb;
  display: flex; align-items: center; justify-content: center;
  cursor: pointer;
  transition: all 0.3s;
}
.voice-btn:hover:not(:disabled) { background: #eff6ff; color: #3b82f6; border-color: #bfdbfe; }
.voice-btn:disabled { background: #f1f5f9; color: #cbd5e1; border-color: #e5e7eb; cursor: not-allowed; }
.voice-btn.recording { background: #fef2f2; color: #ef4444; border-color: #fecaca; animation: pulse-rec 1.2s infinite; }
.voice-btn.listening { background: #eff6ff; color: #3b82f6; border-color: #93c5fd; animation: pulse-listen 1s infinite; }
@keyframes pulse-listen { 0%, 100% { transform: scale(1); } 50% { transform: scale(1.05); } }
@keyframes pulse-rec { 0%,100%{box-shadow:0 0 0 0 rgba(239,68,68,0.4)} 50%{box-shadow:0 0 0 10px rgba(239,68,68,0)} }

/* 语音状态提示（迁移自 AIGuide voice-status） */
.voice-status {
  display: flex; align-items: center; justify-content: center;
  gap: 8px; padding: 8px 16px; margin-top: 8px;
  border-radius: 8px; font-size: 13px; font-weight: 500;
}
.recording-status { background: #fef2f2; color: #dc2626; }
.listening-status { background: #eff6ff; color: #2563eb; }
.voice-pulse {
  width: 10px; height: 10px; background: #ef4444; border-radius: 50%;
  animation: pulse-rec 1s infinite;
}
.voice-spinner {
  width: 14px; height: 14px;
  border: 2px solid #3b82f6; border-top-color: transparent;
  border-radius: 50%; animation: spin 0.8s linear infinite;
}


/* ========== 分隔条 ========== */
.divider-bar {
  width: 6px;
  background: linear-gradient(180deg, transparent, rgba(148,163,184,0.35), transparent);
  border-radius: 3px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  transition: all 0.2s;
}
.divider-bar:hover {
  background: linear-gradient(180deg, transparent, var(--brand), transparent);
  width: 8px;
}
.divider-dots { display: flex; flex-direction: column; gap: 4px; }
.divider-dots::before,
.divider-dots::after {
  content: ''; width: 4px; height: 4px; border-radius: 50%;
  background: rgba(148,163,184,0.6);
}

/* ========== 右栏：编辑器面板 ========== */
.editor-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  background: var(--bg-editor);
  border-radius: 16px;
  overflow: hidden;
  box-shadow: 0 10px 40px rgba(0,0,0,0.30), 0 0 0 1px rgba(0,0,0,0.20);
  position: relative;
}
.editor-panel::before {
  content: ''; position: absolute; inset: 0;
  border-radius: 16px;
  padding: 1px;
  background: linear-gradient(135deg, rgba(139,92,246,0.3), transparent 40%, rgba(59,130,246,0.2));
  -webkit-mask: linear-gradient(#fff 0 0) content-box, linear-gradient(#fff 0 0);
  -webkit-mask-composite: xor;
  mask-composite: exclude;
  pointer-events: none;
  z-index: 1;
}
.editor-panel > * { position: relative; z-index: 2; }

/* 编辑器工具条 */
.editor-toolbar {
  display: flex; align-items: center;
  padding: 0 12px;
  height: 42px;
  background: linear-gradient(180deg, var(--bg-toolbar), #11111b);
  border-bottom: 1px solid var(--border-dark);
  flex-shrink: 0;
}
.editor-tabs { display: flex; gap: 4px; height: 100%; align-items: flex-end; }
.editor-tab {
  display: flex; align-items: center; gap: 8px;
  padding: 0 16px; height: 36px;
  background: #11111b; color: var(--text-dark-sec);
  border: 1px solid var(--border-dark); border-bottom: none;
  border-radius: 10px 10px 0 0;
  font-size: 12.5px; font-family: 'JetBrains Mono', monospace; font-weight: 500;
  cursor: pointer; transition: all 0.2s; position: relative;
}
.editor-tab.active {
  background: var(--bg-editor); color: #cdd6f4;
  box-shadow: inset 0 2px 0 var(--brand);
}
.editor-tab.active::before {
  content: ''; position: absolute; left: 0; right: 0; top: 0; height: 2px;
  background: linear-gradient(90deg, var(--brand), var(--brand-2));
  border-radius: 10px 10px 0 0;
}
.file-icon { font-size: 13px; }
.tab-close {
  font-size: 14px; color: var(--text-dark-sec); opacity: 0; transition: opacity 0.2s;
  width: 16px; height: 16px;
  display: inline-flex; align-items: center; justify-content: center;
  border-radius: 4px;
}
.editor-tab:hover .tab-close { opacity: 0.6; }
.tab-close:hover { background: rgba(255,255,255,0.08); opacity: 1 !important; color: #fff; }
.toolbar-spacer { flex: 1; }
.toolbar-info { display: flex; align-items: center; gap: 8px; padding-right: 6px; }
.info-dot {
  width: 26px; height: 26px;
  display: inline-flex; align-items: center; justify-content: center;
  border-radius: 7px;
  font-family: 'JetBrains Mono', monospace;
  font-size: 12px; font-weight: 700;
}
.info-lang {
  background: linear-gradient(135deg, #3b82f6, #6366f1);
  color: #fff;
  box-shadow: 0 2px 8px rgba(59,130,246,0.35);
}
.info-text {
  font-size: 11.5px;
  color: var(--text-dark-sec);
  font-family: 'JetBrains Mono', monospace;
}

.editor-wrapper { flex: 1; min-height: 0; overflow: hidden; }

/* 控制台 */
.console-area {
  height: 260px;
  min-height: 140px;
  background: var(--bg-console);
  border-top: 1px solid var(--border-dark);
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
}
.console-header {
  display: flex; align-items: center; justify-content: space-between;
  padding: 0 12px; height: 40px;
  background: linear-gradient(180deg, #181825, #11111b);
  border-bottom: 1px solid var(--border-dark);
  flex-shrink: 0;
}
.console-tabs { display: flex; gap: 2px; height: 100%; align-items: center; }
.console-tab {
  display: inline-flex; align-items: center; gap: 6px;
  padding: 6px 16px;
  background: none; border: none;
  color: var(--text-dark-sec);
  font-size: 12.5px; font-weight: 500;
  cursor: pointer; border-radius: 8px; transition: all 0.2s;
}
.console-tab .ctab-icon {
  font-size: 11px;
  width: 18px; height: 18px;
  display: inline-flex; align-items: center; justify-content: center;
  border-radius: 5px;
  background: rgba(255,255,255,0.06);
}
.console-tab:hover {
  color: #a6adc8;
  background: rgba(255,255,255,0.04);
}
.console-tab.active {
  background: rgba(99,102,241,0.15);
  color: #c4b5fd;
  box-shadow: inset 0 0 0 1px rgba(99,102,241,0.3);
}
.console-tab.active .ctab-icon {
  background: var(--brand);
  color: #fff;
  box-shadow: 0 2px 8px rgba(99,102,241,0.4);
}
.ctab-count {
  margin-left: 4px;
  padding: 1px 8px;
  background: rgba(255,255,255,0.08);
  border-radius: 999px;
  font-size: 11px;
  font-weight: 700;
  font-family: 'JetBrains Mono', monospace;
}
.console-actions { display: flex; gap: 8px; }
.clear-btn {
  padding: 5px 12px;
  background: rgba(255,255,255,0.05);
  border: 1px solid var(--border-dark);
  color: var(--text-dark-sec);
  border-radius: 7px;
  font-size: 11.5px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
}
.clear-btn:hover {
  background: rgba(239,68,68,0.12);
  color: #f38ba8;
  border-color: rgba(239,68,68,0.3);
}

.console-content {
  flex: 1;
  overflow-y: auto;
  padding: 14px 18px;
  font-family: 'JetBrains Mono', 'Fira Code', Consolas, monospace;
  font-size: 12.5px;
}
.console-content::-webkit-scrollbar { width: 8px; }
.console-content::-webkit-scrollbar-track { background: transparent; }
.console-content::-webkit-scrollbar-thumb { background: rgba(108,112,134,0.4); border-radius: 4px; }
.console-content::-webkit-scrollbar-thumb:hover { background: rgba(108,112,134,0.6); }

/* 空状态 */
.console-empty {
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  height: 100%; color: var(--text-dark-sec); text-align: center; gap: 8px;
}
.empty-icon-big {
  font-size: 32px; opacity: 0.5; margin-bottom: 6px;
  filter: drop-shadow(0 4px 12px rgba(99,102,241,0.3));
}
.console-empty p { margin: 0; font-size: 13px; color: #a6adc8; font-weight: 500; }
.console-empty span { font-size: 11.5px; color: var(--text-dark-sec); }

/* 运行中 */
.console-running {
  display: flex; align-items: center; gap: 12px;
  padding: 12px 16px;
  background: linear-gradient(135deg, rgba(59,130,246,0.12), rgba(99,102,241,0.12));
  border: 1px solid rgba(59,130,246,0.25);
  border-radius: 10px;
  color: #93c5fd;
}
.running-text { font-weight: 600; }
.running-hint {
  margin-left: auto;
  font-size: 11px;
  color: rgba(147,197,253,0.7);
}

.spinner, .btn-spinner {
  width: 14px; height: 14px;
  border: 2px solid rgba(96,165,250,0.25);
  border-top-color: #60a5fa;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
  display: inline-block;
}

/* 运行结果卡 */
.run-status-card {
  display: flex; align-items: center; gap: 14px;
  padding: 14px 16px;
  border-radius: 12px;
  margin-bottom: 14px;
  border: 1px solid;
}
.run-status-card.ok {
  background: linear-gradient(135deg, rgba(16,185,129,0.12), rgba(52,211,153,0.08));
  border-color: rgba(16,185,129,0.3);
}
.run-status-card.fail {
  background: linear-gradient(135deg, rgba(239,68,68,0.12), rgba(248,113,113,0.08));
  border-color: rgba(239,68,68,0.3);
}
.run-status-card.timeout {
  background: linear-gradient(135deg, rgba(245,158,11,0.14), rgba(251,191,36,0.08));
  border-color: rgba(245,158,11,0.35);
}
.run-status-icon {
  width: 42px; height: 42px;
  display: inline-flex; align-items: center; justify-content: center;
  border-radius: 12px;
  font-size: 20px;
  flex-shrink: 0;
}
.ok .run-status-icon {
  background: linear-gradient(135deg, #10b981, #059669);
  box-shadow: 0 4px 16px rgba(16,185,129,0.35);
}
.fail .run-status-icon {
  background: linear-gradient(135deg, #ef4444, #dc2626);
  box-shadow: 0 4px 16px rgba(239,68,68,0.35);
}
.timeout .run-status-icon {
  background: linear-gradient(135deg, #f59e0b, #d97706);
  box-shadow: 0 4px 16px rgba(245,158,11,0.35);
}
.run-status-title {
  font-size: 14.5px; font-weight: 700;
  color: #f5f5f5;
  margin-bottom: 3px;
}
.run-status-sub {
  font-size: 11.5px; color: #9399b2;
}
.run-status-sub strong {
  font-family: 'JetBrains Mono', monospace;
  color: #cdd6f4;
  font-weight: 700;
}

/* 控制台段 */
.console-section { margin-bottom: 14px; }
.cs-label {
  display: flex; align-items: center; gap: 7px;
  font-size: 11px; font-weight: 600;
  text-transform: uppercase; letter-spacing: 0.6px;
  color: #7f849c;
  margin-bottom: 6px;
}
.cs-dot {
  width: 7px; height: 7px; border-radius: 50%;
}
.cs-dot.ok { background: #a6e3a1; box-shadow: 0 0 8px rgba(166,227,161,0.5); }
.cs-dot.warn { background: #fab387; box-shadow: 0 0 8px rgba(250,179,135,0.5); }
.cs-dot.fail { background: #f38ba8; box-shadow: 0 0 8px rgba(243,139,168,0.5); }

.compile-error, .stdout-block, .stderr-block {
  background: #181825;
  border: 1px solid var(--border-dark);
  border-radius: 10px;
  padding: 12px 14px;
  margin: 0;
  white-space: pre-wrap;
  word-break: break-all;
  line-height: 1.7;
}
.compile-error {
  color: #f38ba8;
  border-color: rgba(243,139,168,0.3);
  background: linear-gradient(180deg, rgba(243,139,168,0.06), #181825);
}
.stdout-block { color: #cdd6f4; }
.stderr-block {
  color: #f9e2af;
  border-color: rgba(249,226,175,0.25);
  background: linear-gradient(180deg, rgba(249,226,175,0.05), #181825);
}

.empty-output {
  display: flex; align-items: center; gap: 8px;
  color: #6c7086; font-style: italic;
  padding: 10px 4px;
}
.empty-icon { font-size: 16px; opacity: 0.6; }

/* 判题汇总 */
.judge-summary {
  display: flex; align-items: center; gap: 16px;
  padding: 16px 18px;
  border-radius: 14px;
  margin-bottom: 16px;
  border: 1px solid;
  position: relative;
  overflow: hidden;
}
.judge-summary::before {
  content: '';
  position: absolute;
  top: -50%; right: -20%;
  width: 240px; height: 240px;
  background: radial-gradient(circle, currentColor, transparent 70%);
  opacity: 0.06;
  border-radius: 50%;
  pointer-events: none;
}
.judge-summary.pass {
  background: linear-gradient(135deg, rgba(16,185,129,0.14), rgba(52,211,153,0.06));
  border-color: rgba(16,185,129,0.35);
  color: #a6e3a1;
}
.judge-summary.fail {
  background: linear-gradient(135deg, rgba(239,68,68,0.14), rgba(248,113,113,0.06));
  border-color: rgba(239,68,68,0.35);
  color: #f38ba8;
}
.js-score {
  font-family: 'JetBrains Mono', monospace;
  display: flex; align-items: baseline; gap: 2px;
  line-height: 1;
}
.js-num {
  font-size: 36px; font-weight: 800;
  color: inherit;
  filter: drop-shadow(0 2px 12px currentColor);
}
.js-sep {
  font-size: 20px; font-weight: 700;
  opacity: 0.5; margin: 0 2px;
}
.js-total {
  font-size: 22px; font-weight: 700; opacity: 0.6;
}
.js-text { flex: 1; min-width: 0; }
.js-title {
  font-size: 15px; font-weight: 700;
  color: #f5f5f5;
  margin-bottom: 3px;
}
.js-sub { font-size: 12px; color: #9399b2; }
.js-progress {
  width: 140px; height: 8px;
  background: rgba(255,255,255,0.06);
  border-radius: 999px;
  overflow: hidden; flex-shrink: 0;
}
.js-progress-fill {
  height: 100%;
  background: linear-gradient(90deg, #34d399, #10b981);
  border-radius: 999px;
  transition: width 0.6s cubic-bezier(0.4,0,0.2,1);
  box-shadow: 0 0 12px rgba(52,211,153,0.5);
}
.fail .js-progress-fill {
  background: linear-gradient(90deg, #f87171, #ef4444);
  box-shadow: 0 0 12px rgba(248,113,113,0.5);
}

/* 判题用例 */
.judge-case {
  padding: 14px 16px;
  border-radius: 12px;
  margin-bottom: 12px;
  background: #181825;
  border: 1px solid var(--border-dark);
  transition: all 0.25s ease;
}
.judge-case:hover { border-color: rgba(255,255,255,0.08); }
.judge-case.pass {
  border-left: 3.5px solid #a6e3a1;
  background: linear-gradient(90deg, rgba(166,227,161,0.06), transparent 40%);
}
.judge-case.fail {
  border-left: 3.5px solid #f38ba8;
  background: linear-gradient(90deg, rgba(243,139,168,0.06), transparent 40%);
}
.case-header {
  display: flex; align-items: center; gap: 10px;
  margin-bottom: 12px;
  font-weight: 600;
}
.case-icon { font-size: 16px; }
.case-title { font-size: 13.5px; color: #f5f5f5; font-weight: 700; }
.case-status {
  margin-left: auto;
  font-family: 'JetBrains Mono', monospace;
  font-size: 11px;
  padding: 3px 10px;
  border-radius: 999px;
}
.pass .case-status {
  background: rgba(166,227,161,0.12);
  color: #a6e3a1;
}
.fail .case-status {
  background: rgba(243,139,168,0.12);
  color: #f38ba8;
}

.case-diff {
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  gap: 12px;
  align-items: start;
}
.diff-arrow {
  width: 32px; height: 32px;
  display: flex; align-items: center; justify-content: center;
  border-radius: 50%;
  background: rgba(139,92,246,0.15);
  color: #c4b5fd;
  font-size: 15px; font-weight: 700;
  align-self: center; flex-shrink: 0;
}
.diff-label {
  font-size: 10.5px; font-weight: 700;
  text-transform: uppercase; letter-spacing: 0.7px;
  color: #7f849c;
  margin-bottom: 5px;
}
.diff-expected, .diff-actual {
  background: #11111b;
  border: 1px solid var(--border-dark);
  border-radius: 10px;
  padding: 10px 12px;
  white-space: pre-wrap;
  word-break: break-all;
  margin: 0;
  font-size: 12px; line-height: 1.7;
  color: #cdd6f4;
  max-height: 160px; overflow-y: auto;
}
.diff-actual.mismatch {
  background: linear-gradient(180deg, rgba(243,139,168,0.08), #11111b);
  border-color: rgba(243,139,168,0.3);
  color: #f38ba8;
}

/* ========== 底部操作条 ========== */
.action-bar {
  display: flex; align-items: center; gap: 10px;
  padding: 12px 18px;
  background: linear-gradient(180deg, #11111b, #0a0a13);
  border-top: 1px solid var(--border-dark);
  flex-shrink: 0;
}
.action-hint {
  display: flex; align-items: center; gap: 8px;
  font-size: 12px; color: var(--text-dark-sec);
}
.hint-icon { font-size: 13px; }
.action-hint strong {
  color: #cdd6f4;
  font-weight: 700;
  font-family: 'JetBrains Mono', monospace;
  padding: 1px 6px;
  background: rgba(255,255,255,0.06);
  border-radius: 5px;
  margin: 0 2px;
}
.action-spacer { flex: 1; }

.action-btn {
  display: inline-flex; align-items: center; gap: 7px;
  padding: 9px 18px;
  border: none;
  border-radius: 10px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.25s cubic-bezier(0.4,0,0.2,1);
  position: relative;
  overflow: hidden;
}
.action-btn::before {
  content: ''; position: absolute; inset: 0;
  background: linear-gradient(180deg, rgba(255,255,255,0.12), transparent 50%);
  pointer-events: none;
}
.action-btn:disabled { opacity: 0.45; cursor: not-allowed; transform: none !important; }
.action-btn:disabled::before { display: none; }
.ab-icon {
  font-size: 14px;
  position: relative;
  z-index: 1;
}

.btn-reset {
  background: #1e1e2e;
  color: #a6adc8;
  border: 1px solid var(--border-dark);
}
.btn-reset:hover:not(:disabled) {
  background: #313244; color: #cdd6f4;
  transform: translateY(-1px);
  border-color: rgba(255,255,255,0.12);
}

.btn-debug {
  background: linear-gradient(135deg, #3b82f6 0%, #2563eb 100%);
  color: #fff;
  box-shadow: 0 4px 14px rgba(59,130,246,0.35);
}
.btn-debug:hover:not(:disabled) {
  transform: translateY(-1.5px);
  box-shadow: 0 6px 22px rgba(59,130,246,0.50);
  filter: brightness(1.08);
}
.btn-debug:active:not(:disabled) {
  transform: translateY(0);
  box-shadow: 0 2px 10px rgba(59,130,246,0.4);
}

.btn-submit {
  background: linear-gradient(135deg, #10b981 0%, #059669 50%, #047857 100%);
  color: #fff;
  box-shadow: 0 4px 14px rgba(16,185,129,0.35), inset 0 1px 0 rgba(255,255,255,0.2);
  padding: 9px 22px;
}
.btn-submit:hover:not(:disabled) {
  transform: translateY(-1.5px);
  box-shadow: 0 6px 24px rgba(16,185,129,0.50), inset 0 1px 0 rgba(255,255,255,0.2);
  filter: brightness(1.08);
}
.btn-submit:active:not(:disabled) {
  transform: translateY(0);
  box-shadow: 0 2px 12px rgba(16,185,129,0.4), inset 0 1px 0 rgba(255,255,255,0.2);
}

/* ========== 响应式 ========== */
@media (max-width: 1200px) {
  .hero-tags { flex-direction: row; }
  .complexity-card { grid-template-columns: repeat(3, 1fr); }
  .quick-cards { grid-template-columns: repeat(2, 1fr); }
  .workbench-body { padding: 10px; gap: 10px; }
}
@media (max-width: 1024px) {
  .workbench-body { flex-direction: column; }
  .problem-panel {
    width: 100%; min-width: 0; max-height: 48vh;
  }
  .divider-bar {
    width: 100%; height: 6px;
    background: linear-gradient(90deg, transparent, rgba(148,163,184,0.35), transparent);
  }
  .divider-bar:hover { width: 100%; height: 8px; }
  .divider-dots { flex-direction: row; }
  .case-diff { grid-template-columns: 1fr; }
  .diff-arrow {
    justify-self: center;
    transform: rotate(90deg);
  }
  .complexity-card { grid-template-columns: 1fr; }
}
@media (max-width: 768px) {
  .code-workbench::before {
    content: '⚠️ 编程工作台建议在桌面端使用以获得最佳体验';
    display: block;
    padding: 10px;
    background: linear-gradient(90deg, rgba(245,158,11,0.15), rgba(245,158,11,0.08));
    color: #92400e;
    text-align: center;
    font-size: 12.5px;
    font-weight: 500;
    border-bottom: 1px solid rgba(245,158,11,0.3);
  }
  .action-hint { display: none; }
  .workbench-header { padding: 12px 16px; gap: 12px; }
  .btn-reset { display: none; }
  .quick-cards { grid-template-columns: 1fr; }
  .side-nav { width: 48px; }
  .nav-item { width: 36px; height: 36px; }
}

/* ==========================================================================
   题解 Tab v2：体检报告 Hero + 侧边目录点 + 6 大板块
   ========================================================================== */
.solution-tab-v2 {
  height: 100%;
  display: flex;
  gap: 0;
  overflow: hidden;
  background: #f6f7fb;
}
/* ---------- 右侧主内容 ---------- */
.solution-main {
  flex: 1;
  min-width: 0;
  position: relative;
}
/* Hero 体检报告卡片 */
.solution-hero-v2 {
  position: relative;
  padding: 28px 36px 24px;
  overflow: hidden;
  border-bottom: 1px solid rgba(226,232,240,0.9);
}
.shv2-bg {
  position: absolute; inset: 0;
  background:
    radial-gradient(600px 240px at 12% -20%, rgba(99,102,241,0.22), transparent 60%),
    radial-gradient(500px 260px at 95% 10%, rgba(16,185,129,0.18), transparent 55%),
    radial-gradient(400px 240px at 60% 110%, rgba(245,158,11,0.15), transparent 55%),
    linear-gradient(135deg, #ffffff 0%, #fafbff 60%, #f1f5ff 100%);
  z-index: 0;
}
.shv2-bg::after {
  content: ''; position: absolute; inset: 0;
  background-image:
    linear-gradient(rgba(99,102,241,0.05) 1px, transparent 1px),
    linear-gradient(90deg, rgba(99,102,241,0.05) 1px, transparent 1px);
  background-size: 26px 26px;
  mask-image: radial-gradient(ellipse 70% 70% at 50% 50%, black 30%, transparent 85%);
}
.shv2-content { position: relative; z-index: 1; }
.shv2-top { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 12px; margin-bottom: 20px; }
.shv2-badge {
  display: inline-flex; align-items: center; gap: 8px;
  padding: 6px 14px 6px 12px;
  background: linear-gradient(135deg, rgba(16,185,129,0.12), rgba(59,130,246,0.12));
  border: 1px solid rgba(16,185,129,0.28);
  border-radius: 999px;
  font-size: 13px; font-weight: 600; color: #0f766e;
  position: relative; overflow: hidden;
  box-shadow: 0 4px 14px rgba(16,185,129,0.10);
}
.shv2b-glow {
  position: absolute; left: -30%; top: 0; height: 100%; width: 40%;
  background: linear-gradient(90deg, transparent, rgba(255,255,255,0.55), transparent);
  transform: skewX(-20deg); animation: shv2-glow 3.5s ease-in-out infinite;
}
@keyframes shv2-glow { 0%,100%{ transform: skewX(-20deg) translateX(0);} 50%{ transform: skewX(-20deg) translateX(240%);} }
.shv2b-icon { font-size: 15px; filter: drop-shadow(0 1px 1px rgba(16,185,129,0.3)); }
.shv2-actions { display: flex; gap: 8px; flex-wrap: wrap; }
.shv2-btn {
  display: inline-flex; align-items: center; gap: 6px;
  padding: 7px 14px; border-radius: 10px;
  font-size: 12.5px; font-weight: 600; cursor: pointer;
  transition: all .18s ease;
  border: 1px solid transparent;
}
.shv2-btn.ghost {
  background: #ffffff; color: #334155;
  border-color: #e2e8f0;
  box-shadow: 0 1px 2px rgba(15,23,42,0.04);
}
.shv2-btn.ghost:hover:not(:disabled) {
  border-color: #cbd5e1;
  transform: translateY(-1px);
  box-shadow: 0 4px 10px rgba(15,23,42,0.08);
}
.shv2-btn:disabled { opacity: .5; cursor: not-allowed; }

/* Hero 中部：健康度 + 标签 + 指标 */
.shv2-health {
  display: grid;
  grid-template-columns: minmax(200px, 220px) 1fr;
  gap: 36px;
  align-items: center;
}
.health-ring {
  position: relative; width: 140px; height: 140px; margin: 0 auto;
  filter: drop-shadow(0 10px 20px rgba(99,102,241,0.18));
}
.hr-svg { width: 100%; height: 100%; }
.hr-inner {
  position: absolute; inset: 0;
  display: flex; flex-direction: column; align-items: center; justify-content: center;
}
.hr-num {
  font-size: 44px; font-weight: 800; line-height: 1;
  background: linear-gradient(135deg, #6366f1 0%, #8b5cf6 50%, #ec4899 100%);
  -webkit-background-clip: text; background-clip: text; color: transparent;
  letter-spacing: -1px;
}
.hr-unit { font-size: 12px; color: #64748b; margin-top: 2px; font-weight: 600; }

.shv2-right { display: flex; flex-direction: column; gap: 14px; min-width: 0; }
.hero-title-row { display: flex; flex-wrap: wrap; align-items: center; gap: 10px 14px; }
.hero-title {
  font-size: 22px; font-weight: 800; color: #0f172a; letter-spacing: -0.2px;
  margin: 0;
  display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden;
}
.hero-meta { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.hero-meta .diff-chip {
  display: inline-flex; align-items: center; gap: 4px;
  padding: 3px 10px; border-radius: 6px; font-size: 11.5px; font-weight: 700;
  border: 1px solid rgba(99,102,241,0.35); color: #4338ca; background: rgba(99,102,241,0.08);
}
.hero-meta .diff-chip.easy { color: #047857; border-color: rgba(16,185,129,.35); background: rgba(16,185,129,.08); }
.hero-meta .diff-chip.medium { color: #92400e; border-color: rgba(245,158,11,.4); background: rgba(245,158,11,.08); }
.hero-meta .diff-chip.hard { color: #991b1b; border-color: rgba(239,68,68,.35); background: rgba(239,68,68,.08); }
.hero-meta .time-chip { font-size: 11.5px; color: #475569; display: inline-flex; align-items: center; gap: 4px; }

/* 4 项细粒度指标 */
.metrics-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 10px; }
.metric-bar {
  background: rgba(255,255,255,0.8);
  border: 1px solid rgba(226,232,240,0.9);
  border-radius: 12px;
  padding: 10px 12px;
  box-shadow: 0 1px 3px rgba(15,23,42,0.04);
}
.metric-bar:hover { transform: translateY(-1px); box-shadow: 0 6px 18px rgba(15,23,42,0.07); transition: all .2s ease; }
.metric-top { display: flex; justify-content: space-between; align-items: baseline; margin-bottom: 7px; }
.metric-name { font-size: 11.5px; font-weight: 600; color: #475569; }
.metric-val { font-size: 13px; font-weight: 800; }
.metric-track {
  height: 6px; background: #eef2ff; border-radius: 99px; overflow: hidden;
}
.metric-fill { height: 100%; border-radius: 99px; width: 0%; transition: width 1.2s cubic-bezier(.4,0,.2,1); }

/* Hero 底部：标签条 + 标签输入 */
.tags-bar-row { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.tags-label { font-size: 12px; color: #475569; font-weight: 600; }
.tags-list { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; }
.tag-chip {
  position: relative;
  display: inline-flex; align-items: center; gap: 6px;
  padding: 4px 10px 4px 12px; border-radius: 99px;
  color: #ffffff; font-size: 11.5px; font-weight: 600;
  cursor: default;
  box-shadow: 0 2px 8px rgba(15,23,42,0.08);
}
.tag-chip:hover { filter: brightness(1.05); transform: translateY(-0.5px); transition: all .15s ease; }
.tag-x {
  width: 15px; height: 15px; border-radius: 50%;
  display: inline-flex; align-items: center; justify-content: center;
  background: rgba(255,255,255,0.2); color: #ffffff; cursor: pointer;
  font-size: 10px; line-height: 1; padding: 0;
  border: 1px solid rgba(255,255,255,0.35);
}
.tag-x:hover { background: rgba(255,255,255,0.38); }
.add-tag-btn {
  display: inline-flex; align-items: center; gap: 4px;
  padding: 4px 10px; border-radius: 99px;
  background: #ffffff; color: #6366f1;
  border: 1px dashed #c7d2fe; font-size: 11.5px; font-weight: 700;
  cursor: pointer; transition: all .15s ease;
}
.add-tag-btn:hover { background: #eef2ff; border-color: #a5b4fc; }
.tag-input-box { display: inline-flex; align-items: center; gap: 6px; }
.tag-input-box input {
  width: 140px; padding: 4px 10px; border-radius: 8px;
  border: 1px solid #c7d2fe; background: #ffffff; font-size: 12px; outline: none;
}
.tag-input-box input:focus { border-color: #6366f1; box-shadow: 0 0 0 3px rgba(99,102,241,0.12); }
.tag-input-box button {
  padding: 3px 8px; border-radius: 6px; border: none; background: #6366f1;
  color: #fff; font-size: 11.5px; font-weight: 600; cursor: pointer;
}

/* ---------- 6 大板块滚动区 ---------- */
.solution-body-v2 {
  height: calc(100% - 230px);
  overflow-y: auto;
  padding: 22px 36px 80px 36px;
  scroll-behavior: smooth;
}
.solution-body-v2::-webkit-scrollbar { width: 8px; }
.solution-body-v2::-webkit-scrollbar-thumb { background: #cbd5e1; border-radius: 4px; }
.solution-body-v2::-webkit-scrollbar-thumb:hover { background: #94a3b8; }

.section-card {
  background: #ffffff;
  border: 1px solid #e2e8f0;
  border-radius: 16px;
  padding: 24px 28px;
  margin-bottom: 22px;
  position: relative;
  box-shadow: 0 2px 8px rgba(15,23,42,0.04);
  transition: box-shadow .25s ease, transform .25s ease;
}
.section-card:hover {
  box-shadow: 0 10px 30px rgba(15,23,42,0.08);
  transform: translateY(-2px);
}
.sc-head { display: flex; align-items: center; gap: 14px; margin-bottom: 16px; padding-bottom: 14px; border-bottom: 1px solid #f1f5f9; }
.sc-icon {
  width: 40px; height: 40px; flex-shrink: 0;
  display: inline-flex; align-items: center; justify-content: center;
  border-radius: 12px; font-size: 18px;
  color: #ffffff;
  box-shadow: 0 4px 10px rgba(15,23,42,0.12);
}
.sc-icon.i-sum  { background: linear-gradient(135deg, #6366f1, #8b5cf6); }
.sc-icon.i-th   { background: linear-gradient(135deg, #06b6d4, #3b82f6); }
.sc-icon.i-cpx  { background: linear-gradient(135deg, #10b981, #14b8a6); }
.sc-icon.i-pit  { background: linear-gradient(135deg, #f59e0b, #f97316); }
.sc-icon.i-code { background: linear-gradient(135deg, #1e293b, #334155); }
.sc-icon.i-exp  { background: linear-gradient(135deg, #ec4899, #8b5cf6); }
.sc-title { font-size: 17px; font-weight: 800; color: #0f172a; letter-spacing: -0.1px; }
.sc-sub { font-size: 12px; color: #64748b; margin-left: auto; font-weight: 500; }

/* 板块内容通用 */
.sec-text { font-size: 14.2px; line-height: 1.78; color: #334155; }
.sec-text :deep(p) { margin: 0 0 10px; }
.sec-text :deep(strong) { color: #0f172a; background: linear-gradient(transparent 60%, rgba(99,102,241,0.14) 60%); padding: 0 2px; }
.sec-text :deep(ul), .sec-text :deep(ol) { padding-left: 22px; margin: 8px 0; }
.sec-text :deep(li) { margin: 4px 0; }
.sec-text :deep(code) { background: #f1f5f9; padding: 1.5px 6px; border-radius: 4px; font-size: 13px; color: #4f46e5; }

/* 复杂度卡片网格 */
.cpx-grid-3 { display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; }
.cpx-card {
  border-radius: 14px; padding: 16px; position: relative; overflow: hidden;
  border: 1px solid #e2e8f0; background: #fafbff;
}
.cpx-card.t { background: linear-gradient(135deg, #ecfeff, #ffffff); border-color: rgba(6,182,212,0.25); }
.cpx-card.s { background: linear-gradient(135deg, #f0fdf4, #ffffff); border-color: rgba(16,185,129,0.25); }
.cpx-card.l { background: linear-gradient(135deg, #fff7ed, #ffffff); border-color: rgba(245,158,11,0.25); }
.cpx-label { font-size: 11px; font-weight: 700; color: #475569; letter-spacing: 0.4px; text-transform: uppercase; margin-bottom: 8px; }
.cpx-value { font-size: 17px; font-weight: 800; color: #0f172a; letter-spacing: -0.2px; line-height: 1.3; }
.cpx-sub { font-size: 11.5px; color: #64748b; margin-top: 4px; }

/* 坑点 timeline */
.pit-timeline { display: flex; flex-direction: column; gap: 12px; }
.pit-item {
  display: grid; grid-template-columns: 38px 1fr; gap: 12px; align-items: flex-start;
  padding: 12px 14px; border-radius: 12px;
  background: #fff7ed; border: 1px solid rgba(245,158,11,0.18);
  position: relative;
}
.pit-num {
  width: 28px; height: 28px; border-radius: 8px;
  background: linear-gradient(135deg, #f59e0b, #f97316);
  color: #fff; font-size: 13px; font-weight: 800;
  display: inline-flex; align-items: center; justify-content: center;
  box-shadow: 0 3px 8px rgba(245,158,11,0.28);
}
.pit-text { font-size: 14px; line-height: 1.65; color: #78350f; padding-top: 3px; }
.pit-text strong { color: #92400e; }

/* 参考代码：窗口外壳 */
.code-window {
  background: #0f172a;
  border-radius: 14px;
  overflow: hidden;
  box-shadow: 0 12px 30px rgba(15,23,42,0.22);
  border: 1px solid #1e293b;
}
.cw-head {
  display: flex; align-items: center; justify-content: space-between;
  padding: 10px 14px;
  background: linear-gradient(180deg, #1e293b, #172033);
  border-bottom: 1px solid #334155;
}
.cw-dots { display: flex; align-items: center; gap: 7px; }
.cw-dots span { width: 12px; height: 12px; border-radius: 50%; }
.cw-dots .d-r { background: #ef4444; }
.cw-dots .d-y { background: #f59e0b; }
.cw-dots .d-g { background: #10b981; }
.cw-title {
  display: flex; align-items: center; gap: 8px;
  color: #94a3b8; font-size: 12px; font-weight: 600;
}
.cw-actions { display: flex; gap: 6px; }
.cw-btn {
  padding: 5px 11px; border-radius: 7px; border: none;
  font-size: 11.5px; font-weight: 600; cursor: pointer;
  display: inline-flex; align-items: center; gap: 4px;
  transition: all .15s ease;
}
.cw-btn.copy { background: #334155; color: #e2e8f0; }
.cw-btn.copy:hover { background: #475569; transform: translateY(-0.5px); }
.cw-btn.apply { background: linear-gradient(135deg, #6366f1, #8b5cf6); color: #ffffff; box-shadow: 0 2px 8px rgba(99,102,241,0.3); }
.cw-btn.apply:hover { filter: brightness(1.08); transform: translateY(-0.5px); box-shadow: 0 4px 14px rgba(99,102,241,0.4); }
.cw-body { padding: 16px 18px; overflow-x: auto; }
.cw-body :deep(pre) { margin: 0; background: transparent !important; padding: 0 !important; }
.cw-body :deep(code) { background: transparent !important; font-family: 'JetBrains Mono', Consolas, monospace; font-size: 13px; line-height: 1.65; color: #e2e8f0; }
.no-ref-code {
  text-align: center; padding: 30px 16px; color: #94a3b8;
  background: #f8fafc; border-radius: 12px; border: 1px dashed #cbd5e1;
  font-size: 13px;
}
.code-tip {
  margin-top: 14px; padding: 10px 14px;
  display: flex; align-items: center; gap: 8px;
  background: linear-gradient(90deg, rgba(99,102,241,0.06), rgba(139,92,246,0.04));
  border: 1px solid rgba(99,102,241,0.14);
  border-radius: 10px; font-size: 12.5px; color: #4338ca;
}

/* 延伸拓展 */
.ext-wrap {
  padding: 18px 20px;
  border-radius: 14px;
  background: linear-gradient(135deg, #fdf4ff, #eef2ff);
  border: 1px solid rgba(139,92,246,0.15);
  position: relative; overflow: hidden;
}
.ext-wrap::before {
  content: '💡'; position: absolute; right: 18px; top: 14px; font-size: 44px; opacity: 0.08;
}
.ext-title {
  display: inline-flex; align-items: center; gap: 6px;
  font-size: 12px; font-weight: 700; color: #6d28d9; letter-spacing: 0.4px;
  margin-bottom: 8px; text-transform: uppercase;
}

/* loading / empty / error 块 */
.solution-empty { padding: 70px 20px; text-align: center; color: #64748b; }
.se-icon { font-size: 56px; opacity: 0.4; margin-bottom: 18px; }
.se-title { font-size: 18px; font-weight: 700; color: #0f172a; margin: 0 0 8px; }
.se-sub { font-size: 13px; max-width: 480px; margin: 0 auto 22px; line-height: 1.6; }
.btn-primary-lg {
  display: inline-flex; align-items: center; gap: 8px;
  padding: 11px 22px; border-radius: 10px; border: none;
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  color: #fff; font-size: 14px; font-weight: 700; cursor: pointer;
  box-shadow: 0 6px 20px rgba(99,102,241,0.35);
  transition: all .2s ease;
}
.btn-primary-lg:hover:not(:disabled) { transform: translateY(-2px); box-shadow: 0 10px 28px rgba(99,102,241,0.45); filter: brightness(1.05); }
.btn-primary-lg:disabled { opacity: .65; cursor: not-allowed; }
.solution-error { padding: 30px; background: #fef2f2; border: 1px solid #fecaca; border-radius: 14px; color: #991b1b; }

/* ---------- 侧边目录点 ---------- */
.solution-dots-nav {
  width: 60px; flex-shrink: 0;
  padding: 30px 0;
  display: flex; flex-direction: column; align-items: center;
  gap: 4px;
  border-left: 1px solid #e2e8f0;
  background: linear-gradient(180deg, #fafbff, #f1f5f9);
}
.sdn-item {
  position: relative;
  display: flex; flex-direction: column; align-items: center;
  padding: 10px 4px; width: 100%;
  cursor: pointer;
  transition: all .15s ease;
  border-radius: 10px;
}
.sdn-dot {
  width: 10px; height: 10px; border-radius: 50%;
  background: #cbd5e1;
  box-shadow: 0 1px 2px rgba(15,23,42,0.08);
  transition: all .25s ease;
}
.sdn-label {
  font-size: 9.5px; font-weight: 700; color: #94a3b8;
  margin-top: 5px; opacity: 0; transform: translateX(-3px);
  transition: all .2s ease;
}
.sdn-item:hover .sdn-dot { background: #94a3b8; transform: scale(1.18); }
.sdn-item:hover .sdn-label { opacity: 1; transform: translateX(0); color: #475569; }
.sdn-item.active { background: rgba(99,102,241,0.08); }
.sdn-item.active .sdn-dot {
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  transform: scale(1.35);
  box-shadow: 0 0 0 4px rgba(99,102,241,0.14), 0 3px 10px rgba(99,102,241,0.35);
}
.sdn-item.active .sdn-label { opacity: 1; color: #4f46e5; transform: translateX(0); }
.sdn-line {
  flex-grow: 1; width: 2px; margin: 4px 0;
  background: linear-gradient(180deg, transparent, #e2e8f0 20%, #e2e8f0 80%, transparent);
}

/* loading steps */
.loading-steps { display: flex; flex-direction: column; gap: 12px; padding: 20px 0; }
.step-item { display: grid; grid-template-columns: 28px 1fr auto; align-items: center; gap: 12px; }
.step-circle {
  width: 26px; height: 26px; border-radius: 50%;
  display: inline-flex; align-items: center; justify-content: center;
  border: 2px solid #e2e8f0; font-size: 12px; font-weight: 800; color: #cbd5e1;
}
.step-circle.done { background: linear-gradient(135deg, #10b981, #14b8a6); color: #fff; border-color: transparent; box-shadow: 0 3px 8px rgba(16,185,129,0.3); }
.step-circle.run { border-color: transparent; background: linear-gradient(135deg, #6366f1, #8b5cf6); color: #fff; box-shadow: 0 0 0 4px rgba(99,102,241,0.12); animation: pulse-ring 1.4s ease-in-out infinite; }
@keyframes pulse-ring { 0%,100%{ box-shadow: 0 0 0 4px rgba(99,102,241,0.12);} 50%{ box-shadow: 0 0 0 7px rgba(99,102,241,0.05);} }
.step-text { font-size: 13.5px; color: #334155; font-weight: 500; }
.step-text.wait { color: #94a3b8; }
.step-check { font-size: 13px; font-weight: 700; }
.step-check.done { color: #10b981; }
.step-check.run { color: #6366f1; }
.spinner-sm {
  width: 14px; height: 14px; border: 2px solid #c7d2fe; border-top-color: #6366f1;
  border-radius: 50%; animation: spin 0.8s linear infinite;
}
@keyframes spin { to { transform: rotate(360deg); } }

/* ==========================================================================
   AI 学习助手 Tab v2：气泡 + TTS + 打字机 + 编辑 + 语音 + RAG侧栏
   ========================================================================== */
.tutor-layout {
  height: 100%;
  display: flex;
  min-width: 0;
  background: linear-gradient(180deg, #f8fafc 0%, #f1f5f9 100%);
  position: relative;
}
.tutor-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  position: relative;
}
.tutor-main::before {
  content: '';
  position: absolute; inset: 0;
  background-image:
    radial-gradient(circle at 15% 20%, rgba(139,92,246,0.07), transparent 30%),
    radial-gradient(circle at 85% 80%, rgba(59,130,246,0.07), transparent 30%);
  pointer-events: none;
}
/* Header */
.tutor-header {
  display: flex; align-items: center; justify-content: space-between;
  padding: 16px 22px;
  background: rgba(255,255,255,0.85);
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
  border-bottom: 1px solid rgba(226,232,240,0.85);
  position: relative; z-index: 2;
}
.th-left { display: flex; align-items: center; gap: 12px; min-width: 0; }
.th-avatar {
  width: 42px; height: 42px; border-radius: 12px;
  background: linear-gradient(135deg, #6366f1, #8b5cf6 50%, #ec4899);
  display: inline-flex; align-items: center; justify-content: center;
  font-size: 20px; color: #fff;
  box-shadow: 0 6px 16px rgba(99,102,241,0.35);
  position: relative;
}
.th-avatar::after {
  content: ''; position: absolute; right: -1px; bottom: -1px; width: 11px; height: 11px;
  background: #10b981; border-radius: 50%; border: 2px solid #ffffff;
}
.th-title { font-size: 15px; font-weight: 800; color: #0f172a; margin: 0; }
.th-sub { font-size: 11.5px; color: #64748b; margin-top: 2px; display: flex; align-items: center; gap: 6px; }
.th-status-dot { width: 7px; height: 7px; border-radius: 50%; background: #10b981; box-shadow: 0 0 0 3px rgba(16,185,129,0.18); }
.th-status-dot.busy { background: #f59e0b; box-shadow: 0 0 0 3px rgba(245,158,11,0.18); animation: pulse-dot 1.2s ease-in-out infinite; }
@keyframes pulse-dot { 0%,100%{ opacity: 1;} 50%{ opacity: 0.45;} }
.th-right { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; }
.icon-btn {
  width: 34px; height: 34px; border-radius: 9px;
  border: 1px solid #e2e8f0; background: #ffffff;
  color: #475569; cursor: pointer;
  display: inline-flex; align-items: center; justify-content: center;
  font-size: 15px; transition: all .15s ease;
  padding: 0;
}
.icon-btn:hover { transform: translateY(-1px); color: #6366f1; border-color: #c7d2fe; box-shadow: 0 4px 10px rgba(99,102,241,0.12); }
.icon-btn.on { background: linear-gradient(135deg, #6366f1, #8b5cf6); color: #fff; border-color: transparent; box-shadow: 0 4px 12px rgba(99,102,241,0.3); }
.icon-btn.on:hover { filter: brightness(1.08); color: #fff; }
.icon-btn.danger:hover { color: #ef4444; border-color: #fecaca; box-shadow: 0 4px 10px rgba(239,68,68,0.15); }
.icon-btn:disabled { opacity: .5; cursor: not-allowed; transform: none; }

/* Quick questions */
.quick-wrap {
  padding: 12px 22px 0;
  position: relative; z-index: 1;
}
.quick-cards { display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px; }
.quick-card {
  padding: 9px 12px;
  border-radius: 10px;
  background: #ffffff;
  border: 1px solid #e2e8f0;
  display: flex; align-items: flex-start; gap: 8px;
  cursor: pointer;
  transition: all .18s ease;
}
.quick-card:hover {
  transform: translateY(-2px);
  border-color: #c7d2fe;
  box-shadow: 0 8px 18px rgba(99,102,241,0.12);
  background: linear-gradient(135deg, #ffffff, #f5f3ff);
}
.qc-icon {
  width: 26px; height: 26px; border-radius: 8px; flex-shrink: 0;
  display: inline-flex; align-items: center; justify-content: center;
  font-size: 13px; color: #fff;
}
.qc-icon.i1 { background: linear-gradient(135deg, #06b6d4, #3b82f6); }
.qc-icon.i2 { background: linear-gradient(135deg, #f59e0b, #f97316); }
.qc-icon.i3 { background: linear-gradient(135deg, #10b981, #14b8a6); }
.qc-icon.i4 { background: linear-gradient(135deg, #6366f1, #8b5cf6); }
.qc-icon.i5 { background: linear-gradient(135deg, #ef4444, #ec4899); }
.qc-icon.i6 { background: linear-gradient(135deg, #8b5cf6, #ec4899); }
.qc-text { font-size: 12px; color: #334155; font-weight: 600; line-height: 1.4; }

/* 空态欢迎页 */
.tutor-welcome {
  margin: auto;
  padding: 40px 24px;
  text-align: center;
  max-width: 560px;
}
.tw-avatar {
  width: 76px; height: 76px; border-radius: 22px;
  background: linear-gradient(135deg, #6366f1, #8b5cf6 50%, #ec4899);
  display: inline-flex; align-items: center; justify-content: center;
  font-size: 34px; color: #fff;
  box-shadow: 0 14px 32px rgba(99,102,241,0.35), inset 0 1px 0 rgba(255,255,255,0.2);
  margin-bottom: 22px;
  position: relative;
}
.tw-avatar::after {
  content: '';
  position: absolute; inset: -6px;
  border-radius: 28px;
  background: linear-gradient(135deg, rgba(99,102,241,0.12), rgba(236,72,153,0.08));
  z-index: -1; filter: blur(12px);
}
.tw-title { font-size: 24px; font-weight: 800; color: #0f172a; letter-spacing: -0.3px; margin: 0 0 8px; }
.tw-sub { font-size: 13.5px; color: #64748b; line-height: 1.7; margin: 0 0 24px; }
.tw-features {
  display: grid; grid-template-columns: repeat(3, 1fr); gap: 10px;
  text-align: left;
  margin-bottom: 24px;
}
.tw-feat {
  padding: 12px 13px; border-radius: 12px;
  background: #ffffff; border: 1px solid #e2e8f0;
  box-shadow: 0 2px 6px rgba(15,23,42,0.04);
  transition: all .2s ease;
}
.tw-feat:hover { transform: translateY(-2px); box-shadow: 0 8px 20px rgba(15,23,42,0.08); border-color: #c7d2fe; }
.twf-icon { font-size: 18px; margin-bottom: 5px; }
.twf-title { font-size: 12.5px; font-weight: 700; color: #0f172a; margin-bottom: 2px; }
.twf-text { font-size: 11px; color: #64748b; line-height: 1.45; }

/* Input 输入框 */
.tutor-input-box {
  padding: 12px 22px 18px;
  background: rgba(255,255,255,0.85);
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
  border-top: 1px solid rgba(226,232,240,0.85);
  position: relative; z-index: 2;
}
.tiptool-row { display: flex; align-items: center; justify-content: space-between; margin-bottom: 8px; gap: 10px; }
.tiptool-left { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; }
.tiptool-right { display: flex; align-items: center; gap: 6px; }
.tt-chip {
  display: inline-flex; align-items: center; gap: 5px;
  padding: 4px 10px;
  background: #ffffff; border: 1px solid #e2e8f0; border-radius: 8px;
  font-size: 11.5px; color: #475569; font-weight: 600;
  cursor: pointer; transition: all .15s ease;
}
.tt-chip:hover { border-color: #c7d2fe; color: #6366f1; background: #f5f3ff; }
.tt-chip.on { background: linear-gradient(135deg, #6366f1, #8b5cf6); color: #fff; border-color: transparent; box-shadow: 0 3px 8px rgba(99,102,241,0.25); }
.tip-box {
  display: flex; align-items: flex-end; gap: 8px;
  padding: 8px 8px 8px 14px;
  background: #ffffff;
  border: 1.5px solid #e2e8f0;
  border-radius: 14px;
  transition: all .18s ease;
  box-shadow: 0 6px 20px rgba(15,23,42,0.06);
}
.tip-box:focus-within {
  border-color: #6366f1;
  box-shadow: 0 0 0 4px rgba(99,102,241,0.10), 0 10px 28px rgba(99,102,241,0.12);
  transform: translateY(-1px);
}
.tip-ta {
  flex: 1;
  border: none; outline: none; background: transparent; resize: none;
  font-size: 14px; line-height: 1.55; color: #0f172a;
  font-family: inherit;
  padding: 6px 0;
  max-height: 140px;
  min-height: 26px;
}
.tip-ta::placeholder { color: #94a3b8; }
.tip-actions { display: flex; align-items: center; gap: 4px; padding: 2px 0; }
.tip-mic {
  width: 34px; height: 34px; border-radius: 10px;
  border: 1px solid #e2e8f0; background: #f8fafc;
  color: #64748b; cursor: pointer;
  display: inline-flex; align-items: center; justify-content: center;
  font-size: 15px; padding: 0;
  transition: all .15s ease;
  flex-shrink: 0;
}
.tip-mic:hover:not(:disabled) { color: #ec4899; border-color: #fbcfe8; background: #fdf2f8; }
.tip-mic.listening {
  background: linear-gradient(135deg, #ef4444, #ec4899); color: #fff;
  border-color: transparent;
  box-shadow: 0 0 0 5px rgba(239,68,68,0.10), 0 4px 14px rgba(239,68,68,0.3);
  animation: mic-pulse 1s ease-in-out infinite;
}
@keyframes mic-pulse { 0%,100%{ box-shadow: 0 0 0 5px rgba(239,68,68,0.10), 0 4px 14px rgba(239,68,68,0.3);} 50%{ box-shadow: 0 0 0 8px rgba(239,68,68,0.04), 0 4px 14px rgba(239,68,68,0.3);} }
.tip-mic:disabled { opacity: .5; cursor: not-allowed; }

.tip-send {
  width: 38px; height: 38px; border-radius: 11px;
  border: none; cursor: pointer;
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  color: #ffffff;
  display: inline-flex; align-items: center; justify-content: center;
  font-size: 15px; padding: 0; flex-shrink: 0;
  box-shadow: 0 5px 14px rgba(99,102,241,0.35);
  transition: all .18s ease;
}
.tip-send:hover:not(:disabled) { filter: brightness(1.08); transform: translateY(-1px) scale(1.04); box-shadow: 0 8px 22px rgba(99,102,241,0.45); }
.tip-send:disabled { opacity: .5; cursor: not-allowed; transform: none; }

.tip-hint { font-size: 11px; color: #94a3b8; margin-top: 7px; display: flex; align-items: center; justify-content: space-between; }
.tip-hint .kbd {
  display: inline-flex; align-items: center; gap: 2px;
  padding: 1px 6px; border-radius: 4px;
  background: #f1f5f9; border: 1px solid #e2e8f0; border-bottom-width: 2px;
  font-family: 'JetBrains Mono', monospace; font-size: 10.5px; color: #475569; font-weight: 700;
}

/* ---------- RAG 侧栏（迁移自 AIGuide 的 Notebook 面板） ---------- */
.rag-sidepanel {
  width: 290px; flex-shrink: 0;
  border-left: 1px solid #e2e8f0;
  background: linear-gradient(180deg, #ffffff, #f8fafc);
  display: flex; flex-direction: column;
  overflow: hidden;
  position: relative;
}
.rag-header {
  padding: 14px 16px 10px;
  border-bottom: 1px solid #e2e8f0;
  background: rgba(255,255,255,0.8);
}
.rag-title {
  display: flex; align-items: center; justify-content: space-between;
  margin-bottom: 10px;
}
.rt-left { display: flex; align-items: center; gap: 8px; }
.rt-icon {
  width: 28px; height: 28px; border-radius: 8px;
  background: linear-gradient(135deg, #f59e0b, #f97316);
  color: #fff; display: inline-flex; align-items: center; justify-content: center;
  font-size: 14px; box-shadow: 0 3px 8px rgba(245,158,11,0.28);
}
.rt-text { font-size: 14px; font-weight: 800; color: #0f172a; }
.rag-tabs { display: flex; gap: 3px; background: #f1f5f9; padding: 3px; border-radius: 9px; }
.rag-tab {
  flex: 1; padding: 5px 6px; text-align: center;
  font-size: 11.5px; font-weight: 700; color: #64748b;
  border-radius: 6px; cursor: pointer; transition: all .15s ease;
}
.rag-tab:hover { color: #334155; }
.rag-tab.active {
  background: #ffffff;
  color: #0f172a;
  box-shadow: 0 1px 3px rgba(15,23,42,0.08);
}

.rag-body {
  flex: 1;
  overflow-y: auto;
  padding: 12px 14px;
  display: flex; flex-direction: column; gap: 10px;
}
.rag-body::-webkit-scrollbar { width: 6px; }
.rag-body::-webkit-scrollbar-thumb { background: #cbd5e1; border-radius: 3px; }

.rag-card {
  padding: 10px 12px;
  background: #ffffff;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  font-size: 12px; line-height: 1.55; color: #334155;
  transition: all .15s ease;
  position: relative; overflow: hidden;
}
.rag-card:hover { transform: translateY(-1px); box-shadow: 0 6px 14px rgba(15,23,42,0.07); border-color: #cbd5e1; }
.rc-label {
  font-size: 10.5px; font-weight: 800; letter-spacing: 0.5px; text-transform: uppercase;
  color: #6366f1; margin-bottom: 5px;
}
.rag-card.t-code .rc-label { color: #0f766e; }
.rag-card.t-sample .rc-label { color: #92400e; }
.rag-card.t-result .rc-label { color: #dc2626; }
.rc-meta {
  display: flex; align-items: center; justify-content: space-between;
  font-size: 10.5px; color: #94a3b8; margin-top: 6px;
  padding-top: 6px; border-top: 1px dashed #e2e8f0;
}
.rc-badge {
  display: inline-flex; align-items: center; gap: 3px;
  padding: 1.5px 7px; border-radius: 5px;
  font-size: 10px; font-weight: 700;
}
.rc-badge.ok { background: rgba(16,185,129,0.1); color: #047857; }
.rc-badge.fail { background: rgba(239,68,68,0.1); color: #991b1b; }
.rc-badge.info { background: rgba(59,130,246,0.1); color: #1d4ed8; }

.rag-empty {
  padding: 20px 8px; text-align: center;
  color: #94a3b8; font-size: 12px;
}
.rag-empty .re-ico { font-size: 26px; opacity: 0.5; margin-bottom: 6px; }

/* 输入区 */
.rag-input-area {
  padding: 10px 14px 14px;
  border-top: 1px solid #e2e8f0;
  background: rgba(255,255,255,0.85);
}
.ria-ta {
  width: 100%; min-height: 60px; max-height: 120px;
  padding: 8px 10px; border-radius: 9px;
  border: 1.5px solid #e2e8f0; background: #fafbff;
  font-size: 12.5px; line-height: 1.55; color: #0f172a;
  resize: vertical; outline: none; font-family: inherit;
}
.ria-ta:focus { border-color: #f59e0b; background: #ffffff; box-shadow: 0 0 0 3px rgba(245,158,11,0.10); }
.ria-ta::placeholder { color: #94a3b8; font-size: 12px; }
.ria-actions { display: flex; gap: 6px; margin-top: 7px; }
.ria-btn {
  flex: 1; padding: 6px 10px; border-radius: 8px; border: 1px solid #e2e8f0;
  font-size: 11.5px; font-weight: 700; cursor: pointer;
  transition: all .15s ease;
  display: inline-flex; align-items: center; justify-content: center; gap: 4px;
}
.ria-btn.append { background: #ffffff; color: #475569; }
.ria-btn.append:hover { border-color: #cbd5e1; color: #0f172a; }
.ria-btn.ask {
  background: linear-gradient(135deg, #f59e0b, #f97316); color: #fff;
  border-color: transparent; box-shadow: 0 3px 10px rgba(245,158,11,0.3);
}
.ria-btn.ask:hover { filter: brightness(1.08); transform: translateY(-0.5px); box-shadow: 0 5px 14px rgba(245,158,11,0.4); }

/* ---- 响应式：AI助手 ---- */
@media (max-width: 1200px) {
  .metrics-grid { grid-template-columns: repeat(2, 1fr); }
  .cpx-grid-3 { grid-template-columns: 1fr; }
  .tw-features { grid-template-columns: 1fr; }
  .quick-cards { grid-template-columns: repeat(2, 1fr); }
}
@media (max-width: 1024px) {
  .shv2-health { grid-template-columns: 1fr; gap: 20px; }
  .tutor-layout { flex-direction: column; }
  .rag-sidepanel { width: 100%; max-height: 40vh; border-left: none; border-top: 1px solid #e2e8f0; }
  .solution-dots-nav { display: none; }
  .solution-body-v2 { padding: 18px 18px 60px; }
  .solution-hero-v2 { padding: 20px 18px 16px; }
}
@media (max-width: 640px) {
  .quick-cards { grid-template-columns: 1fr; }
  .metrics-grid { grid-template-columns: 1fr 1fr; }
  .section-card { padding: 18px 16px; margin-bottom: 16px; border-radius: 12px; }
  .sc-head { padding-bottom: 10px; margin-bottom: 12px; }
  .sc-title { font-size: 15px; }
  .sc-icon { width: 34px; height: 34px; font-size: 15px; border-radius: 10px; }
  .hero-title { font-size: 18px; }
  .tutor-minimal-header { padding: 10px 14px; flex-wrap: wrap; gap: 6px; }
  .chat-messages { padding: 14px 14px; }
  .chat-input-area { padding: 10px 14px 14px; }
  /* 题解区响应式 */
  .solution-simple-header { padding: 10px 14px; }
  .ssh-title { font-size: 14px; }
  .ssh-btn { padding: 4px 8px; font-size: 11px; }
  .solution-section-v2 { padding: 12px 14px; }
  .ssv2-h { font-size: 14px; }
  .ssv2-index { width: 24px; height: 24px; font-size: 11px; }
  .solution-body { font-size: 13.5px; line-height: 1.85; }
  .complexity-card-v2 { grid-template-columns: 1fr !important; }
  .ref-code { font-size: 12px; }
}

/* ==========================================================================
   题解 Tab 简化版（移除体检报告，纯官方解析）
   ========================================================================== */
.solution-simple-header {
  display: flex; align-items: center; justify-content: space-between;
  padding: 14px 20px;
  border-bottom: 1px solid #e2e8f0;
  background: #fff;
}
.ssh-title { font-size: 16px; font-weight: 800; color: #0f172a; margin: 0; }
.ssh-actions { display: flex; gap: 6px; }
.ssh-btn {
  padding: 5px 12px; border-radius: 8px;
  border: 1px solid #e2e8f0; background: #fff;
  color: #475569; font-size: 12px; font-weight: 600; cursor: pointer;
  transition: all .15s ease;
}
.ssh-btn:hover:not(:disabled) { border-color: #c7d2fe; color: #6366f1; background: #f5f3ff; }
.ssh-btn:disabled { opacity: .5; cursor: not-allowed; }

/* AI 增强指示器细条 */
.solution-enhancing-bar {
  display: flex; align-items: center; gap: 8px;
  padding: 7px 16px;
  background: linear-gradient(90deg, rgba(99,102,241,0.06), rgba(139,92,246,0.06));
  border-bottom: 1px solid #e0e7ff;
}
.seb-spinner {
  width: 14px; height: 14px;
  border: 2px solid #c7d2fe; border-top-color: #6366f1;
  border-radius: 50%; animation: spin 0.7s linear infinite;
}
.seb-text { font-size: 12.5px; color: #6366f1; font-weight: 600; }

.solution-loading-simple {
  padding: 40px 20px; text-align: center;
}
.sls-spinner {
  width: 36px; height: 36px; margin: 0 auto 14px;
  border: 3px solid #e0e7ff; border-top-color: #6366f1;
  border-radius: 50%; animation: spin 0.8s linear infinite;
}
.sls-text { font-size: 14px; color: #475569; font-weight: 600; margin: 0 0 16px; }
.sls-steps { display: flex; flex-wrap: wrap; gap: 8px; justify-content: center; }
.sls-step {
  display: inline-flex; align-items: center; gap: 5px;
  padding: 4px 10px; border-radius: 8px;
  background: #f1f5f9; font-size: 11.5px; color: #94a3b8; font-weight: 600;
}
.sls-step.active { background: #eef2ff; color: #6366f1; }
.sls-step.done { background: rgba(16,185,129,0.1); color: #047857; }
.sls-check { font-size: 12px; }
.sls-num { width: 16px; height: 16px; border-radius: 50%; background: currentColor; color: #fff; display: inline-flex; align-items: center; justify-content: center; font-size: 9px; }
.sls-label { }

.solution-empty-simple, .solution-error-simple {
  padding: 50px 20px; text-align: center;
}
.ses-icon { font-size: 48px; opacity: 0.4; margin-bottom: 14px; }
.ses-icon.error { opacity: 1; }
.ses-title { font-size: 16px; font-weight: 700; color: #0f172a; margin: 0 0 6px; }
.ses-sub { font-size: 13px; color: #64748b; margin: 0 0 18px; }

/* ===== RAG 知识库检索结果折叠栏 ===== */
.rag-panel-chat {
  margin: 0 16px 6px;
  background: #fff;
  border: 1px solid rgba(59,130,246,0.12);
  border-radius: 10px;
  overflow: hidden;
  box-shadow: 0 1px 6px rgba(59,130,246,0.06);
  animation: ragFadeIn 0.3s ease;
}
@keyframes ragFadeIn {
  from { opacity: 0; transform: translateY(6px); }
  to { opacity: 1; transform: translateY(0); }
}
.rag-panel-header {
  display: flex; align-items: center; justify-content: space-between;
  padding: 9px 12px; cursor: pointer; transition: background 0.15s; user-select: none;
}
.rag-panel-header:hover { background: rgba(59,130,246,0.03); }
.rag-panel-left { display: flex; align-items: center; gap: 6px; }
.rag-panel-icon { font-size: 14px; }
.rag-panel-title { font-size: 12px; font-weight: 600; color: #1e293b; }
.rag-panel-count {
  font-size: 10px; color: #3b82f6; background: rgba(59,130,246,0.08);
  padding: 1px 6px; border-radius: 6px;
}
.rag-panel-right { display: flex; align-items: center; gap: 8px; }
.rag-hallucination-badge {
  font-size: 10px; font-weight: 600; padding: 2px 8px; border-radius: 6px;
  font-family: 'JetBrains Mono', monospace; display: inline-flex; align-items: center; gap: 4px;
}
.hallucination-dot { width: 6px; height: 6px; border-radius: 50%; background: currentColor; display: inline-block; }
.rag-hallucination-badge.level-low { color: #16a34a; background: rgba(34,197,94,0.1); }
.rag-hallucination-badge.level-medium { color: #d97706; background: rgba(245,158,11,0.1); }
.rag-hallucination-badge.level-high { color: #dc2626; background: rgba(239,68,68,0.1); }
.rag-score-badge {
  font-size: 10px; font-weight: 600; padding: 2px 8px; border-radius: 6px;
  font-family: 'JetBrains Mono', monospace;
}
.rag-score-badge.score-high { color: #16a34a; background: rgba(34,197,94,0.1); }
.rag-score-badge.score-medium { color: #d97706; background: rgba(245,158,11,0.1); }
.rag-score-badge.score-low { color: #64748b; background: rgba(100,116,139,0.1); }
.rag-toggle-icon { font-size: 10px; color: #94a3b8; transition: transform 0.2s; display: inline-block; }
.rag-toggle-icon.open { transform: rotate(180deg); }
.rag-panel-body {
  border-top: 1px solid rgba(59,130,246,0.06);
  padding: 12px; max-height: 420px; overflow-y: auto;
}
.rag-panel-body::-webkit-scrollbar { width: 4px; }
.rag-panel-body::-webkit-scrollbar-thumb { background: #cbd5e1; border-radius: 4px; }
.rag-metrics-section { display: flex; align-items: stretch; gap: 16px; padding: 4px; }
.rag-metric-circle { flex-shrink: 0; display: flex; align-items: center; justify-content: center; }
.circle-progress {
  position: relative; width: 96px; height: 96px;
  display: flex; align-items: center; justify-content: center; color: #16a34a;
}
.circle-progress.level-medium { color: #d97706; }
.circle-progress.level-high { color: #dc2626; }
.circle-svg { position: absolute; inset: 0; width: 100%; height: 100%; }
.circle-content { position: relative; text-align: center; z-index: 2; }
.circle-value {
  font-size: 20px; font-weight: 800; font-family: 'JetBrains Mono', monospace; line-height: 1.1; color: inherit;
}
.circle-label { font-size: 10px; color: #64748b; margin-top: 2px; font-weight: 500; }
.rag-metric-details { flex: 1; min-width: 0; display: flex; flex-direction: column; justify-content: center; }

/* ===== 右侧简洁可信度描述 ===== */
.rag-credibility-summary {
  flex: 1; min-width: 0; display: flex; flex-direction: column; justify-content: center; gap: 6px;
}
.credibility-status-row { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.credibility-status-tag {
  font-size: 11px; font-weight: 700; padding: 2px 9px; border-radius: 6px;
  font-family: 'JetBrains Mono', monospace; flex-shrink: 0;
}
.credibility-status-tag.level-low { color: #16a34a; background: rgba(34,197,94,0.12); }
.credibility-status-tag.level-medium { color: #d97706; background: rgba(245,158,11,0.12); }
.credibility-status-tag.level-high { color: #dc2626; background: rgba(239,68,68,0.14); }
.credibility-hint { font-size: 11.5px; font-weight: 500; line-height: 1.4; min-width: 0; }
.credibility-hint.level-low { color: #16a34a; }
.credibility-hint.level-medium { color: #d97706; }
.credibility-hint.level-high { color: #dc2626; }
/* 低幻觉率保障机制标识 */
.rag-guard {
  display: inline-flex; align-items: center; gap: 5px; align-self: flex-start;
  font-size: 10.5px; font-weight: 500; color: #475569;
  padding: 3px 8px; border-radius: 6px;
  background: rgba(59,130,246,0.06); border: 1px solid rgba(59,130,246,0.12);
}
.rag-guard .guard-icon { font-size: 11px; }
.rag-guard .guard-threshold { color: #64748b; font-weight: 400; }
.rag-guard.triggered {
  color: #16a34a; background: rgba(34,197,94,0.08); border-color: rgba(34,197,94,0.2);
}
.rag-guard .guard-triggered-tag {
  font-size: 10px; font-weight: 700; color: #16a34a;
  padding-left: 4px; margin-left: 2px; border-left: 1px solid rgba(34,197,94,0.25);
}
.credibility-bar-wrap {
  height: 5px; border-radius: 5px; background: #f1f5f9; overflow: hidden;
}
.credibility-bar-fill { height: 100%; border-radius: 5px; transition: width 0.6s ease; }
.credibility-bar-fill.level-low { background: linear-gradient(90deg, #22c55e, #16a34a); }
.credibility-bar-fill.level-medium { background: linear-gradient(90deg, #f59e0b, #d97706); }
.credibility-bar-fill.level-high { background: linear-gradient(90deg, #f87171, #dc2626); }
.credibility-meta {
  display: flex; align-items: center; gap: 6px; flex-wrap: wrap;
  font-size: 10.5px; color: #64748b;
}
.check-pass { color: #16a34a; font-weight: 600; }
.check-warn { color: #d97706; font-weight: 600; }
.credibility-divider { color: #cbd5e1; }
/* 超阈值时整面板左侧警示条 */
.rag-panel-chat.level-high { border-left: 3px solid #dc2626; }
.rag-panel-chat.level-medium { border-left: 3px solid #f59e0b; }
.rag-panel-chat.level-low { border-left: 3px solid #22c55e; }
.rag-sources-section { margin-top: 10px; padding-top: 10px; border-top: 1px dashed #e2e8f0; }
.rag-chunks-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 8px; padding: 0 2px; }
.rag-chunks-title { font-size: 11px; font-weight: 600; color: #1e293b; }
.rag-chunks-hint { font-size: 9px; color: #94a3b8; }
.rag-chunk-item {
  padding: 10px 12px; background: #f8fafc; border-radius: 8px; margin-bottom: 8px;
  border: 1px solid #e2e8f0; transition: all 0.2s; cursor: pointer;
}
.rag-chunk-item:hover, .rag-chunk-item.expanded {
  background: #fff; border-color: rgba(59,130,246,0.2); box-shadow: 0 2px 8px rgba(59,130,246,0.06);
}
.rag-chunk-item:last-child { margin-bottom: 0; }
.rag-chunk-top { display: flex; align-items: center; justify-content: space-between; margin-bottom: 6px; }
.rag-chunk-top-left { display: flex; align-items: center; gap: 6px; min-width: 0; }
.rag-chunk-idx {
  font-size: 9px; font-weight: 700; color: #3b82f6; background: rgba(59,130,246,0.1);
  padding: 1px 5px; border-radius: 3px; flex-shrink: 0; font-family: 'JetBrains Mono', monospace;
}
.rag-chunk-source {
  font-size: 10px; color: #64748b; font-weight: 500;
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.rag-chunk-score {
  font-size: 10px; font-weight: 700; padding: 2px 6px; border-radius: 4px;
  font-family: 'JetBrains Mono', monospace; flex-shrink: 0;
}
.rag-chunk-score.score-high { color: #16a34a; background: rgba(34,197,94,0.1); }
.rag-chunk-score.score-medium { color: #d97706; background: rgba(245,158,11,0.1); }
.rag-chunk-score.score-low { color: #64748b; background: rgba(100,116,139,0.1); }
.rag-chunk-text {
  font-size: 11.5px; color: #334155; line-height: 1.65; text-align: justify;
  display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; transition: all 0.2s;
}
.rag-chunk-text.full-text { -webkit-line-clamp: unset; display: block; overflow: visible; white-space: pre-wrap; }
.rag-chunk-bottom { display: flex; align-items: flex-end; justify-content: space-between; gap: 8px; margin-top: 6px; }
.rag-chunk-kp {
  font-size: 10px; color: #7c3aed; background: rgba(124,58,237,0.06);
  padding: 2px 6px; border-radius: 4px; line-height: 1.4; flex: 1; min-width: 0;
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.rag-chunk-expand-hint { font-size: 9px; color: #94a3b8; flex-shrink: 0; font-weight: 500; }
.rag-chunk-item.expanded .rag-chunk-expand-hint { color: #3b82f6; }
.rag-slide-enter-active, .rag-slide-leave-active { transition: all 0.3s ease; }
.rag-slide-enter-from, .rag-slide-leave-to { opacity: 0; transform: translateY(8px); max-height: 0; }

.tutor-quick-row {
  display: flex; gap: 6px; padding: 8px 16px;
  overflow-x: auto;
  border-top: 1px solid #f1f5f9;
  background: #fafbff;
}
.tutor-quick-row::-webkit-scrollbar { height: 4px; }
.tutor-quick-row::-webkit-scrollbar-thumb { background: #cbd5e1; border-radius: 2px; }
.tqr-chip {
  flex-shrink: 0;
  padding: 5px 12px;
  border-radius: 99px;
  border: 1px solid #e2e8f0;
  background: #fff;
  color: #475569; font-size: 12px; font-weight: 600;
  cursor: pointer; transition: all .15s ease;
  white-space: nowrap;
}
.tqr-chip:hover { border-color: #c7d2fe; color: #6366f1; background: #f5f3ff; transform: translateY(-1px); }

/* ==========================================================================
   题解区主题色 Token（WCAG AA 高对比度）
   ========================================================================== */
.code-workbench {
  /* 思路点拨卡片 - 高对比靛蓝系 */
  --sol-thought-bg: linear-gradient(135deg, #eef2ff 0%, #e0e7ff 100%);
  --sol-thought-border: #c7d2fe;
  --sol-thought-text: #1e1b4b;            /* 正文（对比 #eef2ff = 14.2:1） */
  --sol-thought-strong: #4338ca;          /* 强调色（对比白底 8.1:1） */
  --sol-thought-code-bg: #fdf2f8;          /* 行内代码背景 */
  --sol-thought-code-fg: #9d174d;          /* 行内代码前景（对比 9.5:1） */
  --sol-thought-code-border: #f9a8d4;

  /* 参考代码卡片 - 深色高对比 Catppuccin Mocha */
  --sol-code-window-bg: #181825;
  --sol-code-window-border: #313244;
  --sol-code-toolbar-bg: #1e1e2e;
  --sol-code-toolbar-text: #a6adc8;        /* 文件名（对比 5.8:1） */
  --sol-code-toolbar-accent: #89b4fa;      /* 按钮高亮（对比 5.6:1） */
  --sol-code-bg: #11111b;
  --sol-code-text: #cdd6f4;                /* 代码正文（对比 12.3:1） */
  --sol-code-accent: #f38ba8;              /* 交通灯红 */
  --sol-code-warn: #f9e2af;                /* 交通灯黄 */
  --sol-code-ok: #a6e3a1;                  /* 交通灯绿 */

  /* 板块标题栏 - 高对比深靛 */
  --sol-hbar-bg: linear-gradient(90deg, rgba(99,102,241,0.12), rgba(139,92,246,0.06));
  --sol-hbar-border: #e0e7ff;
  --sol-h-text: #0f172a;                   /* h3 标题（对比 15.8:1） */
  --sol-h-index-bg: linear-gradient(135deg, #6366f1, #8b5cf6);
  --sol-h-index-text: #ffffff;             /* 编号白字（对比 6.8:1） */
  --sol-h-emoji-bg: #ffffff;
  --sol-h-emoji-shadow: 0 2px 8px rgba(99,102,241,0.22);
  --sol-code-meta: #475569;                /* 代码行数字段（对比 8.6:1） */
  --sol-copy-btn-bg: #ffffff;
  --sol-copy-btn-border: #e2e8f0;
  --sol-copy-btn-fg: #0f172a;
}

/* ===== 板块 section ===== */
.solution-section-v2 {
  padding: 18px 22px 14px;
  margin-bottom: 18px;
  background: #ffffff;
  border: 1px solid var(--sol-hbar-border);
  border-radius: 14px;
  box-shadow: 0 2px 10px rgba(15,23,42,0.05);
  animation: solFadeIn 0.45s cubic-bezier(0.4,0,0.2,1) both;
}
@keyframes solFadeIn {
  from { opacity: 0; transform: translateY(8px); }
  to { opacity: 1; transform: translateY(0); }
}

/* ===== h3 标题栏（ssv2） ===== */
.ssv2-hbar {
  display: flex; align-items: center; gap: 12px;
  padding: 10px 12px;
  margin-bottom: 14px;
  background: var(--sol-hbar-bg);
  border: 1px solid var(--sol-hbar-border);
  border-radius: 10px;
}
.ssv2-index {
  width: 32px; height: 32px; flex-shrink: 0;
  display: inline-flex; align-items: center; justify-content: center;
  background: var(--sol-h-index-bg);
  color: var(--sol-h-index-text);
  border-radius: 9px;
  font-family: 'JetBrains Mono', monospace;
  font-size: 13px; font-weight: 800;
  letter-spacing: 0.2px;
  box-shadow: 0 4px 10px rgba(99,102,241,0.32);
}
.solution-h.ssv2-h {
  font-size: 16.5px; font-weight: 800;
  color: var(--sol-h-text);
  margin: 0; padding: 0;
  background: none;
  border: none;
  border-radius: 0;
  letter-spacing: 0.2px;
  display: flex; align-items: center; gap: 10px;
  flex: 1;
}
.solution-h.ssv2-h .sh-emoji {
  width: 30px; height: 30px;
  background: var(--sol-h-emoji-bg);
  border-radius: 8px;
  box-shadow: var(--sol-h-emoji-shadow);
}
.solution-h.ssv2-h .code-meta {
  font-size: 11.5px;
  color: var(--sol-code-meta);
  font-weight: 600;
  font-family: 'JetBrains Mono', monospace;
  padding: 2px 8px;
  background: #f1f5f9;
  border-radius: 999px;
  margin-left: 4px;
}
.copy-ref-btn {
  margin-left: auto;
  padding: 4px 12px;
  background: var(--sol-copy-btn-bg);
  border: 1px solid var(--sol-copy-btn-border);
  color: var(--sol-copy-btn-fg);
  font-size: 11.5px; font-weight: 600;
  border-radius: 7px;
  cursor: pointer;
  transition: all 0.15s ease;
  display: inline-flex; align-items: center; gap: 5px;
}
.copy-ref-btn:hover {
  border-color: #10b981;
  color: #065f46;
  background: #ecfdf5;
  box-shadow: 0 2px 8px rgba(16,185,129,0.15);
}

/* ===== 思路点拨卡片 ===== */
.ssv2-card.thought-card {
  background: var(--sol-thought-bg);
  border: 1px solid var(--sol-thought-border);
  border-radius: 12px;
  padding: 18px 22px;
  color: var(--sol-thought-text);
  box-shadow: inset 0 1px 0 rgba(255,255,255,0.7),
              0 2px 12px rgba(99,102,241,0.08);
}
.ssv2-body.thought-card .thought-steps {
  color: var(--sol-thought-text);
  font-size: 14.5px; line-height: 2;
}
.thought-card :deep(p) { color: var(--sol-thought-text) !important; }
.thought-card :deep(strong) {
  color: var(--sol-thought-strong) !important;
  background: linear-gradient(180deg, transparent 60%, rgba(99,102,241,0.18) 60%);
  padding: 0 2px;
  font-weight: 800;
}
.thought-card :deep(li) { color: var(--sol-thought-text) !important; }
.thought-card :deep(li::marker) { color: var(--sol-thought-strong) !important; font-weight: 800; }
.thought-card :deep(code) {
  background: var(--sol-thought-code-bg) !important;
  color: var(--sol-thought-code-fg) !important;
  border: 1px solid var(--sol-thought-code-border) !important;
  padding: 2px 8px;
  border-radius: 6px;
  font-weight: 600;
}
.thought-card :deep(pre) {
  background: #0f172a !important;
  border: 1px solid #1e293b !important;
  border-radius: 10px !important;
  padding: 14px 18px !important;
  box-shadow: 0 4px 16px rgba(15,23,42,0.25) !important;
}
.thought-card :deep(pre code) {
  background: transparent !important;
  border: none !important;
  color: #e2e8f0 !important;
  padding: 0 !important;
  font-weight: 400;
}

/* ===== 参考代码 v2 容器 ===== */
.ref-code-wrap-v2 {
  background: var(--sol-code-bg);
  border: 1px solid var(--sol-code-window-border);
  border-radius: 13px;
  overflow: hidden;
  box-shadow: 0 10px 32px rgba(15,23,42,0.45);
}
.rcv2-window {
  display: flex; align-items: center; gap: 12px;
  padding: 10px 16px;
  background: var(--sol-code-window-bg);
  border-bottom: 1px solid var(--sol-code-window-border);
}
.rcv2-traffic { display: inline-flex; gap: 7px; align-items: center; }
.rcv2-traffic .rct {
  width: 13px; height: 13px; border-radius: 50%;
  cursor: pointer;
  transition: transform 0.15s ease, filter 0.15s ease;
  display: inline-block;
}
.rcv2-traffic .rct.r { background: var(--sol-code-accent); box-shadow: 0 0 0 1px rgba(243,139,168,0.3); }
.rcv2-traffic .rct.y { background: var(--sol-code-warn); box-shadow: 0 0 0 1px rgba(249,226,175,0.3); }
.rcv2-traffic .rct.g { background: var(--sol-code-ok); box-shadow: 0 0 0 1px rgba(166,227,161,0.3); }
.rcv2-traffic .rct:hover { transform: scale(1.18); filter: brightness(1.12); }
.rcv2-filename {
  flex: 1; min-width: 0;
  display: inline-flex; align-items: center; gap: 6px;
  font-family: 'JetBrains Mono', Consolas, monospace;
  font-size: 12px; font-weight: 600;
  color: var(--sol-code-toolbar-text);
  letter-spacing: 0.1px;
}
.rcv2-filename svg { flex-shrink: 0; color: var(--sol-code-toolbar-accent); }
.rcv2-actions { flex-shrink: 0; }
.rcv2-btn {
  display: inline-flex; align-items: center; gap: 5px;
  padding: 4px 12px;
  font-size: 11.5px; font-weight: 600;
  color: var(--sol-code-toolbar-accent);
  background: rgba(137,180,250,0.1);
  border: 1px solid rgba(137,180,250,0.25);
  border-radius: 7px;
  cursor: pointer;
  transition: all 0.15s ease;
}
.rcv2-btn:hover {
  color: #fff;
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  border-color: transparent;
  box-shadow: 0 4px 12px rgba(99,102,241,0.35);
}
.ref-code-wrap-v2 .ref-code {
  margin: 0;
  padding: 20px 22px;
  background: var(--sol-code-bg);
  color: var(--sol-code-text);
  font-family: 'JetBrains Mono', Consolas, monospace;
  font-size: 13.5px; line-height: 1.85;
  font-weight: 500;
  letter-spacing: 0.15px;
  overflow-x: auto;
  max-height: 560px;
}
.ref-code-wrap-v2 .ref-code::-webkit-scrollbar { width: 10px; height: 10px; }
.ref-code-wrap-v2 .ref-code::-webkit-scrollbar-track { background: transparent; }
.ref-code-wrap-v2 .ref-code::-webkit-scrollbar-thumb {
  background: rgba(148,163,184,0.45);
  border-radius: 5px;
  border: 2px solid var(--sol-code-bg);
}
.ref-code-wrap-v2 .ref-code::-webkit-scrollbar-thumb:hover { background: rgba(148,163,184,0.65); }

/* ===== 空状态/错误态 - 可访问性增强 ===== */
.solution-empty-simple, .solution-error-simple {
  background: linear-gradient(180deg, #fafbff, #f5f3ff);
  border: 1px dashed #c7d2fe;
  border-radius: 14px;
  margin: 20px 0;
}
.ses-title {
  font-size: 16px; font-weight: 800;
  color: #0f172a !important;
}
.ses-sub {
  font-size: 13px;
  color: #334155 !important;
}
.se-btn.big {
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  color: #ffffff;
  border: none;
  padding: 10px 22px;
  border-radius: 10px;
  font-size: 13.5px; font-weight: 700;
  cursor: pointer;
  box-shadow: 0 6px 16px rgba(99,102,241,0.35);
  transition: all 0.18s ease;
}
.se-btn.big:hover {
  transform: translateY(-1px);
  box-shadow: 0 10px 24px rgba(99,102,241,0.42);
  filter: brightness(1.05);
}

/* ===== 响应式小屏适配 ===== */
@media (max-width: 768px) {
  .solution-section-v2 { padding: 14px 14px; }
  .ssv2-hbar { padding: 8px 10px; gap: 8px; }
  .ssv2-index { width: 28px; height: 28px; font-size: 12px; }
  .solution-h.ssv2-h { font-size: 14.5px; }
  .solution-h.ssv2-h .code-meta { font-size: 10.5px; padding: 1px 6px; }
  .ssv2-card.thought-card { padding: 14px 16px; }
  .ref-code-wrap-v2 .ref-code { font-size: 12px; padding: 14px 14px; }
  .rcv2-btn { padding: 3px 8px; font-size: 10.5px; }
}

</style>
