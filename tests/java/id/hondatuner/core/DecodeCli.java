package id.hondatuner.core;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public final class DecodeCli {
    public static void main(String[] args) throws Exception {
        BufferedReader input = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        for (String line; (line = input.readLine()) != null;) {
            String[] parts = line.split("\t");
            byte[] body = new byte[parts[1].length() / 2];
            for (int i = 0; i < body.length; i++) body[i] = (byte) Integer.parseInt(parts[1].substring(i * 2, i * 2 + 2), 16);
            SensorDecoder.Result result = SensorDecoder.decode(Integer.parseInt(parts[0]), body);
            StringBuilder json = new StringBuilder("{");
            for (Map.Entry<String, Object> field : result.fields.entrySet()) {
                if (json.length() > 1) json.append(',');
                json.append(quote(field.getKey())).append(':');
                Object value = field.getValue();
                if (value instanceof String || (value instanceof Double && !Double.isFinite((Double) value))) json.append(quote(value.toString()));
                else json.append(value);
            }
            System.out.println(json.append('}'));
        }
    }
    private static String quote(String value) { return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""; }
}
