package com.checkdata.agent.tools;

import com.checkdata.agent.domain.entities.SqlResult;
import com.checkdata.agent.domain.entities.ValidationResult;
import com.checkdata.agent.service.DataScopeRewriter;
import com.checkdata.agent.service.ExplainPrecheckService;
import com.checkdata.agent.service.ReadOnlyQueryRunner;
import com.checkdata.agent.service.SensitiveFilter;
import com.checkdata.agent.service.SqlSafetyGuard;
import com.checkdata.agent.common.datascope.DataScopeContext;
import com.checkdata.agent.common.datascope.DataScopeResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * SQL 执行工具（只读）。
 * 流程：安全校验 → 数据权限改写 → EXPLAIN 预检查 → 执行 → 脱敏 → 审计。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExecuteSqlTool {

    /**
     * markdown 表格预览行数上限。
     */
    private static final int PREVIEW_ROWS = 20;

    private final SqlSafetyGuard guard;
    private final DataScopeResolver dataScopeResolver;
    private final DataScopeRewriter dataScopeRewriter;
    private final ExplainPrecheckService explainPrecheckService;
    private final ReadOnlyQueryRunner queryRunner;
    private final SensitiveFilter sensitiveFilter;

    @Value("${data-agent.data-permission.enabled:false}")
    private boolean dataPermissionEnabled;

    @Tool(name = "executeSql",
            description = "执行只读 SELECT/WITH 查询并返回结果数据。" +
                    "参数名严格为 sql 和 userId，严谨使用任何其他参数名，" +
                    "传错会收到 \"参数名错误\" 提示，必须改用 sql 重新调用。" +
                    "内部会强制做 SQL 安全校验（白名单/强制 LIMIT）和保守 EXPLAIN 预检查，" +
                    "明显的大表明细扫描或 JOIN 扩张会被拦截。" +
                    "若执行失败，会返回 error 字段——请根据错误信息重写 SQL（最多重试 3 次），" +
                    "常见错误：字段名拼错（参考 describeTables 输出）、表名错、JOIN 条件错。" +
                    "严禁写 INSERT/UPDATE/DELETE/DROP 等。")
    public String executeSql(
            @ToolParam(description = "要执行的 SELECT 或 WITH 查询语句（建议先用 validateSql 校验拿安全 SQL）。参数名必须是 sql")
            String sql,
            @ToolParam(description = "用户身份标识。参数名必须是 userId，由框架自动注入真实值", required = false)
            String userId) {

        if (StringUtils.isBlank(sql)) {
            return "❌ 参数错误：sql 为空。本工具参数名严格为 `sql` 和 `userId`，禁止用任何其他名字。" +
                    "请重新调用 executeSql，把 SQL 语句放在 `sql` 参数中。";
        }

        log.info("[checkdata-agent] executeSql 入参 | userId={}, sql={}", userId, sql);

        ValidationResult vr = guard.validate(sql);
        if (!vr.valid()) {
            return "❌ 已拦截（未执行）。原因：" + vr.reason()
                    + "\n请根据原因重写 SQL：统计问题优先改为 GROUP BY 聚合；明细问题先按主表稳定分页；"
                    + "一对多明细请先聚合或按主键二次钻取，不要一次性展开大结果集。";
        }
        String safeSql = vr.safeSql();

        Long uid = parseUserId(userId);
        if (uid == null) {
            log.error("[checkdata-agent] executeSql userId 未注入（值={}），agentx 工具参数替换异常", userId);
            return "❌ 内部错误：用户身份未注入，无法执行 SQL。请告知用户稍后重试，若持续出现请重新登录。";
        }
        String finalSql;
        if (!dataPermissionEnabled) {
            finalSql = safeSql;
        } else {
            try {
                DataScopeContext ctx = dataScopeResolver.resolve(uid);
                finalSql = dataScopeRewriter.rewrite(safeSql, ctx);
            } catch (DataScopeRewriter.RewriteException e) {
                log.warn("[checkdata-agent] 数据权限改写失败（已拦截）：{}", e.getMessage());
                return "❌ 已拦截（数据权限改写失败）。原因：" + e.getMessage()
                        + "\n请检查 SQL 语法并重写。若涉及 LEFT JOIN 受权限控制表，请将右表过滤条件放入 JOIN ON，"
                        + "或先过滤右表子查询后再 LEFT JOIN；如果业务只需要有匹配数据，请改用 INNER JOIN。";
            }
        }

        ExplainPrecheckService.PrecheckResult precheck = explainPrecheckService.check(finalSql);
        if (!precheck.allowed()) {
            log.warn("[checkdata-agent] EXPLAIN 预检查拦截 | reason={} | sql={}",
                    precheck.reason(), truncate(finalSql, 300));
            return "❌ 已拦截（EXPLAIN 预检查）。原因：" + precheck.reason()
                    + "\n请重写 SQL：统计问题优先用 GROUP BY 聚合；明细问题增加选择性 WHERE，"
                    + "并按主表主键或时间字段稳定分页；一对多关系请先对子表聚合后 JOIN，"
                    + "或先查主键列表再按主键二次钻取。";
        }

        SqlResult result = queryRunner.execute(finalSql);

        if (result.success()) {
            sensitiveFilter.mask(result.columnMetas(), result.rows());
        }

        if (result.success()) {
            log.info("[checkdata-agent] SQL OK | rows={} truncated={} cost={}ms | sql={}",
                    result.rowCount(), result.truncated(), result.durationMs(), truncate(finalSql, 300));
        } else {
            log.warn("[checkdata-agent] SQL FAIL | cost={}ms err={} | sql={}",
                    result.durationMs(), truncate(result.error(), 300), truncate(finalSql, 300));
        }

        return format(result);
    }

    // ── LLM 友好的输出格式化 ──

    private String format(SqlResult r) {
        StringBuilder sb = new StringBuilder();
        if (!r.success()) {
            sb.append("❌ 执行失败（耗时 ").append(r.durationMs()).append("ms）：")
                    .append(r.error()).append('\n');
            sb.append("执行的 SQL：\n```sql\n").append(r.executedSql()).append("\n```\n");
            sb.append("请根据上述报错重写 SQL（最多重试 3 次）。")
                    .append("常见原因：字段名拼错、表名错、JOIN 条件错、类型不兼容。")
                    .append("建议重新调用 describeTables 确认真实字段名；")
                    .append("如果 JOIN 结果异常放大，请先按主表分页或对子表聚合后再 JOIN。");
            return sb.toString().trim();
        }

        sb.append("✅ 执行成功（耗时 ").append(r.durationMs())
                .append("ms，返回 ").append(r.rowCount()).append(" 行")
                .append(r.truncated() ? "，已截断到 max-rows 上限" : "")
                .append("）\n\n");
        sb.append("执行的 SQL：\n```sql\n").append(r.executedSql()).append("\n```\n\n");

        if (r.rowCount() == 0) {
            sb.append("结果：空结果集（0 行）。\n")
                    .append("若这不符合预期，可能是 WHERE 条件过严或 JOIN 关联键错误——"
                            + "建议去掉部分条件重查，或先 SELECT COUNT(*) 看真实命中量。");
            return sb.toString().trim();
        }

        sb.append("结果（").append(r.rowCount()).append("行");
        if (r.rowCount() > PREVIEW_ROWS) {
            sb.append("，预览前 ").append(PREVIEW_ROWS).append(" 行");
        }
        sb.append("）：\n\n").append(renderTable(r.columns(), r.rows(), PREVIEW_ROWS));

        if (r.rowCount() > PREVIEW_ROWS) {
            sb.append("\n\n⚠ 共 ").append(r.rowCount()).append(" 行，仅展示前 ").append(PREVIEW_ROWS)
                    .append(" 行，剩余 ").append(r.rowCount() - PREVIEW_ROWS).append(" 行未在上下文中展示——")
                    .append("如需明细请按主表稳定排序分页重查，并注明当前页范围；")
                    .append("如是一对多关系，请优先对子表聚合成数量/金额/最新时间后再 JOIN，")
                    .append("用户点名某条主记录时再按主键二次查询明细；")
                    .append("如是分析/汇总任务，请改写为聚合 SQL（GROUP BY + SUM/COUNT/AVG）后重新查询，")
                    .append("不要试图从预览行里手算总量或均值。");
        }
        return sb.toString().trim();
    }

    private String renderTable(List<String> columns, List<Map<String, Object>> rows, int preview) {
        StringBuilder sb = new StringBuilder();
        sb.append("| ").append(String.join(" | ", columns)).append(" |\n");
        sb.append("|").append("---|".repeat(columns.size())).append("\n");
        int n = Math.min(preview, rows.size());
        for (int i = 0; i < n; i++) {
            Map<String, Object> row = rows.get(i);
            sb.append("| ");
            List<String> vals = new ArrayList<>(columns.size());
            for (String c : columns) {
                vals.add(renderCell(row.get(c)));
            }
            sb.append(String.join(" | ", vals)).append(" |\n");
        }
        return sb.toString().trim();
    }

    private String renderCell(Object v) {
        if (v == null) return "NULL";
        String s = String.valueOf(v);
        s = s.replace("|", "\\|").replace("\r", " ").replace("\n", " ").replace("\t", " ");
        return truncate(s, 200);
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    /**
     * userId 字符串 → Long。空或非数字返回 null，
     * 调用方收到 null 后直接 fail-fast 返回错误，避免 dataScopeResolver 误抛"未登录"。
     */
    private static Long parseUserId(String userId) {
        if (userId == null || userId.isBlank() || "default".equalsIgnoreCase(userId)) {
            return null;
        }
        try {
            return Long.parseLong(userId.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}