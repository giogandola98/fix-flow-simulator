package com.fixflow.engine.variable;

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
            new UuidPlugin(),
            new SeqPlugin(sequences),
            new EnvPlugin(),
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
            Matcher m = P.matcher(e);
            if (!m.matches()) throw new IllegalArgumentException("Bad now:offset expression: " + e);
            long amount = Long.parseLong(m.group(2));
            ChronoUnit cu = switch (m.group(3)) {
                case "s" -> ChronoUnit.SECONDS;
                case "m" -> ChronoUnit.MINUTES;
                case "h" -> ChronoUnit.HOURS;
                case "d" -> ChronoUnit.DAYS;
                default  -> throw new IllegalArgumentException("Bad unit: " + m.group(3));
            };
            Instant now = Instant.now();
            return (m.group(1).equals("+") ? now.plus(amount, cu) : now.minus(amount, cu)).toString();
        }
    }

    static final class NowDateOffsetPlugin implements VariableResolverPlugin {
        private static final Pattern P = Pattern.compile("^nowdate:offset:([+\\-])(\\d+)([smhd])$");
        private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("yyyyMMdd-HH:mm:ss").withZone(ZoneOffset.UTC);
        public boolean supports(String e) { return P.matcher(e).matches(); }
        public String resolve(String e, ExecutionContext c) {
            Matcher m = P.matcher(e);
            if (!m.matches()) throw new IllegalArgumentException("Bad nowdate:offset expression: " + e);
            long amount = Long.parseLong(m.group(2));
            ChronoUnit cu = switch (m.group(3)) {
                case "s" -> ChronoUnit.SECONDS;
                case "m" -> ChronoUnit.MINUTES;
                case "h" -> ChronoUnit.HOURS;
                case "d" -> ChronoUnit.DAYS;
                default  -> throw new IllegalArgumentException("Bad unit: " + m.group(3));
            };
            Instant now = Instant.now();
            Instant result = m.group(1).equals("+") ? now.plus(amount, cu) : now.minus(amount, cu);
            return FMT.format(result);
        }
    }

    static final class NowDatePlugin implements VariableResolverPlugin {
        public boolean supports(String e) { return e.equals("nowdate"); }
        public String resolve(String e, ExecutionContext c) {
            return LocalDate.now(ZoneOffset.UTC).format(DateTimeFormatter.BASIC_ISO_DATE);
        }
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
}
