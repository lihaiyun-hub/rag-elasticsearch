package com.spring.ai.app.rag.flow;

import com.spring.ai.app.rag.flow.Card;
import com.spring.ai.app.rag.flow.ChatResponse;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class ConsumerCreditStateMachine implements FlowStateMachine {

    @Override
    public String supportedIntent() {
        return "CONSUMER_CREDIT";
    }

    @Override
    public ChatResponse start(String userId, IntentResult ir) {
        // 首次进入：返回第一步卡片（活体检测）
        Card liveDetectCard = new Card(
            "live_detect", 
            "消费贷授信第一步：活体检测", 
            "请完成人脸识别以继续授信",
            "intent",
            Map.of("action", "next_step", "label", "开始消费贷授信第一步：活体检测")
        );
        return new ChatResponse(
            "card_list",
            List.of(liveDetectCard),
            "CREDIT_STEP_0",
            UUID.randomUUID().toString()
        );
    }

    @Override
    public ChatResponse next(String userId, String text, Map<String, String> payload) {
        // 根据当前步骤返回对应的下一步卡片
        String currentStep = payload.getOrDefault("current_step", "0");
        
        // 如果没有current_step，表示是初始调用，返回第一步
        if (currentStep == null || currentStep.isEmpty()) {
            return start(userId, new IntentResult("CONSUMER_CREDIT", 0.9, Map.of(), null));
        }
        
        switch (currentStep) {
            case "0":
                return buildStep1Card();
            case "1":
                return buildStep2Card();
            case "2":
                return buildStep3Card();
            case "3":
                return buildStep4Card();
            case "4":
                return buildStep5Card();
            case "5":
                return buildStep6Card();
            case "6":
                return buildStep7Card();
            case "7":
                return buildCompletionCard();
            default:
                return buildErrorResponse();
        }
    }

    private ChatResponse buildStep1Card() {
        Card idUploadCard = new Card(
            "id_upload",
            "第二步：上传身份证",
            "请拍摄或上传身份证正反面",
            "intent",
            Map.of("action", "next_step", "label", "开始第二步：上传身份证")
        );
        return new ChatResponse("card_list", List.of(idUploadCard), "CREDIT_STEP_1", UUID.randomUUID().toString());
    }

    private ChatResponse buildStep2Card() {
        Card passwordCard = new Card(
            "set_password",
            "第三步：设置交易密码",
            "请设置6位数字交易密码",
            "intent",
            Map.of("action", "next_step", "label", "开始第三步：设置交易密码")
        );
        return new ChatResponse("card_list", List.of(passwordCard), "CREDIT_STEP_2", UUID.randomUUID().toString());
    }

    private ChatResponse buildStep3Card() {
        Card agreementCard = new Card(
            "sign_agreement",
            "第四步：签署授信协议",
            "阅读并同意《消费贷授信协议》",
            "intent",
            Map.of("action", "next_step", "label", "开始第四步：签署授信协议")
        );
        return new ChatResponse("card_list", List.of(agreementCard), "CREDIT_STEP_3", UUID.randomUUID().toString());
    }

    private ChatResponse buildStep4Card() {
        Card bindCard = new Card(
            "bind_card",
            "第五步：绑定银行卡",
            "请绑定本人一类储蓄卡",
            "intent",
            Map.of("action", "next_step", "label", "开始第五步：绑定银行卡")
        );
        return new ChatResponse("card_list", List.of(bindCard), "CREDIT_STEP_4", UUID.randomUUID().toString());
    }

    private ChatResponse buildStep5Card() {
        Card occupationCard = new Card(
            "occupation",
            "第六步：填写职业信息",
            "请选择职业类型并填写单位名称",
            "intent",
            Map.of("action", "next_step", "label", "开始第六步：填写职业信息")
        );
        return new ChatResponse("card_list", List.of(occupationCard), "CREDIT_STEP_5", UUID.randomUUID().toString());
    }

    private ChatResponse buildStep6Card() {
        Card contactCard = new Card(
            "contact",
            "第七步：填写联系人",
            "请添加两位常用联系人",
            "intent",
            Map.of("action", "next_step", "label", "开始第七步：填写联系人")
        );
        return new ChatResponse("card_list", List.of(contactCard), "CREDIT_STEP_6", UUID.randomUUID().toString());
    }

    private ChatResponse buildStep7Card() {
        Card assessCard = new Card(
            "assess_wait",
            "第八步：系统评估中",
            "授信审核中，请稍候...",
            "intent",
            Map.of("action", "next_step", "label", "开始第八步：系统评估")
        );
        return new ChatResponse("card_list", List.of(assessCard), "CREDIT_STEP_7", UUID.randomUUID().toString());
    }

    private ChatResponse buildCompletionCard() {
        // 授信完成，返回借款方案卡片
        Map<String, String> payload = Map.of(
            "action", "confirm_loan",
            "label", "确认借款",
            "change_action", "change_plan",
            "change_label", "更换方案"
        );
        Card loanCard = new Card(
            "consumer_loan_offers",
            "授信已通过！为您推荐以下消费贷方案",
            "多款额度可选，随借随还",
            "intent",
            payload
        );
        return new ChatResponse("card_list", List.of(loanCard), "CREDIT_DONE", UUID.randomUUID().toString());
    }

    private ChatResponse buildErrorResponse() {
        return new ChatResponse("error", "非法步骤", "ERROR", "");
    }
}