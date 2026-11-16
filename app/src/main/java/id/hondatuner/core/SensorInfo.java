package id.hondatuner.core;

import java.util.Locale;

/** Display labels; unscaled source signals intentionally have no guessed unit. */
public final class SensorInfo {
    private SensorInfo() {}
    public static String label(String name) {
        switch (name) {
            case "engine_speed": return "Putaran mesin";
            case "tp_sensor_angle": return "Posisi gas";
            case "tp_sensor_1_angle": return "Posisi gas 1";
            case "tp_sensor_2_angle": return "Posisi gas 2";
            case "tp_sensor_voltage": return "Tegangan sensor gas";
            case "tp_sensor_1_voltage": return "Sensor gas 1";
            case "tp_sensor_2_voltage": return "Sensor gas 2";
            case "tp_target_angle": return "Target bukaan gas";
            case "tcp_sensor_1_angle": return "Pedal gas 1";
            case "tcp_sensor_2_angle": return "Pedal gas 2";
            case "tcp_sensor_1_voltage": return "Tegangan pedal 1";
            case "tcp_sensor_2_voltage": return "Tegangan pedal 2";
            case "battery_voltage": return "Tegangan baterai";
            case "ect_sensor_temperature": return "Suhu pendingin";
            case "ect_sensor_voltage": return "Tegangan sensor ECT";
            case "eot_sensor_temperature": return "Suhu oli";
            case "eot_sensor_voltage": return "Tegangan sensor EOT";
            case "iat_sensor_temperature": return "Suhu udara masuk";
            case "iat_sensor_voltage": return "Tegangan sensor IAT";
            case "map_sensor_pressure": return "Tekanan manifold";
            case "map_sensor_voltage": return "Tegangan MAP";
            case "spark_advance": return "Sudut pengapian";
            case "injector": return "Sinyal injektor";
            case "vss": return "Speed / VSS";
            case "iacv_pulse_counter": return "Pulsa katup idle";
            case "iac_valve_command": return "Perintah katup idle";
            case "ethanol_index": return "Campuran etanol";
            case "env_revision_coeff": return "Koreksi lingkungan";
            case "o2_sensor_voltage_1": return "Tegangan O2 1";
            case "o2_sensor_voltage_2": return "Tegangan O2 2";
            case "o2_stft_1": return "Koreksi bahan bakar 1";
            case "o2_stft_2": return "Koreksi bahan bakar 2";
            case "o2_heater_voltage_1": return "Pemanas O2 1";
            case "o2_heater_voltage_2": return "Pemanas O2 2";
            case "byte01.eop_switch": return "Saklar EOP";
            case "byte01.scs": return "Saklar SCS";
            case "byte01.sstand": return "Standar samping";
            case "byte01.nlsw": case "byte03.gear_position_switch_n": return "Saklar netral";
            default: return name.replace('_', ' ').replace('.', '/');
        }
    }
    public static String unit(String name) {
        if (name.equals("engine_speed")) return "RPM";
        if (name.contains("voltage")) return "V";
        if (name.endsWith("_temperature")) return "°C";
        if (name.endsWith("_angle") || name.equals("spark_advance")) return "°";
        return "";
    }
    public static String group(String name) {
        if (name.startsWith("byte") || name.contains("switch") || name.contains(".scs") || name.contains(".sstand") || name.contains(".nlsw")) return "Saklar & status";
        if (name.startsWith("o2_")) return "O2 & bahan bakar";
        if (name.startsWith("ect_") || name.startsWith("eot_") || name.startsWith("iat_")) return "Temperatur";
        if (name.contains("voltage")) return "Tegangan";
        return "Mesin & udara";
    }
    public static int priority(String name) {
        String[] priority = {"engine_speed", "vss", "tp_sensor_angle", "tp_sensor_1_angle", "battery_voltage", "ect_sensor_temperature", "eot_sensor_temperature", "iat_sensor_temperature", "map_sensor_pressure", "spark_advance", "injector"};
        for (int i = 0; i < priority.length; i++) if (priority[i].equals(name)) return i;
        return priority.length;
    }
    public static boolean nonNegative(String name) {
        return name.equals("engine_speed") || name.equals("vss") || name.contains("voltage") ||
                (name.endsWith("_angle") && (name.startsWith("tp_") || name.startsWith("tcp_")));
    }
    public static String compactLabel(String name) {
        if (name.equals("engine_speed")) return "RPM";
        if (name.equals("vss")) return "SPEED / VSS";
        if (name.equals("battery_voltage")) return "BATTERY";
        if (name.equals("tp_sensor_angle") || name.equals("tp_sensor_1_angle")) return "TPS";
        if (name.equals("ect_sensor_temperature")) return "ECT";
        if (name.equals("eot_sensor_temperature")) return "EOT";
        if (name.equals("iat_sensor_temperature")) return "IAT";
        return label(name).toUpperCase(Locale.ROOT);
    }
    public static String statisticsLabel(TelemetryHistory.Statistics values) {
        return "Min " + format(values.minimum) + " · Avg " + format(values.average) + " · Max " + format(values.maximum);
    }
    public static String format(Object value) {
        if (value == null || value instanceof Number && TelemetryHistory.numeric(value) == null) return "—";
        if (value instanceof Boolean) return (Boolean) value ? "ON" : "OFF";
        if (value instanceof Double || value instanceof Float) return String.format(Locale.getDefault(), "%.2f", ((Number) value).doubleValue());
        return value.toString();
    }
}
