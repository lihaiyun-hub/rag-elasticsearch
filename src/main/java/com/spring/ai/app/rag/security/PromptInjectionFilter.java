package com.spring.ai.app.rag.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Prompt注入攻击过滤器
 * 用于检测和阻止常见的prompt注入攻击模式
 */
@Component
public class PromptInjectionFilter {
    
    private static final Logger logger = LoggerFactory.getLogger(PromptInjectionFilter.class);
    
    // 常见的注入攻击模式
    private static final List<Pattern> INJECTION_PATTERNS = Arrays.asList(
        // 忽略之前指令
        Pattern.compile("ignore.*previous.*instructions", Pattern.CASE_INSENSITIVE),
        Pattern.compile("忽略.*之前.*指令", Pattern.CASE_INSENSITIVE),
        Pattern.compile("forget.*what.*was.*said", Pattern.CASE_INSENSITIVE),
        Pattern.compile("忘记.*之前.*说.*的", Pattern.CASE_INSENSITIVE),
        
        // 角色扮演攻击
        Pattern.compile("you.*are.*now", Pattern.CASE_INSENSITIVE),
        Pattern.compile("你现在.*是", Pattern.CASE_INSENSITIVE),
        Pattern.compile("pretend.*to.*be", Pattern.CASE_INSENSITIVE),
        Pattern.compile("假装.*是", Pattern.CASE_INSENSITIVE),
        
        // 系统提示泄露
        Pattern.compile("show.*your.*system.*prompt", Pattern.CASE_INSENSITIVE),
        Pattern.compile("显示.*系统.*提示", Pattern.CASE_INSENSITIVE),
        Pattern.compile("what.*are.*your.*instructions", Pattern.CASE_INSENSITIVE),
        Pattern.compile("你.*的.*指令.*是.*什么", Pattern.CASE_INSENSITIVE),
        
        // 权限提升
        Pattern.compile("bypass.*restrictions", Pattern.CASE_INSENSITIVE),
        Pattern.compile("绕过.*限制", Pattern.CASE_INSENSITIVE),
        Pattern.compile("override.*rules", Pattern.CASE_INSENSITIVE),
        Pattern.compile("覆盖.*规则", Pattern.CASE_INSENSITIVE),
        
        // 恶意代码执行
        Pattern.compile("execute.*code", Pattern.CASE_INSENSITIVE),
        Pattern.compile("执行.*代码", Pattern.CASE_INSENSITIVE),
        Pattern.compile("run.*command", Pattern.CASE_INSENSITIVE),
        Pattern.compile("运行.*命令", Pattern.CASE_INSENSITIVE),
        
        // 信息泄露
        Pattern.compile("leak.*information", Pattern.CASE_INSENSITIVE),
        Pattern.compile("泄露.*信息", Pattern.CASE_INSENSITIVE),
        Pattern.compile("reveal.*secrets", Pattern.CASE_INSENSITIVE),
        Pattern.compile("透露.*秘密", Pattern.CASE_INSENSITIVE),
        
        // 特殊字符攻击
        Pattern.compile("[\\x00-\\x08\\x0B-\\x0C\\x0E-\\x1F]"), // 控制字符
        Pattern.compile("\\\\u[0-9a-fA-F]{4}"), // Unicode编码攻击
        Pattern.compile("\\\\x[0-9a-fA-F]{2}"), // 十六进制编码攻击
        
        // 分隔符攻击
        Pattern.compile("\\n\\n.*\\n\\n"), // 多个换行符
        Pattern.compile("```.*```"), // 代码块攻击
        Pattern.compile("---.*---"), // 分隔线攻击
        
        // 重复关键词攻击
        Pattern.compile("(ignore|忽略|forget|忘记){2,}"), // 重复关键词
        Pattern.compile("(system|系统|prompt|提示){3,}"), // 过度重复
        
        // 混淆攻击
        Pattern.compile("[a-zA-Z]{20,}"), // 超长无意义字符串
        Pattern.compile("(.)\\1{10,}"), // 重复字符攻击
        
        // 多语言混合攻击
        Pattern.compile("ignore.*忽略.*previous.*之前"), // 中英混合
        Pattern.compile("bypass.*绕过.*restrictions.*限制") // 中英混合
    );
    
    // 白名单模式 - 允许的内容
    private static final List<Pattern> ALLOWED_PATTERNS = Arrays.asList(
        Pattern.compile("^[\\u4e00-\\u9fa5a-zA-Z0-9\\s\\.,!?。，！？()（）+-/*=<>≤≥￥¥%\"']*$"), // 基本字符
        Pattern.compile("^\\d{1,10}$"), // 纯数字
        Pattern.compile("^[\\u4e00-\\u9fa5]{1,50}$") // 纯中文
    );
    
    // 风险阈值
    private static final double RISK_THRESHOLD = 0.7;
    private static final int MAX_INPUT_LENGTH = 1000;
    private static final int MIN_INPUT_LENGTH = 1;
    
    /**
     * 检测输入是否包含注入攻击
     * 
     * @param input 用户输入
     * @return 检测结果
     */
    public DetectionResult detectInjection(String input) {
        if (input == null || input.trim().isEmpty()) {
            return new DetectionResult(false, 0.0, "输入为空");
        }
        
        String trimmedInput = input.trim();
        
        // 检查长度
        if (trimmedInput.length() > MAX_INPUT_LENGTH) {
            return new DetectionResult(true, 1.0, "输入过长（超过" + MAX_INPUT_LENGTH + "字符）");
        }
        
        if (trimmedInput.length() < MIN_INPUT_LENGTH) {
            return new DetectionResult(false, 0.0, "输入过短");
        }
        
        double riskScore = calculateRiskScore(trimmedInput);
        boolean isMalicious = riskScore >= RISK_THRESHOLD;
        String reason = isMalicious ? "检测到注入攻击模式" : "输入安全";
        
        logger.warn("注入检测结果 - 风险分数: {}, 是否恶意: {}, 原因: {}", 
                   riskScore, isMalicious, reason);
        
        return new DetectionResult(isMalicious, riskScore, reason);
    }
    
    /**
     * 计算风险分数
     * 
     * @param input 用户输入
     * @return 风险分数 (0.0 - 1.0)
     */
    private double calculateRiskScore(String input) {
        double score = 0.0;
        int matchedPatterns = 0;
        
        // 检查注入模式
        for (Pattern pattern : INJECTION_PATTERNS) {
            if (pattern.matcher(input).find()) {
                matchedPatterns++;
                score += 0.15; // 每个匹配模式增加15%风险
                logger.debug("检测到注入模式: {}", pattern.pattern());
            }
        }
        
        // 检查字符异常
        score += checkCharacterAnomalies(input);
        
        // 检查结构异常
        score += checkStructureAnomalies(input);
        
        // 检查白名单
        score += checkWhitelistCompliance(input);
        
        // 确保分数不超过1.0
        return Math.min(score, 1.0);
    }
    
    /**
     * 检查字符异常
     * 
     * @param input 用户输入
     * @return 风险分数增量
     */
    private double checkCharacterAnomalies(String input) {
        double score = 0.0;
        
        // 检查特殊字符比例
        long specialCharCount = input.chars()
            .filter(ch -> !Character.isLetterOrDigit(ch) && !Character.isWhitespace(ch))
            .count();
        
        double specialCharRatio = (double) specialCharCount / input.length();
        if (specialCharRatio > 0.3) { // 特殊字符超过30%
            score += 0.2;
            logger.debug("特殊字符比例过高: {}", specialCharRatio);
        }
        
        // 检查大写字母比例（可能表示强调攻击）
        long upperCaseCount = input.chars()
            .filter(Character::isUpperCase)
            .count();
        
        double upperCaseRatio = (double) upperCaseCount / input.length();
        if (upperCaseRatio > 0.5) { // 大写字母超过50%
            score += 0.1;
            logger.debug("大写字母比例过高: {}", upperCaseRatio);
        }
        
        return score;
    }
    
    /**
     * 检查结构异常
     * 
     * @param input 用户输入
     * @return 风险分数增量
     */
    private double checkStructureAnomalies(String input) {
        double score = 0.0;
        
        // 检查重复模式
        String[] words = input.toLowerCase().split("\\s+");
        java.util.Set<String> uniqueWords = new java.util.HashSet<>(Arrays.asList(words));
        
        double uniquenessRatio = (double) uniqueWords.size() / words.length;
        if (uniquenessRatio < 0.5) { // 词汇重复度很高
            score += 0.15;
            logger.debug("词汇重复度过高: {}", uniquenessRatio);
        }
        
        // 检查行数异常
        String[] lines = input.split("\\n");
        if (lines.length > 10) { // 行数过多
            score += 0.1;
            logger.debug("行数过多: {}", lines.length);
        }
        
        return score;
    }
    
    /**
     * 检查白名单合规性
     * 
     * @param input 用户输入
     * @return 风险分数增量
     */
    private double checkWhitelistCompliance(String input) {
        boolean matchesWhitelist = false;
        
        for (Pattern pattern : ALLOWED_PATTERNS) {
            if (pattern.matcher(input).matches()) {
                matchesWhitelist = true;
                break;
            }
        }
        
        if (!matchesWhitelist) {
            logger.debug("输入不符合白名单模式");
            return 0.1;
        }
        
        return 0.0;
    }
    
    /**
     * 清理输入（如果需要）
     * 
     * @param input 用户输入
     * @return 清理后的输入
     */
    public String sanitizeInput(String input) {
        if (input == null) {
            return null;
        }
        
        // 移除控制字符
        String sanitized = input.replaceAll("[\\x00-\\x08\\x0B-\\x0C\\x0E-\\x1F]", "");
        
        // 移除过多的空白字符
        sanitized = sanitized.replaceAll("\\s{3,}", " ");
        
        // 限制长度
        if (sanitized.length() > MAX_INPUT_LENGTH) {
            sanitized = sanitized.substring(0, MAX_INPUT_LENGTH);
        }
        
        return sanitized.trim();
    }
    
    /**
     * 检测结果类
     */
    public static class DetectionResult {
        private final boolean isMalicious;
        private final double riskScore;
        private final String reason;
        
        public DetectionResult(boolean isMalicious, double riskScore, String reason) {
            this.isMalicious = isMalicious;
            this.riskScore = riskScore;
            this.reason = reason;
        }
        
        public boolean isMalicious() {
            return isMalicious;
        }
        
        public double getRiskScore() {
            return riskScore;
        }
        
        public String getReason() {
            return reason;
        }
        
        @Override
        public String toString() {
            return String.format("DetectionResult{isMalicious=%s, riskScore=%.2f, reason='%s'}", 
                               isMalicious, riskScore, reason);
        }
    }
}