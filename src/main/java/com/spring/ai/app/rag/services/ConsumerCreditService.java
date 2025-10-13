package com.spring.ai.app.rag.services;

import com.spring.ai.app.rag.flow.Card;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class ConsumerCreditService {

    /** key: userId, value: 已授信 */
    private final Map<String, Boolean> authorizedMap = new ConcurrentHashMap<>();

    /** key: userId, value: 当前已完成的步骤序号（0~10） */
    private final Map<String, Integer> stepMap = new ConcurrentHashMap<>();

    private static final int STEPS = 8; // 0-7共8步

    /**
     * 查询用户消费贷授信状态
     */
    public boolean isAuthorized(String userId) {
        log.info("isAuthorized userId={}", userId);
        return authorizedMap.getOrDefault(userId, false);
    }

    /**
     * 显式设置授信状态（由前端配置传入）
     */
    public void setAuthorized(String chatId, boolean authorized) {
        if (authorized) {
            this.authorizedMap.put(chatId, true);
        } else {
            this.authorizedMap.remove(chatId);
        }
    }

    /**
     * 标记用户已授信（步骤10完成后调用）
     */
    public void markAuthorized(String userId) {
        authorizedMap.put(userId, true);
    }

    /**
     * 获取当前步骤编号（0-7）
     */
    public int getCurrentStep(String userId) {
        int step = stepMap.getOrDefault(userId, 0);
        log.info("getCurrentStep userId={} step={}", userId, step);
        return step;
    }

    /**
     * 根据步骤生成对应卡片
     */
    public Card buildStepCard(String userId) {
        int step = getCurrentStep(userId);
        return switch (step) {
            case 0 -> new Card("live_detect", "消费贷授信第一步：活体检测", "请完成人脸识别以继续授信","intent",Map.of());
            case 1 -> new Card("id_upload", "第二步：上传身份证", "请拍摄或上传身份证正反面","intent",Map.of());
            case 2 -> new Card("set_password", "第三步：设置交易密码", "请设置6位数字交易密码","intent",Map.of());
            case 3 -> new Card("sign_agreement", "第四步：签署授信协议", "阅读并同意《消费贷授信协议》","intent",Map.of());
            case 4 -> new Card("bind_card", "第五步：绑定银行卡", "请绑定本人一类储蓄卡","intent",Map.of());
            case 5 -> new Card("occupation", "第六步：填写职业信息", "请选择职业类型并填写单位名称","intent",Map.of());
            case 6 -> new Card("contact", "第七步：填写联系人", "请添加两位常用联系人","intent",Map.of());
            case 7 -> new Card("assess_wait", "第八步：系统评估中", "授信审核中，请稍候...","intent",Map.of());
            default -> new Card("unknown", "未知步骤", "","intent",Map.of());
        };
    }

    /**
     * 完成指定步骤
     */
    public void completeStep(String userId, int step) {
        log.info("completeStep userId={} step={}", userId, step);
        stepMap.put(userId, step + 1);  // 完成后步骤+1
    }

    /**
     * 重置流程（可选）
     */
    public void reset(String userId) {
        stepMap.remove(userId);
        authorizedMap.remove(userId);
    }

    /**
     * 初始化用户数据（若不存在）
     */
    public void initIfAbsent(String userId) {
        stepMap.putIfAbsent(userId, 0);
        authorizedMap.putIfAbsent(userId, false);
    }
}