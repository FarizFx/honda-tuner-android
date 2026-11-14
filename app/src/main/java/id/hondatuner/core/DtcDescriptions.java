// Generated from the desktop source DTC description catalog.
package id.hondatuner.core;

import java.util.LinkedHashMap;
import java.util.Map;

public final class DtcDescriptions {
    private DtcDescriptions() {}
    private static final Map<String, String> ITEMS = new LinkedHashMap<>();
    static {
        ITEMS.put("1-1", "MAP sensor circuit low voltage");
        ITEMS.put("1-2", "MAP sensor circuit high voltage");
        ITEMS.put("2-1", "MAP sensor performance problem");
        ITEMS.put("6-1", "Barometric sensor circuit low voltage");
        ITEMS.put("6-2", "Barometric sensor circuit high voltage");
        ITEMS.put("7-1", "ECT sensor circuit low voltage");
        ITEMS.put("7-2", "ECT sensor circuit high voltage");
        ITEMS.put("8-1", "TP sensor circuit low voltage");
        ITEMS.put("8-2", "TP sensor circuit high voltage");
        ITEMS.put("9-1", "IAT sensor circuit low voltage");
        ITEMS.put("9-2", "IAT sensor circuit high voltage");
        ITEMS.put("11-1", "VS sensor no signal");
        ITEMS.put("12-1", "No.1 primary injector circuit malfunction");
        ITEMS.put("13-1", "No.2 primary injector circuit malfunction");
        ITEMS.put("14-1", "No.3 primary injector circuit malfunction");
        ITEMS.put("15-1", "No.4 primary injector circuit malfunction");
        ITEMS.put("16-1", "No.1 secondary injector circuit malfunction");
        ITEMS.put("17-1", "No.2 secondary injector circuit malfunction");
        ITEMS.put("18-1", "CMP sensor no signal");
        ITEMS.put("19-1", "CKP sensor no signal");
        ITEMS.put("21-1", "0\u2082 sensor bank 1 low voltage");
        ITEMS.put("21-2", "0\u2082 sensor bank 1 high voltage");
        ITEMS.put("22-1", "0\u2082 sensor bank 1 low voltage");
        ITEMS.put("22-2", "0\u2082 sensor bank 2 high voltage");
        ITEMS.put("23-1", "0\u2082 sensor bank 1 heater malfunction");
        ITEMS.put("24-1", "0\u2082 sensor bank 2 heater malfunction");
        ITEMS.put("25-2", "Knock sensor circuit malfunction");
        ITEMS.put("25-3", "Knock sensor circuit malfunction");
        ITEMS.put("29-1", "IACV circuit malfunction");
        ITEMS.put("33-2", "ECM EEPROM malfunction");
        ITEMS.put("34-1", "ECV POT low voltage malfunction");
        ITEMS.put("34-2", "ECV POT high voltage malfunction");
        ITEMS.put("35-1", "EGCA malfunction");
        ITEMS.put("36-1", "A/F sensor malfunction");
        ITEMS.put("38-1", "A/F sensor heater malfunction");
        ITEMS.put("41-1", "Shiftdrum angle sensor circuit low voltage");
        ITEMS.put("41-2", "Shiftdrum angle sensor circuit high voltage");
        ITEMS.put("48-1", "No.3 secondary injector circuit malfunction");
        ITEMS.put("49-1", "No.4 secondary injector circuit malfunction");
        ITEMS.put("51-1", "HESD linear solenoid malfunction");
        ITEMS.put("54-1", "Bank angle sensor circuit low voltage");
        ITEMS.put("54-2", "Bank angle sensor circuit high voltage");
        ITEMS.put("56-1", "Knock sensor IC malfunction");
        ITEMS.put("66-1", "Rear wheel speed sensor no signal");
        ITEMS.put("67-1", "Front wheel speed sensor no signal");
        ITEMS.put("71-1", "TP sensor 1 low voltage");
        ITEMS.put("71-2", "TP sensor 1 high voltage");
        ITEMS.put("72-1", "TP sensor 2 low voltage");
        ITEMS.put("72-2", "TP sensor 2 high voltage");
        ITEMS.put("73-1", "TP sensors 1 and 2 voltage correlation malfunction");
        ITEMS.put("73-2", "TP sensors 1 and 2 short circuit");
        ITEMS.put("74-1", "APS 1 (TCP) low voltage");
        ITEMS.put("74-2", "APS 1 (TCP) high voltage");
        ITEMS.put("75-1", "APS 2 (TCP) low voltage");
        ITEMS.put("75-2", "APS 2 (TCP) high voltage");
        ITEMS.put("76-1", "APS 1 - 2 (TCP) voltage correlation malfunction");
        ITEMS.put("77-1", "TBW return spring malfunction");
        ITEMS.put("78-1", "TBW motor malfunction");
        ITEMS.put("79-1", "TBW system control correlation malfunction");
        ITEMS.put("82-1", "Fast idle solenoid valve malfunction");
        ITEMS.put("84-1", "CPU in the ECM malfunction");
        ITEMS.put("85-1", "TBW relay failure (ON side)");
        ITEMS.put("85-2", "TBW relay failure (OFF side)");
        ITEMS.put("86-1", "Serial communication malfunction");
        ITEMS.put("88-1", "EVAP purge control solenoid valve malfunction");
        ITEMS.put("89-1", "PAIR control solenoid valve malfunction");
        ITEMS.put("91-1", "No. 1 ignition coil circuit malfunction");
        ITEMS.put("92-1", "No. 2 ignition coil circuit malfunction");
        ITEMS.put("93-1", "No. 3 ignition coil circuit malfunction");
        ITEMS.put("94-1", "No. 4 ignition coil circuit malfunction");
        ITEMS.put("99-1", "IMU circuit malfunction");
        ITEMS.put("103-1", "CAN communication malfunction");
        ITEMS.put("103-2", "CAN communication malfunction");
        ITEMS.put("103-3", "CAN communication malfunction");
        ITEMS.put("103-4", "CAN communication malfunction");
        ITEMS.put("107-1", "Shift stroke sensor circuit low voltage");
        ITEMS.put("107-2", "Shift stroke sensor circuit high voltage");
        ITEMS.put("108-1", "Shift spindle switch circuit low voltage");
        ITEMS.put("108-2", "Shift spindle switch circuit high voltage");
        ITEMS.put("113-1", "Clutch switch circuit low voltage");
    }
    public static String describe(String code) {
        return ITEMS.containsKey(code) ? ITEMS.get(code) : "DTC belum ada dalam katalog: " + code;
    }
}
