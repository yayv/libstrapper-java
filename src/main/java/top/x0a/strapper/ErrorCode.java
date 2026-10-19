package top.x0a.strapper;

/**
 * 校验过程中可能产生的问题码。
 *
 * <p>前缀含义：
 * <ul>
 *   <li>{@code FORMAT_*} —— 格式描述本身写错了（开发期问题，应当在接口文档评审时就消灭）</li>
 *   <li>{@code DATA_*}   —— 格式描述没问题，是传入的数据不符合约定（运行期问题）</li>
 *   <li>{@code TYPE_*}   —— 类型表里找不到可用的类型或校验方法</li>
 * </ul>
 *
 * <p>{@code *_JSON_STRUCT_ERROR} 与 {@code *_JSON_READER_ERROR} 的区别：
 * <ul>
 *   <li>用内置解析器时，失败只可能是 JSON 语法问题，报 {@code *_JSON_STRUCT_ERROR}</li>
 *   <li>换了 {@link JsonReader} 之后，失败原因本库无从知道 —— 可能是语法错，也可能是
 *       适配器自己的策略把它挡了（比如请求体超限）—— 所以报 {@code *_JSON_READER_ERROR}，
 *       真实原因在 {@link StrapIssue#detail()} 里</li>
 * </ul>
 */
public enum ErrorCode {

    // ---- 格式描述自身的错误 ----
    FORMAT_JSON_STRUCT_ERROR("格式描述的 JSON 字符串有语法错误"),
    FORMAT_JSON_READER_ERROR("读取格式描述时，外部 JSON 解析器报错"),
    FORMAT_SYNTAX_ERROR("格式描述有语法错误"),
    FORMAT_VALUEFORMAT_SYNTAX_ERROR("格式描述的参数值有语法错误"),
    FORMAT_KEY_SYNTAX_ERROR("格式描述的 KEY 有语法错误"),
    FORMAT_RANGE_SYNTAX_ERROR("格式描述的取值范围有语法错误"),
    FORMAT_UNKNOWN_KEYTYPE_ERROR("格式描述中指定的数据类型未定义"),
    FORMAT_TYPE_DISABLED("格式描述中用到了被本项目禁用的数据类型"),
    FORMAT_NOT_SUPPORT_MULTIFORMAT_ARRAY("格式描述中的数组只能有且只有1个格式"),
    FORMAT_NOT_SUPPORT_NOFORMAT_ARRAY("格式描述中的数组使用了不支持的格式"),
    FORMAT_DEFAULT_VALUE_INVALID("格式描述中的默认值本身不符合该格式"),

    // ---- 传入数据的错误 ----
    DATA_JSON_STRUCT_ERROR("传入数据的 JSON 字符串有语法错误"),
    DATA_JSON_READER_ERROR("读取传入数据时，外部 JSON 解析器报错"),
    DATA_NOT_MATCHED("传入数据格式不匹配"),
    DATA_NOT_IN_VALID_RANGE("传入数据不在合理的范围"),
    DATA_NOT_IN_SET_RANGE("传入数据不符合要求的范围"),
    DATA_OVER_LENGTH("传入数据超过了格式约定的长度"),
    DATA_ARRAY_LENGTH_ERROR("传入数组的长度不符合格式约定"),
    DATA_KEY_NEED_EXIST("传入数据缺少了必填的KEY"),
    DATA_NOT_EXIST("传入数据的KEY不存在"),
    DATA_UNKNOWN_KEY_ERROR("传入数据存在格式中不存在的参数"),
    DATA_USE_DEFAULT_VALUE("传入数据无效，已使用格式约定的默认值"),

    // ---- 类型系统 ----
    TYPE_NO_MATCHED("没有匹配的类型"),
    TYPE_WITHOUT_METHOD("没有匹配的解析方法");

    private final String message;

    ErrorCode(String message) {
        this.message = message;
    }

    /** 这个问题码的中文说明。 */
    public String message() {
        return message;
    }
}
