package top.x0a.strapper;

import java.util.List;
import java.util.Map;

import top.x0a.json.Json;

/**
 * JSON 解析的插拔点。
 *
 * <p>本库只在两处需要 JSON 解析（{@link Strapper#strap} 和 {@link Strapper#checkFormat} 各一次），
 * 所以这个接口只有一个方法。默认用内置的 {@link Json}，想换成项目里已经在用的第三方库就传一个适配器进来，
 * 这样一个进程里不必同时驮着两套 JSON 解析实现。
 *
 * <h2>约定</h2>
 * <p>{@link #read} 必须把 JSON 文本解析成这样一棵通用树：
 * <ul>
 *   <li>object &rarr; {@link Map}，<b>并且保持 key 的书写顺序</b>（校验结果按这个顺序产出）</li>
 *   <li>array &rarr; {@link List}</li>
 *   <li>string &rarr; {@link String}，true/false &rarr; {@link Boolean}，null &rarr; {@code null}</li>
 *   <li>number &rarr; 任意 {@link Number}（{@code Integer} / {@code Long} / {@code Double} /
 *       {@code BigDecimal} 都行，本库按它的字符串形式做校验）</li>
 * </ul>
 * <p>解析失败时抛 {@link JsonReadException}；用 {@link #wrapping} 包一层会自动完成转换。
 *
 * <h2>写第三方适配器</h2>
 * <pre>{@code
 * ObjectMapper mapper = new ObjectMapper();
 * strapper.jsonReader(JsonReader.wrapping(t -> mapper.readValue(t, Object.class)));   // Jackson
 *
 * Gson gson = new Gson();
 * strapper.jsonReader(JsonReader.wrapping(t -> gson.fromJson(t, Object.class)));      // Gson
 *
 * strapper.jsonReader(JsonReader.wrapping(JSON::parse));                              // fastjson2
 * }</pre>
 *
 * <h2>换之前要知道的三件事</h2>
 * <ol>
 *   <li><b>Gson 把所有数字都解析成 {@code Double}</b>，超过 2^53 的整数会丢精度：
 *       {@code 1234567890123456789} 会变成 {@code 1234567890123456800}，
 *       校验照样能过，值已经错了。ID 类字段用 Gson 后端时要留意。</li>
 *   <li><b>各家的宽松度不同</b>：Jackson 默认接受文档尾部的多余内容，
 *       Gson 和 fastjson2 默认接受单引号，fastjson2 还接受尾逗号，
 *       Gson 和 fastjson2 对空串返回 {@code null} 而不是报错。
 *       内置的 {@link Json} 这些一律拒绝 —— 换后端等于放松了这一层把关。</li>
 *   <li><b>只有读是可换的，写不换</b>：{@link StrapResult#toJson()} 始终用内置的写出实现，
 *       因为它写的是本库自己产出的 Map/List，不涉及第三方的对象模型。</li>
 * </ol>
 */
@FunctionalInterface
public interface JsonReader {

    /**
     * 把 JSON 文本解析成 Map / List / String / Number / Boolean / null 构成的树。
     *
     * @throws JsonReadException 文本不是合法 JSON
     */
    Object read(String text);

    /** 解析失败。适配层用 {@link #wrapping} 时会自动把第三方的异常转成这个。 */
    final class JsonReadException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        public JsonReadException(String message) {
            super(message);
        }

        public JsonReadException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** 允许抛受检异常的解析函数，方便直接写 {@code t -> mapper.readValue(t, Object.class)}。 */
    @FunctionalInterface
    interface Parser {
        Object parse(String text) throws Exception;
    }

    /** 内置实现，零第三方依赖，严格按 RFC 8259 解析。这是默认值。 */
    static JsonReader builtin() {
        return Builtin.INSTANCE;
    }

    /**
     * 把任意第三方解析器包成 {@link JsonReader}：它自己抛什么异常都会被转成
     * {@link JsonReadException}，本库只认这一种。
     */
    static JsonReader wrapping(Parser parser) {
        java.util.Objects.requireNonNull(parser, "parser");
        return text -> {
            try {
                return parser.parse(text);
            } catch (JsonReadException e) {
                throw e;
            } catch (Exception e) {
                String message = e.getMessage();
                throw new JsonReadException(
                        message == null ? e.getClass().getSimpleName() : message, e);
            }
        };
    }

    /** 持有内置实现的单例，避免每次 {@link #builtin()} 都新建一个 lambda。 */
    final class Builtin {
        static final JsonReader INSTANCE = text -> {
            try {
                return Json.parse(text);
            } catch (Json.JsonException e) {
                throw new JsonReadException(e.getMessage(), e);
            }
        };

        private Builtin() {
        }
    }
}
