package com.lilac.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 提示词模板渲染器
 *
 * <p>负责把 {@code {varName}} 占位符替换成实际值，并做变量校验。
 *
 * <p><b>为什么单独抽出来</b>
 *
 * <p>原代码里到处都是这种写法：
 * <pre>
 * PromptConstant.AGENT2_OUTLINE_PROMPT
 *     .replace("{mainTitle}", mainTitle)
 *     .replace("{subTitle}", subTitle)
 *     .replace("{descriptionSection}", descriptionSection)
 *     + getStylePrompt(style);
 * </pre>
 * 手工链式 replace 有几个问题：
 * <ul>
 *   <li>漏一个占位符不会报错，会在 prompt 里留下字面量 {@code {xxx}}，
 *       模型会把它们当成正文内容，导致输出跑偏</li>
 *   <li>变量值本身含有 {@code {xxx}} 时会二次替换（虽然本例概率低）</li>
 *   <li>无法统计"哪些变量没被提供"</li>
 * </ul>
 */
@Slf4j
@Service
public class PromptRenderer {

    /** 占位符：{varName}，变量名只允许字母数字下划线 */
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([a-zA-Z][a-zA-Z0-9_]*)}");

    /**
     * 渲染模板。
     *
     * <p>找不到值的占位符<b>保留原样</b>并打 WARN ——
     * 这样在日志里能立刻发现「模板定义了变量但代码没传」。
     *
     * @param template 模板正文
     * @param vars     变量表
     * @return 渲染结果
     */
    public String render(String template, Map<String, ?> vars) {
        if (template == null || template.isEmpty()) {
            return "";
        }
        Map<String, ?> safeVars = vars == null ? Collections.emptyMap() : vars;

        Matcher m = PLACEHOLDER.matcher(template);
        StringBuilder sb = new StringBuilder(template.length() + 256);
        Set<String> missing = new LinkedHashSet<>();

        while (m.find()) {
            String key = m.group(1);
            Object val = safeVars.get(key);
            String replacement;
            if (val == null) {
                missing.add(key);
                replacement = m.group(0);   // 保留字面量，便于发现
            } else {
                // 用 Matcher.quoteReplacement 避免值里的 $ 和 \ 被当成正则替换符
                replacement = Matcher.quoteReplacement(String.valueOf(val));
            }
            m.appendReplacement(sb, replacement);
        }
        m.appendTail(sb);

        if (!missing.isEmpty()) {
            log.warn("提示词渲染缺少变量: {}, 模板片段: {}", missing, abbreviate(template));
        }
        return sb.toString();
    }

    /**
     * 提取模板里用到的所有变量名（保持出现顺序）。
     */
    public Set<String> extractVariables(String template) {
        if (template == null) {
            return Collections.emptySet();
        }
        Matcher m = PLACEHOLDER.matcher(template);
        Set<String> vars = new LinkedHashSet<>();
        while (m.find()) {
            vars.add(m.group(1));
        }
        return vars;
    }

    private String abbreviate(String s) {
        if (s == null) {
            return "";
        }
        return s.length() > 80 ? s.substring(0, 80) + "..." : s;
    }
}
