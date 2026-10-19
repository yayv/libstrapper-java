package top.x0a.strapper;

/** 参数项的必填性，对应格式描述开头的 {@code *} / {@code !} / 无标记。 */
public enum Requirement {
    /** 无标记：选填。 */
    OPTIONAL,
    /** {@code *}：必填。 */
    REQUIRED,
    /**
     * {@code !}：根据前后文条件确定是否必填。
     *
     * <p>spec 明确这类条件"需要在具体情境下具体描述"，格式本身给不出答案，
     * 因此本库把判断权交给调用方：注册 {@link Strapper.ConditionalResolver} 即可；
     * 不注册时按选填处理。
     */
    CONDITIONAL;

    static Requirement of(String marker) {
        if ("*".equals(marker)) {
            return REQUIRED;
        }
        if ("!".equals(marker)) {
            return CONDITIONAL;
        }
        return OPTIONAL;
    }

    /** key 上的标记与 value 上的标记同时存在时，取更强的一个。 */
    static Requirement strongest(Requirement a, Requirement b) {
        if (a == REQUIRED || b == REQUIRED) {
            return REQUIRED;
        }
        if (a == CONDITIONAL || b == CONDITIONAL) {
            return CONDITIONAL;
        }
        return OPTIONAL;
    }
}
