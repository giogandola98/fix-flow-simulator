package com.fixflow.engine.variable;

import com.fixflow.core.domain.execution.FIXMessageData;
import com.fixflow.engine.execution.ExecutionContext;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class VariableResolver {

    private static final Pattern EXPR = Pattern.compile("\\{\\{([^}]+)\\}\\}");

    private final List<VariableResolverPlugin> plugins;
    private final ConcurrentHashMap<String, AtomicLong> sequences = new ConcurrentHashMap<>();

    public VariableResolver() {
        this.plugins = List.of(
            new NowPlugin(),
            new NowOffsetPlugin(),
            new NowDateOffsetPlugin(),
            new NowDatePlugin(),
            new FixTimePlugin(),
            new FixTimeOffsetPlugin(),
            new UuidPlugin(),
            new SeqPlugin(sequences),
            new EnvPlugin(),
            new GroupFieldOffsetPlugin(),
            new GroupFieldPlugin(),
            new DateOffsetPlugin(),
            new NodeFieldPlugin(),
            new VarPlugin()
        );
    }

    public String resolveAll(String template, ExecutionContext ctx) {
        if (template == null) return null;
        Matcher m = EXPR.matcher(template);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String expr = m.group(1);
            String value = dispatch(expr, ctx);
            m.appendReplacement(out, Matcher.quoteReplacement(value == null ? "" : value));
        }
        m.appendTail(out);
        return out.toString();
    }

    private String dispatch(String expression, ExecutionContext ctx) {
        for (VariableResolverPlugin p : plugins) {
            if (p.supports(expression)) {
                return p.resolve(expression, ctx);
            }
        }
        throw new IllegalArgumentException("No plugin handles expression: " + expression);
    }

    static final class NowPlugin implements VariableResolverPlugin {
        public boolean supports(String e) { return e.equals("now"); }
        public String resolve(String e, ExecutionContext c) { return Instant.now().toString(); }
    }

    static final class NowOffsetPlugin implements VariableResolverPlugin {
        private static final Pattern P = Pattern.compile("^now:offset:([+\\-])(\\d+)([smhd])$");
        public boolean supports(String e) { return P.matcher(e).matches(); }
        public String resolve(String e, ExecutionContext c) {
            return applyOffset(P, e, "now:offset", Instant.now()).toString();
        }
    }

    /** {@code {{nowdate:offset:+1d}}} — UTC date with offset, {@code yyyyMMdd} (same format as {@code {{nowdate}}}). */
    static final class NowDateOffsetPlugin implements VariableResolverPlugin {
        private static final Pattern P = Pattern.compile("^nowdate:offset:([+\\-])(\\d+)([smhd])$");
        public boolean supports(String e) { return P.matcher(e).matches(); }
        public String resolve(String e, ExecutionContext c) {
            return FIX_DATE.format(applyOffset(P, e, "nowdate:offset", Instant.now()));
        }
    }

    static final class NowDatePlugin implements VariableResolverPlugin {
        public boolean supports(String e) { return e.equals("nowdate"); }
        public String resolve(String e, ExecutionContext c) {
            return LocalDate.now(ZoneOffset.UTC).format(DateTimeFormatter.BASIC_ISO_DATE);
        }
    }

    /** {@code {{fixtime}}} — current time as FIX UTCTimestamp {@code yyyyMMdd-HH:mm:ss.SSS}. */
    static final class FixTimePlugin implements VariableResolverPlugin {
        public boolean supports(String e) { return e.equals("fixtime"); }
        public String resolve(String e, ExecutionContext c) { return FIX_TIMESTAMP.format(Instant.now()); }
    }

    /** {@code {{fixtime:offset:+5m}}} — FIX UTCTimestamp {@code yyyyMMdd-HH:mm:ss.SSS} with offset. */
    static final class FixTimeOffsetPlugin implements VariableResolverPlugin {
        private static final Pattern P = Pattern.compile("^fixtime:offset:([+\\-])(\\d+)([smhd])$");
        public boolean supports(String e) { return P.matcher(e).matches(); }
        public String resolve(String e, ExecutionContext c) {
            return FIX_TIMESTAMP.format(applyOffset(P, e, "fixtime:offset", Instant.now()));
        }
    }

    static final DateTimeFormatter FIX_DATE =
        DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);
    static final DateTimeFormatter FIX_TIMESTAMP =
        DateTimeFormatter.ofPattern("yyyyMMdd-HH:mm:ss.SSS").withZone(ZoneOffset.UTC);

    /** Applies a {@code [+-]N[smhd]} offset captured by {@code p} (groups 1-3) to {@code base}. */
    private static Instant applyOffset(Pattern p, String e, String label, Instant base) {
        Matcher m = p.matcher(e);
        if (!m.matches()) throw new IllegalArgumentException("Bad " + label + " expression: " + e);
        long amount = Long.parseLong(m.group(2));
        ChronoUnit cu = switch (m.group(3)) {
            case "s" -> ChronoUnit.SECONDS;
            case "m" -> ChronoUnit.MINUTES;
            case "h" -> ChronoUnit.HOURS;
            case "d" -> ChronoUnit.DAYS;
            default  -> throw new IllegalArgumentException("Bad unit: " + m.group(3));
        };
        return m.group(1).equals("+") ? base.plus(amount, cu) : base.minus(amount, cu);
    }

    static final class UuidPlugin implements VariableResolverPlugin {
        public boolean supports(String e) { return e.equals("uuid"); }
        public String resolve(String e, ExecutionContext c) { return UUID.randomUUID().toString(); }
    }

    static final class SeqPlugin implements VariableResolverPlugin {
        private final ConcurrentHashMap<String, AtomicLong> sequences;
        SeqPlugin(ConcurrentHashMap<String, AtomicLong> s) { this.sequences = s; }
        public boolean supports(String e) { return e.startsWith("seq:"); }
        public String resolve(String e, ExecutionContext c) {
            String name = e.substring("seq:".length());
            return Long.toString(sequences.computeIfAbsent(name, k -> new AtomicLong(0)).incrementAndGet());
        }
    }

    static final class EnvPlugin implements VariableResolverPlugin {
        public boolean supports(String e) { return e.startsWith("env:"); }
        public String resolve(String e, ExecutionContext c) {
            String var = e.substring("env:".length());
            String value = System.getenv(var);
            return value == null ? "" : value;
        }
    }

    static final class DateOffsetPlugin implements VariableResolverPlugin {
        private static final Pattern P = Pattern.compile(
            "^node:([^:]+):tag(\\d+):offset:([+\\-])(\\d+)([smhd])$"
        );
        public boolean supports(String e) { return P.matcher(e).matches(); }
        public String resolve(String e, ExecutionContext c) {
            Matcher m = P.matcher(e);
            if (!m.matches()) throw new IllegalArgumentException("Bad date offset: " + e);
            String nodeId = m.group(1);
            int tag = Integer.parseInt(m.group(2));
            String sign = m.group(3);
            long amount = Long.parseLong(m.group(4));
            String unit = m.group(5);
            Map<Integer, String> fields = c.getNodeMessage(nodeId);
            if (fields == null) throw new IllegalStateException("No stored message for node: " + nodeId);
            String raw = fields.get(tag);
            if (raw == null) throw new IllegalStateException("No tag " + tag + " on node " + nodeId);
            Instant base = Instant.parse(raw);
            ChronoUnit cu = switch (unit) {
                case "s" -> ChronoUnit.SECONDS;
                case "m" -> ChronoUnit.MINUTES;
                case "h" -> ChronoUnit.HOURS;
                case "d" -> ChronoUnit.DAYS;
                default -> throw new IllegalArgumentException("Bad unit: " + unit);
            };
            return (sign.equals("+") ? base.plus(amount, cu) : base.minus(amount, cu)).toString();
        }
    }

    static final class NodeFieldPlugin implements VariableResolverPlugin {
        private static final Pattern P = Pattern.compile("^node:([^:]+):tag(\\d+)$");
        public boolean supports(String e) { return P.matcher(e).matches(); }
        public String resolve(String e, ExecutionContext c) {
            Matcher m = P.matcher(e);
            if (!m.matches()) throw new IllegalArgumentException("Bad node ref: " + e);
            String nodeId = m.group(1);
            int tag = Integer.parseInt(m.group(2));
            Map<Integer, String> fields = c.getNodeMessage(nodeId);
            if (fields == null) throw new IllegalStateException("No stored message for node: " + nodeId);
            String v = fields.get(tag);
            return v == null ? "" : v;
        }
    }

    static final class VarPlugin implements VariableResolverPlugin {
        public boolean supports(String e) { return e.startsWith("var:"); }
        public String resolve(String e, ExecutionContext c) {
            String key = e.substring("var:".length());
            String v = c.getVariable(key);
            return v == null ? "" : v;
        }
    }

    private static FIXMessageData requireMessage(ExecutionContext c, String nodeId) {
        FIXMessageData data = c.getNodeMessageData(nodeId);
        if (data == null) throw new IllegalStateException("No stored message for node: " + nodeId);
        return data;
    }

    private static String requireGroupValue(ExecutionContext c, String nodeId,
                                            int counterTag, int index, int tag) {
        FIXMessageData data = requireMessage(c, nodeId);
        if (index < 0 || index >= data.group(counterTag).size()) {
            throw new IllegalStateException(
                "Group " + counterTag + " on node " + nodeId + " has "
                + data.group(counterTag).size() + " entries; index " + index + " is out of range");
        }
        return data.groupValue(counterTag, index, tag).orElse("");
    }

    static final class GroupFieldPlugin implements VariableResolverPlugin {
        private static final Pattern P =
            Pattern.compile("^node:([^:]+):g(\\d+)\\.(\\d+):tag(\\d+)$");
        public boolean supports(String e) { return P.matcher(e).matches(); }
        public String resolve(String e, ExecutionContext c) {
            Matcher m = P.matcher(e);
            if (!m.matches()) throw new IllegalArgumentException("Bad group ref: " + e);
            return requireGroupValue(c, m.group(1),
                Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)), Integer.parseInt(m.group(4)));
        }
    }

    static final class GroupFieldOffsetPlugin implements VariableResolverPlugin {
        private static final Pattern P =
            Pattern.compile("^node:([^:]+):g(\\d+)\\.(\\d+):tag(\\d+):offset:([+\\-])(\\d+)([smhd])$");
        public boolean supports(String e) { return P.matcher(e).matches(); }
        public String resolve(String e, ExecutionContext c) {
            Matcher m = P.matcher(e);
            if (!m.matches()) throw new IllegalArgumentException("Bad group date offset: " + e);
            String raw = requireGroupValue(c, m.group(1),
                Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)), Integer.parseInt(m.group(4)));
            Instant base = Instant.parse(raw);
            long amount = Long.parseLong(m.group(6));
            ChronoUnit cu = switch (m.group(7)) {
                case "s" -> ChronoUnit.SECONDS;
                case "m" -> ChronoUnit.MINUTES;
                case "h" -> ChronoUnit.HOURS;
                case "d" -> ChronoUnit.DAYS;
                default  -> throw new IllegalArgumentException("Bad unit: " + m.group(7));
            };
            return (m.group(5).equals("+") ? base.plus(amount, cu) : base.minus(amount, cu)).toString();
        }
    }
}
