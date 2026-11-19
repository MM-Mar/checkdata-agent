package com.checkdata.agent.tools;

import com.checkdata.agent.domain.entities.SchemaCatalog.GlossaryDesc;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.util.Assert;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * 业务术语口径查询工具，编程式注册。不走 @Tool 注解（description 必须编译时常量），
 * 改用 FunctionToolCallback 构建，把 YAML 术语列表拼进 description，让 LLM 直接看到所有术语名。
 * 用精确匹配而非向量检索：口径必须一术语一标准答案，模糊检索会出错。
 */
public final class LookupGlossaryTool {

    private LookupGlossaryTool() {
    }

    /**
     * 工具描述模板，%s 为术语列表注入位置。
     */
    private static final String TOOL_DESCRIPTION_TEMPLATE = """
            查询业务术语的标准口径（含定义 + 可复用 SQL 片段 + 同义词）。
            
            何时使用本工具：
            - 用户问题涉及"活跃客户""VIP""热门影片""高消费客户""租金"等业务指标术语时
            - 用户问题涉及"近 N 个月""最近""本月""上月""今年"等相对时间时
              （sakila 是历史样本库，所有相对时间必须基于 MAX(rental_date) 而非 NOW()）
            
            <可用术语列表>
            %s
            </可用术语列表>
            
            传入上述术语名或其同义词即可命中。未命中会返回全部术语供你重试。
            """;

    /**
     * 工具入参。
     */
    public record GlossaryInput(
            @ToolParam(description = "要查的术语名或同义词，必传，如 \"活跃客户\"、\"VIP\"、\"近3个月\"、\"最近\"" ,required = true)
            String term) {
    }

    /**
     * 工具执行体，从 term/synonym 索引中精确匹配。
     */
    public static class GlossaryFunction implements Function<GlossaryInput, String> {

        private final Map<String, GlossaryDesc> termIndex;     // 小写 term → desc
        private final Map<String, GlossaryDesc> synonymIndex;  // 小写 synonym → desc
        private final List<GlossaryDesc> allTerms;

        public GlossaryFunction(List<GlossaryDesc> terms) {
            this.allTerms = terms == null ? List.of() : terms;
            this.termIndex = new HashMap<>();
            this.synonymIndex = new HashMap<>();
            for (GlossaryDesc g : this.allTerms) {
                if (g.term() != null && !g.term().isBlank()) {
                    termIndex.put(g.term().trim().toLowerCase(Locale.ROOT), g);
                }
                for (String syn : g.synonyms()) {
                    if (syn != null && !syn.isBlank()) {
                        synonymIndex.put(syn.trim().toLowerCase(Locale.ROOT), g);
                    }
                }
            }
        }

        @Override
        public String apply(GlossaryInput input) {
            if (input == null || input.term() == null || input.term().isBlank()) {
                return "入参为空，请必须传入具体术语名（如 \"活跃客户\"），参考工具说明中的可用术语列表，再次尝试调用本工具。";
            }
            String trimmed = input.term().trim();
            String lower = trimmed.toLowerCase(Locale.ROOT);

            // 1. term 精确匹配 -> 2. synonyms 精确匹配
            GlossaryDesc hit = termIndex.get(lower);
            if (hit == null) {
                hit = synonymIndex.get(lower);
            }
            return hit != null ? format(hit) : formatMiss(trimmed);
        }

        private String format(GlossaryDesc g) {
            StringBuilder sb = new StringBuilder();
            sb.append("【术语】").append(g.term()).append("\n\n");
            if (g.description() != null && !g.description().isBlank()) {
                sb.append("【标准口径】").append(g.description()).append("\n\n");
            }
            if (g.sqlFragment() != null && !g.sqlFragment().isBlank()) {
                sb.append("【SQL 片段（可直接复用）】\n").append(g.sqlFragment()).append("\n\n");
            }
            if (!g.synonyms().isEmpty()) {
                sb.append("【同义词】").append(String.join("、", g.synonyms())).append('\n');
            }
            return sb.toString().trim();
        }

        private String formatMiss(String query) {
            if (allTerms.isEmpty()) {
                return "未找到术语「" + query + "」的标准口径。当前 glossary 为空——"
                        + "可能是 schema 字典未正确加载，请检查 checkdata_agent.yml 的 glossary section。";
            }
            StringBuilder sb = new StringBuilder();
            sb.append("未找到术语「").append(query).append("」的标准口径。\n\n");
            sb.append("当前已登记的术语（用准确名或同义词重试）：\n");
            for (GlossaryDesc g : allTerms) {
                sb.append("- ").append(g.term());
                if (!g.synonyms().isEmpty()) {
                    sb.append("（同义词：").append(String.join("/", g.synonyms())).append("）");
                }
                sb.append('\n');
            }
            return sb.toString().trim();
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private List<GlossaryDesc> terms = new ArrayList<>();

        public Builder terms(List<GlossaryDesc> terms) {
            this.terms = terms == null ? new ArrayList<>() : terms;
            return this;
        }

        /**
         * 构建 ToolCallback，把术语列表拼进 description。
         */
        public ToolCallback build() {
            Assert.notEmpty(this.terms, "glossary 术语列表不能为空——请检查 checkdata_agent.yml 的 glossary section");

            String termsBlock = formatTermsList(this.terms);
            return FunctionToolCallback.builder("lookupGlossary", new GlossaryFunction(this.terms))
                    .description(TOOL_DESCRIPTION_TEMPLATE.formatted(termsBlock))
                    .inputType(GlossaryInput.class)
                    .build();
        }

        /**
         * 把术语列表渲染为紧凑格式，每个术语一行附带同义词。
         */
        private static String formatTermsList(List<GlossaryDesc> terms) {
            StringBuilder sb = new StringBuilder();
            for (GlossaryDesc g : terms) {
                sb.append("- ").append(g.term());
                if (!g.synonyms().isEmpty()) {
                    sb.append("（同义词：").append(String.join("、", g.synonyms())).append("）");
                }
                sb.append('\n');
            }
            return sb.toString().trim();
        }
    }
}
