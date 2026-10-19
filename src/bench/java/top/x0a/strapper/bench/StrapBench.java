package top.x0a.strapper.bench;

import top.x0a.json.Json;
import top.x0a.strapper.StrapResult;
import top.x0a.strapper.Strapper;

/**
 * 把 JSON 解析放回真实场景里看：一次 {@code strap()} 调用当中，解析占多少、校验占多少。
 *
 * <p>这决定了"换一个更快的 JSON 库"到底值不值 —— 如果解析只占一小部分，换了也带不动整体。
 */
public final class StrapBench {

    private static final String FORMAT = """
            {"enterpriseId":"*int//企业id","username":"*string:32//username",
             "mobile":"*mobile//mobile","idcard":"*idcard//idcard",
             "province":"*string//province","city":"*string//city",
             "countPeople":"int[1,7]//可选countPeople","vehicleModelId":"*int//车系id",
             "orderStartDate":"*datetime//StartDate","orderEndDate":"*datetime//EndDate",
             "baseService":"*bool//基础服务费","insurance":"*bool//insurance"}""";

    private static final String DATA = """
            {"enterpriseId":"1","username":"闫大瑶","mobile":"13800138000",
             "idcard":"110101199001011234","province":"北京市","city":"北京",
             "countPeople":3,"vehicleModelId":1,
             "orderStartDate":"2020-02-14 10:00:00","orderEndDate":"2020-02-18 10:00:00",
             "baseService":true,"insurance":true}""";

    public static void main(String[] args) {
        Strapper strapper = new Strapper();
        if (!strapper.strap(FORMAT, DATA).ok()) {
            throw new IllegalStateException("基准数据本身就没通过校验");
        }

        double parseBoth = measure(() ->
                Json.parse(FORMAT).hashCode() + Json.parse(DATA).hashCode());
        double fullStrap = measure(() -> {
            StrapResult r = strapper.strap(FORMAT, DATA);
            return r.ok() ? 1 : 0;
        });

        double nanosParse = 1e9 / parseBoth;
        double nanosStrap = 1e9 / fullStrap;

        System.out.println();
        System.out.println("一次 strap(format, json) 做了什么（格式 " + FORMAT.length()
                + " 字符 + 数据 " + DATA.length() + " 字符，12 个字段）");
        System.out.println("-".repeat(64));
        System.out.printf("  只解析这两段 JSON      %,12.0f 次/秒   %7.2f µs%n",
                parseBoth, nanosParse / 1000);
        System.out.printf("  完整 strap（解析+校验） %,12.0f 次/秒   %7.2f µs%n",
                fullStrap, nanosStrap / 1000);
        System.out.println("-".repeat(64));
        System.out.printf("  JSON 解析占整体        %6.1f%%%n", nanosParse / nanosStrap * 100);
        System.out.printf("  格式自检 + 正则校验占   %6.1f%%%n", (1 - nanosParse / nanosStrap) * 100);
    }

    private interface Work {
        long run();
    }

    private static double measure(Work work) {
        long sink = 0;
        long warmupEnd = System.nanoTime() + 2_000_000_000L;
        while (System.nanoTime() < warmupEnd) {
            sink += work.run();
        }
        double best = 0;
        for (int round = 0; round < 7; round++) {
            long start = System.nanoTime();
            long end = start + 500_000_000L;
            long count = 0;
            long now;
            do {
                sink += work.run();
                count++;
                now = System.nanoTime();
            } while (now < end);
            best = Math.max(best, count * 1_000_000_000.0 / (now - start));
        }
        if (sink == Long.MIN_VALUE) {
            System.out.println(sink);
        }
        return best;
    }

    private StrapBench() {
    }
}
