package com.spring.ai.app.rag.utils;

/**
 * @author LHY
 * @date 2025-09-28 15:02
 * @description
 */
public class ChangeLoanPlanUtils {



//    private ChatVO modifyLoanPlan(LoanInfoCache newCache, ChatRequestDTO chatRequestDTO,
//                                  Map<String, Object> parameters, LoanInfoCache firstCache, LoanRequestDTO loanRequest) {
//        ChatVO chatVO = new ChatVO();
//        if (ChatConstants.CONTRACT_STATUS.OVER_DUE.equals(newCache.getContractStatus())) {
//            chatVO.setContent(YYHChatScriptConstants.OVER_DUE);
//            return chatVO;
//        }
//
//        String tenantCode = chatRequestDTO.getTenantCode();
//        String uuid = chatRequestDTO.getUuid();
//        String sessionId = chatRequestDTO.getSessionId();
//        String query = chatRequestDTO.getQuery();
//        BigDecimal maxAmount = new BigDecimal(newCache.getMaxPrice());
//
//        if (maxAmount.compareTo(BigDecimal.ZERO) == 0) {
//            chatVO.setContent(YYHChatScriptConstants.MAX_PRICE_EQ_ZERO);
//            return chatVO;
//        }
//
//        // 最大借款小于100
//        if (maxAmount.compareTo(MIN_LIMIT) < 0) {
//            chatVO.setContent(ragBiz.requestWorkflowScript(chatRequestDTO, YYHWorkFlowEnum.B02_4.getCode()));
//            return chatVO;
//        }
//
//        String inputAmount = MapUtils.getStringOrNull(parameters, "amount");
//        String inputPeriod = MapUtils.getStringOrNull(parameters, "period");
//
//// log.info("用户uuid：{}, sessionId: {}, query:{} 识别参数 amount:{}, term：{}",
//// uuid, sessionId, query, inputAmount, inputPeriod);
//
//        // 未指定金额和期限
//        if (Objects.isNull(inputAmount) && Objects.isNull(inputPeriod)) {
//            String abTestFlag = abTestApi.getABTestVariables(tenantCode, chatRequestDTO.getUserId())
//                    .stream().findFirst().map(Variable::getValue).orElse(Strings.EMPTY);
//            LoanResponseDTO loanResponseDTO = new LoanResponseDTO();
//            // AB test最大借款
//            if (StringUtils.isNotEmpty(abTestFlag) && "B".equals(abTestFlag)) {
//                loanResponseDTO = generatePlanBiz.defaultMaxGeneratePlan(tenantCode, sessionId,
//                        generatePlanBiz.buildLoanInfoBO(chatRequestDTO.getLoanInfo(), newCache));
//            } else {
//                loanResponseDTO = generatePlanBiz.generateCardInfo(chatRequestDTO,
//                        generatePlanBiz.buildLoanInfoBO(loanRequest, newCache));
//            }
//
//            // 针对场景，首次B01话术，后面同样询问是其他话术
//            String content;
//            if (Objects.isNull(firstCache)) {
//                content = ragBiz.requestWorkflowScript(chatRequestDTO, YYHWorkFlowEnum.B01_1.getCode())
//                        .replace("${alias}", getAlias(chatRequestDTO))
//                        .replace("${maxPrice}", loanRequest.getMaxPrice());
//            } else {
//                content = ragBiz.requestWorkflowScript(chatRequestDTO, chatRequestDTO.getQuery());
//            }
//
//            chatVO.setLoanInfo(loanResponseDTO);
//            chatVO.setContent(content);
//            return chatVO;
//        }
//
//
//        BigDecimal amount = null;
//
//
//        // 先判断两个参数是否都不合法
//        if (StringUtils.isNotBlank(inputAmount) && StringUtils.isNotBlank(inputPeriod)) {
//            amount = new BigDecimal(inputAmount);
//            ChatVO checkChatVO = checkAmountTerm(amount, maxAmount, newCache, inputPeriod, chatVO, chatRequestDTO);
//            if (checkChatVO != null) {
//                return checkChatVO;
//            }
//        }
//
//        // 根据具体业务场景判断是否覆盖content
//        boolean isCoverContent = true;
//
//        // 处理金额参数
//        if (StringUtils.isNotBlank(inputAmount)) {
//
//            amount = (amount == null) ? new BigDecimal(inputAmount) : amount;
//
//            // 缓存的金额为空，说明没推荐过借款 走首次推荐逻辑
//            if (StringUtils.isBlank(newCache.getPrice()) && StringUtils.isBlank(newCache.getTerm())) {
//                return yyhGeneratePlanBiz.generatePlan(chatRequestDTO, newCache, inputAmount, inputPeriod);
//            }
//
//            // 小于100
//            if (amount.compareTo(MIN_LIMIT) < 0) {
//                chatVO.setContent(ragBiz.requestWorkflowScript(chatRequestDTO, YYHWorkFlowEnum.B02_4.getCode()));
//                return chatVO;
//            }
//
//            // 超过限制最大金额
//            if (amount.compareTo(maxAmount) > 0) {
//                chatVO.setContent(ragBiz.requestWorkflowScript(chatRequestDTO, YYHWorkFlowEnum.B02_1.getCode()).replace("${maxPrice}", newCache.getMaxPrice()));
//                newCache.setPrice(newCache.getMaxPrice());
//                isCoverContent = false;
//            }
//
//            // 超过单笔限制5W
//            if (amount.compareTo(MAX_LIMIT) > 0) {
//                isCoverContent = false;
//                // 判断最大借款是否超过5W，超过赋值5w,没超过赋值最大值
//                if (maxAmount.compareTo(MAX_LIMIT) > 0) {
//                    chatVO.setContent(ragBiz.requestWorkflowScript(chatRequestDTO, YYHWorkFlowEnum.B02_2.getCode()));
//                    newCache.setPrice("50000");
//                } else {
//                    // 超过最大额度
//                    chatVO.setContent(ragBiz.requestWorkflowScript(chatRequestDTO, YYHWorkFlowEnum.B02_1.getCode())
//                            .replace("${maxPrice}", newCache.getMaxPrice()));
//                    newCache.setPrice(newCache.getMaxPrice());
//                }
//            }
//
//
//            if (isCoverContent) {
//                // 判断不是100的倍数
//                if (!(amount.remainder(MIN_LIMIT).compareTo(BigDecimal.ZERO) == 0)) {
//                    chatVO.setContent(ragBiz.requestWorkflowScript(chatRequestDTO, YYHWorkFlowEnum.B02_3.getCode()));
//                    // 百位向下取整
//                    newCache.setPrice(new BigDecimal(amount.toString()).setScale(-2, RoundingMode.DOWN).toPlainString());
//                } else {
//                    chatVO.setContent(ragBiz.requestWorkflowScript(chatRequestDTO, YYHWorkFlowEnum.B01_2.getCode()));
//                    // 百位向下取整
//                    newCache.setPrice(new BigDecimal(amount.toString()).toPlainString());
//                }
//            }
//
//            // set 返回卡片
//            LoanResponseDTO loanResponse = new LoanResponseDTO();
//            loanResponse.setTerm(newCache.getTerm());
//            loanResponse.setPrice(newCache.getPrice());
//            loanResponse.setLoanPurseCode(newCache.getLoanPurseCode());
//            loanResponse.setBankCardNo(newCache.getBankCardNo());
//            chatVO.setLoanInfo(loanResponse);
//        }
//
//        // 处理期限参数
//        if (StringUtils.isNotBlank(inputPeriod)) {
//            // 缓存的期限为空，说明没推荐过借款 走首次推荐逻辑
//            if (StringUtils.isBlank(newCache.getTerm())) {
//                return yyhGeneratePlanBiz.generatePlan(chatRequestDTO, newCache, null, inputPeriod);
//            }
//
//            try {
//                // 用户修改的信息不支持
//                if (!newCache.getTerms().contains(Integer.parseInt(inputPeriod))) {
//                    chatVO.setLoanInfo(null);
//                    chatVO.setContent(ragBiz.requestWorkflowScript(chatRequestDTO, YYHWorkFlowEnum.B03.getCode()).replace("${terms}", StringUtils.joinByParam(newCache.getTerms())));
//                    return chatVO;
//                }
//            } catch (NumberFormatException e) {
//                chatVO.setLoanInfo(null);
//                chatVO.setContent(ragBiz.requestWorkflowScript(chatRequestDTO, YYHWorkFlowEnum.B03.getCode()).replace("${terms}", StringUtils.joinByParam(newCache.getTerms())));
//                return chatVO;
//            }
//
//            newCache.setTerm(inputPeriod.toString());
//
//            if (isCoverContent) {
//                chatVO.setContent(ragBiz.requestWorkflowScript(chatRequestDTO, YYHWorkFlowEnum.B01_2.getCode()));
//            }
//
//            // set 返回卡片
//            LoanResponseDTO loanResponse = new LoanResponseDTO();
//            loanResponse.setTerm(newCache.getTerm());
//            loanResponse.setPrice(newCache.getPrice());
//            loanResponse.setLoanPurseCode(newCache.getLoanPurseCode());
//            loanResponse.setBankCardNo(newCache.getBankCardNo());
//            chatVO.setLoanInfo(loanResponse);
//        }
//
//        redisCache.setCacheObject(tenantCode + ChatConstants.LOAD_CARD_KEY + sessionId, newCache, ChatConstants.EXPIRE_TIME_HOURS, TimeUnit.HOURS);
//        return chatVO;
//    }
//
//    private ChatVO checkAmountTerm(BigDecimal amount, BigDecimal maxAmount,
//                                   LoanInfoCache cacheLoan, String period,
//                                   ChatVO chatVO, ChatRequestDTO chatRequestDTO) {
//        boolean amountFlag = false;
//        boolean termFlag = false;
//
//        if (amount.compareTo(MIN_LIMIT) < 0 || amount.compareTo(maxAmount) > 0 || amount.compareTo(MAX_LIMIT) > 0) {
//            amountFlag = true;
//        }
//
//
//        try {
//            if (!cacheLoan.getTerms().contains(Integer.parseInt(period))) {
//                termFlag = true;
//            }
//        } catch (NumberFormatException e) {
//            termFlag = true;
//        }
//
//
//        if (amountFlag && termFlag) {
//            chatVO.setContent(ragBiz.requestWorkflowScript(chatRequestDTO, YYHWorkFlowEnum.YJ02.getCode())
//                    .replace("${maxPrice}", cacheLoan.getMaxPrice())
//                    .replace("${terms}", StringUtils.joinByParam(cacheLoan.getTerms())));
//            return chatVO;
//        }
//        return null;
//    }
//
//
//    private static String getAlias(ChatRequestDTO chatRequestDTO) {
//        ProfileDTO profile = chatRequestDTO.getProfile();
//        if (profile == null) {
//            return "尊敬的客户";
//        }
//
//        String realName = profile.getRealName();
//        String gender = profile.getGender();
//        String alias;
//        if (StringUtils.isBlank(realName) || StringUtils.isBlank(gender)) {
//            alias = "尊敬的客户";
//        } else {
//            String surname;
//            if (realName.length() == 3 || realName.length() == 2) {
//                surname = realName.substring(0, 1);
//            } else {
//                surname = realName.substring(0, 2);
//            }
//            alias = surname + (gender.equals("M") ? "先生" : "女士");
//        }
//        return alias;
//    }

}
