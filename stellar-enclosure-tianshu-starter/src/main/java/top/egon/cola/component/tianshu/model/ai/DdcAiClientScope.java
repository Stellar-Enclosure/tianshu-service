package top.egon.cola.component.tianshu.model.ai;

/**
 * 客户端传输固定的非敏感读取范围；不代表资源读取授权。
 * Non-secret read scope fixed by the client transport; it does not grant resource access.
 */
public record DdcAiClientScope(long tenantId, String bizCode, String env, String appCode) {

    public DdcAiClientScope {
        if (tenantId <= 0 || !valid(bizCode) || !valid(env) || !valid(appCode)) {
            throw new IllegalArgumentException("AI client scope is invalid");
        }
    }

    private static boolean valid(String value) {
        return value != null && !value.isBlank() && value.length() <= 128
                && value.equals(value.trim()) && value.chars().noneMatch(Character::isISOControl);
    }
}
