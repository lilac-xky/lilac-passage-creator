package com.lilac.domain.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 登录用户视图
 */
@Data
public class LoginUserVO implements Serializable {
    /**
     * id
     */
    private Long id;

    /**
     * 用户账号
     */
    private String userAccount;

    /**
     * 用户名
     */
    private String userName;

    /**
     * 用户头像
     */
    private String userAvatar;

    /**
     * 用户简介
     */
    private String userProfile;

    /**
     * 用户角色：user-普通用户 admin-管理员
     */
    private String userRole;

    /**
     * 剩余额度
     */
    private Integer quota;

    /**
     * 成为会员时间
     */
    private LocalDateTime vipTime;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
