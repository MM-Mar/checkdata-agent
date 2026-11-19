package com.checkdata.agent;

import com.checkdata.agent.domain.entities.CheckdataFile;
import com.checkdata.agent.prompt.SystemPrompt;
import com.checkdata.agent.service.FileManageService;
import com.checkdata.agent.service.SchemaCatalogService;
import com.checkdata.agent.service.UserContextBuilder;
import com.checkdata.agent.skill.SkillManager;
import com.checkdata.agent.tools.CalculateTool;
import com.checkdata.agent.tools.DescribeTablesTool;
import com.checkdata.agent.tools.ExecuteSqlTool;
import com.checkdata.agent.tools.AnalyzeFileTool;
import com.checkdata.agent.tools.ListTablesTool;
import com.checkdata.agent.tools.LookupGlossaryTool;
import com.checkdata.agent.tools.TimeTool;
import com.checkdata.agent.tools.ValidateSqlTool;
import com.agentx.ai.core.agent.ReactAgent;
import com.agentx.ai.core.agent.internal.AgentTaskManager;
import com.agentx.ai.core.chatmodels.DeepSeekV4ChatModel;
import com.agentx.ai.core.context.ContextPolicy;
import com.agentx.ai.core.interrupt.JdbcPauseStateStore;

import com.agentx.ai.core.model.AgentStreamEvent;
import com.agentx.ai.core.model.RunnableParams;
import com.agentx.ai.core.model.ThinkingMode;
import com.agentx.ai.core.tools.BashTool;
import com.agentx.ai.core.tools.FileSystemTools;
import com.agentx.ai.core.tools.GrepTool;
import com.agentx.ai.core.tools.SkillsTool;
import com.agentx.ai.core.tools.TodoWriteTool;
import com.agentx.ai.core.tools.toolsearch.ToolSearchConfig;
import io.modelcontextprotocol.client.McpSyncClient;
import io.netty.resolver.DefaultAddressResolverGroup;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.deepseek.api.DeepSeekApi;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.ReactorClientHttpRequestFactory;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.netty.http.client.HttpClient;

import javax.sql.DataSource;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.agentx.ai.core.utils.ToolMergeUtil.mergeTools;

/**
 * 数据智能体，负责装配工具、构建 Agent 并执行流式请求。
 */
@Slf4j
@Service
public class CheckdataAgent {

    private static final int TOOL_SEARCH_MAX_RESULTS = 10;

    private final DataSource dataSource;
    private final SchemaCatalogService schemaCatalogService;
    private final ListTablesTool listTablesTool;
    private final DescribeTablesTool describeTablesTool;
    private final ValidateSqlTool validateSqlTool;
    private final ExecuteSqlTool executeSqlTool;
    private final CalculateTool calculateTool;
    private final McpSyncClient chartMcpSyncClient;
    private final McpSyncClient tavilyMcpSyncClient;
    private final SkillManager skillManager;
    private final AnalyzeFileTool analyzeFileTool;
    private final TimeTool timeTool;
    private final FileManageService fileManageService;
    private final UserContextBuilder userContextBuilder;
    @Value("${spring.ai.deepseek.base-url}")
    private String baseUrl;

    @Value("${spring.ai.deepseek.api-key}")
    private String apiKey;

    @Value("${spring.ai.deepseek.chat.options.model}")
    private String model;

    @Value("${spring.ai.deepseek.chat.options.temperature:0.7}")
    private double temperature;

    private ChatModel chatModel;
    private ToolCallback[] alwaysOnBaseTools;
    private ToolCallback[] tavilyTools;
    private ToolCallback[] deferredTools;
    private ContextPolicy contextPolicy;
    private final ToolSearchConfig toolSearchConfig =
            ToolSearchConfig.builder().maxResults(TOOL_SEARCH_MAX_RESULTS).build();

    private final AgentTaskManager sharedTaskManager = new AgentTaskManager();
    private final JdbcPauseStateStore sharedStateStore;

    public CheckdataAgent(DataSource dataSource,
                     SchemaCatalogService schemaCatalogService,
                     ListTablesTool listTablesTool,
                     DescribeTablesTool describeTablesTool,
                     ValidateSqlTool validateSqlTool,
                     ExecuteSqlTool executeSqlTool,
                     CalculateTool calculateTool,
                     @Nullable McpSyncClient chartMcpSyncClient,
                     @Nullable McpSyncClient tavilyMcpSyncClient,
                     SkillManager skillManager,
                     AnalyzeFileTool analyzeFileTool,
                     TimeTool timeTool,
                     FileManageService fileManageService,
                     UserContextBuilder userContextBuilder) {
        this.dataSource = dataSource;
        this.schemaCatalogService = schemaCatalogService;
        this.listTablesTool = listTablesTool;
        this.describeTablesTool = describeTablesTool;
        this.validateSqlTool = validateSqlTool;
        this.executeSqlTool = executeSqlTool;
        this.calculateTool = calculateTool;
        this.chartMcpSyncClient = chartMcpSyncClient;
        this.tavilyMcpSyncClient = tavilyMcpSyncClient;
        this.skillManager = skillManager;
        this.analyzeFileTool = analyzeFileTool;
        this.timeTool = timeTool;
        this.fileManageService = fileManageService;
        this.userContextBuilder = userContextBuilder;
        this.sharedStateStore = new JdbcPauseStateStore(dataSource);
    }

    @PostConstruct
    void init() {
        this.chatModel = buildChatModel();
        this.alwaysOnBaseTools = buildAlwaysOnBaseTools();
        this.tavilyTools = buildTavilyTools();
        this.deferredTools = buildDeferredTools();
        this.contextPolicy = buildContextPolicy();
        this.sharedStateStore.initialize();
        log.info("[checkdata-agent] CheckdataAgent 初始化完成 | 常驻={} | tavily={} | deferred={}",
                alwaysOnBaseTools.length,
                tavilyTools.length,
                deferredTools.length);
    }

    /**
     * 流式调用。根据 userId + 会话文件构造 instructions，根据 online 注入 Tavily，
     * 会话有任意文件（本次或历史）时注入 analyzeFile。
     * 文件清单不再拼到 query 末尾，改由 buildInstructions 写进 system prompt。
     */
    public Flux<AgentStreamEvent> streamForResult(String query, RunnableParams params,
                                                  boolean online, List<String> fileIds) {
        String convId = params.getConversationId();

        // 本次新上传的文件立即关联到会话，让本轮 system prompt 能反查到
        if (fileIds != null && !fileIds.isEmpty()) {
            fileManageService.linkToConversation(fileIds, convId);
        }

        // 会话所有文件（含本次新传），决定是否注入 analyzeFile
        boolean hasAnyFile = !fileManageService.listByConversationId(convId).isEmpty();

        String instructions = buildInstructions(params.getUserId(), convId, fileIds);
        ReactAgent agent = buildReactAgent(instructions, online, hasAnyFile);

        Flux<AgentStreamEvent> baseFlux = agent.streamForResult(query, params);

        // session_id 仍在 Complete 事件后补写（历史回放按轮次取附件用）
        if (fileIds == null || fileIds.isEmpty()) {
            return baseFlux;
        }
        return baseFlux.doOnNext(evt -> linkFilesFromEvent(fileIds, evt));
    }

    /**
     * 用户主动中断指定会话的流式任务，持久化快照以便后续 resume。
     */
    public boolean interrupt(String conversationId) {
        return sharedTaskManager.interrupt(conversationId, "用户主动中断");
    }

    /**
     * 检查指定会话是否存在未恢复的中断状态。
     */
    public boolean hasInterruptedState(String conversationId) {
        return sharedStateStore.exists(conversationId);
    }

    /**
     * 从中断状态恢复流式执行。按当前 userId + 会话文件重建 instructions。
     *
     * @param resumeQuery 用户中断后输入的澄清/继续语句（可为空，直接恢复不追加消息）
     */
    public Flux<AgentStreamEvent> resumeStream(Long userId, String conversationId, boolean online,
                                               List<String> fileIds, @Nullable String resumeQuery) {
        if (!sharedStateStore.exists(conversationId)) {
            return Flux.error(new IllegalStateException(
                    "No interrupted state for conversation: " + conversationId));
        }
        // 本次新上传的文件立即关联到会话
        if (fileIds != null && !fileIds.isEmpty()) {
            fileManageService.linkToConversation(fileIds, conversationId);
        }
        boolean hasAnyFile = !fileManageService.listByConversationId(conversationId).isEmpty();
        String instructions = buildInstructions(
                userId == null ? null : userId.toString(), conversationId, fileIds);
        ReactAgent agent = buildReactAgent(instructions, online, hasAnyFile);
        return agent.resumeStream(conversationId, resumeQuery)
                .doOnNext(evt -> linkFilesFromEvent(fileIds, evt));
    }

    /**
     * 从流式事件中提取 sessionId，关联文件到 session。
     * Complete（正常完成）和 Paused（用户中断）都处理。
     */
    private void linkFilesFromEvent(List<String> fileIds, AgentStreamEvent evt) {
        if (fileIds == null || fileIds.isEmpty()) {
            return;
        }
        Long sessionId = null;
        String conversationId = null;
        if (evt instanceof AgentStreamEvent.Complete c && c.sessionId() != null) {
            sessionId = c.sessionId();
            conversationId = c.conversationId();
        } else if (evt instanceof AgentStreamEvent.Paused p
                && p.state() != null && p.state().getSessionId() > 0) {
            sessionId = p.state().getSessionId();
        }
        if (sessionId != null) {
            try {
                fileManageService.linkToSession(fileIds, sessionId, conversationId);
            } catch (Exception e) {
                log.warn("[checkdata-agent] link 文件失败（不影响主流程）: sessionId={}, fileIds={}",
                        sessionId, fileIds, e);
            }
        }
    }

    /**
     * 按用户身份 + 会话文件构造 instructions，文件按时间统一编号。
     */
    private String buildInstructions(String userIdStr, String conversationId,
                                     List<String> currentFileIds) {
        Long userId = parseUserId(userIdStr);
        String base = (userId == null)
                ? SystemPrompt.build("")
                : SystemPrompt.build(userContextBuilder.build(userId));
        return appendSessionFiles(base, conversationId, currentFileIds);
    }

    /**
     * 把会话文件分两组展示：本次新上传（一组）+ 历史文件（按 session_id 分多组，同一轮上传的聚在一起）。
     */
    private String appendSessionFiles(String instructions, String conversationId,
                                      List<String> currentFileIds) {
        if (conversationId == null || conversationId.isBlank()) {
            return instructions;
        }
        List<CheckdataFile> files = fileManageService.listByConversationId(conversationId);
        if (files.isEmpty()) {
            return instructions;
        }
        Set<String> currentSet = currentFileIds == null ? Set.of() : new LinkedHashSet<>(currentFileIds);
        List<CheckdataFile> currentFiles = new ArrayList<>();
        Map<Long, List<CheckdataFile>> historyBySession = new LinkedHashMap<>();
        for (CheckdataFile f : files) {
            if (currentSet.contains(f.getFileId())) {
                currentFiles.add(f);
            } else {
                historyBySession.computeIfAbsent(f.getSessionId(), k -> new ArrayList<>()).add(f);
            }
        }
        int historyCount = historyBySession.values().stream().mapToInt(List::size).sum();

        List<String> parts = new ArrayList<>();
        parts.add(instructions);
        parts.add("## 当前会话文件（重要）");
        parts.add("");

        if (currentFiles.isEmpty()) {
            parts.add("（本次无新文件上传，仅可追问以下历史文件）");
            parts.add("");
        } else {
            parts.add("### 本次新上传（" + currentFiles.size() + " 个，是一个整体）");
            parts.add("");
            parts.add(renderGroup(currentFiles));
            parts.add("");
        }

        if (historyCount > 0) {
            parts.add("### 历史文件（" + historyCount + " 个，按上传轮次分组，从旧到新）");
            parts.add("");
            int sessionIdx = 0;
            int sessionTotal = historyBySession.size();
            for (Map.Entry<Long, List<CheckdataFile>> entry : historyBySession.entrySet()) {
                sessionIdx++;
                List<CheckdataFile> groupFiles = entry.getValue();
                String tag = entry.getKey() == null
                        ? "未知轮次"
                        : "第 " + sessionIdx + " 轮";
                if (entry.getKey() != null && sessionTotal > 1) {
                    if (sessionIdx == 1) tag += "（最早）";
                    else if (sessionIdx == sessionTotal) tag += "（最新）";
                }
                parts.add("【" + tag + "，" + groupFiles.size() + " 个】");
                parts.add(renderGroup(groupFiles));
                parts.add("");
            }
        }

        parts.add("【触发规则】");
        parts.add("- 用户说\"这个/这些/它们/总结一下/讲了什么/分析下\"等指代词或泛指时，默认指【本次新上传】整组，必须挨个调 analyzeFile(fileId, question) 加载");
        parts.add("- 用户说\"上一篇/上一个/刚才的文档\"时，默认指【历史文件】里最新一轮的全部");
        parts.add("- 用户明说某个文件名/序号时，按指定的加载");
        parts.add("- 用户问题没明示文件但语义上可能指文件时，默认尝试调 analyzeFile");
        parts.add("- 普通闲聊（天气、写代码、打招呼）不要调");

        return String.join("\n", parts);
    }

    /** 渲染一组文件列表，组内编号从 1 开始。 */
    private String renderGroup(List<CheckdataFile> groupFiles) {
        return IntStream.range(0, groupFiles.size())
                .mapToObj(i -> {
                    CheckdataFile f = groupFiles.get(i);
                    return (i + 1) + ". fileId: " + f.getFileId()
                            + "，文件名: " + f.getFileName()
                            + "，类型: " + f.getFileType();
                })
                .collect(Collectors.joining("\n"));
    }

    private static Long parseUserId(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private ReactAgent buildReactAgent(String instructions, boolean online, boolean injectFileTool) {
        ToolCallback[] skillsTools = buildSkillsTools();
        ToolCallback[] alwaysOn = mergeTools(alwaysOnBaseTools, skillsTools);

        // 会话有任意文件（本次新传或历史）→ 注入 analyzeFile（文件清单已在 system prompt 里）
        if (injectFileTool) {
            ToolCallback[] fileTools = ToolCallbacks.from(analyzeFileTool);
            alwaysOn = mergeTools(alwaysOn, fileTools);
        }

        if (online && tavilyTools.length > 0) {
            alwaysOn = mergeTools(alwaysOn, tavilyTools);
        }
        return ReactAgent.builder()
                .chatModel(chatModel)
                .instructions(instructions)
                .dataSource(dataSource)
                .taskManager(sharedTaskManager)
                .stateStore(sharedStateStore)
                .contextPolicy(contextPolicy)
                .thinkingMode(ThinkingMode.REASONING_CONTENT)
                .tools(alwaysOn)
                .deferredTools(toolSearchConfig, deferredTools)
                .maxRounds(100)
                .build();
    }

    /**
     * 从 DB 查 enabled skills，构建 SkillsTool。
     * 无启用 skill 时返回空数组（不注册 SkillsTool）。
     */
    private ToolCallback[] buildSkillsTools() {
        List<String> enabledDirs = skillManager.getEnabledSkillDirs();
        if (enabledDirs.isEmpty()) {
            return new ToolCallback[0];
        }
        SkillsTool.Builder builder = SkillsTool.builder();
        for (String dir : enabledDirs) {
            builder.addSkillsDirectory(dir);
        }
        return new ToolCallback[]{builder.build()};
    }

    /**
     * 常驻工具：内置能力，每次请求必带。
     * 包含：规划 / Bash / 文件系统 / 代码搜索 / 当前时间 / SkillsTool（每次请求重建）
     */
    private ToolCallback[] buildAlwaysOnBaseTools() {
        return mergeTools(
                TodoWriteTool.create(),
//                BashTool.create(),
                FileSystemTools.create(),
                GrepTool.create(),
                ToolCallbacks.from(timeTool)
        );
    }

    /**
     * Tavily 联网搜索工具。client 为 null（未配置或初始化失败）时返回空数组。
     */
    private ToolCallback[] buildTavilyTools() {
        if (tavilyMcpSyncClient == null) {
            log.warn("[checkdata-agent] Tavily MCP client 未就绪，联网工具不注入");
            return new ToolCallback[0];
        }
        ToolCallback[] tools = new SyncMcpToolCallbackProvider(List.of(tavilyMcpSyncClient))
                .getToolCallbacks();
        log.info("[checkdata-agent] Tavily 注入联网工具 {} 个: {}",
                tools.length, toolNames(tools));
        return tools;
    }

    /**
     * 延迟工具：数据领域工具 + 图表 + 术语表。
     * LLM 通过 tool_search 元工具按需发现，不会一次性塞入上下文。
     */
    private ToolCallback[] buildDeferredTools() {
        ToolCallback glossaryTool = LookupGlossaryTool.builder()
                .terms(schemaCatalogService.glossary())
                .build();

        ToolCallback[] chartTools;
        if (chartMcpSyncClient == null) {
            log.warn("[checkdata-agent] mcp-echarts client 未就绪，图表工具不注入");
            chartTools = new ToolCallback[0];
        } else {
            chartTools = new SyncMcpToolCallbackProvider(List.of(chartMcpSyncClient))
                    .getToolCallbacks();
        }
        log.info("[checkdata-agent] mcp-echarts 注入图表工具 {} 个: {}",
                chartTools.length, toolNames(chartTools));

        return mergeTools(
                ToolCallbacks.from(listTablesTool),
                ToolCallbacks.from(describeTablesTool),
                ToolCallbacks.from(validateSqlTool),
                ToolCallbacks.from(executeSqlTool),
                ToolCallbacks.from(calculateTool),
                new ToolCallback[]{glossaryTool},
                chartTools
        );
    }

    private ContextPolicy buildContextPolicy() {
        return ContextPolicy.defaults();
    }

    private ChatModel buildChatModel() {
        HttpClient httpClient = HttpClient.create()
                .resolver(DefaultAddressResolverGroup.INSTANCE).responseTimeout(Duration.ofSeconds(300));
        DeepSeekApi deepSeekApi = DeepSeekApi.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .restClientBuilder(RestClient.builder()
                        .requestFactory(new ReactorClientHttpRequestFactory(httpClient)))
                .webClientBuilder(WebClient.builder()
                        .clientConnector(new ReactorClientHttpConnector(httpClient)))
                .build();
        DeepSeekChatOptions options = DeepSeekChatOptions.builder()
                .model(model)
                .temperature(temperature)
                .build();
        return DeepSeekV4ChatModel.builder()
                .deepSeekApi(deepSeekApi)
                .defaultOptions(options)
                .build();
    }

    private static List<String> toolNames(ToolCallback[] tools) {
        List<String> names = new ArrayList<>(tools.length);
        for (ToolCallback ct : tools) {
            names.add(ct.getToolDefinition().name());
        }
        return names;
    }
}