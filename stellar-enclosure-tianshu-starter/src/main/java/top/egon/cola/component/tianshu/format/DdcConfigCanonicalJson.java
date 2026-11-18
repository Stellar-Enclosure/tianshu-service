package top.egon.cola.component.tianshu.format;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import top.egon.cola.component.tianshu.model.config.DdcConfigBaselineVO;
import top.egon.cola.component.tianshu.model.config.DdcConfigReceiptCommand;
import top.egon.cola.component.tianshu.model.config.DdcConfigScopeVO;
import top.egon.cola.component.tianshu.model.config.DdcConfigSnapshotDTO;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;

/** RFC 8785 serialization and the fixed, credential-free configuration digest combinations. */
public final class DdcConfigCanonicalJson {

    private static final ObjectMapper MAPPER = new ObjectMapper(new JsonFactory()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION))
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private static final int MAX_DEPTH = MAPPER.getFactory().streamReadConstraints().getMaxNestingDepth();
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private DdcConfigCanonicalJson() {
    }

    /** Parses exactly one UTF-8 JSON value, rejecting ambiguous objects and invalid I-JSON data. */
    public static JsonNode parse(byte[] utf8) {
        if (utf8 == null) { throw new IllegalArgumentException("JSON input is required"); }
        try {
            String json = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(utf8)).toString();
            JsonNode value = MAPPER.readTree(json);
            if (value == null || value.isMissingNode()) { throw new IllegalArgumentException("JSON value is required"); }
            // Validate strings and numeric values without changing their JSON types or Unicode data.
            canonicalBytes(value);
            return value;
        } catch (IOException invalid) {
            throw new IllegalArgumentException("Invalid canonical JSON input");
        }
    }

    /** Emits JCS UTF-8: natural UTF-16 property order, unchanged array order, ECMAScript numbers. */
    public static byte[] canonicalBytes(JsonNode value) {
        if (value == null) { throw new IllegalArgumentException("JSON value is required"); }
        StringBuilder output = new StringBuilder();
        append(value, output, new IdentityHashMap<>(), 0);
        return output.toString().getBytes(StandardCharsets.UTF_8);
    }

    public static String contentSha256(DdcConfigSnapshotDTO snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        ObjectNode content = MAPPER.createObjectNode();
        content.put("schemaVersion", snapshot.getSchemaVersion());
        content.set("scope", scope(snapshot.getScope()));
        content.put("releaseRevision", snapshot.getReleaseRevision());
        content.put("enabled", snapshot.getEnabled());
        content.put("restartDigest", snapshot.getRestartDigest());
        content.set("hotValues", values(snapshot.getHotValues()));
        content.put("dependencySnapshotId", snapshot.getDependencySnapshotId());
        return digest(content);
    }

    /** Rule arrays are a set by unique key in the baseline contract, sorted before JCS serialization. */
    public static String restartDigest(DdcConfigBaselineVO baseline) {
        Objects.requireNonNull(baseline, "baseline");
        ObjectNode content = MAPPER.createObjectNode();
        content.set("restartValues", values(baseline.getRestartValues()));
        ArrayNode rules = content.putArray("rules");
        baseline.getRules().stream().filter(rule -> "RESTART".equals(rule.getMode().name()))
                .sorted(Comparator.comparing(DdcConfigBaselineVO.RuleVO::getKey)).forEach(rule -> {
                    ObjectNode item = rules.addObject();
                    item.put("key", rule.getKey());
                    item.put("mode", rule.getMode().name());
                    item.put("valueType", rule.getValueType());
                    item.put("schemaRef", rule.getSchemaRef());
                    item.put("nullable", rule.getNullable());
                    item.put("deletionAllowed", rule.getDeletionAllowed());
                });
        ObjectNode dependencies = content.putObject("startupDependencies");
        baseline.getStartupDependencies().forEach(dependencies::put);
        ObjectNode secrets = content.putObject("secretReferences");
        baseline.getSecretReferences().forEach((key, reference) -> {
            ObjectNode item = secrets.putObject(key);
            item.put("name", reference.getName());
            item.put("version", reference.getVersion());
        });
        return digest(content);
    }

    public static String receiptDigest(DdcConfigReceiptCommand receipt) {
        Objects.requireNonNull(receipt, "receipt");
        ObjectNode content = MAPPER.createObjectNode();
        content.put("schemaVersion", receipt.getSchemaVersion());
        content.put("bootId", receipt.getBootId());
        content.put("releaseRevision", receipt.getReleaseRevision());
        content.put("reportSeq", receipt.getReportSeq());
        content.put("appliedRevision", receipt.getAppliedRevision());
        content.put("contentSha256", receipt.getContentSha256());
        content.put("appliedContentSha256", receipt.getAppliedContentSha256());
        content.put("baselineRestartDigest", receipt.getBaselineRestartDigest());
        content.put("status", receipt.getStatus() == null ? null : receipt.getStatus().getCode());
        content.put("reportedAt", instant(receipt.getReportedAt()));
        content.put("appliedAt", instant(receipt.getAppliedAt()));
        content.put("errorCode", emptyError(receipt.getErrorCode()));
        content.put("errorMessage", emptyError(receipt.getErrorMessage()));
        return digest(content);
    }

    public static String etag(DdcConfigSnapshotDTO snapshot) {
        return "\"cfg-r" + snapshot.getReleaseRevision() + "-" + contentSha256(snapshot).substring("sha256:".length()) + "\"";
    }

    private static ObjectNode scope(DdcConfigScopeVO scope) {
        Objects.requireNonNull(scope, "scope");
        ObjectNode content = MAPPER.createObjectNode();
        content.put("tenantId", scope.getTenantId());
        content.put("bizCode", scope.getBizCode());
        content.put("env", scope.getEnv());
        content.put("appCode", scope.getAppCode());
        content.put("resourceName", scope.getResourceName());
        return content;
    }

    private static ObjectNode values(Map<String, JsonNode> values) {
        ObjectNode content = MAPPER.createObjectNode();
        Objects.requireNonNull(values, "values").forEach((key, value) -> content.set(Objects.requireNonNull(key, "key"), value));
        return content;
    }

    private static String instant(Instant value) { return value == null ? null : value.toString(); }

    private static String emptyError(String value) { return value == null || value.isBlank() ? null : value; }

    private static String digest(JsonNode content) {
        try {
            return "sha256:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonicalBytes(content)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static void append(JsonNode value, StringBuilder output, IdentityHashMap<JsonNode, Boolean> active, int depth) {
        if (depth > MAX_DEPTH) { throw new IllegalArgumentException("JSON nesting exceeds the parser limit"); }
        if (value.isObject() || value.isArray()) {
            if (active.put(value, Boolean.TRUE) != null) { throw new IllegalArgumentException("Cyclic JSON value"); }
            try {
                if (value.isObject()) {
                    output.append('{');
                    ArrayList<String> names = new ArrayList<>();
                    value.fieldNames().forEachRemaining(names::add);
                    names.sort(Comparator.naturalOrder());
                    boolean first = true;
                    for (String name : names) {
                        if (!first) { output.append(','); }
                        appendString(name, output);
                        output.append(':');
                        append(value.get(name), output, active, depth + 1);
                        first = false;
                    }
                    output.append('}');
                } else {
                    output.append('[');
                    boolean first = true;
                    for (JsonNode item : value) {
                        if (!first) { output.append(','); }
                        append(item, output, active, depth + 1);
                        first = false;
                    }
                    output.append(']');
                }
            } finally { active.remove(value); }
        } else if (value.isTextual()) { appendString(value.textValue(), output); }
        else if (value.isNumber()) { output.append(number(value.doubleValue())); }
        else if (value.isBoolean()) { output.append(value.booleanValue()); }
        else if (value.isNull()) { output.append("null"); }
        else { throw new IllegalArgumentException("Unsupported JSON value"); }
    }

    private static void appendString(String value, StringBuilder output) {
        output.append('"');
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"' -> output.append("\\\"");
                case '\\' -> output.append("\\\\");
                case '\b' -> output.append("\\b");
                case '\t' -> output.append("\\t");
                case '\n' -> output.append("\\n");
                case '\f' -> output.append("\\f");
                case '\r' -> output.append("\\r");
                default -> {
                    if (character < 0x20) {
                        output.append("\\u00").append(HEX[character >>> 4]).append(HEX[character & 15]);
                    } else if (Character.isHighSurrogate(character)) {
                        if (index + 1 >= value.length() || !Character.isLowSurrogate(value.charAt(index + 1))) {
                            throw new IllegalArgumentException("Invalid JSON Unicode");
                        }
                        output.append(character).append(value.charAt(++index));
                    } else if (Character.isLowSurrogate(character)) {
                        throw new IllegalArgumentException("Invalid JSON Unicode");
                    } else { output.append(character); }
                }
            }
        }
        output.append('"');
    }

    /** Finds the shortest decimal that rounds to this binary64; nearest candidate, with ties to even. */
    private static String number(double value) {
        if (!Double.isFinite(value)) { throw new IllegalArgumentException("Non-finite JSON number"); }
        if (value == 0.0d) { return "0"; }
        boolean negative = value < 0;
        double positive = Math.abs(value);
        BigDecimal exact = new BigDecimal(positive);
        for (int precision = 1; precision <= 17; precision++) {
            BigDecimal lower = exact.round(new MathContext(precision, RoundingMode.FLOOR)).stripTrailingZeros();
            BigDecimal upper = exact.round(new MathContext(precision, RoundingMode.CEILING)).stripTrailingZeros();
            boolean lowerEligible = roundTrips(lower, positive);
            boolean upperEligible = roundTrips(upper, positive);
            if (!lowerEligible && !upperEligible) { continue; }
            BigDecimal selected;
            if (!upperEligible) { selected = lower; }
            else if (!lowerEligible) { selected = upper; }
            else {
                int distance = exact.subtract(lower).abs().compareTo(upper.subtract(exact).abs());
                selected = distance < 0 || distance == 0 && !lower.unscaledValue().testBit(0) ? lower : upper;
            }
            return (negative ? "-" : "") + decimal(selected);
        }
        throw new IllegalArgumentException("JSON number cannot be represented as binary64");
    }

    private static boolean roundTrips(BigDecimal candidate, double value) {
        return Double.doubleToLongBits(Double.parseDouble(candidate.toString())) == Double.doubleToLongBits(value);
    }

    private static String decimal(BigDecimal value) {
        int exponent = value.precision() - value.scale() - 1;
        if (exponent >= -6 && exponent < 21) { return value.toPlainString(); }
        String digits = value.unscaledValue().toString();
        return digits.charAt(0) + (digits.length() == 1 ? "" : "." + digits.substring(1))
                + "e" + (exponent >= 0 ? "+" : "") + exponent;
    }

    /** Keeps protocol text in a JSON string without scalar coercion. */
    public static final class WireStringDeserializer extends JsonDeserializer<String> {
        @Override public String deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            if (!parser.hasToken(JsonToken.VALUE_STRING)) {
                return context.reportInputMismatch(String.class, "A configuration text field must be a JSON string");
            }
            return parser.getText();
        }
    }

    /** Parses the same UTC string shape for snapshot expiry and receipt diagnostics. */
    public static final class UtcInstantDeserializer extends JsonDeserializer<Instant> {
        @Override
        public Instant deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            if (parser.hasToken(JsonToken.VALUE_STRING)) {
                String value = parser.getText();
                if (value.length() <= 40 && value.endsWith("Z")) {
                    try {
                        return Instant.parse(value);
                    } catch (DateTimeParseException invalid) {
                        // Keep invalid wire input out of exception text.
                    }
                }
            }
            return context.reportInputMismatch(Instant.class, "A configuration instant must be an ISO UTC string");
        }
    }
}
