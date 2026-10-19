package top.x0a.strapper;

import java.util.List;
import java.util.Map;

/**
 * 最小 JSON 值模型 —— sealed + records（Java 21 惯用法）。
 * libstrapper 零依赖：不引 Jackson/Gson，自带最小 JSON 层。
 */
public sealed interface JsonValue {

    record JNull() implements JsonValue {}

    record JBool(boolean value) implements JsonValue {}

    /**
     * 数字。integral 标记原文是否为整数字面量（无小数点/指数），
     * 供 int 类型校验区分 1 与 1.0。
     */
    record JNum(double value, boolean integral) implements JsonValue {
        public long asLong() { return (long) value; }
    }

    record JStr(String value) implements JsonValue {}

    record JArr(List<JsonValue> items) implements JsonValue {}

    /** 保序对象（LinkedHashMap 语义由解析器保证）。 */
    record JObj(Map<String, JsonValue> fields) implements JsonValue {}
}
