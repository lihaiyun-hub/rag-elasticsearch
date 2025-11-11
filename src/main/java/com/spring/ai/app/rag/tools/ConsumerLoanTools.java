package com.spring.ai.app.rag.tools;

import cn.hutool.core.util.ObjectUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.spring.ai.app.rag.cache.RedisHashCache;
import com.spring.ai.app.rag.model.ChatVO;
import com.spring.ai.app.rag.model.LoanResponseDTO;
import com.spring.ai.app.rag.model.UserContext;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.*;

@Component
public class ConsumerLoanTools {

    private static final Logger logger = LoggerFactory.getLogger(ConsumerLoanTools.class);
    private final RedisHashCache redisHashCache;
    private static final String MSG_CLARIFY_PREFIX = "为了为您生成合适的借款方案，请补充：";
    private static final String MSG_NO_PARAMS_DETECTED = "当前未检测到需要澄清的参数，如需生成方案请提供金额、期限或用途信息。";
    private static final String MSG_CLARIFY_FALLBACK = "为了为你生成合适的借款方案，请补充：借款金额（元）、借款期限（月）、借款用途。";

    private static final String MSG_OFFER_CONTENT = "为您推荐如下借款方案，若与您的需求不符，您可以直接在卡片上修改，或者告诉我您的需求，例如，您可以对我说我要借500元或者我要分12期等等。";

    private static final String MSG_QUERY_LIMIT_FMT = "您当前的可用额度为 %s。您要借多少呢？";
    private static final String MSG_QUERY_ERROR = "抱歉，查询额度时出现问题，请稍后再试。";

    private static final String MSG_APPLY_CREDIT_START = "好的，已为您开启授信申请流程。接下来需要完成实名校验与必要信息采集，以评估您的可用额度";
    private static final String MSG_APPLY_CREDIT_ERROR = "抱歉，开启授信流程时出现问题，请稍后再试。";


    //单笔最大可借金额
    private static final double MAXIMUM_SINGLE_BORROWABLE_AMOUNT = 50000.0;
    //单笔最小可借金额
    private static final double MINIMUM_SINGLE_BORROWABLE_AMOUNT = 100.0;
    private static final double ROUND_UNIT = 100.0;

    public ConsumerLoanTools(RedisHashCache redisHashCache) {
        this.redisHashCache = redisHashCache;
    }

    // 从参数 Map 解析金额、期限、用途
    // 现支持的结构（非嵌套 Map）：
    // - 修改意愿：布尔或字符串 "true"（例如 amount: true），表示该字段需要澄清；不携带具体值
    // - 具体值：字符串或数值（例如 amount: "5000" 或 5000，term: "12" 或 12，purpose: "装修"）
    // - 兼容别名：期数字段可用 term 或 installments
    private Map<String, Object> parseLoanParameters(Map<String, Object> params) {
        Map<String, Object> parsed = new HashMap<>();

        // 金额
        Object amountObj = ObjectUtil.isNotEmpty(params) ? params.get("amount") : null;
        Double amount = parseAmountField(amountObj);
        if (amount != null) {
            parsed.put("amount", amount);
        }

        // 期数
        Object termObj = ObjectUtil.isNotEmpty(params) ? params.get("term") : null;
        Integer term = parseTermField(termObj);
        if (term != null) {
            parsed.put("term", term);
        }

        // 用途
        Object purposeObj = ObjectUtil.isNotEmpty(params) ? params.get("purpose") : null;
        String purpose = parsePurposeField(purposeObj);
        if (purpose != null && !purpose.isEmpty()) {
            parsed.put("purpose", purpose);
        }

        return parsed;
    }


    private String clarification(Map<String, Object> parameters) {
        boolean askAmount = needsClarification(ObjectUtil.isNotEmpty(parameters ) ? parameters.get("amount") : null);
        boolean askTerm = needsClarification(ObjectUtil.isNotEmpty(parameters ) ? parameters.get("term") : null);
        boolean askPurpose = needsClarification(ObjectUtil.isNotEmpty(parameters ) ? parameters.get("purpose") : null);
        if (askAmount || askTerm || askPurpose) {
            return buildClarificationMessage(askAmount, askTerm, askPurpose);
        }
        return null;
    }


    // 重载：接收 Map 参数，内置澄清判断；当某字段为布尔/字符串 "true" 时直接返回澄清话术
    public ChatVO generateLoanOffers(UserContext userContext, Map<String, Object> parameters) {
        try {
            String cacheKey = buildCacheKey(userContext);
            Map<String, String> fields = redisHashCache.hEntries(cacheKey);
            String contractStatus = fields.get("contractStatus");
            if (StringUtils.equals(contractStatus, "1")){
                return new ChatVO("尊敬的客户,小优非常理解您的需求，也希望能帮助您解决当前的财务问题。不过，根据我司记录显示，您名下仍有逾期未结清的借款，因此小优暂时无法为您办理新的业务。\n如果您有任何疑问或需要进一步的帮助，建议您进入【我的】-【在线客服】进一步咨询");
            }
            // 校验当前用户是否有可用额度
            String maxPriceStr = fields.get("maxPrice");
            if (ObjectUtil.isEmpty(maxPriceStr) || Double.parseDouble(maxPriceStr.trim()) <= 0) {
                return new ChatVO("您暂无可用额度");
            }
            double credit = Double.parseDouble(maxPriceStr);
            // offer_count>0 时，说明用户已生成过借款方案
            int offerCount = Integer.parseInt(ObjectUtil.isEmpty(fields.get("offer_count")) ? "0" : fields.get("offer_count"));
            Map<String, Object> parsed = parseLoanParameters(parameters);
            String contractNum = fields.get("contractNum");
            List<Integer> termOptions = redisHashCache.hGetJson(cacheKey, "terms_json", new TypeReference<>() {
            });

            double amount;
            int termMonths;
            String purpose;

            if (offerCount > 0) {
                // 对用户意图进行澄清
                String clarification = clarification(parameters);
                if (clarification != null) {
                    return new ChatVO(clarification);
                }
                // 金额：若当前轮次对话未指定，使用历史 price 字段值
                amount = ObjectUtil.isEmpty(parsed.get("amount")) ? Double.parseDouble(fields.get("price").trim()) : (Double) parsed.get("amount");
                // 期数：若当前轮次对话未指定，使用历史 term 字段值
                termMonths = ObjectUtil.isEmpty(parsed.get("term")) ? Integer.parseInt(fields.get("term").trim()) : (Integer) parsed.get("term");
                // 用途：若当前轮次对话未指定，使用历史 loanPurseCode 字段值

            } else {
                // 金额：若当前轮次对话未指定，根据历史借款记录计算平均借款金额
                amount = ObjectUtil.isEmpty(parsed.get("amount")) ? getAvgPrice(fetchRecentBorrowRecords(contractNum, UUID.randomUUID().toString()), credit) : (Double) parsed.get("amount");
                // 期数：若当前轮次对话未指定，使用历史借款记录中频率最高的期数
                termMonths = ObjectUtil.isEmpty(parsed.get("term")) ? getModeTerm(fetchRecentBorrowRecords(contractNum, UUID.randomUUID().toString()), termOptions) : (Integer) parsed.get("term");
                // 用途：若当前轮次对话未指定，使用缓存中的数据
            }
            purpose = ObjectUtil.isEmpty(parsed.get("purpose")) ? fields.get("loanPurseCode") : (String) parsed.get("purpose");
            String content = MSG_OFFER_CONTENT;
            // 借款金额小于100,取100
            if (amount < MINIMUM_SINGLE_BORROWABLE_AMOUNT) {
                amount = MINIMUM_SINGLE_BORROWABLE_AMOUNT;
                content = "单笔借款金额不能小于100元，您的借款金额已调整为100元";
            }

            // 借款金额大于最大可用额度，取最大可用额度
            if (amount > credit) {
                amount = credit;
                content = "单笔借款金额不能大于您的最大可用额度" + credit + "元，您的借款金额已调整为" + credit + "元";
            }
            // 借款金额大于单笔最大可借金额，取单笔最大可借金额
            if (amount > MAXIMUM_SINGLE_BORROWABLE_AMOUNT) {
                amount = MAXIMUM_SINGLE_BORROWABLE_AMOUNT;
                content = "单笔借款金额不能大于单笔最大可借金额" + MAXIMUM_SINGLE_BORROWABLE_AMOUNT + "元，您的借款金额已调整为" + MAXIMUM_SINGLE_BORROWABLE_AMOUNT + "元";
            }
            // 借款金额必须是100的整数倍
            if (amount % ROUND_UNIT != 0) {
                amount = Math.floor(amount / ROUND_UNIT) * ROUND_UNIT;
                content = "借款金额必须是100的整数倍，您的借款金额已调整为" + amount + "元";
            }
            // 借款期数不在可选分期选项中，取最近的一个
            if (!termOptions.contains(termMonths)) {
                Integer finalTermMonths = termMonths;
                termMonths = termOptions.stream().min(Comparator.comparingInt(i -> Math.abs(i - finalTermMonths))).orElse(termOptions.get(0));
                content = "借款期数不在可选分期选项中，您的借款期数已调整为" + termMonths + "月";
            }
            logger.info("Generating loan offers for authorized user: {}, amount: {}, term: {}, purpose: {}", userContext.getUserId(), amount, termMonths, purpose);
            ChatVO loanOffer = generateLoanOffers(amount, termMonths, purpose,cacheKey);
            loanOffer.setContent(content);
            // 生成成功后，更新缓存中的 offer_count（用于下次判断是否为首次）

            redisHashCache.hPut(cacheKey, "offer_count", String.valueOf(offerCount + 1));
            return loanOffer;
        } catch (Exception e) {
            logger.error("Failed to generateLoanOffers with parameters (with clarification check)", e);
            ChatVO vo = new ChatVO();
            vo.setContent(MSG_CLARIFY_FALLBACK);
            return vo;
        }
    }


    private static String buildCacheKey(UserContext userContext) {
        String tenant = userContext != null ? userContext.getTenantCode() : null;
        String sessionId = userContext != null ? userContext.getSessionId() : null;
        String storageId = (tenant != null && !tenant.isBlank()) ? (tenant + ":" + sessionId) : sessionId;
        return storageId != null ? ("loaninfo:" + storageId) : null;
    }


    public ChatVO generateLoanOffers(Double amount, Integer termMonths, String purpose,String cacheKey) {
            Map<String, String> map = new HashMap<>();
            LoanResponseDTO loan = new LoanResponseDTO();
            loan.setPrice(String.valueOf(amount));
            loan.setTerm(String.valueOf(termMonths));
            loan.setLoanPurseCode(purpose);
            ChatVO vo = new ChatVO();
            vo.setTypeCode(1); // 标记为卡片回复，便于上层判断
            vo.setLoanInfo(loan);
            map.put("price", String.valueOf(amount));
            map.put("term", String.valueOf(termMonths));
            redisHashCache.hPutAll(cacheKey, map);
            return vo;
    }


    /**
     * 查询用户最近的借款记录（示例方法，待接入真实API）。
     * 按照合同编号查询，并在调用处按180天过滤。
     */
    private List<BorrowRecord> fetchRecentBorrowRecords(String contractNum, String uuid) {
        // TODO: 接入外部服务：通过合同编号查询历史借款记录
        // 返回字段至少包含借款金额、分期与借款日期
        return List.of();
    }

    /**
     * 简化的借款记录结构（示例）
     */
    private static class BorrowRecord {
        double amount;
        int term;
        LocalDate date;

        BorrowRecord(double amount, int term, LocalDate date) {
            this.amount = amount;
            this.term = term;
            this.date = date;
        }
    }

    /**
     * 首次推荐：仅根据最近180天的借款记录给出金额与推荐，不覆盖用户已明确的参数。
     * 返回更新后的金额
     */
    private double getAvgPrice(List<BorrowRecord> recent, Double credit) {
        // todo 1.校验是否存在历史借款记录
        // 2.如果不存在，取最高额度
        // 3.如果存在，取历史记录中金额的平均值
        // 4.比较平均值与最高额度，取较小值
        return credit;
    }

    /**
     * 首次推荐：仅根据最近180天的借款记录给出分期推荐，不覆盖用户已明确的参数。
     * 返回更新后的金额
     */
    private int getModeTerm(List<BorrowRecord> recent, List<Integer> termOptions) {
        // todo 1.校验是否存在历史借款记录
        // 2.如果不存在，取可选期数中的最大值
        // 3.如果存在，取历史记录中分期的众数
        // 4.如果众数不在可选期数中，取可选期数中的最大值
        return termOptions.get(0);
    }


    /**
     * 查询额度
     * - 已授信：返回具体额度话术；
     */
    public ChatVO handleQueryCreditLimit(UserContext userContext) {
        try {
            Double credit = userContext.getAvailableCredit();
            String amountStr = String.format("¥%,.0f", credit);
            ChatVO vo = new ChatVO();
            vo.setContent(String.format(MSG_QUERY_LIMIT_FMT, amountStr));
            return vo;
        } catch (Exception e) {
            logger.error("handleQueryCreditLimit failed for user: {}", userContext.getUserId(), e);
            ChatVO vo = new ChatVO();
            vo.setContent(MSG_QUERY_ERROR);
            return vo;
        }
    }


    /**
     * 申请额度（开启授信流程）
     * 当用户回复“申请额度”时，开启授信流程并返回对应话术。
     */
    public ChatVO handleApplyCreditLimit() {
        try {
            ChatVO vo = new ChatVO();
            vo.setContent(MSG_APPLY_CREDIT_START);
            return vo;
        } catch (Exception e) {
            ChatVO vo = new ChatVO();
            vo.setContent(MSG_APPLY_CREDIT_ERROR);
            return vo;
        }
    }


    private String buildClarificationMessage(boolean amount, boolean term, boolean purpose) {
        List<String> items = new ArrayList<>();
        if (amount) items.add("借款金额（元）");
        if (term) items.add("借款期限（月）");
        if (purpose) items.add("借款用途");
        if (items.isEmpty()) {
            return MSG_NO_PARAMS_DETECTED;
        }
        return MSG_CLARIFY_PREFIX + String.join("、", items) + "。";
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
