package top.x0a.strapper.bench;

import java.util.LinkedHashMap;
import java.util.Map;

import com.alibaba.fastjson2.JSON;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;

import top.x0a.strapper.JsonReader;
import top.x0a.strapper.StrapResult;
import top.x0a.strapper.Strapper;

/**
 * 三个第三方 JSON 库的 {@link JsonReader} 适配器写法，并当场验证它们确实能跑通。
 *
 * <p>放在 bench 源集里：库本体不引任何第三方依赖，这些适配器是给使用方抄的模板。
 * 每个适配器就是一行。
 */
public final class JsonReaderAdapters {

    // ===== 三个适配器，各一行 =====

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Gson GSON = new Gson();

    /** Jackson。注意：Jackson 默认会忽略文档尾部的多余内容。 */
    public static final JsonReader JACKSON =
            JsonReader.wrapping(text -> MAPPER.readValue(text, Object.class));

    /** Gson。注意：所有数字都解析成 Double，超过 2^53 的整数会丢精度。 */
    public static final JsonReader GSON_READER =
            JsonReader.wrapping(text -> GSON.fromJson(text, Object.class));

    /** fastjson2。注意：默认接受单引号和尾逗号。 */
    public static final JsonReader FASTJSON2 = JsonReader.wrapping(JSON::parse);

    // =================================================================

    private static final String FORMAT = """
            {
              "userId":"*int//用户ID",
              "username":"*string:32//用户名",
              "mobile":"*mobile//手机号",
              "amount":"*currency//金额",
              "vip":"*bool//是否会员",
              "startAt":"*datetime//开始时间",
              "*tags":["string//标签"],
              "*detail":{"plateNumber":"*plateNumber//车牌号","mileage":"*float//里程"}
            }""";

    private static final String DATA = """
            {
              "userId":1024,
              "username":"yayv",
              "mobile":"13800138000",
              "amount":"199.00",
              "vip":true,
              "startAt":"2020-02-14T10:00:00.000Z",
              "tags":["长租","企业"],
              "detail":{"plateNumber":"京A12345","mileage":1024.5}
            }""";

    public static void main(String[] args) {
        Map<String, JsonReader> readers = new LinkedHashMap<>();
        readers.put("内置（默认）", JsonReader.builtin());
        readers.put("Jackson", JACKSON);
        readers.put("Gson", GSON_READER);
        readers.put("fastjson2", FASTJSON2);

        System.out.println("① 同一份格式 + 同一份数据，换四种解析后端，结果应当完全一致");
        System.out.println("─".repeat(78));
        String expected = null;
        for (Map.Entry<String, JsonReader> e : readers.entrySet()) {
            StrapResult r = new Strapper().jsonReader(e.getValue()).strap(FORMAT, DATA);
            String json = r.ok() ? r.toJson() : "失败: " + r.report();
            if (expected == null) {
                expected = json;
            }
            System.out.printf("  %-12s ok=%-6s %s%n", e.getKey(), r.ok(),
                    json.equals(expected) ? "取值与内置实现一致" : "取值不同 -> " + json);
        }

        System.out.println();
        System.out.println("② 大整数：Gson 把所有数字转 Double，这里会丢精度");
        System.out.println("─".repeat(78));
        String bigFormat = "{\"id\":\"*int//主键\"}";
        String bigData = "{\"id\":1234567890123456789}";
        readers.forEach((name, reader) -> {
            StrapResult r = new Strapper().jsonReader(reader).strap(bigFormat, bigData);
            Object id = r.ok() ? r.asMap().get("id") : null;
            boolean intact = Long.valueOf(1234567890123456789L).equals(id);
            System.out.printf("  %-12s id=%-22s %s%n", name, id, intact ? "原值" : "!! 丢精度 !!");
        });

        System.out.println();
        System.out.println("③ 畸形输入：各家宽松度不同，换后端等于放松了这一层把关");
        System.out.println("─".repeat(78));
        String[] malformed = {
            "{\"id\":1} 尾部多余内容",
            "{'id':1}",
            "{\"id\":1,}",
            "",
        };
        System.out.printf("  %-26s", "输入");
        readers.keySet().forEach(n -> System.out.printf("%-14s", n));
        System.out.println();
        for (String bad : malformed) {
            System.out.printf("  %-26s", "<" + bad + ">");
            readers.forEach((name, reader) -> {
                StrapResult r = new Strapper().jsonReader(reader).strap(bigFormat, bad);
                System.out.printf("%-14s", r.ok() ? "接受了" : "拒绝");
            });
            System.out.println();
        }
        System.out.println();
        System.out.println("  （空串是本库刻意按 {} 处理的，所以四家都只报缺少必填项，属于\"拒绝\"）");
    }

    private JsonReaderAdapters() {
    }
}
