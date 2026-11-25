package com.campusoverflow.identity.application;

/** 用例：本人修改资料（PB-41）。四项都按「提交什么就是什么」处理，不做部分更新合并。 */
public record UpdateProfileCommand(String displayName, String email, String college, String className) {
}
