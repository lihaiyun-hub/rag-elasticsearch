package com.spring.ai.app.rag.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spring.ai.app.rag.model.UserContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class ConsumerLoanTools {

    private static final Logger logger = LoggerFactory.getLogger(ConsumerLoanTools.class);
    private final ObjectMapper objectMapper;


    public ConsumerLoanTools(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    // 从参数 Map 解析金额、期限、用途
    // 现支持的结构（非嵌套 Map）：
    // - 修改意愿：布尔或字符串 "true"（例如 amount: true），表示该字段需要澄清；不携带具体值
    // - 具体值：字符串或数值（例如 amount: "5000" 或 5000，term: "12" 或 12，purpose: "装修"）
    // - 兼容别名：期数字段可用 term 或 installments
    private Map<String, Object> parseLoanParameters(Map<String, Object> params) {
        Map<String, Object> parsed = new HashMap<>();

        // 金额
        Object amountObj = params != null ? params.get("amount") : null;
        Double amount = parseAmountField(amountObj);
        if (amount != null) {
            parsed.put("amount", amount);
        }

        // 期数（兼容旧参数名：installments）
        Object termObj = params != null ? params.get("term") : null;
        Integer term = parseTermField(termObj);
        if (term != null) {
            parsed.put("term", term);
        }

        // 用途
        Object purposeObj = params != null ? params.get("purpose") : null;
        String purpose = parsePurposeField(purposeObj);
        if (purpose != null && !purpose.isEmpty()) {
            parsed.put("purpose", purpose);
        }

        return parsed;
    }


    // 重载：接收 Map 参数，内置澄清判断；当某字段为布尔/字符串 "true" 时直接返回澄清话术
    public String generateLoanOffers(String userId, UserContext userContext, Map<String, Object> parameters) {
        try {
            boolean askAmount = needsClarification(parameters != null ? parameters.get("amount") : null);
            boolean askTerm = needsClarification(parameters != null ? parameters.get("term") : null);
            boolean askPurpose = needsClarification(parameters != null ? parameters.get("purpose") : null);

            if (askAmount || askTerm || askPurpose) {
                return buildClarificationMessage(askAmount, askTerm, askPurpose);
            }

            Map<String, Object> parsed = parseLoanParameters(parameters);
            Double amount = (Double) parsed.get("amount");
            Integer termMonths = (Integer) parsed.get("term");
            String purpose = (String) parsed.get("purpose");
            return generateLoanOffers(userId, userContext, amount, termMonths, purpose);
        } catch (Exception e) {
            logger.error("Failed to generateLoanOffers with parameters (with clarification check)", e);
            return "为了为你生成合适的借款方案，请补充：借款金额（元）、借款期限（月）、借款用途。";
        }
    }

    /**
     * 为已授信用户生成借款方案
     * 当用户已授信并表达借款意愿时调用
     * 支持参数化生成：可指定金额、期数、用途等参数
     */
    public String generateLoanOffers(String userId, UserContext userContext, Double amount, Integer termMonths, String purpose) {
        logger.info("Generating loan offers for authorized user: {}, amount: {}, term: {}, purpose: {}",
                userId, amount, termMonths, purpose);

        try {
            // 构建借款方案
            Map<String, Object> offerData = new HashMap<>();

            // 设置默认值
            double finalAmount = amount != null ? amount : 50000.0;
            int finalTerm = termMonths != null ? termMonths : 12;

            // 计算利率和还款信息
            double annualRate = 0.0375; // 3.75%的年利率
            double monthlyRate = annualRate / 12;

            // 使用等额本息计算月供
            double monthlyPayment = finalAmount * monthlyRate * Math.pow(1 + monthlyRate, finalTerm)
                    / (Math.pow(1 + monthlyRate, finalTerm) - 1);

            // 计算总利息
            double totalInterest = monthlyPayment * finalTerm - finalAmount;

            // 构建完整的借款方案数据
            offerData.put("amount", finalAmount);
            offerData.put("rate", annualRate * 100); // 转换为百分比
            offerData.put("term", finalTerm);
            offerData.put("monthlyPayment", (int) monthlyPayment);
            offerData.put("totalInterest", (int) totalInterest);
            offerData.put("tag", "推荐");
            offerData.put("bankName", "建设银行");
            offerData.put("repayMode", "等额本息");
            offerData.put("purpose", purpose != null ? purpose : "个人消费");
            offerData.put("bankTail", "1123");
            offerData.put("discountText", "查看优惠");
            offerData.put("firstPayment", (int) monthlyPayment);
            offerData.put("lender", "建设银行");
            offerData.put("annualRate", annualRate * 100);
            return objectMapper.writeValueAsString(offerData);

        } catch (Exception e) {
            logger.error("Failed to generate loan offers for user: {}", userId, e);
            return "{\"error\":\"生成借款方案失败，请稍后再试\"}";
        }
    }

    /**
     * 借款申请（意图处理）
     * 当用户有借款意图：
     * - 已授信：生成借款方案卡片；
     * - 未授信：提示需先申请额度；如同意，请回复“申请额度”。
     */
    public String handleLoanApplicationIntent(String userId, UserContext userContext, Map<String, Object> parameters) {
        try {
            Boolean authorized = userContext != null ? userContext.getAuthorized() : null;
            if (Boolean.TRUE.equals(authorized)) {
                return generateLoanOffers(userId, userContext, parameters);
            }
            return "您当前尚未授信可用额度。如需获取额度，请先进行申请。如果同意，请回复：申请额度。";
        } catch (Exception e) {
            logger.error("handleLoanApplicationIntent failed for user: {}", userId, e);
            return "抱歉，处理借款申请时出现问题，请稍后再试。";
        }
    }

    /**
     * 查询额度
     * - 已授信：返回具体额度话术；
     * - 未授信：提示需先申请额度；如同意，请回复“申请额度”。
     */
    public String handleQueryCreditLimit(String userId, UserContext userContext) {
        try {
            Double credit = userContext.getAvailableCredit();
            String amountStr = String.format("¥%,.0f", credit);
            return "您当前的可用额度为 " + amountStr + "。您要借多少呢？";
        } catch (Exception e) {
            logger.error("handleQueryCreditLimit failed for user: {}", userId, e);
            return "抱歉，查询额度时出现问题，请稍后再试。";
        }
    }

    /**
     * 查询额度（对外方法，与 ChatService 调用名对齐）
     * 包装调用 handleQueryCreditLimit，保持命名一致性。
     */
    public String queryCreditLimit(String userId, UserContext userContext) {
        return handleQueryCreditLimit(userId, userContext);
    }

    /**
     * 申请额度（开启授信流程）
     * 当用户回复“申请额度”时，开启授信流程并返回对应话术。
     */
    public String handleApplyCreditLimit(String userId, UserContext userContext) {
        try {
            return "好的，已为您开启授信申请流程。接下来需要完成实名校验与必要信息采集，以评估您的可用额度";
        } catch (Exception e) {
            logger.error("handleApplyCreditLimit failed for user: {}", userId, e);
            return "抱歉，开启授信流程时出现问题，请稍后再试。";
        }
    }


    private String buildClarificationMessage(boolean amount, boolean term, boolean purpose) {
        List<String> items = new ArrayList<>();
        if (amount) items.add("借款金额（元）");
        if (term) items.add("借款期限（月）");
        if (purpose) items.add("借款用途");
        if (items.isEmpty()) {
            return "当前未检测到需要澄清的参数，如需生成方案请提供金额、期限或用途信息。";
        }
        return "为了为您生成合适的借款方案，请补充：" + String.join("、", items) + "。";
    }


    // 判断是否需要澄清：
    // - 布尔值或字符串 "true" 表示用户要修改该字段但未给出具体值
    private boolean needsClarification(Object field) {
        if (field == null) return false;
        String v = String.valueOf(field).trim();
        return "true".equalsIgnoreCase(v);
    }

    // 解析金额字段，仅支持布尔或字符串/数值
    private Double parseAmountField(Object amountObj) {
        try {
            if (amountObj == null) return null;
            if (amountObj instanceof Boolean) return null; // true 表示澄清，false 表示不修改
            if (amountObj instanceof Number) return ((Number) amountObj).doubleValue();
            String s = String.valueOf(amountObj).trim();
            if (s.isEmpty() || "true".equalsIgnoreCase(s)) return null;
            return Double.valueOf(s);
        } catch (Exception ignore) {
        }
        return null;
    }

    // 解析期数字段，仅支持布尔或字符串/数值
    private Integer parseTermField(Object termObj) {
        try {
            if (termObj == null) return null;
            if (termObj instanceof Boolean) return null; // true 表示澄清，false 表示不修改
            if (termObj instanceof Number) return ((Number) termObj).intValue();
            String s = String.valueOf(termObj).trim();
            if (s.isEmpty() || "true".equalsIgnoreCase(s)) return null;
            return Integer.valueOf(s);
        } catch (Exception ignore) {
        }
        return null;
    }

    // 解析用途字段，仅支持布尔或字符串
    private String parsePurposeField(Object purposeObj) {
        if (purposeObj == null) return null;
        if (purposeObj instanceof Boolean) return null; // true 表示澄清，false 表示不修改
        String p = String.valueOf(purposeObj).trim();
        if (p.isEmpty() || "true".equalsIgnoreCase(p)) return null;
        return p;
    }
}
