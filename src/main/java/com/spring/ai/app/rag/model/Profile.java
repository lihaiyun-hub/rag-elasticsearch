package com.spring.ai.app.rag.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author LHY
 * @date 2025-10-30 16:57
 * @description
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Profile {
    private String realName;  // 真实姓名
    private String gender;  // 性别
    private String currentPhone;  // 当前手机号
    private String registerPhone;  // 注册手机号

}
