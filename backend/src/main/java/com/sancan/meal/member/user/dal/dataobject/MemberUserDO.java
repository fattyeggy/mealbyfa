package com.sancan.meal.member.user.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import com.sancan.meal.common.po.BaseDO;

/** 小程序会员。生产环境由 RuoYi {@code member_user} 承接。 */
@TableName("member_user")
public class MemberUserDO extends BaseDO {

    private String openid;
    private String unionid;
    private String nickname;
    private String avatar;
    private String mobile;
    /** 0 正常，1 停用。 */
    private Integer status;

    public String getOpenid() {
        return openid;
    }

    public void setOpenid(String openid) {
        this.openid = openid;
    }

    public String getUnionid() {
        return unionid;
    }

    public void setUnionid(String unionid) {
        this.unionid = unionid;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getAvatar() {
        return avatar;
    }

    public void setAvatar(String avatar) {
        this.avatar = avatar;
    }

    public String getMobile() {
        return mobile;
    }

    public void setMobile(String mobile) {
        this.mobile = mobile;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }
}
