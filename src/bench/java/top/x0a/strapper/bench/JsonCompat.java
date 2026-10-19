package top.x0a.strapper.bench;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import com.alibaba.fastjson2.JSON;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;

import top.x0a.json.Json;

/** 探测四个解析器在"解析成通用树"这件事上的行为差异，用于判断能不能做成可插拔后端。 */
public final class JsonCompat {

    public static void main(String[] args) {
        ObjectMapper mapper = new ObjectMapper();
        Gson gson = new Gson();

        Map<String, Function<String, Object>> parsers = new LinkedHashMap<>();
        parsers.put("strapper", Json::parse);
        parsers.put("jackson", t -> {
            try {
                return mapper.readValue(t, Object.class);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        parsers.put("gson", t -> gson.fromJson(t, Object.class));
        parsers.put("fastjson2", JSON::parse);

        section("① 容器类型与 key 顺序");
        probe(parsers, "{\"b\":1,\"a\":2,\"c\":3}", v -> v.getClass().getSimpleName()
                + "  key顺序=" + ((Map<?, ?>) v).keySet());
        probe(parsers, "[1,2]", v -> v.getClass().getSimpleName()
                + "  是List=" + (v instanceof List));

        section("② 数字类型（决定 int 字段能不能正确取值）");
        probe(parsers, "{\"n\":1}", v -> typeOf(get(v)));
        probe(parsers, "{\"n\":2147483648}", v -> typeOf(get(v)));
        probe(parsers, "{\"n\":1234567890123456789}", v -> typeOf(get(v)) + " -> " + get(v));
        probe(parsers, "{\"n\":1.5}", v -> typeOf(get(v)));
        probe(parsers, "{\"n\":3.141592653589793238462643383279}", v -> typeOf(get(v)) + " -> " + get(v));
        probe(parsers, "{\"n\":1e3}", v -> typeOf(get(v)) + " -> " + get(v));

        section("③ null、布尔、空对象");
        probe(parsers, "{\"n\":null}", v -> "值=" + get(v)
                + "  containsKey=" + ((Map<?, ?>) v).containsKey("n"));
        probe(parsers, "{\"n\":true}", v -> typeOf(get(v)));
        probe(parsers, "{}", v -> v.getClass().getSimpleName() + " size=" + ((Map<?, ?>) v).size());

        section("④ 重复 key");
        probe(parsers, "{\"a\":1,\"a\":2}", v -> "留下 " + get(v, "a"));

        section("⑤ 顶层纯量");
        probe(parsers, "\"just a string\"", v -> typeOf(v) + " -> " + v);
        probe(parsers, "123", JsonCompat::typeOf);

        section("⑥ 出错时抛什么（这决定了适配层要 catch 什么）");
        probeError(parsers, "{\"a\":}");
        probeError(parsers, "{\"a\":1} 多余内容");
        probeError(parsers, "");
        probeError(parsers, "{'a':1}");
        probeError(parsers, "{\"a\":1,}");
    }

    // =================================================================

    private static void section(String title) {
        System.out.println();
        System.out.println("═".repeat(78));
        System.out.println(" " + title);
        System.out.println("═".repeat(78));
    }

    private static void probe(Map<String, Function<String, Object>> parsers, String input,
                              Function<Object, String> render) {
        System.out.println("  输入 " + input);
        parsers.forEach((name, parser) -> {
            String out;
            try {
                out = render.apply(parser.apply(input));
            } catch (Exception e) {
                out = "抛 " + rootName(e);
            }
            System.out.printf("     %-11s %s%n", name, out);
        });
        System.out.println();
    }

    private static void probeError(Map<String, Function<String, Object>> parsers, String input) {
        System.out.println("  输入 <" + input + ">");
        parsers.forEach((name, parser) -> {
            String out;
            try {
                Object v = parser.apply(input);
                out = "不报错，返回 " + (v == null ? "null" : typeOf(v) + " " + v);
            } catch (Exception e) {
                out = "抛 " + rootName(e);
            }
            System.out.printf("     %-11s %s%n", name, out);
        });
        System.out.println();
    }

    private static String rootName(Throwable e) {
        Throwable t = e;
        while (t.getCause() != null && t instanceof RuntimeException && t.getClass() == RuntimeException.class) {
            t = t.getCause();
        }
        return t.getClass().getName();
    }

    private static Object get(Object v) {
        return get(v, "n");
    }

    private static Object get(Object v, String key) {
        return ((Map<?, ?>) v).get(key);
    }

    private static String typeOf(Object v) {
        return v == null ? "null" : v.getClass().getSimpleName();
    }

    private JsonCompat() {
    }
}
