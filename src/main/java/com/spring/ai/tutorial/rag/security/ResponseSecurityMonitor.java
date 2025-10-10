package com.spring.ai.tutorial.rag.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 响应安全监控器
 * 用于检测AI响应中的异常模式，防止信息泄露和不当内容
 */
@Component
public class ResponseSecurityMonitor {
    
    private static final Logger logger = LoggerFactory.getLogger(ResponseSecurityMonitor.class);
    
    @Autowired
    private PromptInjectionFilter injectionFilter;
    
    // 敏感信息模式
    private static final String[] SENSITIVE_PATTERNS = {
        "system prompt", "系统提示", "instructions", "指令",
        "ignore previous", "忽略之前", "forget", "忘记",
        "you are now", "你现在", "pretend", "假装",
        "bypass", "绕过", "override", "覆盖",
        "internal configuration", "内部配置", "secret", "秘密"
    };
    
    // 异常响应模式
    private static final String[] ABNORMAL_PATTERNS = {
        "i'm sorry", "对不起", "apologize", "道歉",
        "cannot", "不能", "unable", "无法",
        "not allowed", "不允许", "forbidden", "禁止"
    };
    
    /**
     * 监控AI响应的安全性
     * 
     * @param originalInput 原始用户输入
     * @param aiResponse AI响应内容
     * @return 安全监控结果
     */
    public SecurityCheckResult checkResponse(String originalInput, String aiResponse) {
        if (aiResponse == null || aiResponse.trim().isEmpty()) {
            return new SecurityCheckResult(false, 0.0, "响应为空");
        }
        
        String response = aiResponse.toLowerCase().trim();
        double riskScore = 0.0;
        StringBuilder issues = new StringBuilder();
        
        // 1. 检查是否泄露了输入内容
        if (containsInputLeak(originalInput, response)) {
            riskScore += 0.3;
            issues.append("检测到输入内容泄露; ");
            logger.warn("⚠️ 响应中包含用户输入内容，可能存在信息泄露风险");
        }
        
        // 2. 检查是否包含敏感信息
        int sensitiveCount = countSensitivePatterns(response);
        if (sensitiveCount > 0) {
            riskScore += (sensitiveCount * 0.1);
            issues.append("检测到").append(sensitiveCount).append("个敏感词汇; ");
            logger.warn("⚠️ 响应中包含敏感词汇数量: {}", sensitiveCount);
        }
        
        // 3. 检查响应长度异常
        if (isResponseLengthAbnormal(originalInput, response)) {
            riskScore += 0.2;
            issues.append("响应长度异常; ");
            logger.warn("⚠️ 响应长度与用户输入不匹配");
        }
        
        // 4. 检查是否包含系统相关信息
        if (containsSystemInformation(response)) {
            riskScore += 0.4;
            issues.append("检测到系统信息泄露; ");
            logger.error("🚨 响应中包含系统信息，高风险！");
        }
        
        // 5. 检查是否偏离了贷款主题
        if (isOffTopic(originalInput, response)) {
            riskScore += 0.3;
            issues.append("响应偏离贷款主题; ");
            logger.warn("⚠️ 响应偏离了贷款主题");
        }
        
        // 6. 检查是否包含代码或命令
        if (containsCodeOrCommands(response)) {
            riskScore += 0.3;
            issues.append("检测到代码或命令; ");
            logger.warn("⚠️ 响应中包含代码或命令");
        }
        
        // 7. 检查是否包含URL或链接
        if (containsUrls(response)) {
            riskScore += 0.1;
            issues.append("检测到URL链接; ");
            logger.warn("⚠️ 响应中包含URL链接");
        }
        
        // 确保风险分数不超过1.0
        riskScore = Math.min(riskScore, 1.0);
        
        boolean isSafe = riskScore < 0.5; // 0.5以下为安全
        String reason = issues.length() > 0 ? issues.toString() : "响应安全";
        
        if (!isSafe) {
            logger.error("🚨 响应安全检测未通过 - 风险分数: {}, 原因: {}", riskScore, reason);
        } else {
            logger.info("✅ 响应安全检测通过 - 风险分数: {}", riskScore);
        }
        
        return new SecurityCheckResult(isSafe, riskScore, reason);
    }
    
    /**
     * 检查是否泄露了输入内容
     */
    private boolean containsInputLeak(String originalInput, String response) {
        if (originalInput == null || originalInput.trim().isEmpty()) {
            return false;
        }
        
        String input = originalInput.toLowerCase().trim();
        
        // 检查响应是否包含完整的用户输入（可能被用于证明攻击成功）
        if (response.contains(input) && input.length() > 10) {
            return true;
        }
        
        // 检查是否包含输入中的敏感词汇
        String[] inputWords = input.split("\\s+");
        int matchedWords = 0;
        
        for (String word : inputWords) {
            if (word.length() > 3 && response.contains(word)) { // 只检查长度大于3的词
                matchedWords++;
            }
        }
        
        // 如果输入中超过50%的词出现在响应中，认为可能泄露
        return matchedWords > (inputWords.length * 0.5);
    }
    
    /**
     * 计算敏感词汇数量
     */
    private int countSensitivePatterns(String response) {
        int count = 0;
        for (String pattern : SENSITIVE_PATTERNS) {
            if (response.contains(pattern.toLowerCase())) {
                count++;
            }
        }
        return count;
    }
    
    /**
     * 检查响应长度是否异常
     */
    private boolean isResponseLengthAbnormal(String originalInput, String response) {
        if (originalInput == null || originalInput.trim().isEmpty()) {
            return response.length() > 500; // 没有输入但响应很长，可能异常
        }
        
        double inputLength = originalInput.length();
        double responseLength = response.length();
        
        // 如果响应比输入长10倍以上，可能异常
        return responseLength > (inputLength * 10);
    }
    
    /**
     * 检查是否包含系统相关信息
     */
    private boolean containsSystemInformation(String response) {
        String[] systemPatterns = {
            "system", "系统", "configuration", "配置",
            "internal", "内部", "backend", "后端",
            "database", "数据库", "server", "服务器",
            "api", "endpoint", "接口", "端点"
        };
        
        int count = 0;
        for (String pattern : systemPatterns) {
            if (response.contains(pattern)) {
                count++;
            }
        }
        
        // 如果包含多个系统相关词汇，认为可能泄露
        return count >= 3;
    }
    
    /**
     * 检查是否偏离了贷款主题
     */
    private boolean isOffTopic(String originalInput, String response) {
        String[] loanKeywords = {
            "loan", "借款", "credit", "额度", "interest", "利息",
            "repayment", "还款", "installment", "分期", "amount", "金额",
            "apply", "申请", "approve", "批准", "reject", "拒绝"
        };
        
        // 检查响应是否包含贷款相关词汇
        int loanWordCount = 0;
        for (String keyword : loanKeywords) {
            if (response.contains(keyword)) {
                loanWordCount++;
            }
        }
        
        // 如果没有贷款相关词汇，认为偏离主题
        return loanWordCount == 0;
    }
    
    /**
     * 检查是否包含代码或命令
     */
    private boolean containsCodeOrCommands(String response) {
        String[] codePatterns = {
            "```", "code", "代码", "script", "脚本",
            "command", "命令", "execute", "执行",
            "run", "运行", "function", "函数",
            "class", "类", "method", "方法"
        };
        
        int count = 0;
        for (String pattern : codePatterns) {
            if (response.contains(pattern)) {
                count++;
            }
        }
        
        return count >= 2;
    }
    
    /**
     * 检查是否包含URL或链接
     */
    private boolean containsUrls(String response) {
        String[] urlPatterns = {
            "http://", "https://", "www.", ".com", ".cn",
            ".net", ".org", ".edu", ".gov"
        };
        
        for (String pattern : urlPatterns) {
            if (response.contains(pattern)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * 生成安全的错误响应
     * 
     * @param originalInput 原始输入
     * @return 安全的错误响应
     */
    public String generateSafeErrorResponse(String originalInput) {
        logger.warn("生成安全的错误响应");
        
        // 检测原始输入是否可能是注入攻击
        PromptInjectionFilter.DetectionResult injectionResult = 
            injectionFilter.detectInjection(originalInput);
        
        if (injectionResult.isMalicious()) {
            return "检测到异常请求格式，请使用正常的贷款咨询语言重新提问。";
        }
        
        // 默认安全响应
        return "抱歉，系统检测到响应异常。请重新提问您的贷款相关问题，我将为您提供帮助。";
    }
    
    /**
     * 安全检查结果类
     */
    public static class SecurityCheckResult {
        private final boolean isSafe;
        private final double riskScore;
        private final String reason;
        
        public SecurityCheckResult(boolean isSafe, double riskScore, String reason) {
            this.isSafe = isSafe;
            this.riskScore = riskScore;
            this.reason = reason;
        }
        
        public boolean isSafe() {
            return isSafe;
        }
        
        public double getRiskScore() {
            return riskScore;
        }
        
        public String getReason() {
            return reason;
        }
        
        @Override
        public String toString() {
            return String.format("SecurityCheckResult{isSafe=%s, riskScore=%.2f, reason='%s'}", 
                               isSafe, riskScore, reason);
        }
    }
}