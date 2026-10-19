package top.x0a.strapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import top.x0a.json.Json;
import top.x0a.json.JsonWriter;

/**
 * 零依赖的自检用例。用 {@code ./gradlew selfTest} 运行，有用例不过时以非 0 退出码结束。
 *
 * <p>没有引入 JUnit 是为了守住"不引入第三方库"这条线；用例本身仍然一条一条独立可读。
 */
public final class SelfTest {

    private static int passed;
    private static int failed;
    private static String group = "";

    public static void main(String[] args) {
        jsonTests();
        jsonWriterTests();
        jsonStreamTests();
        jsonReaderTests();
        valueFormatTests();
        keyFormatTests();
        typeTests();
        disableTests();
        objectTests();
        rangeAndLengthTests();
        defaultValueTests();
        arrayTests();
        extraKeyTests();
        conditionalTests();
        formatCheckTests();
        dateTimeTests();
        specExampleTests();
        concurrencyTests();
        streamConfinementTests();

        System.out.println();
        System.out.println("=".repeat(60));
        System.out.printf("通过 %d，失败 %d%n", passed, failed);
        if (failed > 0) {
            System.exit(1);
        }
    }

    // =================================================================
    // 用例
    // =================================================================

    private static void jsonTests() {
        group("JSON 解析与序列化");

        Object v = Json.parse("{\"a\":1,\"b\":[true,null,\"x\"],\"c\":{\"d\":1.5}}");
        eq("对象保序且类型正确", "[a, b, c]", ((Map<?, ?>) v).keySet().toString());
        eq("整数解析为 Long", Long.valueOf(1), ((Map<?, ?>) v).get("a"));
        eq("小数解析为 Double", Double.valueOf(1.5),
                ((Map<?, ?>) ((Map<?, ?>) v).get("c")).get("d"));
        eq("序列化回环", "{\"a\":1,\"b\":[true,null,\"x\"],\"c\":{\"d\":1.5}}", Json.write(v));
        eq("转义处理", "他说\"你好\"\n", Json.parse("\"他说\\\"你好\\\"\\n\""));
        eq("Unicode 转义", "中", Json.parse("\"\\u4e2d\""));
        eq("大数不写成科学计数法", "10000000000", Json.write(1.0E10));
        throwsJsonException("尾部多余内容", "{} {}");
        throwsJsonException("字符串未闭合", "{\"a\":\"x}");
    }

    private static void jsonWriterTests() {
        group("JSON 写出");

        Map<String, Object> tree = orderedMap(
                "code", "ok",
                "n", Long.valueOf(1),
                "d", Double.valueOf(1.5),
                "b", Boolean.TRUE,
                "nil", null,
                "list", List.of(1, "两", true));

        eq("紧凑输出", "{\"code\":\"ok\",\"n\":1,\"d\":1.5,\"b\":true,\"nil\":null,"
                        + "\"list\":[1,\"两\",true]}",
                JsonWriter.compact().write(tree));
        eq("Json.write 等价于紧凑输出", JsonWriter.compact().write(tree), Json.write(tree));
        eq("Json.writePretty 等价于缩进输出", JsonWriter.pretty().write(tree), Json.writePretty(tree));
        ok("缩进输出有换行", JsonWriter.pretty().write(tree).contains("\n"));
        eq("缩进宽度可调", 4,
                JsonWriter.pretty(4).write(tree).split("\n")[1].indexOf('"'));
        eq("pretty(0) 等同于紧凑", JsonWriter.compact().write(tree), JsonWriter.pretty(0).write(tree));

        eq("skipNulls 丢掉值为 null 的 key", "{\"a\":1}",
                JsonWriter.compact().skipNulls(true).write(orderedMap("a", 1, "b", null)));
        eq("skipNulls 不影响数组里的 null", "[null]",
                JsonWriter.compact().skipNulls(true).write(java.util.Arrays.asList((Object) null)));

        eq("escapeNonAscii", "{\"a\":\"\\u4e2d\"}",
                JsonWriter.compact().escapeNonAscii(true).write(orderedMap("a", "中")));
        eq("默认不转义非 ASCII", "{\"a\":\"中\"}",
                JsonWriter.compact().write(orderedMap("a", "中")));

        eq("控制字符必须转义", "\"a\\u0001b\"", JsonWriter.compact().write("a\u0001b"));
        eq("常见转义", "\"\\\"\\\\\\n\\t\"", JsonWriter.compact().write("\"\\\n\t"));

        eq("空对象", "{}", JsonWriter.pretty().write(Map.of()));
        eq("空数组", "[]", JsonWriter.pretty().write(List.of()));

        eq("double 不写成科学计数法", "10000000000", JsonWriter.compact().write(1.0E10));
        eq("NaN 写成 null", "null", JsonWriter.compact().write(Double.NaN));
        eq("BigDecimal 保原样", "3.14159265358979323846",
                JsonWriter.compact().write(new java.math.BigDecimal("3.14159265358979323846")));

        eq("Java 数组当作数组写", "[1,2,3]", JsonWriter.compact().write(new int[]{1, 2, 3}));
        eq("对象数组", "[\"a\",\"b\"]", JsonWriter.compact().write(new String[]{"a", "b"}));
        eq("Iterable 当作数组写", "[1,2]",
                JsonWriter.compact().write(new java.util.LinkedHashSet<>(List.of(1, 2))));
        eq("枚举写成 name", "\"REQUIRED\"", JsonWriter.compact().write(Requirement.REQUIRED));
        eq("不认识的类型退化成 toString", "\"x\"",
                JsonWriter.compact().write(new Object() {
                    @Override
                    public String toString() {
                        return "x";
                    }
                }));
        throwsIllegal("strict 模式下不认识的类型直接报错",
                () -> JsonWriter.compact().strict(true).write(new Object()));

        // 写进 Appendable，不攒中间串
        StringBuilder sb = new StringBuilder();
        try {
            JsonWriter.compact().writeTo(sb, orderedMap("a", 1));
        } catch (java.io.IOException e) {
            throw new AssertionError(e);
        }
        eq("writeTo", "{\"a\":1}", sb.toString());

        eq("配置方法返回新实例，原实例不变", "{\"a\":null}",
                JsonWriter.compact().skipNulls(true) == JsonWriter.compact()
                        ? "被改了" : JsonWriter.compact().write(orderedMap("a", null)));
    }

    private static void jsonStreamTests() {
        group("JSON 流式写出");

        StringBuilder sb = new StringBuilder();
        try (JsonWriter.Stream out = JsonWriter.compact().stream(sb)) {
            out.beginObject()
               .field("code", "ok")
               .name("data").beginArray()
                   .beginObject().field("id", 1).field("name", "甲").endObject()
                   .beginObject().field("id", 2).field("name", "乙").endObject()
               .endArray()
               .field("total", 2)
               .endObject();
        }
        eq("流式拼出的结果", "{\"code\":\"ok\",\"data\":[{\"id\":1,\"name\":\"甲\"},"
                + "{\"id\":2,\"name\":\"乙\"}],\"total\":2}", sb.toString());

        StringBuilder empty = new StringBuilder();
        try (JsonWriter.Stream out = JsonWriter.compact().stream(empty)) {
            out.beginObject().name("a").beginObject().endObject().endObject();
        }
        eq("空的嵌套对象", "{\"a\":{}}", empty.toString());

        StringBuilder pretty = new StringBuilder();
        try (JsonWriter.Stream out = JsonWriter.pretty().stream(pretty)) {
            out.beginObject().field("a", 1).endObject();
        }
        eq("流式也支持缩进", "{\n  \"a\": 1\n}", pretty.toString());

        StringBuilder scalar = new StringBuilder();
        try (JsonWriter.Stream out = JsonWriter.compact().stream(scalar)) {
            out.value("单个值");
        }
        eq("顶层写单个值", "\"单个值\"", scalar.toString());

        StringBuilder mixed = new StringBuilder();
        try (JsonWriter.Stream out = JsonWriter.compact().stream(mixed)) {
            // 流式和整棵树写出可以混着用：大结构流式拼，小对象直接丢进去
            out.beginObject().field("meta", orderedMap("page", 1)).name("list").beginArray()
               .value(List.of(1, 2)).endArray().endObject();
        }
        eq("流式里混着整棵树写出", "{\"meta\":{\"page\":1},\"list\":[[1,2]]}", mixed.toString());

        StringBuilder skip = new StringBuilder();
        try (JsonWriter.Stream out = JsonWriter.compact().skipNulls(true).stream(skip)) {
            out.beginObject().field("a", 1).field("b", null).endObject();
        }
        eq("流式也认 skipNulls", "{\"a\":1}", skip.toString());

        throwsIllegalState("对象里不 name 直接写值要报错", () -> {
            try (JsonWriter.Stream out = JsonWriter.compact().stream(new StringBuilder())) {
                out.beginObject().value(1);
            }
        });
        throwsIllegalState("name 之后不写值就闭合要报错", () -> {
            try (JsonWriter.Stream out = JsonWriter.compact().stream(new StringBuilder())) {
                out.beginObject().name("a").endObject();
            }
        });
        throwsIllegalState("括号类型对不上要报错", () -> {
            try (JsonWriter.Stream out = JsonWriter.compact().stream(new StringBuilder())) {
                out.beginObject().endArray();
            }
        });
        throwsIllegalState("close 时括号没闭合要报错", () -> {
            try (JsonWriter.Stream out = JsonWriter.compact().stream(new StringBuilder())) {
                out.beginObject();
            }
        });
        throwsIllegalState("数组里不能用 name", () -> {
            try (JsonWriter.Stream out = JsonWriter.compact().stream(new StringBuilder())) {
                out.beginArray().name("a");
            }
        });
        throwsIllegalState("顶层只能有一个值", () -> {
            try (JsonWriter.Stream out = JsonWriter.compact().stream(new StringBuilder())) {
                out.value(1).value(2);
            }
        });
    }

    private static void jsonReaderTests() {
        group("可插拔的 JSON 解析");

        ok("默认是内置实现", new Strapper().jsonReader() == JsonReader.builtin());

        // 一个"第三方解析器"的替身：按约定的两个输入分别产出格式树和数据树，
        // 数据树里的数字故意用 Integer —— Jackson 和 fastjson2 就是这么产出小整数的
        JsonReader fake = JsonReader.wrapping(text -> switch (text) {
            case "FMT" -> orderedMap("userId", "*int//用户ID", "name", "*string//姓名");
            case "DATA" -> orderedMap("userId", Integer.valueOf(42), "name", "张三");
            default -> throw new IllegalStateException("这个解析器不认: " + text);
        });

        Strapper pluggable = new Strapper().jsonReader(fake);
        StrapResult viaFake = pluggable.strap("FMT", "DATA");
        ok("换进来的解析器确实被用上了", viaFake.ok());
        eq("Integer 形态的数字也能正确取值", Long.valueOf(42), viaFake.asMap().get("userId"));
        eq("字符串照常", "张三", viaFake.asMap().get("name"));

        StrapResult rejected = pluggable.strap("认不出来的格式串", "DATA");
        ok("第三方解析器报错会被转成格式错误", !rejected.ok());
        eq("换了 reader 之后格式侧的错误码", ErrorCode.FORMAT_JSON_READER_ERROR,
                rejected.errors().get(0).code());

        StrapResult dataRejected = pluggable.strap("FMT", "认不出来的数据串");
        eq("换了 reader 之后数据侧的错误码", ErrorCode.DATA_JSON_READER_ERROR,
                dataRejected.errors().get(0).code());
        ok("真实原因在 detail 里", dataRejected.errors().get(0).detail().contains("认不出来"));
        eq("用内置 reader 时仍然报语法错误码", ErrorCode.DATA_JSON_STRUCT_ERROR,
                new Strapper().strap("{\"a\":\"int\"}", "{\"a\":}").errors().get(0).code());

        // wrapping 把任何异常都归一成 JsonReadException
        JsonReader boom = JsonReader.wrapping(text -> {
            throw new IllegalArgumentException("炸了");
        });
        try {
            boom.read("{}");
            ok("wrapping 统一异常类型", false);
        } catch (JsonReader.JsonReadException e) {
            ok("wrapping 统一异常类型", true);
            eq("保留原始消息", "炸了", e.getMessage());
            ok("保留原始异常", e.getCause() instanceof IllegalArgumentException);
        }

        // 各家产出的数字类型不同（Jackson 给 Integer/Long，Gson 一律 Double，
        // fastjson2 小数给 BigDecimal），本库按字符串形式校验，所以都得能收
        for (Number number : List.of(Integer.valueOf(7), Long.valueOf(7),
                Double.valueOf(7), new java.math.BigDecimal("7"))) {
            JsonReader reader = JsonReader.wrapping(text -> "FMT".equals(text)
                    ? orderedMap("n", "*int")
                    : orderedMap("n", number));
            eq("能吃下 " + number.getClass().getSimpleName() + " 形态的数字", Long.valueOf(7),
                    new Strapper().jsonReader(reader).strap("FMT", "DATA").asMap().get("n"));
        }

        // Gson 把所有数字转 Double，超过 2^53 的整数会丢精度 —— 这是换后端的真实代价
        JsonReader lossy = JsonReader.wrapping(text -> "FMT".equals(text)
                ? orderedMap("id", "*int")
                : orderedMap("id", Double.valueOf(1.2345678901234568E18)));
        StrapResult lost = new Strapper().jsonReader(lossy).strap("FMT", "DATA");
        ok("丢精度的值校验照样能过（所以文档必须写明）", lost.ok());
        ok("但值已经不是原值了",
                !Long.valueOf(1234567890123456789L).equals(lost.asMap().get("id")));

        ok("内置实现严格拒绝尾部多余内容",
                !new Strapper().strap("{\"a\":\"int\"}", "{\"a\":1} 多余内容").ok());
        ok("内置实现严格拒绝单引号",
                !new Strapper().strap("{\"a\":\"int\"}", "{'a':1}").ok());
        ok("内置实现严格拒绝尾逗号",
                !new Strapper().strap("{\"a\":\"int\"}", "{\"a\":1,}").ok());
    }

    /** 保序的小 map，Map.of 不保序，用在格式树上会打乱 key 顺序。 */
    private static Map<String, Object> orderedMap(Object... kv) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            map.put((String) kv[i], kv[i + 1]);
        }
        return map;
    }

    private static void valueFormatTests() {
        group("值格式描述解析");

        ValueFormat f = ValueFormat.parse("*string{男,女}:4#男//性别");
        eq("必填标记", Requirement.REQUIRED, f.requirement());
        eq("格式名", "string", f.typeName());
        eq("枚举成员", List.of("男", "女"), f.range().members());
        eq("长度", Integer.valueOf(4), f.length());
        eq("默认值", "男", f.defaultValue());
        eq("说明", "性别", f.comment());

        ValueFormat g = ValueFormat.parse("*int[1990,2030]//必填参数年份");
        eq("区间下界", "1990", g.range().min());
        eq("区间上界", "2030", g.range().max());
        ok("方括号为闭区间", g.range().minInclusive() && g.range().maxInclusive());

        ValueFormat h = ValueFormat.parse("float(1,100]");
        ok("圆括号为开区间", !h.range().minInclusive() && h.range().maxInclusive());

        eq("条件必填", Requirement.CONDITIONAL, ValueFormat.parse("!string//错误消息").requirement());
        eq("无标记为选填", Requirement.OPTIONAL, ValueFormat.parse("string").requirement());
        eq("点分全名", "int.vehicleNo", ValueFormat.parse("*int.vehicleNo//车辆编号").typeName());
        ok("没写默认值", !ValueFormat.parse("int").hasDefault());
        ok("写了空默认值", ValueFormat.parse("*int[0,100]:3#").hasDefault());

        throwsFormatException("没写格式名", () -> ValueFormat.parse("//只有说明"));
        throwsFormatException("括号不配对", () -> ValueFormat.parse("string{a,b)"));
        throwsFormatException("区间缺逗号", () -> ValueFormat.parse("int[1]"));
    }

    private static void keyFormatTests() {
        group("KEY 格式描述解析");
        Types types = Types.standard();

        KeyFormat a = KeyFormat.parse("*[4,9]fruits", types);
        eq("key 名", "fruits", a.name());
        eq("key 必填", Requirement.REQUIRED, a.requirement());
        ok("数组长度区间", a.acceptsSize(4) && a.acceptsSize(9) && !a.acceptsSize(3) && !a.acceptsSize(10));

        KeyFormat b = KeyFormat.parse("*[3]fruits", types);
        ok("[n] 表示恰好 n 个", b.acceptsSize(3) && !b.acceptsSize(2) && !b.acceptsSize(4));

        eq("... 为放行 key", KeyFormat.Kind.MORE_KEYS, KeyFormat.parse("...", types).kind());
        eq("!string 为通配 key", KeyFormat.Kind.WILDCARD, KeyFormat.parse("!string", types).kind());
        eq("!未知名 为条件必填的普通 key", KeyFormat.Kind.NORMAL,
                KeyFormat.parse("!invoiceTitle", types).kind());
        eq("条件必填的必填性", Requirement.CONDITIONAL,
                KeyFormat.parse("!invoiceTitle", types).requirement());
        eq("普通 key", "carData", KeyFormat.parse("*carData", types).name());
    }

    private static void typeTests() {
        group("类型表");

        Types types = Types.standard();
        ok("基础类型可解析", types.has("int") && types.has("string") && types.has("datetime"));
        ok("扩展类型全名可解析", types.has("string.email"));
        ok("扩展类型短名可解析", types.has("email"));
        eq("短名解析到全名", "string.email", types.resolve("email").name());
        eq("扩展类型继承基础类型的取值语义", Types.Kind.INT, types.resolve("year").kind());
        ok("未定义类型", !types.has("没这个类型"));

        // 明文加载
        Types custom = Types.base().load("""
                # 共享电动车领域
                int.vehicleNo : ^20\\d{2}\\d{5}$
                string.orderNo = ^SO\\d{12}$
                """);
        ok("明文用 : 分隔", custom.has("int.vehicleNo"));
        ok("明文用 = 分隔", custom.has("string.orderNo"));
        eq("明文导出可回读", 2, Types.base().load(custom.text()).extendedTypes().size());

        // 短名歧义
        Types ambiguous = Types.base().add("int.birthday", "^\\d{8}$").add("date.birthday", "^.*$");
        ok("短名有歧义时拒绝解析", !ambiguous.has("birthday"));
        ok("歧义时全名仍可用", ambiguous.has("date.birthday"));
        ok("歧义原因说得清", ambiguous.resolveProblem("birthday").contains("歧义"));

        // 覆盖内置
        Types overridden = Types.standard().add("string.retCode", "^(ok|fail)$");
        StrapResult r = new Strapper().types(overridden).strap("{\"code\":\"*retCode\"}",
                "{\"code\":\"deny\"}");
        ok("同名覆盖内置规则", !r.ok());

        throwsIllegal("基础类型不存在", () -> Types.base().add("nosuch.foo", "^.*$"));
        throwsIllegal("扩展类型名必须带点", () -> Types.base().add("foo", "^.*$"));
        throwsIllegal("正则语法错误", () -> Types.base().add("string.bad", "^([a-z$"));
    }

    private static void disableTests() {
        group("禁用类型");

        Types types = Types.standard();
        ok("默认 text 可用", types.has("text"));
        ok("默认 inlineImage 可用", types.has("inlineImage"));

        Types noText = Types.standard().disable("text");
        ok("禁用后基础类型不可用", !noText.has("text"));
        ok("禁用基础类型会连带禁用派生的扩展类型", !noText.has("text.inlineImage"));
        ok("短名也跟着不可用", !noText.has("inlineImage"));
        ok("string 不受影响", noText.has("string") && noText.has("string.email"));
        ok("禁用的原因说得清", noText.resolveProblem("text").contains("已被禁用"));
        ok("派生类型的禁用原因指向基础类型",
                noText.resolveProblem("inlineImage").contains("基础类型 text 已被禁用"));

        StrapResult blocked = new Strapper().disableType("text")
                .checkFormat("{\"note\":\"text//备注\"}");
        ok("格式描述里写被禁用的类型即报错", !blocked.ok());
        eq("被禁用类型有独立的错误码", ErrorCode.FORMAT_TYPE_DISABLED,
                blocked.errors().get(0).code());
        StrapResult notDisabled = new Strapper().checkFormat("{\"a\":\"*没这个类型\"}");
        eq("打错类型名仍然是未定义", ErrorCode.FORMAT_UNKNOWN_KEYTYPE_ERROR,
                notDisabled.errors().get(0).code());
        StrapResult stillBlocked = new Strapper()
                .unknownTypePolicy(Strapper.UnknownTypePolicy.PASS_THROUGH)
                .disableType("text")
                .strap("{\"a\":\"*text\"}", "{\"a\":\"x\"}");
        ok("PASS_THROUGH 放不过被禁用的类型", !stillBlocked.ok());

        Strapper safe = new Strapper().disableType("text");
        ok("string 类型照常可用", safe.strap("{\"a\":\"*string\"}", "{\"a\":\"单行文本\"}").ok());
        ok("string 不接受换行", !safe.strap("{\"a\":\"*string\"}", "{\"a\":\"两\\n行\"}").ok());
        ok("text 关掉之后带换行的值没有任何类型能收",
                !safe.strap("{\"a\":\"*text\"}", "{\"a\":\"两\\n行\"}").ok());
        ok("未禁用时 text 收得下换行",
                new Strapper().strap("{\"a\":\"*text\"}", "{\"a\":\"两\\n行\"}").ok());

        Types single = Types.standard().disable("string.email");
        ok("也可以只禁用单条扩展类型", !single.has("email"));
        ok("同基础类型的其他扩展不受影响", single.has("mobile") && single.has("string"));

        Types restored = Types.standard().disable("text").enable("text");
        ok("可以解除禁用", restored.has("text") && restored.has("inlineImage"));

        eq("禁用清单", 1, Types.standard().disable("text").disabledTypes().size());
        ok("按短名禁用会归一成全名",
                Types.standard().disable("email").disabledTypes().contains("string.email"));

        throwsIllegal("禁用不存在的类型要报错", () -> Types.standard().disable("没这个类型"));

        ok("禁用不影响已复制出去的表",
                Types.standard().copy().has("text") && !Types.standard().disable("text").has("text"));
    }

    private static void objectTests() {
        group("对象校验");

        String format = """
                {
                  "userId":"*int//用户ID",
                  "name":"*string:32//姓名",
                  "note":"string//备注"
                }""";

        StrapResult ok = new Strapper().strap(format, "{\"userId\":\"123\",\"name\":\"闫大瑶\"}");
        ok("必填齐全即通过", ok.ok());
        eq("int 归一化为 Long", Long.valueOf(123), ok.asMap().get("userId"));
        ok("选填缺失则不出现在取值里", !ok.asMap().containsKey("note"));

        StrapResult missing = new Strapper().strap(format, "{\"name\":\"张三\"}");
        ok("缺必填项则失败", !missing.ok());
        eq("缺必填项的错误码", ErrorCode.DATA_KEY_NEED_EXIST, missing.errors().get(0).code());
        eq("错误路径", "$.userId", missing.errors().get(0).path());

        StrapResult nullValue = new Strapper().strap(format, "{\"userId\":null,\"name\":\"张三\"}");
        ok("显式 null 等同于缺失", !nullValue.ok());

        StrapResult badType = new Strapper().strap(format, "{\"userId\":\"abc\",\"name\":\"张三\"}");
        ok("值不符合类型则失败", !badType.ok());
        eq("类型不符的错误码", ErrorCode.DATA_NOT_MATCHED, badType.errors().get(0).code());

        StrapResult shapeMismatch = new Strapper()
                .strap("{\"a\":{\"b\":\"*int\"}}", "{\"a\":\"不是对象\"}");
        ok("格式要对象而数据是标量", !shapeMismatch.ok());

        StrapResult nested = new Strapper().strap("""
                {
                  "userId":"*int",
                  "*carData":{
                     "plateNumber":"*string//车牌号",
                     "mileage":"*float//里程"
                  }
                }""", "{\"userId\":1,\"carData\":{\"plateNumber\":\"京A12345\",\"mileage\":\"1024.5\"}}");
        ok("嵌套对象通过", nested.ok());
        eq("嵌套取值", Double.valueOf(1024.5), nested.find("carData.mileage").orElse(null));

        StrapResult nestedMissing = new Strapper()
                .strap("{\"*carData\":{\"a\":\"*int\"}}", "{}");
        ok("对象整体必填标在 key 上", !nestedMissing.ok());

        StrapResult nestedOptional = new Strapper()
                .strap("{\"carData\":{\"a\":\"*int\"}}", "{}");
        ok("对象整体选填时可以整个不传", nestedOptional.ok());

        StrapResult boolTest = new Strapper()
                .strap("{\"vip\":\"*bool\"}", "{\"vip\":false}");
        ok("false 是合法取值，不能当成缺失", boolTest.ok());
        eq("bool 归一化", Boolean.FALSE, boolTest.asMap().get("vip"));

        StrapResult zeroTest = new Strapper()
                .strap("{\"amount\":\"*float\"}", "{\"amount\":\"0\"}");
        ok("0 是合法取值", zeroTest.ok());
        eq("0 归一化", Double.valueOf(0), zeroTest.asMap().get("amount"));
    }

    private static void rangeAndLengthTests() {
        group("取值范围与长度");

        Strapper s = new Strapper();

        ok("闭区间含端点", s.strap("{\"y\":\"*int[1990,2030]\"}", "{\"y\":1990}").ok());
        ok("闭区间上端点", s.strap("{\"y\":\"*int[1990,2030]\"}", "{\"y\":2030}").ok());
        ok("越下界", !s.strap("{\"y\":\"*int[1990,2030]\"}", "{\"y\":1989}").ok());
        ok("越上界", !s.strap("{\"y\":\"*int[1990,2030]\"}", "{\"y\":2031}").ok());
        ok("开区间不含端点", !s.strap("{\"y\":\"*int(1,100]\"}", "{\"y\":1}").ok());
        ok("开区间内", s.strap("{\"y\":\"*int(1,100]\"}", "{\"y\":2}").ok());
        ok("单侧无界", s.strap("{\"y\":\"*int[0,]\"}", "{\"y\":99999}").ok());
        ok("单侧无界仍检查另一侧", !s.strap("{\"y\":\"*int[0,]\"}", "{\"y\":-1}").ok());

        eq("越界的错误码", ErrorCode.DATA_NOT_IN_VALID_RANGE,
                s.strap("{\"y\":\"*int[1990,2030]\"}", "{\"y\":1989}").errors().get(0).code());

        ok("枚举命中", s.strap("{\"g\":\"*string{男,女}\"}", "{\"g\":\"女\"}").ok());
        ok("枚举未命中", !s.strap("{\"g\":\"*string{男,女}\"}", "{\"g\":\"保密\"}").ok());
        eq("枚举未命中的错误码", ErrorCode.DATA_NOT_IN_SET_RANGE,
                s.strap("{\"g\":\"*string{男,女}\"}", "{\"g\":\"保密\"}").errors().get(0).code());
        ok("数值枚举按数值比较", s.strap("{\"n\":\"*int{1,2,3}\"}", "{\"n\":\"01\"}").ok());

        ok("长度合规", s.strap("{\"a\":\"*string:4\"}", "{\"a\":\"abcd\"}").ok());
        ok("长度超限", !s.strap("{\"a\":\"*string:4\"}", "{\"a\":\"abcde\"}").ok());
        eq("超长的错误码", ErrorCode.DATA_OVER_LENGTH,
                s.strap("{\"a\":\"*string:4\"}", "{\"a\":\"abcde\"}").errors().get(0).code());
        ok("长度按原始串算，数字也算", !s.strap("{\"a\":\"*int:2\"}", "{\"a\":12345}").ok());

        ok("日期区间", s.strap("{\"d\":\"*date[2020-01-01,2020-12-31]\"}", "{\"d\":\"2020/06/15\"}").ok());
        ok("日期越界", !s.strap("{\"d\":\"*date[2020-01-01,2020-12-31]\"}", "{\"d\":\"2021-01-01\"}").ok());
    }

    private static void defaultValueTests() {
        group("默认值");

        Strapper s = new Strapper();

        StrapResult fallback = s.strap("{\"y\":\"int[1990,2030]#2000//年份\"}", "{\"y\":1800}");
        ok("取值越界时用默认值兜底", fallback.ok());
        eq("兜底后的取值", Long.valueOf(2000), fallback.asMap().get("y"));
        eq("兜底时记一条告警", 1, fallback.warnings().size());
        eq("兜底的告警码", ErrorCode.DATA_USE_DEFAULT_VALUE, fallback.warnings().get(0).code());

        StrapResult filled = s.strap("{\"y\":\"int[1990,2030]#2000//年份\"}", "{}");
        ok("选填项缺失时补上默认值", filled.ok());
        eq("补上的默认值", Long.valueOf(2000), filled.asMap().get("y"));
        eq("补默认值不算告警", 0, filled.warnings().size());

        StrapResult noDefault = s.strap("{\"y\":\"int[1990,2030]//年份\"}", "{\"y\":1800}");
        ok("没有默认值就报错", !noDefault.ok());

        StrapResult badDefault = s.strap("{\"y\":\"int[1990,2030]#9999//年份\"}", "{\"y\":1800}");
        ok("默认值自身不合规时报格式错", !badDefault.ok());
        eq("默认值不合规的错误码", ErrorCode.FORMAT_DEFAULT_VALUE_INVALID,
                badDefault.errors().get(0).code());

        StrapResult required = s.strap("{\"y\":\"*int#2000\"}", "{}");
        ok("必填项不会被默认值顶替", !required.ok());
    }

    private static void arrayTests() {
        group("数组");

        Strapper s = new Strapper();

        StrapResult plain = s.strap("{\"fruits\":[\"string//水果名\"]}",
                "{\"fruits\":[\"苹果\",\"香蕉\"]}");
        ok("值数组通过", plain.ok());
        eq("值数组取值", List.of("苹果", "香蕉"), plain.asMap().get("fruits"));

        StrapResult objects = s.strap("""
                {"list":[{"modelId":"*int//车型id","modelName":"*string//车型名称"}]}""",
                "{\"list\":[{\"modelId\":1,\"modelName\":\"A\"},{\"modelId\":2,\"modelName\":\"B\"}]}");
        ok("对象数组通过", objects.ok());
        eq("对象数组按下标取值", Long.valueOf(2), objects.find("list[1].modelId").orElse(null));

        StrapResult badItem = s.strap("{\"list\":[{\"modelId\":\"*int\"}]}",
                "{\"list\":[{\"modelId\":1},{\"modelId\":\"x\"}]}");
        ok("数组元素有错即失败", !badItem.ok());
        eq("错误路径带下标", "$.list[1].modelId", badItem.errors().get(0).path());

        ok("[n] 长度恰好", s.strap("{\"*[2]fruits\":[\"string\"]}", "{\"fruits\":[\"a\",\"b\"]}").ok());
        ok("[n] 长度不符", !s.strap("{\"*[2]fruits\":[\"string\"]}", "{\"fruits\":[\"a\"]}").ok());
        ok("[n,m] 区间内", s.strap("{\"[0,9]fruits\":[\"string\"]}", "{\"fruits\":[]}").ok());
        ok("[n,m] 超上界", !s.strap("{\"*[1,2]fruits\":[\"string\"]}",
                "{\"fruits\":[\"a\",\"b\",\"c\"]}").ok());
        eq("长度不符的错误码", ErrorCode.DATA_ARRAY_LENGTH_ERROR,
                s.strap("{\"*[2]f\":[\"string\"]}", "{\"f\":[\"a\"]}").errors().get(0).code());

        StrapResult notArray = s.strap("{\"fruits\":[\"string\"]}", "{\"fruits\":\"苹果\"}");
        ok("格式要数组而数据不是", !notArray.ok());

        StrapResult nestedArray = s.strap("{\"m\":[[\"int\"]]}", "{\"m\":[[1,2],[3]]}");
        ok("嵌套数组通过", nestedArray.ok());
    }

    private static void extraKeyTests() {
        group("未定义的 KEY");

        Strapper s = new Strapper();

        StrapResult dropped = s.strap("{\"a\":\"int\"}", "{\"a\":1,\"b\":2}");
        ok("没有 ... 时多余 key 被丢弃但不算错", dropped.ok());
        ok("丢弃的 key 不在取值里", !dropped.asMap().containsKey("b"));
        eq("丢弃时记一条告警", ErrorCode.DATA_UNKNOWN_KEY_ERROR, dropped.warnings().get(0).code());

        StrapResult kept = s.strap("{\"a\":\"int\",\"...\":\"string\"}", "{\"a\":1,\"b\":\"x\"}");
        ok("有 ... 时收下多余 key", kept.ok());
        eq("收下的多余 key", "x", kept.asMap().get("b"));

        StrapResult checked = s.strap("{\"a\":\"int\",\"...\":\"mobile\"}",
                "{\"a\":1,\"b\":\"13800138000\",\"c\":\"不是手机号\"}");
        ok("... 的值格式对多余 key 生效", !checked.ok());

        StrapResult loose = s.strap("{\"a\":\"int\",\"...\":{}}", "{\"a\":1,\"b\":{\"x\":1}}");
        ok("... 的值为字典时不再往下检查", loose.ok());
        ok("原样收下", loose.asMap().get("b") instanceof Map);

        StrapResult wildcard = s.strap("{\"!string\":\"*string//扩展的 key-value\"}",
                "{\"任意键\":\"值\",\"另一个\":\"值2\"}");
        ok("通配 key 收下所有键", wildcard.ok());
        eq("通配 key 的取值", 2, wildcard.asMap().size());

        StrapResult wildcardTyped = s.strap("{\"!int\":\"*string\"}", "{\"123\":\"ok\",\"abc\":\"bad\"}");
        ok("通配 key 会校验 key 名自身", !wildcardTyped.ok());
    }

    private static void conditionalTests() {
        group("条件必填");

        String format = "{\"type\":\"*string{个人,企业}\",\"!taxNo\":\"string//企业才必填\"}";

        StrapResult lenient = new Strapper().strap(format, "{\"type\":\"企业\"}");
        ok("不注册判定器时 ! 按选填处理", lenient.ok());

        Strapper strict = new Strapper().conditionalResolver(
                (path, key, siblings) -> "taxNo".equals(key) && "企业".equals(siblings.get("type")));
        ok("判定器说必填则报错", !strict.strap(format, "{\"type\":\"企业\"}").ok());
        ok("判定器说不必填则通过", strict.strap(format, "{\"type\":\"个人\"}").ok());
        ok("提供了值就不再判定", strict.strap(format, "{\"type\":\"企业\",\"taxNo\":\"91110\"}").ok());
    }

    private static void formatCheckTests() {
        group("格式描述自检");

        Strapper s = new Strapper();

        ok("合法格式", s.checkFormat("{\"a\":\"*int//说明\"}").ok());
        eq("空格式按 {} 处理", true, s.checkFormat("").ok());
        ok("空格式能校验空数据", s.strap("", "{}").ok());

        StrapResult badJson = s.checkFormat("{\"a\":}");
        ok("格式不是合法 JSON", !badJson.ok());
        eq("格式 JSON 错的错误码", ErrorCode.FORMAT_JSON_STRUCT_ERROR, badJson.errors().get(0).code());

        StrapResult scalar = s.checkFormat("\"string//纯字符串\"");
        ok("格式顶层不能是纯量", !scalar.ok());

        StrapResult unknown = s.checkFormat("{\"a\":\"*没这个类型\"}");
        ok("未定义类型报错", !unknown.ok());
        eq("未定义类型的错误码", ErrorCode.FORMAT_UNKNOWN_KEYTYPE_ERROR, unknown.errors().get(0).code());

        StrapResult passThrough = new Strapper()
                .unknownTypePolicy(Strapper.UnknownTypePolicy.PASS_THROUGH)
                .strap("{\"a\":\"*没这个类型\"}", "{\"a\":\"随便什么\"}");
        ok("PASS_THROUGH 时放行", passThrough.ok());
        eq("PASS_THROUGH 时记告警", 1, passThrough.warnings().size());

        StrapResult multi = s.checkFormat("{\"a\":[\"int\",\"string\"]}");
        ok("数组里多个格式报错", !multi.ok());
        eq("多格式数组的错误码", ErrorCode.FORMAT_NOT_SUPPORT_MULTIFORMAT_ARRAY,
                multi.errors().get(0).code());

        StrapResult empty = s.checkFormat("{\"a\":[]}");
        eq("空数组的错误码", ErrorCode.FORMAT_NOT_SUPPORT_NOFORMAT_ARRAY, empty.errors().get(0).code());

        ok("格式有错时不去跑数据校验",
                s.strap("{\"a\":\"*没这个类型\"}", "{\"a\":1}").value() == null);

        StrapResult badData = s.strap("{\"a\":\"int\"}", "{\"a\":}");
        eq("数据 JSON 错的错误码", ErrorCode.DATA_JSON_STRUCT_ERROR, badData.errors().get(0).code());

        StrapResult itemsOnNonArray = s.checkFormat("{\"*[2]a\":\"int\"}");
        ok("key 上写数组长度但值不是数组", !itemsOnNonArray.ok());
    }

    private static void dateTimeTests() {
        group("日期时间归一化");

        Strapper s = new Strapper();

        eq("date 归一化", "2020-01-02",
                s.strap("{\"d\":\"*date\"}", "{\"d\":\"2020/1/2\"}").asMap().get("d"));
        eq("紧凑写法", "2020-01-02",
                s.strap("{\"d\":\"*date\"}", "{\"d\":\"20200102\"}").asMap().get("d"));
        ok("不存在的日期被拒", !s.strap("{\"d\":\"*date\"}", "{\"d\":\"2021-02-30\"}").ok());

        eq("time 补齐秒", "12:34:00",
                s.strap("{\"t\":\"*time\"}", "{\"t\":\"12:34\"}").asMap().get("t"));

        eq("datetime 归一化", "2020-02-02 12:34:56",
                s.strap("{\"t\":\"*datetime\"}", "{\"t\":\"20200202 12:34:56\"}").asMap().get("t"));
        eq("带毫秒和时区后缀取墙上时钟", "2019-12-31 16:00:00",
                s.strap("{\"t\":\"*datetime\"}", "{\"t\":\"2019-12-31T16:00:00.000Z\"}").asMap().get("t"));
        eq("只写日期时补零点", "2020-02-14 00:00:00",
                s.strap("{\"t\":\"*datetime\"}", "{\"t\":\"2020-02-14\"}").asMap().get("t"));
    }

    private static void specExampleTests() {
        group("spec 里的实际例子");

        // example.md User01: 用户登录
        String loginRequest = """
                {
                	"username":"*string//用户名",
                	"password":"*string//密码",
                	"verify":"*string//验证码"
                }""";
        ok("登录请求通过", new Strapper().strap(loginRequest,
                "{\"username\":\"u\",\"password\":\"p\",\"verify\":\"1234\"}").ok());

        String loginResponse = """
                {
                	"code":"*retCode",
                	"data":{
                			"name":"*string//用户显示名",
                			"score":"*int//用户积分"
                		},
                	"message":"!string//错误消息"
                }""";
        StrapResult resp = new Strapper().strap(loginResponse,
                "{\"code\":\"ok\",\"data\":{\"name\":\"张三\",\"score\":100}}");
        ok("登录响应通过", resp.ok());
        ok("retCode 取值受限", !new Strapper().strap(loginResponse,
                "{\"code\":\"whatever\",\"data\":{\"name\":\"张三\",\"score\":1}}").ok());

        // valueFormat.md 的示例
        String profile = """
                {
                	"姓名":"*string:32//用户的真实姓名",
                	"年龄":"*int[0,100]:3#",
                	"手机号":"mobile:16//手机号",
                	"性别":"string{男,女}//性别可以不填"
                }""";
        StrapResult p = new Strapper().strap(profile,
                "{\"姓名\":\"闫大瑶\",\"年龄\":30,\"手机号\":\"13800138000\",\"性别\":\"男\"}");
        ok("valueFormat 示例通过", p.ok());
        ok("手机号格式生效", !new Strapper().strap(profile,
                "{\"姓名\":\"闫大瑶\",\"年龄\":30,\"手机号\":\"12345\"}").ok());

        // dataStruct.md 的 price 对象
        String price = """
                {
                	"userId":"*string //当前登录用户ID",
                	"vehicleModelId":"*int",
                	"price":{
                		"basePrice":"*currency//每日租价",
                		"depositReturnTime":"*int//违章押金退还周期(天)"
                	}
                }""";
        StrapResult pr = new Strapper().strap(price,
                "{\"userId\":\"u1\",\"vehicleModelId\":3,\"price\":{\"basePrice\":\"199.00\",\"depositReturnTime\":15}}");
        ok("dataStruct 示例通过", pr.ok());
        eq("currency 归一化为 Double", Double.valueOf(199), pr.find("price.basePrice").orElse(null));

        // 领域扩展类型：共享电动车的车辆编号
        Strapper domain = new Strapper().addType("int.vehicleNo", "^20\\d{2}\\d{5}$");
        ok("领域类型放行合规值",
                domain.strap("{\"no\":\"*vehicleNo//车辆编号\"}", "{\"no\":\"201900123\"}").ok());
        ok("领域类型拦住批次不对的值",
                !domain.strap("{\"no\":\"*vehicleNo//车辆编号\"}", "{\"no\":\"199900123\"}").ok());
        ok("领域类型拦住位数不对的值",
                !domain.strap("{\"no\":\"*vehicleNo//车辆编号\"}", "{\"no\":\"20190012\"}").ok());
        eq("领域类型继承 int 的取值语义", Long.valueOf(201900123L),
                domain.strap("{\"no\":\"*vehicleNo\"}", "{\"no\":\"201900123\"}").asMap().get("no"));
    }

    private static void concurrencyTests() {
        group("并发");

        // 配置好之后共用一个 Strapper：strap() 不改实例状态，多线程读同一份配置
        Strapper shared = new Strapper()
                .addType("int.vehicleNo", "^20\\d{2}\\d{5}$")
                .disableType("text");
        String format = "{\"no\":\"*vehicleNo\",\"n\":\"*int[0,100]\"}";
        ok("多线程共用一个 Strapper", parallel(8, 500, i -> {
            StrapResult r = shared.strap(format, "{\"no\":\"202300471\",\"n\":" + (i % 101) + "}");
            return r.ok()
                    && Long.valueOf(202300471L).equals(r.asMap().get("no"))
                    && Long.valueOf(i % 101).equals(r.asMap().get("n"));
        }));

        // JsonWriter 是不可变的配置对象，可以当常量共享；Stream 和目标 Appendable 各线程各一份
        JsonWriter config = JsonWriter.compact().skipNulls(true);
        ok("多线程共用一个 JsonWriter 配置", parallel(8, 500, i -> {
            StringBuilder sb = new StringBuilder();
            try (JsonWriter.Stream out = config.stream(sb)) {
                out.beginObject().field("i", i).field("skip", null)
                   .name("l").beginArray().value(1).value("中").endArray().endObject();
            }
            return ("{\"i\":" + i + ",\"l\":[1,\"中\"]}").equals(sb.toString());
        }));

        // Json.parse 每次调用自己新建 Parser，没有共享状态
        ok("多线程共用 Json.parse", parallel(8, 500, i -> {
            Object v = Json.parse("{\"i\":" + i + ",\"s\":\"中文\"}");
            return Long.valueOf(i).equals(((Map<?, ?>) v).get("i"));
        }));

        // Types 配置完之后只读
        Types types = Types.standard().disable("text");
        ok("多线程共用一张类型表", parallel(8, 500, i ->
                types.resolve("mobile") != null && types.resolve("inlineImage") == null));
    }

    private static void streamConfinementTests() {
        group("Stream 的线程封闭");

        // 作用域式入口：Stream 出不了 lambda，结构上就没机会泄漏给别的线程
        eq("stream(body) 自带缓冲", "{\"a\":1}",
                JsonWriter.compact().stream(json -> json.beginObject().field("a", 1).endObject()));

        StringBuilder sb = new StringBuilder();
        JsonWriter.compact().stream(sb, json -> json.beginArray().value(1).value(2).endArray());
        eq("stream(out, body) 写进指定目标", "[1,2]", sb.toString());

        ok("作用域式入口也会检查括号闭合", throwsIllegalStateIn(() ->
                JsonWriter.compact().stream(json -> json.beginObject())));

        // 别的线程碰一下就抛
        JsonWriter.Stream escaped = JsonWriter.compact().stream(new StringBuilder());
        escaped.beginObject();
        ok("别的线程用会抛 IllegalStateException", inOtherThread(() -> escaped.field("a", 1)));
        ok("别的线程 close 也会抛", inOtherThread(escaped::close));
        ok("原线程仍然可用", !throwsIllegalStateIn(() -> escaped.field("a", 1).endObject()));
        eq("归属线程就是创建它的线程", Thread.currentThread(), escaped.owner());
        escaped.close();

        // handOff 之后另一个线程可以接手，且看得到前面写的内容
        StringBuilder relay = new StringBuilder();
        JsonWriter.Stream baton = JsonWriter.compact().stream(relay);
        baton.beginObject().field("first", "甲");
        baton.handOff();
        ok("handOff 之后归属为空", baton.owner() == null);
        ok("另一个线程能接手并写完", !inOtherThread(() -> {
            baton.field("second", "乙").endObject();
            baton.close();
        }));
        eq("接力写出的结果完整", "{\"first\":\"甲\",\"second\":\"乙\"}", relay.toString());

        JsonWriter.Stream notMine = JsonWriter.compact().stream(new StringBuilder());
        ok("非归属线程不能 handOff", inOtherThread(notMine::handOff));

        // 并发压一下：各线程各自的 Stream + 各自的目标，互不干扰
        JsonWriter config = JsonWriter.pretty();
        ok("各线程各自的 Stream 并发写出", parallel(8, 300, i ->
                ("{\n  \"i\": " + i + "\n}").equals(
                        config.stream(json -> json.beginObject().field("i", i).endObject()))));
    }

    /** 在另一个线程里跑，抛 IllegalStateException 返回 true。 */
    private static boolean inOtherThread(Runnable action) {
        java.util.concurrent.atomic.AtomicBoolean threw = new java.util.concurrent.atomic.AtomicBoolean();
        Thread t = new Thread(() -> {
            try {
                action.run();
            } catch (IllegalStateException e) {
                threw.set(true);
            }
        });
        t.start();
        try {
            t.join(10_000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return threw.get();
    }

    private static boolean throwsIllegalStateIn(Runnable action) {
        try {
            action.run();
            return false;
        } catch (IllegalStateException e) {
            return true;
        }
    }

    /** 跑 threads 个线程各 loops 次，全部返回 true 才算通过。 */
    private static boolean parallel(int threads, int loops, java.util.function.IntPredicate task) {
        java.util.concurrent.atomic.AtomicBoolean allOk = new java.util.concurrent.atomic.AtomicBoolean(true);
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(threads);
        List<Thread> workers = new java.util.ArrayList<>();
        for (int t = 0; t < threads; t++) {
            int base = t * loops;
            Thread worker = new Thread(() -> {
                try {
                    start.await();
                    for (int k = 0; k < loops; k++) {
                        if (!task.test(base + k)) {
                            allOk.set(false);
                            return;
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    allOk.set(false);
                } catch (RuntimeException e) {
                    allOk.set(false);
                } finally {
                    done.countDown();
                }
            });
            workers.add(worker);
            worker.start();
        }
        start.countDown();
        try {
            if (!done.await(60, java.util.concurrent.TimeUnit.SECONDS)) {
                allOk.set(false);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            allOk.set(false);
        }
        workers.forEach(Thread::interrupt);
        return allOk.get();
    }

    // =================================================================
    // 迷你断言
    // =================================================================

    private static void group(String name) {
        group = name;
        System.out.println();
        System.out.println("── " + name + " " + "─".repeat(Math.max(0, 50 - name.length() * 2)));
    }

    private static void ok(String name, boolean condition) {
        record(name, condition, condition ? "" : "期望为 true");
    }

    private static void eq(String name, Object expected, Object actual) {
        boolean same = expected == null ? actual == null : expected.equals(actual);
        record(name, same, "期望 <" + expected + ">，实际 <" + actual + ">");
    }

    private static void throwsJsonException(String name, String text) {
        try {
            Json.parse(text);
            record(name, false, "期望抛出 JsonException，实际正常返回");
        } catch (Json.JsonException e) {
            record(name, true, "");
        }
    }

    private static void throwsFormatException(String name, Runnable action) {
        try {
            action.run();
            record(name, false, "期望抛出 FormatException，实际正常返回");
        } catch (FormatException e) {
            record(name, true, "");
        }
    }

    private static void throwsIllegalState(String name, Runnable action) {
        try {
            action.run();
            record(name, false, "期望抛出 IllegalStateException，实际正常返回");
        } catch (IllegalStateException e) {
            record(name, true, "");
        }
    }

    private static void throwsIllegal(String name, Runnable action) {
        try {
            action.run();
            record(name, false, "期望抛出 IllegalArgumentException，实际正常返回");
        } catch (IllegalArgumentException e) {
            record(name, true, "");
        }
    }

    private static void record(String name, boolean success, String detail) {
        if (success) {
            passed++;
            System.out.println("  ✓ " + name);
        } else {
            failed++;
            System.out.println("  ✗ " + name + "  <<< " + detail + "   [" + group + "]");
        }
    }

    private SelfTest() {
    }
}
