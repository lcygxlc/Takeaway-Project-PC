package com.sky.constant;

public class WeChatConstent {
    //微信服务登录接口地址
    public static final String WX_LOGIN_URL = "https://api.weixin.qq.com/sns/jscode2session";

    //微信服务登入传入参数键值
    public static final String WX_LOGIN_APPID = "appid";
    public static final String WX_LOGIN_SECRET = "secret";
    public static final String WX_LOGIN_JSCODE = "js_code";
    public static final String WX_LOGIN_GRANT_TYPE = "grant_type";
    public static final String WX_LOGIN_GRANT_TYPE_VALUE = "authorization_code";

    //微信服务登入返回参数键值
    public static final String WX_LOGIN_OPENID = "openid";
}
