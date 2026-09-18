package com.hparty.common.util;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 密码强度策略。
 *
 * <p>纯函数、无依赖，便于单元测试。返回 {@code null} 表示通过，否则返回**面向用户的中文原因**，
 * 由调用方转成 {@code BizException} —— 这样校验逻辑与提示文案集中在一处，
 * 不会出现「新增用户」和「重置密码」两处提示不一致的情况。</p>
 *
 * <p><b>强度要求是有意克制过的</b>：只要求 8 位以上、字母数字混合、不是弱口令。
 * 强制大小写+符号在党务系统里往往适得其反 —— 用户会把密码写在便签上贴屏幕边。
 * 真正有效的措施是「首次登录强制改密」与「定期过期」，那两条由 {@code AuthService} 负责。</p>
 */
public final class PasswordPolicy {

    /** 最短长度 */
    public static final int MIN_LENGTH = 8;

    /** 最长长度（BCrypt 只取前 72 字节，这里收紧到 32 避免误用） */
    public static final int MAX_LENGTH = 32;

    private PasswordPolicy() {
    }

    private static final Pattern HAS_LETTER = Pattern.compile("[A-Za-z]");
    private static final Pattern HAS_DIGIT = Pattern.compile("\\d");
    private static final Pattern ONLY_REPEAT = Pattern.compile("^(.)\\1+$");

    /**
     * 常见弱口令。命中即拒绝。
     * <p>前几条是本项目初始化脚本里用过的默认值，必须挡住 —— 否则「强制改密」就白做了。</p>
     */
    private static final Set<String> WEAK_PASSWORDS = Set.of(
            "12345678", "123456789", "1234567890", "123456", "11111111",
            "password", "password1", "passw0rd", "p@ssword",
            "qwertyui", "qwerty123", "abc12345", "a1234567",
            "admin123", "administrator", "root1234", "test1234",
            "iloveyou", "welcome1", "sunshine", "princess",
            "88888888", "66666666", "00000000", "5201314520",
            "hparty123", "dangjian1", "zhongguo1"
    );

    /** 连续升序/降序的键盘或字母序列，如 abcdefgh、87654321 */
    private static final List<String> SEQUENCES = List.of(
            "abcdefgh", "abcdefghi", "hgfedcba",
            "12345678", "123456789", "9876543210", "87654321",
            "qwertyui", "asdfghjk", "zxcvbnm,"
    );

    /**
     * 校验密码强度。
     *
     * @param rawPassword 明文密码
     * @param username    登录账号，用于拦截「密码与账号相同/包含账号」；可为 null
     * @return {@code null} 表示通过；否则为拒绝原因（可直接展示给用户）
     */
    public static String validate(String rawPassword, String username) {
        if (rawPassword == null || rawPassword.isEmpty()) {
            return "密码不能为空";
        }

        String pwd = rawPassword.trim();

        if (pwd.length() < MIN_LENGTH) {
            return "密码长度不能少于 " + MIN_LENGTH + " 位";
        }
        if (pwd.length() > MAX_LENGTH) {
            return "密码长度不能超过 " + MAX_LENGTH + " 位";
        }
        if (pwd.contains(" ")) {
            return "密码不能包含空格";
        }
        if (!HAS_LETTER.matcher(pwd).find() || !HAS_DIGIT.matcher(pwd).find()) {
            return "密码必须同时包含字母和数字";
        }
        if (ONLY_REPEAT.matcher(pwd).matches()) {
            return "密码不能是同一个字符的重复";
        }

        String lower = pwd.toLowerCase();
        if (WEAK_PASSWORDS.contains(lower)) {
            return "密码过于简单，请更换";
        }
        for (String seq : SEQUENCES) {
            if (lower.contains(seq)) {
                return "密码不能包含连续的字母或数字序列（如 12345678、abcdefgh）";
            }
        }

        if (username != null && !username.isBlank()) {
            String user = username.trim().toLowerCase();
            if (user.length() >= 3 && lower.contains(user)) {
                return "密码不能包含登录账号";
            }
        }

        return null;
    }

    /** 便捷判断 */
    public static boolean isValid(String rawPassword, String username) {
        return validate(rawPassword, username) == null;
    }
}
