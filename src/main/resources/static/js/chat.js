/**
 * 鑱婂ぉ涓婚〉閫昏緫锛堢嫭绔嬮〉闈紝涓嶅啀娣?Skills 绠＄悊锛夈€?
 *
 * 渚濊禆锛?
 *   - common.js 鏆撮湶鐨?window.DA锛坱oken / api / auth / theme / toast / renderHeader锛?
 *   - Vue 3 / marked / DOMPurify / highlight.js 閫氳繃 CDN 寮曞叆
 *
 * 澶勭悊娴佸紡浜嬩欢锛圥ascalCase 绫诲瀷锛夛細
 *   AgentStart / Thinking / Text / ToolStart / ToolEnd /
 *   TodoProgress / StageOutput / Error / Complete / Paused
 */
const { createApp, ref, nextTick, onMounted, watch } = Vue;

// 娴佸紡浜嬩欢绫诲瀷
const DA_STREAM_EVENTS = {
    AGENT_START: 'AgentStart',
    THINKING: 'Thinking',
    TEXT: 'Text',
    TOOL_START: 'ToolStart',
    TOOL_END: 'ToolEnd',
    TODO_PROGRESS: 'TodoProgress',
    STAGE_OUTPUT: 'StageOutput',
    ERROR: 'Error',
    COMPLETE: 'Complete',
    PAUSED: 'Paused'
};

// todo 鏂囨湰鍏抽敭璇?鈫?宸插畬鎴?tool 鏄犲皠锛堢敤浜庢帹鏂垝鎺夌姸鎬侊級
const TODO_TOOL_MAP = [
    { keys: ['schema', 'tables', 'columns', 'describe', 'list'], tools: ['exploreSchema', 'describeTables', 'listTables'] },
    { keys: ['glossary', 'terms', 'dictionary', 'lookup'], tools: ['lookupGlossary'] },
    { keys: ['chart', 'visualize', 'trend', 'plot', 'graph', 'diagram'], tools: ['generate_chart', 'generateChart'] },
    { keys: ['calculate', 'python', 'ratio', 'formula'], tools: ['bash'] },
    { keys: ['validate', 'verify', 'check'], tools: ['validateSql'] },
    { keys: ['sql', 'query', 'search', 'execute', 'stats'], tools: ['executeSql'] },
    { keys: ['search', 'web', 'tavily'], tools: ['tavily_search', 'tavily-search'] },
    { keys: ['file', 'read', 'analyze'], tools: ['analyzeFile'] }
];

createApp({
    setup() {
        // ==================== 鐘舵€?====================
        const messages = ref([]);
        const input = ref('');
        const sending = ref(false);
        const conversationId = ref(getOrCreateConvId());
        const previewUrl = ref('');
        const messagesContainer = ref(null);
        const scroller = ref(null);   // 鐪熸鐨勬粴鍔ㄥ鍣?.da-main
        const textareaRef = ref(null);
        const fileInputRef = ref(null);
        let streamController = null;

        // ==================== 璇煶杈撳叆 ====================
        const isListening = ref(false);
        const voiceSupported = ref(false);
        let recognition = null;
        let finalTranscript = '';

        const initVoice = () => {
            const SR = window.SpeechRecognition || window.webkitSpeechRecognition;
            if (!SR) {
                voiceSupported.value = false;
                return;
            }
            voiceSupported.value = true;
            recognition = new SR();
            recognition.lang = 'zh-CN';
            recognition.continuous = true;
            recognition.interimResults = true;

            recognition.onresult = (event) => {
                let interim = '';
                for (let i = event.resultIndex; i < event.results.length; i++) {
                    const transcript = event.results[i][0].transcript;
                    if (event.results[i].isFinal) {
                        finalTranscript += transcript;
                    } else {
                        interim += transcript;
                    }
                }
                input.value = finalTranscript + interim;
            };

            recognition.onerror = (e) => {
                console.warn('[voice] 璇嗗埆閿欒:', e.error);
                isListening.value = false;
            };

            recognition.onend = () => {
                if (isListening.value) {
                    isListening.value = false;
                    const text = input.value.trim();
                    if (text) sendMessage();
                }
            };
        };

        const toggleVoice = () => {
            if (!voiceSupported.value) {
                DA.showToast('褰撳墠娴忚鍣ㄤ笉鏀寔璇煶杈撳叆锛岃浣跨敤 Chrome 鎴?Edge');
                return;
            }
            if (isListening.value) {
                recognition.stop();
            } else {
                finalTranscript = input.value ? input.value : '';
                try {
                    recognition.start();
                    isListening.value = true;
                } catch (e) {
                    console.warn('[voice] 鍚姩澶辫触:', e);
                }
            }
        };

        // 鍘嗗彶浼氳瘽鍒楄〃锛堜晶鏍忓睍绀猴紝鍒嗛〉 + 婊氬姩鍔犺浇鏇村锛?
        const conversations = ref([]);
        const conversationsPage = ref(0);
        const conversationsTotal = ref(0);
        const conversationsHasMore = ref(false);
        const conversationsLoading = ref(false);
        const CONV_PAGE_SIZE = 10;

        // 宸ュ叿鏍忕姸鎬侊細鑱旂綉寮€鍏?+ 鏂囨。涓婁紶
        const onlineEnabled = ref(false);
        const uploading = ref(false);
        const uploadedFiles = ref([]);
        const isDragging = ref(false);
        const MAX_FILES = 3;
        const MAX_SIZE_MB = 100;

        // 鏅鸿兘鑷姩婊氬姩锛氭祦寮忚拷鍔犳椂鍙湪 autoScroll=true 鏃舵粴鍒板簳
        const autoScroll = ref(true);
        const SCROLL_BOTTOM_THRESHOLD = 80;

        // 渚ф爮鎶樺彔鐘舵€侊紙鎸佷箙鍖栧埌 localStorage锛?
        const sidebarCollapsed = ref(localStorage.getItem('da-sidebar-collapsed') === 'true');
        const toggleSidebar = () => {
            sidebarCollapsed.value = !sidebarCollapsed.value;
            try { localStorage.setItem('da-sidebar-collapsed', String(sidebarCollapsed.value)); } catch (e) { /* ignore */ }
        };

        // 鎺ㄨ崘闂        const suggestions = [
            { text: '你有哪些技能？' },
            { text: '帮我探索数据库表结构' },
            { text: '分析销售数据趋势' },
            { text: '生成一份可视化图表' }
        ];

        // ==================== 閫氱敤宸ュ叿 ====================
        const generateId = () => 'da_' + Date.now() + '_' + Math.random().toString(36).slice(2, 9);

        function getOrCreateConvId() {
            const url = new URL(window.location.href);
            return url.searchParams.get('conversationId')
                || ('checkdata_conv_' + Date.now() + '_' + Math.random().toString(36).slice(2, 8));
        }

        const scrollToBottom = () => {
            nextTick(() => {
                const el = scroller.value;
                if (!el || !autoScroll.value) return;
                el.scrollTop = el.scrollHeight;
            });
        };

        const onMessagesScroll = () => {
            const el = scroller.value;
            if (!el) return;
            const distanceToBottom = el.scrollHeight - el.scrollTop - el.clientHeight;
            autoScroll.value = distanceToBottom < SCROLL_BOTTOM_THRESHOLD;
        };

        const scrollToBottomManual = () => {
            autoScroll.value = true;
            nextTick(() => {
                const el = scroller.value;
                if (el) el.scrollTop = el.scrollHeight;
            });
        };

        const previewImage = (url) => { previewUrl.value = url; };

        // ==================== Markdown ====================
        const setupMarkdown = () => {
            if (typeof DOMPurify !== 'undefined' && !DOMPurify.__daLinkHookAdded) {
                DOMPurify.addHook('afterSanitizeAttributes', (node) => {
                    if (node && node.nodeName === 'A') {
                        node.setAttribute('target', '_blank');
                        node.setAttribute('rel', 'noopener noreferrer nofollow');
                    }
                });
                DOMPurify.__daLinkHookAdded = true;
            }
            if (typeof marked !== 'undefined' && typeof hljs !== 'undefined') {
                marked.setOptions({
                    highlight(code, lang) {
                        if (lang && hljs.getLanguage(lang)) {
                            try { return hljs.highlight(code, { language: lang }).value; } catch (e) {}
                        }
                        return hljs.highlightAuto(code).value;
                    },
                    breaks: true,
                    gfm: true,
                    sanitize: false
                });
            }
        };

        const renderMarkdown = (content) => {
            if (!content) return '';
            if (typeof marked === 'undefined') return content;
            const processed = content
                .replace(/\\n/g, '\n')
                .replace(/\\r\\n/g, '\n')
                .replace(/\\r/g, '\n');
            const html = marked.parse(processed);
            return typeof DOMPurify !== 'undefined' ? DOMPurify.sanitize(html) : html;
        };

        // ==================== 鏃堕棿绾?/ Todo 杈呭姪 ====================
        const dotClass = (item) => {
            if (item.type === 'thinking') return 'thinking';
            if (item.type === 'text') return 'text';
            if (item.type === 'error') return 'error';
            if (item.type === 'todo') return 'todo';
            return item.status || 'running';
        };

        const todoIcon = (status) => {
            if (status === 'completed') return 'fas fa-check-circle';
            if (status === 'in_progress') return 'fas fa-circle-half-stroke';
            return 'far fa-circle';
        };

        const completedCount = (items) =>
            (items || []).filter(t => t.status === 'completed').length;

        // ==================== 宸ュ叿缁撴灉瑙ｆ瀽 ====================
        const safeJsonParse = (s, fallback) => {
            try { return JSON.parse(s); } catch (e) { return fallback; }
        };

        const formatArguments = (argStr) => {
            if (!argStr) return '';
            const obj = safeJsonParse(argStr, null);
            if (obj && typeof obj === 'object') return JSON.stringify(obj, null, 2);
            return argStr;
        };

        // 瑙ｆ瀽 generate_chart 杩斿洖鏂囨湰閲岀殑鍥剧墖 URL
        const parseChartUrl = (text) => {
            if (!text) return null;
            const urlMatch = text.match(/https?:\/\/[^\s)'"]+\.(?:png|jpg|jpeg|gif|webp)/i);
            if (urlMatch) {
                const titleMatch = text.match(/title\s*[:锛歖\s*([^\n]+)/i);
                return { url: urlMatch[0], title: titleMatch ? titleMatch[1].trim() : '鍥捐〃' };
            }
            const md = text.match(/!\[([^\]]*)\]\(([^)]+)\)/);
            if (md) return { url: md[2], title: md[1] || '鍥捐〃' };
            return null;
        };

        // 鍩轰簬宸插畬鎴愮殑 tool 鎺ㄦ柇 todo 鐘舵€?
        const inferTodoStatus = (todo, completedTools) => {
            if (todo.status === 'completed') return 'completed';
            const text = (todo.text || todo.content || todo.task || '').toLowerCase();
            for (const mapping of TODO_TOOL_MAP) {
                if (mapping.keys.some(k => text.includes(k.toLowerCase()))) {
                    if (mapping.tools.some(t => completedTools.has(t))) {
                        return 'completed';
                    }
                }
            }
            return todo.status || 'pending';
        };

        // ==================== 娑堟伅鏋勫缓 ====================
        const newAssistantMsg = () => {
            const msg = {
                id: generateId(),
                role: 'assistant',
                content: '',
                timeline: [],
                charts: [],
                loading: true,
                timestamp: Date.now()
            };
            messages.value.push(msg);
            return messages.value[messages.value.length - 1];
        };

        // ==================== 浜嬩欢鍒嗗彂 ====================
        const buildTodoSnapshot = (items, aiMsg) => {
            const completedTools = new Set(
                aiMsg.timeline
                    .filter(it => it.type === 'tool' && it.status === 'completed')
                    .map(it => it.toolName)
            );
            return items.map(it => {
                const todo = {
                    text: it.text || it.content || it.task || '',
                    status: it.status || 'pending'
                };
                if (todo.status !== 'completed') {
                    todo.status = inferTodoStatus(todo, completedTools);
                }
                return todo;
            });
        };

        const dispatchToolResult = (toolName, resultText, aiMsg) => {
            const lname = (toolName || '').toLowerCase();
            if (lname === 'generate_chart' || lname === 'generatechart') {
                const chart = parseChartUrl(resultText);
                if (chart) aiMsg.charts.push(chart);
            }
        };

        const dispatchStageOutput = (stage, data, aiMsg) => {
            const payload = (data && typeof data === 'object' && data.content !== undefined)
                ? data.content : data;
            switch (stage) {
                case 'chart_image': {
                    const obj = typeof payload === 'string' ? safeJsonParse(payload, {}) : (payload || {});
                    if (obj.url) {
                        aiMsg.charts.push({ url: obj.url, title: obj.title || '鍥捐〃', type: obj.type || '' });
                    }
                    break;
                }
                case 'report':
                    aiMsg.report = typeof payload === 'string' ? payload : (payload.content || '');
                    break;
                case 'recommend': {
                    const arr = typeof payload === 'string' ? safeJsonParse(payload, []) : (payload || []);
                    if (Array.isArray(arr)) {
                        aiMsg.recommend = arr.map(x =>
                            typeof x === 'string' ? x : (x.question || x.text || '')
                        );
                    }
                    break;
                }
                default:
                    console.debug('[chat] 蹇界暐 stage', stage);
            }
        };

        const processStreamEvent = (event, aiMsg) => {
            switch (event.type) {
                case DA_STREAM_EVENTS.AGENT_START:
                    aiMsg.loading = true;
                    break;

                case DA_STREAM_EVENTS.THINKING: {
                    const content = event.content || '';
                    const last = aiMsg.timeline[aiMsg.timeline.length - 1];
                    if (last && last.type === 'thinking') {
                        last.content += content;
                    } else {
                        aiMsg.timeline.push({ type: 'thinking', content });
                    }
                    break;
                }

                case DA_STREAM_EVENTS.TEXT: {
                    const content = event.content || '';
                    if (!content) break;
                    const last = aiMsg.timeline[aiMsg.timeline.length - 1];
                    if (last && last.type === 'text') {
                        last.content += content;
                    } else {
                        aiMsg.timeline.push({ type: 'text', content });
                    }
                    break;
                }

                case DA_STREAM_EVENTS.TOOL_START: {
                    // TodoWrite 璧扮嫭绔?todo 闈㈡澘锛屼笉杩?tool 鍗＄墖
                    if ((event.toolName || '').toLowerCase() === 'todowrite') {
                        const args = safeJsonParse(event.arguments || '', {});
                        const todos = Array.isArray(args.todos) ? args.todos : [];
                        if (todos.length) {
                            aiMsg.timeline.push({
                                type: 'todo',
                                items: buildTodoSnapshot(todos, aiMsg)
                            });
                        }
                        break;
                    }
                    aiMsg.timeline.push({
                        type: 'tool',
                        toolName: event.toolName || 'unknown',
                        toolCallId: event.toolCallId || '',
                        arguments: event.arguments || '',
                        status: 'running',
                        result: '',
                        showResult: false
                    });
                    break;
                }

                case DA_STREAM_EVENTS.TOOL_END: {
                    if ((event.toolName || '').toLowerCase() === 'todowrite') break;
                    const toolCallId = event.toolCallId || '';
                    const entry = aiMsg.timeline.find(it =>
                        it.type === 'tool' && it.toolCallId === toolCallId && it.status === 'running'
                    );
                    if (entry) {
                        entry.status = 'completed';
                        entry.result = event.result || '';
                    } else {
                        aiMsg.timeline.push({
                            type: 'tool',
                            toolName: event.toolName || 'unknown',
                            toolCallId,
                            arguments: '',
                            status: 'completed',
                            result: event.result || '',
                            showResult: false
                        });
                    }
                    dispatchToolResult(event.toolName, event.result || '', aiMsg);
                    break;
                }

                case DA_STREAM_EVENTS.TODO_PROGRESS: {
                    const items = Array.isArray(event.items) ? event.items : [];
                    aiMsg.timeline.push({
                        type: 'todo',
                        items: buildTodoSnapshot(items, aiMsg)
                    });
                    break;
                }

                case DA_STREAM_EVENTS.STAGE_OUTPUT:
                    dispatchStageOutput(event.stage, event.data, aiMsg);
                    break;

                case DA_STREAM_EVENTS.ERROR:
                    aiMsg.timeline.push({
                        type: 'error',
                        message: event.message || '鏈煡閿欒',
                        detail: event.detail || ''
                    });
                    break;

                case DA_STREAM_EVENTS.COMPLETE:
                    aiMsg.loading = false;
                    break;

                case DA_STREAM_EVENTS.PAUSED:
                    break;

                default:
                    console.debug('[chat] 鏈煡浜嬩欢绫诲瀷', event);
            }
        };

        // ==================== 鍙戦€佹秷鎭紙SSE锛?====================
        const sendMessage = async (presetText) => {
            const text = (presetText !== undefined ? presetText : input.value).toString().trim();
            if (!text || sending.value) return;

            const successFiles = uploadedFiles.value
                .filter(f => f.status === 'SUCCESS' && f.fileId)
                .map(f => ({
                    fileId: f.fileId,
                    fileName: f.fileName,
                    fileType: f.fileType,
                    fileSize: f.fileSize,
                    previewUrl: f.previewUrl || ''
                }));

            messages.value.push({
                id: generateId(),
                role: 'user',
                content: text,
                attachments: successFiles,
                timestamp: Date.now()
            });

            if (presetText === undefined) input.value = '';
            if (textareaRef.value) textareaRef.value.style.height = 'auto';

            const aiMsg = newAssistantMsg();
            autoScroll.value = true;
            scrollToBottom();
            sending.value = true;

            uploadedFiles.value = [];
            uploading.value = false;

            // 鍗?ReactAgent 缁熶竴鍏ュ彛锛歅OST /agent/stream
            const fileIds = successFiles.map(f => f.fileId);
            const requestBody = {
                query: text,
                conversationId: conversationId.value
            };
            if (onlineEnabled.value) {
                requestBody.online = true;
            }
            if (fileIds.length > 0) {
                requestBody.fileIds = fileIds;
            }

            streamController = DA.apiStream('/agent/stream', requestBody, {
                onEvent(eventData, eventName) {
                    // 浜嬩欢 data 鏄?JSON 瀛楃涓?
                    let event = eventData;
                    if (typeof eventData === 'string') {
                        event = safeJsonParse(eventData, null);
                        if (!event) {
                            // 涓嶆槸 JSON锛屾寜 event name 鏋勯€犵畝鍗曚簨浠?
                            event = { type: eventName, content: eventData };
                        }
                    }
                    if (event && event.type) {
                        processStreamEvent(event, aiMsg);
                        scrollToBottom();
                    }
                },
                onError(err) {
                    console.error('[chat] 娴佸紡璇锋眰澶辫触', err);
                    aiMsg.timeline.push({
                        type: 'error',
                        message: '璇锋眰澶辫触',
                        detail: err.message || ''
                    });
                    aiMsg.loading = false;
                    sending.value = false;
                    streamController = null;
                },
                onClose() {
                    aiMsg.loading = false;
                    sending.value = false;
                    streamController = null;
                    // 妗嗘灦宸茶嚜鍔ㄦ寔涔呭寲 timeline锛屽埛鏂颁晶鏍忓巻鍙插垪琛?
                    loadConversations();
                    scrollToBottom();
                }
            });
        };

        const stopMessage = async () => {
            if (!sending.value) return;
            try {
                await DA.apiGet('/agent/stop', { conversationId: conversationId.value });
            } catch (e) { /* ignore */ }
            if (streamController) {
                streamController.abort();
                streamController = null;
            }
            sending.value = false;
            const lastAi = [...messages.value].reverse().find(m => m.role === 'assistant');
            if (lastAi) lastAi.loading = false;
        };

        const newConversation = () => {
            if (sending.value) {
                if (streamController) streamController.abort();
                sending.value = false;
            }
            // 閲婃斁鏈湴 object URL
            messages.value.forEach(m => {
                if (m.attachments) m.attachments.forEach(a => {
                    if (a.previewUrl) { try { URL.revokeObjectURL(a.previewUrl); } catch (e) { /* ignore */ } }
                });
            });
            uploadedFiles.value.forEach(f => {
                if (f.previewUrl) { try { URL.revokeObjectURL(f.previewUrl); } catch (e) { /* ignore */ } }
            });
            messages.value = [];
            uploadedFiles.value = [];
            uploading.value = false;
            const newCid = 'checkdata_conv_' + Date.now() + '_' + Math.random().toString(36).slice(2, 8);
            conversationId.value = newCid;
            const u = new URL(window.location.href);
            u.searchParams.set('conversationId', newCid);
            window.history.replaceState({}, '', u.toString());
        };

        // ==================== 鍘嗗彶浼氳瘽 ====================
        const loadConversations = async (opts) => {
            const reset = !opts || opts.reset;
            const page = reset ? 0 : conversationsPage.value + 1;
            if (conversationsLoading.value) return;
            if (!reset && !conversationsHasMore.value) return;
            conversationsLoading.value = true;
            try {
                const body = await DA.apiGet('/session/list', { page, size: CONV_PAGE_SIZE });
                const p = body && body.data ? body.data : null;
                const items = p && Array.isArray(p.conversations) ? p.conversations : [];
                if (reset) {
                    conversations.value = items;
                } else {
                    const existIds = new Set(conversations.value.map(c => c.conversationId));
                    conversations.value = conversations.value.concat(
                        items.filter(c => !existIds.has(c.conversationId))
                    );
                }
                conversationsPage.value = p ? p.page : page;
                conversationsTotal.value = p ? p.total : items.length;
                conversationsHasMore.value = p ? p.hasMore : false;
            } catch (e) {
                console.warn('[chat] 鍔犺浇鍘嗗彶浼氳瘽澶辫触', e);
            } finally {
                conversationsLoading.value = false;
            }
        };

        const onConvListScroll = (e) => {
            const el = e && e.target;
            if (!el || conversationsLoading.value || !conversationsHasMore.value) return;
            const distanceToBottom = el.scrollHeight - el.scrollTop - el.clientHeight;
            if (distanceToBottom < 40) {
                loadConversations({ reset: false });
            }
        };

        const loadConversation = async (convId) => {
            if (sending.value) return;
            try {
                const body = await DA.apiGet('/session/' + encodeURIComponent(convId));
                if (!body.data || !body.data.messages) return;

                const loaded = [];
                for (const round of body.data.messages) {
                    loaded.push({
                        id: 'hist_u_' + round.id,
                        role: 'user',
                        content: round.question,
                        attachments: (round.attachments || []).map(a => ({
                            fileId: a.fileId,
                            fileName: a.fileName,
                            fileType: a.fileType,
                            fileSize: a.fileSize,
                            previewUrl: /\.(jpg|jpeg|png|gif|bmp|webp)$/i.test('.' + (a.fileType || ''))
                                ? `${DA.BACKEND_URL}/api/files/${a.fileId}/preview`
                                : ''
                        })),
                        timestamp: Date.now()
                    });

                    // 妗嗘灦鍘熺敓 timeline锛歍imelineEntry[] 绾暟缁?
                    let items = [];
                    if (round.timeline) {
                        const parsed = safeJsonParse(round.timeline, null);
                        items = Array.isArray(parsed) ? parsed : [];
                    }
                    items = items.map(it =>
                        it.type === 'tool' ? { ...it, showResult: false } : it
                    );
                    if (items.length === 0 && round.answer) {
                        items.push({ type: 'text', content: round.answer });
                    }

                    // 浠庡伐鍏风粨鏋滀腑閲嶅缓 charts
                    const charts = [];
                    for (const it of items) {
                        if (it.type === 'tool' && it.result) {
                            const lname = (it.toolName || '').toLowerCase();
                            if (lname === 'generate_chart' || lname === 'generatechart') {
                                const chart = parseChartUrl(it.result);
                                if (chart) charts.push(chart);
                            }
                        }
                    }

                    loaded.push({
                        id: 'hist_a_' + round.id,
                        role: 'assistant',
                        content: round.answer || '',
                        timeline: items,
                        charts,
                        loading: false,
                        timestamp: Date.now()
                    });
                }

                messages.value = loaded;
                conversationId.value = convId;
                const u = new URL(window.location.href);
                u.searchParams.set('conversationId', convId);
                window.history.replaceState({}, '', u.toString());
                nextTick(() => scrollToBottom());
            } catch (e) {
                console.error('[chat] 鍔犺浇浼氳瘽澶辫触', e);
            }
        };

        const deleteConversation = async (convId) => {
            if (!confirm('纭鍒犻櫎璇ヤ細璇濓紵')) return;
            try {
                await DA.apiDelete('/session/' + encodeURIComponent(convId));
                DA.showToast('宸插垹闄?);
                if (convId === conversationId.value) {
                    newConversation();
                }
                await loadConversations();
            } catch (e) {
                console.error('[chat] 鍒犻櫎浼氳瘽澶辫触', e);
            }
        };

        const formatConvTime = (isoStr) => {
            if (!isoStr) return '';
            try {
                const d = new Date(isoStr);
                const diffMs = Date.now() - d.getTime();
                const diffMin = Math.floor(diffMs / 60000);
                const diffHour = Math.floor(diffMs / 3600000);
                const diffDay = Math.floor(diffMs / 86400000);
                if (diffMin < 1) return '鍒氬垰';
                if (diffMin < 60) return diffMin + ' 鍒嗛挓鍓?;
                if (diffHour < 24) return diffHour + ' 灏忔椂鍓?;
                if (diffDay < 7) return diffDay + ' 澶╁墠';
                return d.toLocaleDateString('zh-CN', { month: 'short', day: 'numeric' });
            } catch (e) {
                return '';
            }
        };

        // ==================== 鏂囨。涓婁紶 ====================
        const triggerUpload = () => {
            if (fileInputRef.value) fileInputRef.value.click();
        };

        const onFileSelected = (e) => {
            const files = Array.from(e.target.files || []);
            files.forEach(uploadFile);
            if (fileInputRef.value) fileInputRef.value.value = '';
        };

        const IMAGE_EXT_RE = /^(jpg|jpeg|png|gif|bmp|webp)$/i;
        const isImageFile = (f) => {
            const ext = (f && f.fileType) ? f.fileType.toLowerCase() : '';
            return IMAGE_EXT_RE.test(ext);
        };
        const fileThumb = (f) => f.previewUrl || f.minioUrl || '';

        const uploadFile = async (file) => {
            if (uploadedFiles.value.length >= MAX_FILES) {
                DA.showToast(`鏈€澶氫笂浼?${MAX_FILES} 涓枃浠禶);
                return;
            }
            if (file.size > MAX_SIZE_MB * 1024 * 1024) {
                DA.showToast(`鏂囦欢 "${file.name}" 瓒呰繃 ${MAX_SIZE_MB}MB`);
                return;
            }

            const lowerName = file.name.toLowerCase();
            const isImg = /\.(jpg|jpeg|png|gif|bmp|webp)$/.test(lowerName);
            const placeholder = {
                fileId: null,
                fileName: file.name,
                fileType: lowerName.split('.').pop() || '',
                fileSize: file.size,
                status: 'uploading',
                previewUrl: isImg ? URL.createObjectURL(file) : '',
                minioUrl: ''
            };
            uploadedFiles.value.push(placeholder);
            uploading.value = true;

            try {
                const formData = new FormData();
                formData.append('file', file);
                const json = await DA.apiUpload('/api/files/upload', formData);
                if (!json.data) throw new Error(json.msg || '涓婁紶澶辫触');
                placeholder.fileId = json.data.fileId;
                placeholder.fileName = json.data.fileName;
                placeholder.fileType = json.data.fileType;
                placeholder.fileSize = json.data.fileSize;
                placeholder.status = json.data.status || 'SUCCESS';
            } catch (e) {
                console.error('[chat] 涓婁紶澶辫触', e);
                placeholder.status = 'FAILED';
                DA.showToast(`"${file.name}" 涓婁紶澶辫触: ${e.message}`);
            } finally {
                uploading.value = uploadedFiles.value.some(f => f.status === 'uploading');
            }
        };

        const removeFile = async (idx) => {
            const f = uploadedFiles.value[idx];
            if (!f) return;
            if (f.fileId) {
                try {
                    await DA.apiDelete('/api/files/' + f.fileId);
                } catch (e) {
                    console.warn('[chat] 鍒犻櫎鏂囦欢鍏冩暟鎹け璐?, e);
                }
            }
            if (f.previewUrl) {
                try { URL.revokeObjectURL(f.previewUrl); } catch (e) { /* ignore */ }
            }
            uploadedFiles.value.splice(idx, 1);
            uploading.value = uploadedFiles.value.some(f => f.status === 'uploading');
        };

        const onFileDrop = (e) => {
            e.preventDefault();
            isDragging.value = false;
            const files = Array.from(e.dataTransfer.files || []);
            files.forEach(uploadFile);
        };
        const onFileDragOver = (e) => {
            e.preventDefault();
            isDragging.value = true;
        };
        const onFileDragLeave = (e) => {
            e.preventDefault();
            isDragging.value = false;
        };

        // ==================== 椤舵爮 ====================
        // 鐢?common.js 鐨?renderHeader 娓叉煋锛屾湰缁勪欢鍙渶璋冪敤缁戝畾鍑芥暟
        const currentUser = ref(null);

        // ==================== 鐢熷懡鍛ㄦ湡 ====================
        onMounted(async () => {
            // 閴存潈瀹堝崼锛氭湭鐧诲綍璺?/login.html
            const user = await DA.requireAuth();
            if (!user) return;
            currentUser.value = user;

            setupMarkdown();
            initVoice();

            // 娓叉煋椤舵爮锛堢敤 innerHTML 娉ㄥ叆 common.js 鐨?renderHeader锛?
            const headerHost = document.getElementById('da-header-host');
            if (headerHost) {
                headerHost.innerHTML = DA.renderHeader({
                    active: 'chat',
                    user: user,
                    sidebarToggle: true
                });
                DA.bindHeaderEvents();
                // 渚ф爮鎶樺彔鎸夐挳锛堜粎鑱婂ぉ椤垫湁锛?
                const sidebarToggleBtn = document.getElementById('da-sidebar-toggle');
                if (sidebarToggleBtn) {
                    sidebarToggleBtn.addEventListener('click', () => toggleSidebar());
                }
            }

            const u = new URL(window.location.href);
            const urlConvId = u.searchParams.get('conversationId');
            u.searchParams.set('conversationId', conversationId.value);
            window.history.replaceState({}, '', u.toString());

            loadConversations();
            if (urlConvId && urlConvId === conversationId.value) {
                loadConversation(urlConvId);
            }
        });

        watch(input, () => {
            nextTick(() => {
                if (textareaRef.value) {
                    textareaRef.value.style.height = 'auto';
                    textareaRef.value.style.height = Math.min(textareaRef.value.scrollHeight, 200) + 'px';
                }
            });
        });

        return {
            messages,
            input,
            sending,
            conversationId,
            previewUrl,
            suggestions,
            conversations,
            conversationsTotal,
            conversationsHasMore,
            conversationsLoading,
            onConvListScroll,
            onlineEnabled,
            uploading,
            uploadedFiles,
            isDragging,
            sidebarCollapsed,
            toggleSidebar,
            messagesContainer,
            scroller,
            textareaRef,
            fileInputRef,
            autoScroll,
            sendMessage,
            stopMessage,
            toggleVoice,
            isListening,
            voiceSupported,
            newConversation,
            scrollToBottomManual,
            onMessagesScroll,
            renderMarkdown,
            previewImage,
            formatArguments,
            triggerUpload,
            onFileSelected,
            onFileDrop,
            onFileDragOver,
            onFileDragLeave,
            removeFile,
            isImageFile,
            fileThumb,
            loadConversation,
            deleteConversation,
            formatConvTime,
            dotClass,
            todoIcon,
            completedCount
        };
    }
}).mount('#da-app');