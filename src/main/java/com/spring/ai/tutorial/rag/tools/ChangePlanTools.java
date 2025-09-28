package com.spring.ai.tutorial.rag.tools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * @author yingzi
 * @date 2025/5/21 10:59
 */

public class ChangePlanTools {

    private static final Logger logger = LoggerFactory.getLogger(ChangePlanTools.class);

    @Tool(description = "Change the loan plan, allowing independent changes to the loan amount or the number of installments.")
    public String changeLoanPlanMethod(@ToolParam( description = "借款金额",required = false) String loanAmount,
                                       @ToolParam( description = "分期数",required = false) String installments) {
        logger.info("The new loan plan is {} for customer {}", loanAmount, installments);


        return String.format("The new loan plan is %s for customer %s", loanAmount, installments);
    }
}
