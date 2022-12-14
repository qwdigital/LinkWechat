package com.linkwechat.domain.taggroup.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.Date;
import java.util.List;

@ApiModel("老客标签建群列表接口简略信息")
@Data
public class WePresTagTaskListVo {
    @ApiModelProperty("任务id")
    private Long taskId;

    @ApiModelProperty("任务名")
    private String taskName;

    @ApiModelProperty("标签名列表")
    private List<String> tagList;

    @ApiModelProperty("发送类型 0: 企业群发 1: 个人群发")
    private Integer sendType;

    @ApiModelProperty("当前群人数")
    private Integer totalMember;

    @ApiModelProperty("创建时间")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date createTime;

    @ApiModelProperty("创建者")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private String createBy;

    @JsonIgnore
    private String tagListStr;
}
