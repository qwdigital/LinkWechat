package com.linkwechat.domain.customer.vo;

import lombok.Data;

/**
 * 客户社交关系
 */
@Data
public class WeCustomerSocialConnVo {
    /**添加员工数*/
    private  Integer addEmployeNum;
    /**添加群聊数*/
    private Integer addGroupNum;
    /**共同群聊数*/
    private Integer commonGroupNum;
}
