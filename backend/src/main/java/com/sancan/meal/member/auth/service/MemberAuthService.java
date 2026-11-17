package com.sancan.meal.member.auth.service;

import com.sancan.meal.common.api.LoginResp;
import com.sancan.meal.common.exception.ErrorCode;
import com.sancan.meal.common.exception.ServiceException;
import com.sancan.meal.common.security.LoginUser;
import com.sancan.meal.common.security.TokenStore;
import com.sancan.meal.common.security.UserType;
import com.sancan.meal.config.MealProperties;
import com.sancan.meal.member.user.dal.dataobject.MemberUserDO;
import com.sancan.meal.member.user.dal.mapper.MemberUserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/**
 * 小程序登录。
 *
 * <p>生产路径：{@code wx.login()} 取 code → code2Session 换 openid（手册 §5 第 3 步）。
 * 本机联调使用 mock：openid 由 code 稳定映射，便于反复登录同一账号。
 */
@Service
public class MemberAuthService {

    private static final Logger log = LoggerFactory.getLogger(MemberAuthService.class);

    private final MemberUserMapper memberUserMapper;
    private final TokenStore tokenStore;
    private final MealProperties properties;
    private final WeChatApiClient weChatApiClient;

    public MemberAuthService(MemberUserMapper memberUserMapper,
                             TokenStore tokenStore,
                             MealProperties properties,
                             WeChatApiClient weChatApiClient) {
        this.memberUserMapper = memberUserMapper;
        this.tokenStore = tokenStore;
        this.properties = properties;
        this.weChatApiClient = weChatApiClient;
    }

    @Transactional
    public LoginResp login(String code, String nickname, String avatar) {
        if (code == null || code.isBlank()) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, "缺少微信登录 code");
        }
        String openid = resolveOpenid(code);
        MemberUserDO user = memberUserMapper.selectByOpenid(openid);
        if (user == null) {
            user = new MemberUserDO();
            user.setOpenid(openid);
            user.setNickname(nickname == null || nickname.isBlank() ? "新用户" : nickname);
            user.setAvatar(avatar);
            user.setStatus(0);
            memberUserMapper.insert(user);
        } else if (user.getStatus() != null && user.getStatus() != 0) {
            throw new ServiceException(ErrorCode.FORBIDDEN, "账号已停用");
        }
        Set<String> roles = Set.of("MEMBER");
        LoginUser loginUser = new LoginUser(user.getId(), UserType.MEMBER, user.getNickname(), roles);
        String token = tokenStore.issue(loginUser);
        return new LoginResp(token, UserType.MEMBER.name(), user.getId(), user.getNickname(), roles);
    }

    /**
     * mock 仅用于本地开发（local/test profile）；其余环境走 code2Session。
     * 体验/生产环境若开启 mock，日志会明确告警，且 code 可被任何人伪造。
     */
    private String resolveOpenid(String code) {
        if (properties.getWx().isMockEnabled()) {
            log.warn("微信登录处于 mock 模式，仅限本地开发使用");
            return "mock_" + code;
        }
        return weChatApiClient.exchangeOpenid(code);
    }
}
