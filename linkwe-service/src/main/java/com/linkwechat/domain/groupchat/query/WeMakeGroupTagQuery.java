package com.linkwechat.domain.groupchat.query;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.List;

@Data
public class WeMakeGroupTagQuery {

    //群聊ID
    private String chatId;

    //标签Id列表
    private List<String> tagIds;
}
