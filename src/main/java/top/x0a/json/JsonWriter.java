package top.x0a.json;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 把 key-value 结构写成 JSON 串。零第三方依赖，不依赖 {@code top.x0a.strapper}。
 *
 * <p>两种用法：
 *
 * <h2>一、已经有一棵 Map / List 树</h2>
 * <pre>{@code
 * Map<String, Object> body = new LinkedHashMap<>();
 * body.put("code", "ok");
 * body.put("data", List.of(1, 2, 3));
 *
 * String json = JsonWriter.compact().write(body);
 * JsonWriter.pretty().writeTo(out, body);   // out 是任意 Appendable：Writer、StringBuilder……
 * }</pre>
 *
 * <h2>二、流式拼，不必先攒一棵树</h2>
 * <pre>{@code
 * // out 是这次输出专属的 Writer / StringBuilder，不与别的线程共享
 * JsonWriter.compact().stream(out, json -> {
 *     json.beginObject()
 *         .field("code", "ok")
 *         .name("data").beginArray();
 *     for (Order order : orders) {
 *         json.beginObject()
 *             .field("id", order.id())
 *             .field("amount", order.amount())
 *             .endObject();
 *     }
 *     json.endArray().endObject();
 * });
 * }</pre>
 * 适合结果集很大或者边查边写的场景 —— 整个过程不会在内存里出现完整的结果串。
 *
 * <h2>能写哪些类型</h2>
 * <ul>
 *   <li>{@code null}、{@link CharSequence}、{@link Character}、{@link Boolean}、{@link Number}</li>
 *   <li>{@link Map} &rarr; object（key 一律按 {@code String.valueOf} 取字符串）</li>
 *   <li>{@link Iterable}、Java 数组（含基本类型数组）&rarr; array</li>
 *   <li>{@link Enum} &rarr; 它的 {@code name()}</li>
 *   <li>其他类型 &rarr; 退化成它的 {@code toString()}，当字符串写。
 *       想让这种情况直接报错，用 {@link #strict(boolean) strict(true)}</li>
 * </ul>
 *
 * <h2>线程安全</h2>
 * <p>{@code JsonWriter} 本身不可变（字段全 {@code final} 的 {@code final} 类），
 * 配置方法返回新实例，可以做成常量在多线程间共享，不需要任何同步。
 *
 * <p>{@link Stream} 则是<b>线程封闭</b>的：它有写到一半的状态，只能由创建它的线程使用。
 * 这不是靠约定 —— 别的线程一碰就会抛 {@link IllegalStateException}
 * （确实需要跨线程接力时用 {@link Stream#handOff()}）。
 *
 * <p>还有一点容易漏：<b>目标 {@code Appendable} 也必须是这个线程独占的</b>。
 * 两个线程各拿各的 {@code Stream} 往同一个 {@code StringBuilder} 里写，一样会写坏 ——
 * {@code StringBuilder} 自己就不是线程安全的，内容会交错甚至丢字符。
 * 多个线程要往同一个目标汇聚时，正确做法是各自在本地拼完整，再整篇一次性追加：
 * <pre>{@code
 * String doc = JsonWriter.compact().write(payload);      // 本地拼完，互不干扰
 * synchronized (sharedOut) { sharedOut.append(doc); }    // 加锁的粒度是一整篇文档
 * }</pre>
 * 加锁粒度必须是整篇文档，不能是单个方法 —— JSON 的原子单位是文档，不是一次写入。
 */
public final class JsonWriter {

    private static final JsonWriter COMPACT = new JsonWriter(0, false, false, false);

    /** 缩进宽度，0 表示不换行不缩进。 */
    private final int indent;
    private final boolean escapeNonAscii;
    private final boolean skipNulls;
    private final boolean strict;

    private JsonWriter(int indent, boolean escapeNonAscii, boolean skipNulls, boolean strict) {
        this.indent = indent;
        this.escapeNonAscii = escapeNonAscii;
        this.skipNulls = skipNulls;
        this.strict = strict;
    }

    // =================================================================
    // 配置
    // =================================================================

    /** 紧凑输出，不换行。接口响应用这个。 */
    public static JsonWriter compact() {
        return COMPACT;
    }

    /** 缩进 2 空格的输出，给人看的。 */
    public static JsonWriter pretty() {
        return pretty(2);
    }

    /** 指定缩进宽度；传 0 等同于 {@link #compact()}。 */
    public static JsonWriter pretty(int spaces) {
        if (spaces < 0) {
            throw new IllegalArgumentException("缩进宽度不能为负: " + spaces);
        }
        return spaces == 0 ? COMPACT : new JsonWriter(spaces, false, false, false);
    }

    /**
     * 把非 ASCII 字符写成 {@code \\uXXXX}。
     *
     * <p>输出全是 ASCII，就不会再因为响应头里的 charset 写错而乱码；代价是中文内容体积翻几倍。
     */
    public JsonWriter escapeNonAscii(boolean on) {
        return new JsonWriter(indent, on, skipNulls, strict);
    }

    /** 对象里值为 {@code null} 的 key 直接不写出来。数组里的 null 不受影响。 */
    public JsonWriter skipNulls(boolean on) {
        return new JsonWriter(indent, escapeNonAscii, on, strict);
    }

    /**
     * 遇到不认识的类型时直接抛 {@link IllegalArgumentException}，而不是退化成 {@code toString()}。
     *
     * <p>输出的是接口响应时建议打开：一个本该是数字的字段悄悄变成了对象的 toString，
     * 比当场报错难查得多。
     */
    public JsonWriter strict(boolean on) {
        return new JsonWriter(indent, escapeNonAscii, skipNulls, on);
    }

    // =================================================================
    // 写出整棵树
    // =================================================================

    /** 写成字符串。 */
    public String write(Object value) {
        StringBuilder sb = new StringBuilder();
        try {
            writeValue(sb, value, 0);
        } catch (IOException e) {
            throw new UncheckedIOException("写进 StringBuilder 不该发生 IO 错误", e);
        }
        return sb.toString();
    }

    /** 直接写进 {@link Appendable}（{@code Writer}、{@code StringBuilder} 都可以），不攒中间串。 */
    public void writeTo(Appendable out, Object value) throws IOException {
        writeValue(out, value, 0);
    }

    private void writeValue(Appendable out, Object value, int depth) throws IOException {
        switch (value) {
            case null -> out.append("null");
            case CharSequence s -> writeString(out, s);
            case Character c -> writeString(out, String.valueOf(c));
            case Boolean b -> out.append(b ? "true" : "false");
            case Number n -> out.append(number(n));
            case Map<?, ?> map -> writeObject(out, map, depth);
            case Iterable<?> items -> writeArray(out, items, depth);
            case Enum<?> e -> writeString(out, e.name());
            default -> {
                if (value.getClass().isArray()) {
                    writeArray(out, arrayToList(value), depth);
                } else if (strict) {
                    throw new IllegalArgumentException(
                            "不知道怎么把 " + value.getClass().getName() + " 写成 JSON: " + value);
                } else {
                    writeString(out, String.valueOf(value));
                }
            }
        }
    }

    private void writeObject(Appendable out, Map<?, ?> map, int depth) throws IOException {
        boolean first = true;
        out.append('{');
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (skipNulls && entry.getValue() == null) {
                continue;
            }
            if (!first) {
                out.append(',');
            }
            first = false;
            newline(out, depth + 1);
            writeString(out, String.valueOf(entry.getKey()));
            out.append(':');
            if (indent > 0) {
                out.append(' ');
            }
            writeValue(out, entry.getValue(), depth + 1);
        }
        if (!first) {
            newline(out, depth);
        }
        out.append('}');
    }

    private void writeArray(Appendable out, Iterable<?> items, int depth) throws IOException {
        boolean first = true;
        out.append('[');
        for (Object item : items) {
            if (!first) {
                out.append(',');
            }
            first = false;
            newline(out, depth + 1);
            writeValue(out, item, depth + 1);
        }
        if (!first) {
            newline(out, depth);
        }
        out.append(']');
    }

    private static List<Object> arrayToList(Object array) {
        int length = Array.getLength(array);
        List<Object> list = new ArrayList<>(length);
        for (int i = 0; i < length; i++) {
            list.add(Array.get(array, i));
        }
        return list;
    }

    private void newline(Appendable out, int depth) throws IOException {
        if (indent == 0) {
            return;
        }
        out.append('\n');
        for (int i = 0; i < indent * depth; i++) {
            out.append(' ');
        }
    }

    // =================================================================
    // 纯量
    // =================================================================

    /**
     * 数字的 JSON 形式。
     *
     * <p>{@code double} / {@code float} 写成不带指数的形式，避免 {@code 1.0E10} 这种
     * 对前端不友好的输出；NaN 和 Infinity 不是合法 JSON，写成 {@code null}。
     */
    public static String number(Number n) {
        if (n instanceof Double d) {
            return number(d.doubleValue());
        }
        if (n instanceof Float f) {
            return number(f.doubleValue());
        }
        return n.toString();
    }

    /** {@code double} 的 JSON 形式，不带指数。 */
    public static String number(double d) {
        if (Double.isNaN(d) || Double.isInfinite(d)) {
            return "null";
        }
        return BigDecimal.valueOf(d).stripTrailingZeros().toPlainString();
    }

    private void writeString(Appendable out, CharSequence s) throws IOException {
        out.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    // < 0x20 是 JSON 规定必须转义的；0x7F 和非 ASCII 按配置决定
                    if (c < 0x20 || (escapeNonAscii && c > 0x7E)) {
                        out.append(unicodeEscape(c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
    }

    private static String unicodeEscape(char c) {
        return "\\u" + "0123456789abcdef".charAt((c >> 12) & 0xF)
                + "0123456789abcdef".charAt((c >> 8) & 0xF)
                + "0123456789abcdef".charAt((c >> 4) & 0xF)
                + "0123456789abcdef".charAt(c & 0xF);
    }

    // =================================================================
    // 流式写出
    // =================================================================

    /**
     * 边拼边往 {@code out} 里写，写完自动收尾。
     *
     * <p>推荐用这个而不是 {@link #stream(Appendable)}：{@code Stream} 出不了这个 lambda，
     * 也就没机会被别的线程拿到，线程封闭是结构上保证的。
     *
     * @param out  这次输出专属的目标，不能与别的线程共享
     * @param body 往 {@code Stream} 里拼内容；返回时括号必须都闭合了
     */
    public void stream(Appendable out, java.util.function.Consumer<Stream> body) {
        try (Stream stream = new Stream(this, out)) {
            body.accept(stream);
        }
    }

    /**
     * 拼进一个自己管的缓冲区，返回结果串。连目标 {@code Appendable} 都不用操心。
     *
     * @param body 往 {@code Stream} 里拼内容；返回时括号必须都闭合了
     */
    public String stream(java.util.function.Consumer<Stream> body) {
        StringBuilder sb = new StringBuilder();
        stream(sb, body);
        return sb.toString();
    }

    /**
     * 开一个流式写出器，边拼边往 {@code out} 里写。
     *
     * <p>用完要 {@link Stream#close()}（配合 try-with-resources），close 时会检查括号是否都闭合了。
     * 需要把写出过程拆散到几个方法里时用它，否则优先用
     * {@link #stream(Appendable, java.util.function.Consumer)} —— 那个形式不会让 {@code Stream} 泄漏出去。
     *
     * @param out 这次输出专属的目标，不能与别的线程共享
     */
    public Stream stream(Appendable out) {
        return new Stream(this, out);
    }

    /**
     * 流式写出器。不必先攒一棵 Map / List 树，适合结果集很大或者边查边写的场景。
     *
     * <p><b>线程封闭</b>：一个实例只能由创建它的线程使用。别的线程一碰就抛
     * {@link IllegalStateException}，不会让你拿到一段悄悄写坏的 JSON。
     * 确实需要换线程接着写时，先由当前线程调 {@link #handOff()}。
     *
     * <p>注意目标 {@code Appendable} 也必须是这个线程独占的，原因见 {@link JsonWriter} 的线程安全一节。
     */
    public static final class Stream implements AutoCloseable {

        private final JsonWriter writer;
        private final Appendable out;
        /** 当前归属线程；{@code null} 表示已交接、等待下一个线程认领。 */
        private final java.util.concurrent.atomic.AtomicReference<Thread> owner;
        /** 每一层是不是 object；true 为 object，false 为 array。 */
        private final List<Boolean> stack = new ArrayList<>();
        /** 当前层是不是还一个元素都没写。 */
        private final List<Boolean> empty = new ArrayList<>();
        /** object 里是不是刚写完 name、正等着 value。 */
        private boolean expectingValue;
        private boolean rootWritten;
        private boolean closed;

        private Stream(JsonWriter writer, Appendable out) {
            this.writer = writer;
            this.out = out;
            this.owner = new java.util.concurrent.atomic.AtomicReference<>(Thread.currentThread());
        }

        /**
         * 交出归属，让另一个线程接着写。
         *
         * <p>必须由当前归属线程调用。交接是通过一次 volatile 写完成的，所以接手的线程
         * 一定能看到前面写进去的全部内容 —— 这是真正的 happens-before 保证，不只是放行检查。
         *
         * <p>目标 {@code Appendable} 的可见性得你自己保证：它也要跟着一起交接，
         * 不能两个线程同时拿着它。
         */
        public Stream handOff() {
            checkOwner();
            owner.set(null);
            return this;
        }

        /** 当前归属线程；已交接、还没人认领时返回 {@code null}。 */
        public Thread owner() {
            return owner.get();
        }

        private void checkOwner() {
            Thread current = Thread.currentThread();
            Thread holder = owner.get();
            if (holder == current) {
                return;
            }
            if (holder == null && owner.compareAndSet(null, current)) {
                return;
            }
            throw new IllegalStateException("JsonWriter.Stream 是线程封闭的：它属于线程 "
                    + (holder == null ? "(交接中，已被别的线程抢先认领)" : holder.getName())
                    + "，当前线程是 " + current.getName()
                    + "。一次输出用一个 Stream；确实要换线程接着写，先由原线程调用 handOff()。");
        }

        /** 开始一个对象。 */
        public Stream beginObject() {
            return begin('{', true);
        }

        /** 结束当前对象。 */
        public Stream endObject() {
            return end('}', true);
        }

        /** 开始一个数组。 */
        public Stream beginArray() {
            return begin('[', false);
        }

        /** 结束当前数组。 */
        public Stream endArray() {
            return end(']', false);
        }

        /** 写一个 key，之后必须紧跟一个值。 */
        public Stream name(String name) {
            checkOwner();
            if (stack.isEmpty() || !stack.get(stack.size() - 1)) {
                throw new IllegalStateException("name() 只能用在对象里面");
            }
            if (expectingValue) {
                throw new IllegalStateException("上一个 name 还没写值: " + name);
            }
            io(() -> {
                separator();
                writer.writeString(out, name);
                out.append(':');
                if (writer.indent > 0) {
                    out.append(' ');
                }
            });
            expectingValue = true;
            return this;
        }

        /** 写一个值。对象、数组、纯量都行，会按 {@link JsonWriter} 的规则整棵写出去。 */
        public Stream value(Object value) {
            checkOwner();
            if (!stack.isEmpty() && stack.get(stack.size() - 1) && !expectingValue) {
                throw new IllegalStateException("对象里要先 name() 再写值");
            }
            if (stack.isEmpty() && rootWritten) {
                throw new IllegalStateException("顶层只能有一个值");
            }
            if (writer.skipNulls && value == null && expectingValue) {
                throw new IllegalStateException(
                        "开了 skipNulls 就不要先 name() 再写 null，直接跳过这个字段");
            }
            io(() -> {
                if (!expectingValue) {
                    separator();
                }
                writer.writeValue(out, value, stack.size());
            });
            afterValue();
            return this;
        }

        /** {@link #name(String)} + {@link #value(Object)}，对象里最常用的写法。 */
        public Stream field(String name, Object value) {
            if (writer.skipNulls && value == null) {
                checkOwner();
                return this;
            }
            return name(name).value(value);
        }

        /** 写一个 JSON null。 */
        public Stream nullValue() {
            return value(null);
        }

        /**
         * 收尾。检查括号是否都闭合了；{@code out} 如果是 {@link java.io.Closeable} 本身，
         * 这里不会去关它 —— 谁开的谁负责关。
         */
        @Override
        public void close() {
            if (closed) {
                return;
            }
            checkOwner();
            closed = true;
            if (!stack.isEmpty()) {
                throw new IllegalStateException("还有 " + stack.size() + " 层括号没闭合");
            }
        }

        // ---- 内部 ----

        private Stream begin(char bracket, boolean object) {
            checkOwner();
            if (!stack.isEmpty() && stack.get(stack.size() - 1) && !expectingValue) {
                throw new IllegalStateException("对象里要先 name() 再开新的一层");
            }
            if (stack.isEmpty() && rootWritten) {
                throw new IllegalStateException("顶层只能有一个值");
            }
            io(() -> {
                if (!expectingValue) {
                    separator();
                }
                out.append(bracket);
            });
            expectingValue = false;
            stack.add(object);
            empty.add(true);
            return this;
        }

        private Stream end(char bracket, boolean object) {
            checkOwner();
            if (stack.isEmpty()) {
                throw new IllegalStateException("没有可以闭合的括号");
            }
            if (stack.get(stack.size() - 1) != object) {
                throw new IllegalStateException(
                        "括号类型对不上：当前在" + (stack.get(stack.size() - 1) ? "对象" : "数组") + "里");
            }
            if (expectingValue) {
                throw new IllegalStateException("最后一个 name 还没写值");
            }
            boolean wasEmpty = empty.remove(empty.size() - 1);
            stack.remove(stack.size() - 1);
            io(() -> {
                if (!wasEmpty) {
                    writer.newline(out, stack.size());
                }
                out.append(bracket);
            });
            afterValue();
            return this;
        }

        /** 写下一个元素之前：补逗号、补换行缩进。 */
        private void separator() throws IOException {
            if (stack.isEmpty()) {
                return;
            }
            int top = empty.size() - 1;
            if (!empty.get(top)) {
                out.append(',');
            }
            empty.set(top, false);
            writer.newline(out, stack.size());
        }

        private void afterValue() {
            if (expectingValue) {
                expectingValue = false;
                // name() 已经在 separator 里把这一层标成非空了
            }
            if (stack.isEmpty()) {
                rootWritten = true;
            }
        }

        private interface IoAction {
            void run() throws IOException;
        }

        private void io(IoAction action) {
            if (closed) {
                throw new IllegalStateException("已经 close 了");
            }
            try {
                action.run();
            } catch (IOException e) {
                throw new UncheckedIOException("写 JSON 时 IO 出错", e);
            }
        }
    }
}
