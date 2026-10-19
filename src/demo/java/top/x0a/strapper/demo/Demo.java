package top.x0a.strapper.demo;

import java.util.LinkedHashMap;
import java.util.Map;

import top.x0a.json.Json;
import top.x0a.json.JsonWriter;
import top.x0a.strapper.ErrorCode;
import top.x0a.strapper.JsonReader;
import top.x0a.strapper.StrapIssue;
import top.x0a.strapper.StrapResult;
import top.x0a.strapper.Strapper;
import top.x0a.strapper.Types;

/**
 * libstrapper 用法演示。{@code ./gradlew runDemo} 运行。
 *
 * <p>九个场景，从最简单的一次校验开始，到把自己业务领域的类型加进来。
 */
public final class Demo {

    public static void main(String[] args) {
        最简用法();
        取值归一化();
        问题报告();
        嵌套与数组();
        默认值兜底();
        扩展领域类型();
        用明文维护类型表();
        禁用类型收紧攻击面();
        条件必填();
        接口文档自检();
        换掉内置的JSON解析();
        把结果写成JSON响应();
    }

    // =================================================================
    // 1. 最简用法：一个 controller 收到请求体，校验完直接用
    // =================================================================

    private static void 最简用法() {
        title("1. 最简用法");

        String format = """
                {
                  "username":"*string:32//用户名",
                  "password":"*string:32//密码",
                  "verify":"*string//验证码"
                }""";

        String body = """
                {"username":"yayv","password":"12344321","verify":"8a3f"}""";

        StrapResult result = new Strapper().strap(format, body);

        if (!result.ok()) {
            System.out.println("参数不合法：\n" + result.report());
            return;
        }

        Map<String, Object> params = result.asMap();
        System.out.println("校验通过，可以直接取值：");
        System.out.println("  username = " + params.get("username"));
        System.out.println("  verify   = " + params.get("verify"));
    }

    // =================================================================
    // 2. 取值归一化：格式描述里的类型决定拿到手的是什么
    // =================================================================

    private static void 取值归一化() {
        title("2. 取值归一化");

        String format = """
                {
                  "userId":"*int//前端传字符串也好传数字也好，拿到手都是 Long",
                  "amount":"*currency//金额",
                  "vip":"*bool//布尔",
                  "startAt":"*datetime//各种写法统一成 yyyy-MM-dd HH:mm:ss",
                  "birthday":"*date//统一成 yyyy-MM-dd"
                }""";

        String body = """
                {
                  "userId":"1024",
                  "amount":"199.00",
                  "vip":"1",
                  "startAt":"2019-12-31T16:00:00.000Z",
                  "birthday":"2001/3/7"
                }""";

        Map<String, Object> params = new Strapper().strap(format, body).asMap();
        params.forEach((k, v) ->
                System.out.printf("  %-9s = %-24s (%s)%n", k, v, v.getClass().getSimpleName()));
    }

    // =================================================================
    // 3. 问题报告：错在哪一层、哪个字段、为什么
    // =================================================================

    private static void 问题报告() {
        title("3. 问题报告");

        String format = """
                {
                  "userId":"*int//用户ID",
                  "name":"*string:4//姓名，最多 4 个字",
                  "age":"*int[0,150]//年龄",
                  "gender":"*string{男,女}//性别",
                  "email":"*email//邮箱"
                }""";

        String body = """
                {"name":"这个名字明显超长了","age":200,"gender":"保密","email":"not-an-email"}""";

        StrapResult result = new Strapper().strap(format, body);

        System.out.println("ok = " + result.ok());
        System.out.println(result.report());

        System.out.println("\n也可以自己按问题码分流：");
        for (StrapIssue issue : result.errors()) {
            String hint = issue.code() == ErrorCode.DATA_KEY_NEED_EXIST ? "少传了参数" : "值不合规";
            System.out.printf("  %-24s %s%n", issue.path(), hint);
        }
    }

    // =================================================================
    // 4. 嵌套与数组：对象整体的必填标在 key 上，数组长度也标在 key 上
    // =================================================================

    private static void 嵌套与数组() {
        title("4. 嵌套与数组");

        String format = """
                {
                  "orderId":"*int//订单id",
                  "*data":{
                    "*[1,3]list":[
                      {
                        "type":"*string{事故,违章}//每一项都是一条记录",
                        "occurrence":"*datetime//发生时间",
                        "amount":"int//罚款或定损金额(分)",
                        "driver":"string//驾驶人姓名"
                      }
                    ]
                  }
                }""";

        String body = """
                {
                  "orderId": 1,
                  "data": {
                    "list": [
                      {"type":"违章","occurrence":"20200202 12:34:56","amount":1000,"driver":"赵六"},
                      {"type":"事故","occurrence":"2020-03-01 09:00:00","amount":50000}
                    ]
                  }
                }""";

        StrapResult result = new Strapper().strap(format, body);
        System.out.println("ok = " + result.ok());
        System.out.println("取值：\n" + result.toPrettyJson());
        System.out.println("\n按路径取单个值：list[0].driver = "
                + result.find("data.list[0].driver").orElse("(无)"));

        System.out.println("\n把数组塞到 4 条（约定是 1~3 条）：");
        String tooMany = body.replace("{\"type\":\"事故\",\"occurrence\":\"2020-03-01 09:00:00\",\"amount\":50000}",
                """
                {"type":"事故","occurrence":"2020-03-01 09:00:00"},
                      {"type":"事故","occurrence":"2020-03-02 09:00:00"},
                      {"type":"事故","occurrence":"2020-03-03 09:00:00"}""");
        System.out.println(new Strapper().strap(format, tooMany).report());
    }

    // =================================================================
    // 5. 默认值兜底：#默认值 让选填参数有个确定的落点
    // =================================================================

    private static void 默认值兜底() {
        title("5. 默认值兜底");

        String format = """
                {
                  "page":"int[1,]#1//页码，不传按第 1 页",
                  "size":"int[1,100]#20//每页条数，超范围时回落到 20",
                  "order":"string{asc,desc}#desc//排序"
                }""";

        Strapper strapper = new Strapper();

        System.out.println("① 什么都不传，默认值直接补齐：");
        System.out.println("   " + strapper.strap(format, "{}").toJson());

        System.out.println("\n② size 传了个越界的 9999，回落到默认值并记一条告警：");
        StrapResult fallback = strapper.strap(format, "{\"page\":3,\"size\":9999}");
        System.out.println("   取值 = " + fallback.toJson());
        System.out.println("   ok  = " + fallback.ok() + "（告警不影响 ok）");
        System.out.println("   " + fallback.warnings().get(0));
    }

    // =================================================================
    // 6. 扩展领域类型：内置的 mobile、idcard 和你自己的类型是同一个入口
    // =================================================================

    private static void 扩展领域类型() {
        title("6. 扩展自己业务领域的类型");

        // 共享电动车领域：车辆编号 9 位数字，前 4 位是生产批次；
        // 电池编号形如 BAT + 12 位数字；换电柜编号按省份简称分区
        Strapper strapper = new Strapper()
                .addType("int.vehicleNo", "^20\\d{2}\\d{5}$")
                .addType("string.batteryNo", "^BAT\\d{12}$")
                .addType("string.cabinetNo", "^(京|沪|粤|浙)-[A-Z]{2}\\d{4}$");

        String format = """
                {
                  "vehicleNo":"*vehicleNo//车辆编号，前 4 位为生产批次",
                  "batteryNo":"*batteryNo//电池编号",
                  "cabinetNo":"*cabinetNo//换电柜编号",
                  "operator":"*mobile//操作人手机号"
                }""";

        System.out.println("① 合规的一条换电记录：");
        System.out.println("   " + strapper.strap(format, """
                {"vehicleNo":"202300471","batteryNo":"BAT202301000088",
                 "cabinetNo":"京-HD0032","operator":"13800138000"}""").toJson());

        System.out.println("\n② 批次 1999 不存在、电池编号少一位、换电柜省份不在服务范围：");
        StrapResult bad = strapper.strap(format, """
                {"vehicleNo":"199900471","batteryNo":"BAT20230100008",
                 "cabinetNo":"川-CD0032","operator":"13800138000"}""");
        System.out.println(bad.report());

        System.out.println("""

                注意 vehicleNo 派生自 int，所以取值仍然是 Long，长度、区间这些约束照常能用：""");
        System.out.println("   " + strapper
                .strap("{\"vehicleNo\":\"*vehicleNo[202300000,202399999]//只收 2023 批次\"}",
                        "{\"vehicleNo\":\"202300471\"}")
                .toJson());
    }

    // =================================================================
    // 7. 用明文维护类型表：跟接口文档放在一起，也方便跨语言共用
    // =================================================================

    private static void 用明文维护类型表() {
        title("7. 用明文维护类型表");

        // 这段明文完全可以放进 conf/types.txt，用 strapper.loadTypes(Path.of(...)) 读进来。
        // 各语言的实现读的是同一份表，正则方言有出入时改对应那一行即可。
        String table = """
                # 共享电动车 —— 领域类型表
                int.vehicleNo     : ^20\\d{2}\\d{5}$
                string.batteryNo  : ^BAT\\d{12}$
                string.cabinetNo  : ^(京|沪|粤|浙)-[A-Z]{2}\\d{4}$
                string.orderNo    : ^SO\\d{12}$
                """;

        Strapper strapper = new Strapper().loadTypes(table);

        System.out.println("读进来之后，格式描述里直接写短名就行：");
        System.out.println("   " + strapper
                .strap("{\"orderNo\":\"*orderNo//订单号\"}", "{\"orderNo\":\"SO202301000088\"}")
                .toJson());

        System.out.println("\n内置的扩展类型也是同一张表里的几行，可以导出来看：");
        Types builtin = Types.standard();
        builtin.extendedTypes().entrySet().stream()
                .limit(6)
                .forEach(e -> System.out.printf("   %-20s : %s%n", e.getKey(), e.getValue()));
        System.out.println("   ... 共 " + builtin.extendedTypes().size() + " 条");

        System.out.println("\n不满意内置规则就重写同名一行覆盖掉。比如只认中国大陆手机号的严格版：");
        Strapper strict = new Strapper().addType("string.mobile", "^1[3-9]\\d{9}$");
        System.out.println("   带 +86 前缀在内置规则下通过 = "
                + new Strapper().strap("{\"m\":\"*mobile\"}", "{\"m\":\"+8613800138000\"}").ok());
        System.out.println("   带 +86 前缀在覆盖后的规则下通过 = "
                + strict.strap("{\"m\":\"*mobile\"}", "{\"m\":\"+8613800138000\"}").ok());
    }

    // =================================================================
    // 8. 禁用类型：string 是纯字符串，text 允许换行；不想要后者就关掉
    // =================================================================

    private static void 禁用类型收紧攻击面() {
        title("8. 禁用类型，收紧攻击面");

        String withNewline = """
                {"note":"第一行\\n第二行"}""";

        System.out.println("string 是纯字符串，本来就不收带换行的值：");
        System.out.println("   " + new Strapper().strap("{\"note\":\"*string\"}", withNewline).report());

        System.out.println("\ntext 允许换行，默认能收下：");
        System.out.println("   ok = " + new Strapper().strap("{\"note\":\"*text\"}", withNewline).ok());

        System.out.println("\n不想要这种口子，一句话关掉整个 text 分支：");
        Strapper strict = new Strapper().disableType("text");
        StrapResult blocked = strict.checkFormat("{\"note\":\"*text//备注\"}");
        System.out.println("   " + blocked.errors().get(0));

        System.out.println("\n从 text 派生的扩展类型也跟着关掉了（内置表里只有 inlineImage 挂在 text 下）：");
        System.out.println("   " + strict.checkFormat("{\"img\":\"*inlineImage\"}").errors().get(0));

        System.out.println("\n挂在 string 下的扩展类型不受影响：");
        System.out.println("   ok = "
                + strict.strap("{\"m\":\"*mobile\"}", "{\"m\":\"13800138000\"}").ok());

        System.out.println("\n也可以只关掉单独一条，比如这个项目不收邮箱：");
        System.out.println("   " + new Strapper().disableType("string.email")
                .checkFormat("{\"e\":\"*email\"}").errors().get(0));
    }

    // =================================================================
    // 9. 条件必填：! 标记的参数由调用方按上下文裁决
    // =================================================================

    private static void 条件必填() {
        title("9. 条件必填");

        String format = """
                {
                  "titleType":"*string{个人,企业}//抬头类型",
                  "!taxNo":"string:20//企业抬头才必填的税号"
                }""";

        Strapper strapper = new Strapper().conditionalResolver(
                (path, key, siblings) -> "taxNo".equals(key) && "企业".equals(siblings.get("titleType")));

        System.out.println("个人抬头，不传税号：ok = "
                + strapper.strap(format, "{\"titleType\":\"个人\"}").ok());

        StrapResult missing = strapper.strap(format, "{\"titleType\":\"企业\"}");
        System.out.println("企业抬头，不传税号：ok = " + missing.ok());
        System.out.println("   " + missing.errors().get(0));

        System.out.println("企业抬头，传了税号：ok = "
                + strapper.strap(format, "{\"titleType\":\"企业\",\"taxNo\":\"91110108MA01\"}").ok());

        System.out.println("\n不注册裁决器时 ! 一律按选填处理：ok = "
                + new Strapper().strap(format, "{\"titleType\":\"企业\"}").ok());
    }

    // =================================================================
    // 10. 接口文档自检：不需要数据，光检查格式描述写得对不对
    // =================================================================

    private static void 接口文档自检() {
        title("10. 接口文档自检（适合放进 CI）");

        Strapper strapper = new Strapper();

        String[] formats = {
            "{\"a\":\"*int//没问题\"}",
            "{\"a\":\"*inr//类型名打错了\"}",
            "{\"list\":[\"int\",\"string\"]}",
            "{\"list\":[]}",
            "{\"a\":\"*int[1]//区间少写了一半\"}",
            "{\"a\":}",
        };

        for (String format : formats) {
            StrapResult check = strapper.checkFormat(format);
            System.out.printf("%-40s %s%n", format, check.ok() ? "✓" : "✗");
            for (StrapIssue issue : check.errors()) {
                System.out.println("      " + issue);
            }
        }
    }

    // =================================================================
    // 11. 换掉内置的 JSON 解析：项目里已经在用别的库时，不必驮两套
    // =================================================================

    private static void 换掉内置的JSON解析() {
        title("11. 换掉内置的 JSON 解析");

        String format = "{\"userId\":\"*int//用户ID\",\"name\":\"*string:32//姓名\"}";
        String body = "{\"userId\":1024,\"name\":\"闫大瑶\"}";

        System.out.println("默认用内置实现，零依赖：");
        System.out.println("   " + new Strapper().strap(format, body).toJson());

        System.out.println("""

                项目里已经在用 Jackson / Gson / fastjson2 时，包一行就能换过去：

                   ObjectMapper mapper = new ObjectMapper();
                   strapper.jsonReader(JsonReader.wrapping(t -> mapper.readValue(t, Object.class)));

                   Gson gson = new Gson();
                   strapper.jsonReader(JsonReader.wrapping(t -> gson.fromJson(t, Object.class)));

                   strapper.jsonReader(JsonReader.wrapping(JSON::parse));

                （这三个适配器的可运行版本在 src/bench 下的 JsonReaderAdapters，
                  demo 本身不引第三方依赖所以只能这样写出来看）""");

        // 适配器不一定非得是第三方库，也可以是在内置实现外面加一层自己的策略。
        // 这里加的是请求体大小上限 —— 校验逻辑再严密，也挡不住一个 500MB 的 body 把内存打满。
        int maxBytes = 64 * 1024;
        JsonReader guarded = JsonReader.wrapping(text -> {
            if (text.length() > maxBytes) {
                throw new IllegalArgumentException(
                        "请求体 " + text.length() + " 字符，超过上限 " + maxBytes);
            }
            return Json.parse(text);
        });

        Strapper strapper = new Strapper().jsonReader(guarded);
        System.out.println("\n适配器也可以是自己的策略，比如给请求体加个大小上限：");
        System.out.println("   正常请求 ok = " + strapper.strap(format, body).ok());

        String huge = "{\"userId\":1,\"name\":\"" + "填".repeat(100_000) + "\"}";
        StrapResult rejected = strapper.strap(format, huge);
        System.out.println("   超大请求 ok = " + rejected.ok());
        System.out.println("   " + rejected.errors().get(0));
    }

    // =================================================================
    // 12. 写出：top.x0a.json.JsonWriter 是独立的，接口输出环节可以直接用
    // =================================================================

    private static void 把结果写成JSON响应() {
        title("12. 把结果写成 JSON 响应");

        String format = "{\"userId\":\"*int//用户ID\",\"name\":\"*string:32//姓名\"}";
        StrapResult result = new Strapper().strap(format, "{\"userId\":1024,\"name\":\"闫大瑶\"}");

        System.out.println("① 校验结果自带 toJson()：");
        System.out.println("   " + result.toJson());

        System.out.println("\n② 套一层响应信封，用 JsonWriter 写出来：");
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("code", "ok");
        envelope.put("message", null);
        envelope.put("data", result.asMap());

        System.out.println("   紧凑           " + JsonWriter.compact().write(envelope));
        System.out.println("   丢掉 null 字段  " + JsonWriter.compact().skipNulls(true).write(envelope));
        System.out.println("   转义非 ASCII    "
                + JsonWriter.compact().escapeNonAscii(true).skipNulls(true).write(envelope));
        System.out.println("   给人看的：");
        JsonWriter.pretty().write(envelope).lines().forEach(l -> System.out.println("      " + l));

        System.out.println("""

                ③ 响应体很大、或者要边查边写时，用流式写出 ——
                   整个过程不会在内存里出现完整的结果串，直接往 Writer 里灌：""");

        // out 是这次输出专属的目标，任何 Appendable 都行（Writer、StringBuilder……）
        StringBuilder out = new StringBuilder();
        JsonWriter.compact().stream(out, json -> {
            json.beginObject()
                .field("code", "ok")
                .name("data").beginObject()
                    .field("total", 3)
                    .name("list").beginArray();
            for (int i = 1; i <= 3; i++) {
                json.beginObject()
                    .field("modelId", i)
                    .field("modelName", "车型-" + i)
                    .field("price", 199.5 + i)
                    .endObject();
            }
            json.endArray().endObject().endObject();
        });
        System.out.println("   " + out);

        System.out.println("""

                   括号没闭合、对象里漏写 name、顶层写了两个值，这些都会当场抛
                   IllegalStateException，不会悄悄产出一段坏 JSON。""");

        System.out.println("\n④ 连目标都不想操心时，用自带缓冲的那个重载：");
        System.out.println("   " + JsonWriter.compact().stream(json ->
                json.beginObject().field("code", "ok").field("total", 3).endObject()));

        System.out.println("""

                ⑤ Stream 是线程封闭的：只能由创建它的线程用，别的线程一碰就抛
                   IllegalStateException，不会让你拿到一段悄悄写坏的 JSON。""");
        JsonWriter.Stream s = JsonWriter.compact().stream(new StringBuilder());
        s.beginObject();
        Thread other = new Thread(() -> {
            try {
                s.field("来自别的线程", 1);
                System.out.println("   居然没拦住");
            } catch (IllegalStateException e) {
                System.out.println("   " + e.getMessage());
            }
        });
        other.start();
        try {
            other.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        s.endObject();
        s.close();

        System.out.println("\n⑥ JsonWriter 在 top.x0a.json 包里，不依赖 strapper，可以单独拿去用。");
    }

    // =================================================================

    private static void title(String text) {
        System.out.println();
        System.out.println("═".repeat(72));
        System.out.println("  " + text);
        System.out.println("═".repeat(72));
    }

    private Demo() {
    }
}
