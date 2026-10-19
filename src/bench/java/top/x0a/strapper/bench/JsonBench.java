package top.x0a.strapper.bench;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.alibaba.fastjson2.JSON;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;

import top.x0a.json.Json;

/**
 * 把内置的 {@link Json} 和 Jackson / Gson / fastjson2 做一次解析吞吐对比。
 *
 * <p>只进 bench 源集，第三方库不会进 strapper.jar。
 *
 * <p>用法：{@code java -cp <bench-cp> top.x0a.strapper.bench.JsonBench [strapper|jackson|gson|fastjson2]}
 * 不带参数时四个都跑（同一个 JVM，JIT 会互相影响）；带参数时只跑一个，
 * 配合 {@code tools/bench.sh} 可以让每个库各占一个干净的 JVM。
 *
 * <p>这不是 JMH：没有 fork、没有 GC 隔离、没有统计显著性检验。
 * 它测的是"同一台机器、同一个 JDK、同一份输入下的大致量级"，足够回答
 * "自己写的这个解析器够不够用"，但不适合拿去做库之间的正式排名。
 */
public final class JsonBench {

    /** 所有解析器都产出 Map/List 的通用树，保证比的是同一件事。 */
    private interface Parser {
        Object parse(String text);
    }

    private record Payload(String name, String json) {
        int bytes() {
            return json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        }
    }

    public static void main(String[] args) {
        List<Payload> payloads = List.of(
                new Payload("典型请求体", typicalRequest()),
                new Payload("格式描述", formatDescription()),
                new Payload("中等列表", listOf(200)),
                new Payload("大文档", listOf(20_000)));

        Map<String, Parser> all = new java.util.LinkedHashMap<>();
        all.put("strapper", Json::parse);
        ObjectMapper mapper = new ObjectMapper();
        all.put("jackson", text -> {
            try {
                return mapper.readValue(text, Object.class);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        Gson gson = new Gson();
        all.put("gson", text -> gson.fromJson(text, Object.class));
        all.put("fastjson2", JSON::parse);

        List<String> targets = args.length > 0 ? List.of(args) : new ArrayList<>(all.keySet());

        System.out.printf("JDK %s / %s / %s%n",
                System.getProperty("java.version"),
                System.getProperty("os.arch"),
                System.getProperty("java.vm.name"));
        System.out.println();

        for (Payload payload : payloads) {
            System.out.printf("── %s（%,d 字节）%n", payload.name(), payload.bytes());
            double baseline = -1;
            for (String name : targets) {
                Parser parser = all.get(name);
                if (parser == null) {
                    throw new IllegalArgumentException("不认识的解析器: " + name);
                }
                double opsPerSec = measure(parser, payload.json());
                if (baseline < 0) {
                    baseline = opsPerSec;
                }
                double mbPerSec = opsPerSec * payload.bytes() / 1024 / 1024;
                System.out.printf("   %-11s %,12.0f 次/秒   %,8.1f MB/s   %s%n",
                        name, opsPerSec, mbPerSec, ratio(opsPerSec, baseline));
                // 机器可读的一行，便于把多个 JVM 的结果汇总成一张表
                System.out.printf("#DATA\t%s\t%s\t%.1f\t%d%n",
                        payload.name(), name, opsPerSec, payload.bytes());
            }
            System.out.println();
        }
    }

    private static String ratio(double value, double baseline) {
        if (baseline <= 0) {
            return "";
        }
        double r = value / baseline;
        if (Math.abs(r - 1) < 0.005) {
            return "基准";
        }
        return r >= 1 ? String.format("%.2fx", r) : String.format("%.2fx（慢 %.0f%%）", r, (1 - r) * 100);
    }

    // =================================================================
    // 测量：预热到 JIT 稳定，再取多轮里的最好成绩
    // =================================================================

    private static final long WARMUP_NANOS = 2_000_000_000L;
    private static final long ROUND_NANOS = 500_000_000L;
    private static final int ROUNDS = 7;

    private static double measure(Parser parser, String json) {
        long sink = 0;
        // 预热：跑够 2 秒，让 JIT 编译到位
        long warmupEnd = System.nanoTime() + WARMUP_NANOS;
        while (System.nanoTime() < warmupEnd) {
            sink += parser.parse(json).hashCode();
        }

        double best = 0;
        for (int round = 0; round < ROUNDS; round++) {
            long start = System.nanoTime();
            long end = start + ROUND_NANOS;
            long count = 0;
            long now;
            do {
                sink += parser.parse(json).hashCode();
                count++;
                now = System.nanoTime();
            } while (now < end);
            double opsPerSec = count * 1_000_000_000.0 / (now - start);
            best = Math.max(best, opsPerSec);
        }

        if (sink == Long.MIN_VALUE) {
            // 防止 JIT 把解析结果当死代码消掉，这个分支实际走不到
            System.out.println(sink);
        }
        return best;
    }

    // =================================================================
    // 输入
    // =================================================================

    /** 这个库最常见的输入：一个几百字节的接口请求体。 */
    private static String typicalRequest() {
        return """
                {"enterpriseId":"1","username":"闫大瑶","mobile":"13800138000",
                 "idcard":"110101199001011234","province":"北京市","city":"北京",
                 "countPeople":3,"babySeats":1,"vehicleModelId":1,"vehicleId":2,
                 "rentRetailId":1,"returnRetailId":1,"orderStartDate":"2020-02-14 10:00:00",
                 "orderEndDate":"2020-02-18 10:00:00","baseService":true,"carService":true,
                 "deliveryService":false,"insurance":true,"vip":false}""";
    }

    /** 另一个常见输入：格式描述本身也是 JSON。 */
    private static String formatDescription() {
        return """
                {"enterpriseId":"*int//企业id","username":"*string:32//username",
                 "mobile":"*mobile//mobile","idcard":"*idcard//idcard",
                 "province":"*string//province","city":"*string//city",
                 "countPeople":"int[1,7]//可选countPeople","vehicleModelId":"*int//车系id",
                 "orderStartDate":"*datetime//StartDate","orderEndDate":"*datetime//EndDate",
                 "baseService":"*bool//基础服务费","insurance":"*bool//insurance",
                 "objArray":[{"customerId":"*int//obj用户id","customerName":"*string//obj用户名",
                 "customerIdcard":"*idcard//身份证号","customerMobile":"*mobile//手机号"}]}""";
    }

    private static String listOf(int n) {
        StringBuilder sb = new StringBuilder(n * 130);
        sb.append("{\"code\":\"ok\",\"data\":{\"total\":").append(n).append(",\"list\":[");
        for (int i = 0; i < n; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append("{\"modelId\":").append(i)
              .append(",\"modelName\":\"车型-").append(i)
              .append("\",\"price\":").append(199.5 + i)
              .append(",\"available\":").append(i % 3 != 0)
              .append(",\"plateNumber\":\"京A").append(10000 + i)
              .append("\",\"updatedAt\":\"2020-02-14 10:00:00\",\"note\":null}");
        }
        sb.append("]}}");
        return sb.toString();
    }

    private JsonBench() {
    }
}
