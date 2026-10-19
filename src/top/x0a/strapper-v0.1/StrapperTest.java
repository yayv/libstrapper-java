import java.util.List;
import java.util.Map;

import top.x0a.strapper.Json;
import top.x0a.strapper.Strapper;
import top.x0a.strapper.Strapper.StrapResult;
import top.x0a.strapper.Strapper.TypeHook;
import top.x0a.strapper.JsonValue.JStr;

/**
 * libstrapper-java 测试 —— 同时是 fixture 向量清单的雏形。
 * 每个用例即一条"format + 输入 → 期望判定"向量；
 * [DECISION #n] 对应 Strapper.java 中待与 PHP 原版核对的语义点。
 */
public class StrapperTest {

    static int passed = 0, failed = 0;

    public static void main(String[] args) {
        // 来自 addAccount 的真实 format
        String fmt = "{\"name\":\"*string//显示名\",\"phone\":\"*string\","
                   + "\"password\":\"*string//密码\",\"note\":\"string//备注\"}";

        // ---- 1. 必填齐全 + 可选缺失 ----
        var s = new Strapper();
        var r = s.strap(fmt, "{\"name\":\"张三\",\"phone\":\"13800000000\",\"password\":\"pw\"}");
        check("必填齐全解析成功", r.ok());
        check("值正确", "张三".equals(r.values().get("name")));
        check("可选缺失=键不存在(isset语义)", !r.values().containsKey("note"));

        // ---- 2. 必填缺失 ----
        r = s.strap(fmt, "{\"name\":\"张三\",\"password\":\"pw\"}");
        check("必填缺失判失败", !r.ok());
        check("错误定位到 phone", r.errors().get(0).field().equals("phone"));
        check("错误码 MISSING", r.errors().get(0).code().equals(Strapper.StrapError.MISSING));

        // ---- 3. PHP 兼容层 ----
        Map<String, Object> params = s.getJSONParams(fmt,
            "{\"name\":\"李四\",\"phone\":\"139\",\"password\":\"x\",\"note\":\"hi\"}");
        check("getJSONParams 成功返回值表", params != null && "hi".equals(params.get("note")));
        check("isParseOk == true", s.isParseOk());
        check("失败时返回 null", s.getJSONParams(fmt, "{}") == null);
        check("isParseOk == false", !s.isParseOk());

        // ---- 4. [DECISION #4] null 视同缺失 ----
        r = s.strap(fmt, "{\"name\":null,\"phone\":\"1\",\"password\":\"p\"}");
        check("[D4] 必填+null = MISSING", !r.ok()
            && r.errors().get(0).code().equals(Strapper.StrapError.MISSING));

        // ---- 5. int 类型：[DECISION #1] 数字字符串宽容 ----
        String fmtInt = "{\"age\":\"*int\",\"score\":\"number\"}";
        r = s.strap(fmtInt, "{\"age\":25}");
        check("int 接受 JSON 整数", r.ok() && r.values().get("age").equals(25L));
        r = s.strap(fmtInt, "{\"age\":\"25\"}");
        check("[D1] int 接受数字字符串", r.ok() && r.values().get("age").equals(25L));
        r = s.strap(fmtInt, "{\"age\":25.5}");
        check("int 拒绝小数", !r.ok());
        r = s.strap(fmtInt, "{\"age\":\"abc\"}");
        check("int 拒绝非数字串", !r.ok());

        // ---- 6. [DECISION #2] string 严格 ----
        r = s.strap(fmt, "{\"name\":123,\"phone\":\"1\",\"password\":\"p\"}");
        check("[D2] string 拒绝数字", !r.ok()
            && r.errors().get(0).code().equals(Strapper.StrapError.TYPE_MISMATCH));

        // ---- 7. 三层降级：未知类型 → 字符串兜底 + warning ----
        String fmtTypo = "{\"phone\":\"*phon//笔误的类型名\"}";
        r = s.strap(fmtTypo, "{\"phone\":\"138\"}");
        check("未知类型按 string 收", r.ok() && "138".equals(r.values().get("phone")));
        check("未知类型产生 warning", !r.warnings().isEmpty()
            && r.warnings().get(0).contains("phon"));

        // ---- 8. 三层降级：my.* 钩子 ----
        var s2 = new Strapper().registerHook((type, v) -> {
            if (!type.equals("my.phone")) return null;          // 不管 → 继续降级
            if (v instanceof JStr js && js.value().matches("1\\d{10}"))
                return TypeHook.Handled.ok(js.value());
            return TypeHook.Handled.fail("not a valid CN phone");
        });
        String fmtHook = "{\"phone\":\"*my.phone//手机号\"}";
        r = s2.strap(fmtHook, "{\"phone\":\"13812345678\"}");
        check("my.* 钩子放行合法值", r.ok());
        r = s2.strap(fmtHook, "{\"phone\":\"999\"}");
        check("my.* 钩子拒绝非法值", !r.ok());
        // 钩子不得遮蔽标准裸名
        r = s2.strap("{\"x\":\"*string\"}", "{\"x\":\"ok\"}");
        check("裸名 string 仍归标准层", r.ok());

        // ---- 9. "..." 收尾键 ----
        String fmtRest = "{\"host\":\"*string\",\"...\":\"string//其余按字符串收\"}";
        r = s.strap(fmtRest, "{\"host\":\"a.com\",\"x-custom\":\"v1\",\"n\":42}");
        check("... 收未声明键", r.ok() && "v1".equals(r.values().get("x-custom")));
        check("... 按声明类型宽松字符串化", "42".equals(r.values().get("n")));

        // ---- 10. [DECISION #3] 无 ... 时未声明键静默忽略 ----
        r = s.strap(fmt, "{\"name\":\"a\",\"phone\":\"1\",\"password\":\"p\",\"extra\":\"?\"}");
        check("[D3] 未声明键被忽略", r.ok() && !r.values().containsKey("extra"));

        // ---- 11. 嵌套 dataStruct ----
        String fmtNest = "{\"user\":{\"name\":\"*string\",\"age\":\"int\"},"
                       + "\"tags\":[\"string\"]}";
        r = s.strap(fmtNest,
            "{\"user\":{\"name\":\"王五\",\"age\":30},\"tags\":[\"a\",\"b\"]}");
        check("嵌套对象解析", r.ok());
        @SuppressWarnings("unchecked")
        var user = (Map<String, Object>) r.values().get("user");
        check("嵌套字段值", "王五".equals(user.get("name")) && user.get("age").equals(30L));
        check("数组元素", ((List<?>) r.values().get("tags")).equals(List.of("a", "b")));
        r = s.strap(fmtNest, "{\"user\":{\"age\":1},\"tags\":[]}");
        check("嵌套必填缺失, 路径带前缀", !r.ok()
            && r.errors().get(0).field().equals("user.name"));

        // ---- 12. 坏输入 ----
        r = s.strap(fmt, "not json at all");
        check("非法 JSON → BAD_JSON", !r.ok()
            && r.errors().get(0).code().equals(Strapper.StrapError.BAD_JSON));
        r = s.strap("{\"a\":\"\"}", "{}");
        check("空类型名 → BAD_FORMAT", !r.ok()
            && r.errors().get(0).code().equals(Strapper.StrapError.BAD_FORMAT));
        r = s.strap(fmt, "[1,2]");
        check("顶层非对象 → BAD_JSON", !r.ok());

        // ---- 13. Json.encode（java 版 json_encode）----
        String enc = Json.encode(Map.of("code", "ok"));
        check("encode 基本对象", enc.equals("{\"code\":\"ok\"}"));
        check("encode 转义", Json.encode("a\"b\n").equals("\"a\\\"b\\n\""));
        check("encode 整数化 1.0", Json.encode(1.0).equals("1"));
        check("encode 嵌套", Json.encode(
                new java.util.LinkedHashMap<>(Map.of("l", List.of(1, "x", true))))
            .equals("{\"l\":[1,\"x\",true]}"));
        // 回环：parse → encode
        check("parse/encode 回环",
            Json.encode(Json.parse("{\"a\":[1,2.5,null,{\"b\":false}]}"))
                .equals("{\"a\":[1,2.5,null,{\"b\":false}]}"));

        // ---- 14. comment 保留（供 Schema 导出）----
        var spec = Strapper.parseFormat(fmt);
        check("comment 保留", spec.get("name").comment().equals("显示名"));
        check("required 解析", spec.get("name").required() && !spec.get("note").required());

        System.out.println("\n===== " + passed + " passed, " + failed + " failed =====");
        if (failed > 0) System.exit(1);
    }

    static void check(String name, boolean cond) {
        if (cond) { passed++; System.out.println("  ok: " + name); }
        else      { failed++; System.out.println("FAIL: " + name); }
    }
}
