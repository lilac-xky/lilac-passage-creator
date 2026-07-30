package com.lilac.domain.dto.user;

import lombok.Data;

import java.io.Serializable;

/**
 * 管理员创建用户请求。
 */
@Data
public class UserAddRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 账号
     */
    private String userAccount;

    /**
     * 密码
     */
    private String userName;

    /**
     * 头像
     */
    private String userAvatar;

    /**
     * 个性签名
     */
    private String userProfile;

    /**
     * 角色
     */
    private String userRole;

    /**
     * 额度
     */
    private Integer quota;
}
