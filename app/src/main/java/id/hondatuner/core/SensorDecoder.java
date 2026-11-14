package id.hondatuner.core;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Same body lengths, offsets and conversions as Honda Tuner's source decoder. */
public final class SensorDecoder {
    private SensorDecoder() {}

    static final class Field {
        final String name, conversion;
        final int offset, type;
        final String[] bits;
        final Map<Integer, String> labels;
        Field(String name, int offset, int type, String conversion, String[] bits, Map<Integer, String> labels) {
            this.name = name; this.offset = offset; this.type = type; this.conversion = conversion;
            this.bits = bits; this.labels = labels;
        }
    }
    static final class Schema {
        final int table, length;
        final Field[] fields;
        Schema(int table, int length, Field... fields) { this.table = table; this.length = length; this.fields = fields; }
    }

    public static final class Result {
        public final int tableId, bodyLength;
        public final Map<String, Object> fields;
        Result(int tableId, int bodyLength, Map<String, Object> fields) {
            this.tableId = tableId; this.bodyLength = bodyLength;
            this.fields = Collections.unmodifiableMap(new LinkedHashMap<>(fields));
        }
        public Map<String, Object> named() {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : fields.entrySet()) if (isNamed(entry.getKey())) result.put(entry.getKey(), entry.getValue());
            return result;
        }
    }

    public static boolean isNamed(String name) {
        if (name.contains("_unk") || (name.startsWith("byte") && !name.contains("."))) return false;
        String tail = name.substring(name.lastIndexOf('.') + 1);
        if (tail.length() == 0) return false;
        for (int i = 0; i < tail.length(); i++) if (!Character.isDigit(tail.charAt(i))) return true;
        return false;
    }

    public static Result decode(int tableId, byte[] body) throws ProtocolException {
        Schema schema = SensorSchemas.find(tableId, body.length);
        if (schema == null) throw new ProtocolException(String.format("Tabel %02X dengan %d byte sensor belum didukung.", tableId, body.length));
        Map<String, Object> values = new LinkedHashMap<>();
        for (Field field : schema.fields) {
            int raw = body[field.offset] & 255;
            double value = raw;
            if (field.type == 1 || field.type == 3) {
                raw = (raw << 8) | (body[field.offset + 1] & 255);
                value = field.type == 3 ? half(raw) : raw;
            } else if (field.type == 2) { raw = body[field.offset]; value = raw; }
            if (field.bits != null) {
                for (int bit = 0; bit < field.bits.length; bit++) values.put(field.name + "." + field.bits[bit], (raw & (1 << (7 - bit))) != 0);
            } else if (field.labels != null && field.labels.containsKey(raw)) {
                values.put(field.name, field.labels.get(raw));
            } else if (field.conversion != null) {
                values.put(field.name, convert(value, field.conversion));
            } else if (field.type == 3) {
                values.put(field.name, value);
            } else { values.put(field.name, raw); }
        }
        return new Result(tableId, body.length, values);
    }

    private static double convert(double value, String expression) {
        switch (expression) {
            case "voltage": return (value / 255) * 4.973;
            case "angle127_5": return (value / 255) * 127.5;
            case "angle127": return (value / 255) * 127;
            case "temperature": return -40 + value;
            case "battery": return value / 10;
            case "spark": return (value / 2) - 64;
            case "iac_counter": return value * 2;
            case "iac_command": return value * 0.0003;
            case "coefficient": return (value / 255) * 1.988;
            default: throw new IllegalStateException("Konversi sensor tidak dikenal: " + expression);
        }
    }

    private static double half(int bits) {
        int exponent = (bits >> 10) & 31, fraction = bits & 1023;
        double sign = (bits & 0x8000) != 0 ? -1.0 : 1.0;
        if (exponent == 31) return fraction == 0 ? sign * Double.POSITIVE_INFINITY : Double.NaN;
        if (exponent == 0) return sign * Math.scalb((double) fraction, -24);
        return sign * Math.scalb(1.0 + fraction / 1024.0, exponent - 15);
    }
}
