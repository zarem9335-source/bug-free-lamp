package com.ekts.app.model;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * مدل مدار فرمان — شبیه EKTS — نسخه جاوا
 * مختصات روی صفحه مجازی 1000 در 800 است. CircuitView آن را به اندازه گوشی می‌کشد.
 * سیم = اتصال خروجی یک قطعه به ورودی قطعه بعدی (from -> to)
 */
public class CircuitModel {

    public enum ComponentType {
        POWER_L("فاز L", "L", "تغذیه"),
        POWER_N("نول N", "N", "تغذیه"),
        POWER_3PH("برق سه‌فاز 400V", "3~", "تغذیه"),
        FUSE("فیوز", "F", "تغذیه"),
        SWITCH_3P("کلید سه‌پل", "Q", "تغذیه"),
        START_NO("شستی استارت NO", "S1", "فرمان"),
        STOP_NC("شستی استاپ NC", "S0", "فرمان"),
        JOG_NO("شستی لحظه‌ای Jog", "J", "فرمان"),
        SWITCH("کلید", "SW", "فرمان"),
        CONTACTOR_COIL("بوبین کنتاکتور", "KM", "فرمان"),
        RELAY_COIL("بوبین رله", "K", "فرمان"),
        CONTACT_NO("کنتاکت NO", "KM", "فرمان"),
        CONTACT_NC("کنتاکت NC", "KM", "فرمان"),
        OVERLOAD_NC("بی‌متال NC", "F2", "فرمان"),
        TIMER_ON("تایمر تاخیر در وصل", "KT", "فرمان"),
        TIMER_OFF("تایمر تاخیر در قطع", "KT", "فرمان"),
        TIMER_CONTACT_NO("کنتاکت تایمر NO", "KT", "فرمان"),
        TIMER_CONTACT_NC("کنتاکت تایمر NC", "KT", "فرمان"),
        LIMIT_NO("لیمیت سوئیچ NO", "LS", "فرمان"),
        LIMIT_NC("لیمیت سوئیچ NC", "LS", "فرمان"),
        MOTOR("موتور سه‌فاز", "M", "موتور"),
        MOTOR_1PH("موتور تک‌فاز", "M1~", "موتور"),
        LAMP("لامپ", "H", "موتور"),
        BUZZER("آژیر / بازر", "HA", "موتور");

        public final String fa;
        public final String code;
        public final String category;

        ComponentType(String fa, String code, String category) {
            this.fa = fa;
            this.code = code;
            this.category = category;
        }

        public static ComponentType fromName(String name) {
            if (name == null) return null;
            for (ComponentType t : values()) {
                if (t.name().equalsIgnoreCase(name)) return t;
            }
            return null;
        }
    }

    public static class CircuitComponent {
        public String id;
        public ComponentType type;
        public float x;
        public float y;
        public String label;
        public String value = "";
        // مثل ETKS: چرخش، زمان تایمر، رنگ لامپ
        public int rotation = 0; // 0 / 90 / 180 / 270
        public float delaySec = 3f;
        public String color = "";

        public CircuitComponent(String id, ComponentType type, float x, float y, String label) {
            this.id = id;
            this.type = type;
            this.x = x;
            this.y = y;
            this.label = label;
        }

        public void rotate90() {
            rotation = (rotation + 90) % 360;
        }
    }

    public static class Wire {
        public String fromId;
        public String toId;

        public Wire(String fromId, String toId) {
            this.fromId = fromId;
            this.toId = toId;
        }
    }

    public static class Circuit {
        public String name;
        public List<CircuitComponent> components = new ArrayList<>();
        public List<Wire> wires = new ArrayList<>();

        public Circuit(String name) {
            this.name = name;
        }

        public CircuitComponent component(String id) {
            for (CircuitComponent c : components) {
                if (c.id.equals(id)) return c;
            }
            return null;
        }
    }

    private static CircuitComponent c(String id, ComponentType type, float x, float y, String label) {
        return new CircuitComponent(id, type, x, y, label);
    }

    // ------------------------------------------------------------------ presets

    public static Circuit dolStarter() {
        Circuit c = new Circuit("راه‌انداز مستقیم موتور (DOL)");
        c.components.addAll(Arrays.asList(
                c("L1", ComponentType.POWER_L, 80, 180, "L"),
                c("S0", ComponentType.STOP_NC, 230, 180, "S0"),
                c("S1", ComponentType.START_NO, 390, 180, "S1"),
                c("KM1", ComponentType.CONTACTOR_COIL, 580, 180, "KM1"),
                c("N1", ComponentType.POWER_N, 770, 180, "N"),
                c("KM1H", ComponentType.CONTACT_NO, 390, 330, "KM1"),
                c("F2", ComponentType.OVERLOAD_NC, 230, 480, "F2"),
                c("L2", ComponentType.POWER_L, 80, 480, "L"),
                c("KM1M", ComponentType.CONTACT_NO, 390, 480, "KM1"),
                c("M1", ComponentType.MOTOR, 580, 480, "M1"),
                c("N2", ComponentType.POWER_N, 770, 480, "N"),
                c("H1", ComponentType.LAMP, 580, 630, "H1"),
                c("KM1L", ComponentType.CONTACT_NO, 390, 630, "KM1"),
                c("L3", ComponentType.POWER_L, 80, 630, "L"),
                c("N3", ComponentType.POWER_N, 770, 630, "N")
        ));
        c.wires.addAll(Arrays.asList(
                new Wire("L1", "S0"), new Wire("S0", "S1"), new Wire("S1", "KM1"), new Wire("KM1", "N1"),
                new Wire("S0", "KM1H"), new Wire("KM1H", "KM1"),
                new Wire("L2", "F2"), new Wire("F2", "KM1M"), new Wire("KM1M", "M1"), new Wire("M1", "N2"),
                new Wire("L3", "KM1L"), new Wire("KM1L", "H1"), new Wire("H1", "N3")
        ));
        return c;
    }

    public static Circuit starDelta() {
        Circuit c = new Circuit("راه‌انداز ستاره-مثلث");
        c.components.addAll(Arrays.asList(
                c("L1", ComponentType.POWER_L, 80, 150, "L"),
                c("S0", ComponentType.STOP_NC, 230, 150, "S0"),
                c("S1", ComponentType.START_NO, 390, 150, "S1"),
                c("KM1", ComponentType.CONTACTOR_COIL, 580, 150, "KM1"),
                c("N1", ComponentType.POWER_N, 770, 150, "N"),
                c("KM1H", ComponentType.CONTACT_NO, 390, 280, "KM1"),
                c("KT", ComponentType.TIMER_ON, 580, 280, "KT"),
                c("L2", ComponentType.POWER_L, 80, 430, "L"),
                c("KTNC", ComponentType.TIMER_CONTACT_NC, 300, 430, "KT"),
                c("KM2", ComponentType.CONTACTOR_COIL, 520, 430, "KM2"),
                c("N2", ComponentType.POWER_N, 770, 430, "N"),
                c("L3", ComponentType.POWER_L, 80, 570, "L"),
                c("KTNO", ComponentType.TIMER_CONTACT_NO, 300, 570, "KT"),
                c("KM3", ComponentType.CONTACTOR_COIL, 520, 570, "KM3"),
                c("N3", ComponentType.POWER_N, 770, 570, "N"),
                c("M1", ComponentType.MOTOR, 580, 700, "M1"),
                c("KM1M", ComponentType.CONTACT_NO, 390, 700, "KM1"),
                c("L4", ComponentType.POWER_L, 80, 700, "L"),
                c("N4", ComponentType.POWER_N, 770, 700, "N")
        ));
        c.wires.addAll(Arrays.asList(
                new Wire("L1", "S0"), new Wire("S0", "S1"), new Wire("S1", "KM1"), new Wire("KM1", "N1"),
                new Wire("S0", "KM1H"), new Wire("KM1H", "KM1"),
                new Wire("KM1H", "KT"), new Wire("KT", "N1"),
                new Wire("L2", "KTNC"), new Wire("KTNC", "KM2"), new Wire("KM2", "N2"),
                new Wire("L3", "KTNO"), new Wire("KTNO", "KM3"), new Wire("KM3", "N3"),
                new Wire("L4", "KM1M"), new Wire("KM1M", "M1"), new Wire("M1", "N4")
        ));
        return c;
    }

    public static Circuit forwardReverse() {
        Circuit c = new Circuit("چپ‌گرد / راست‌گرد موتور");
        c.components.addAll(Arrays.asList(
                c("L1", ComponentType.POWER_L, 80, 150, "L"),
                c("S0", ComponentType.STOP_NC, 230, 150, "S0"),
                c("SF", ComponentType.START_NO, 390, 150, "SF"),
                c("KM1", ComponentType.CONTACTOR_COIL, 600, 150, "KM1"),
                c("N1", ComponentType.POWER_N, 790, 150, "N"),
                c("KM2NC", ComponentType.CONTACT_NC, 500, 150, "KM2"),
                c("L2", ComponentType.POWER_L, 80, 300, "L"),
                c("SR", ComponentType.START_NO, 390, 300, "SR"),
                c("KM2", ComponentType.CONTACTOR_COIL, 600, 300, "KM2"),
                c("N2", ComponentType.POWER_N, 790, 300, "N"),
                c("KM1NC", ComponentType.CONTACT_NC, 500, 300, "KM1"),
                c("L3", ComponentType.POWER_L, 80, 480, "L"),
                c("KM1M", ComponentType.CONTACT_NO, 320, 480, "KM1"),
                c("KM2M", ComponentType.CONTACT_NO, 320, 620, "KM2"),
                c("M1", ComponentType.MOTOR, 600, 550, "M1"),
                c("N3", ComponentType.POWER_N, 790, 550, "N"),
                c("L4", ComponentType.POWER_L, 80, 620, "L")
        ));
        c.wires.addAll(Arrays.asList(
                new Wire("L1", "S0"), new Wire("S0", "SF"), new Wire("SF", "KM2NC"), new Wire("KM2NC", "KM1"), new Wire("KM1", "N1"),
                new Wire("L2", "S0"), new Wire("S0", "SR"), new Wire("SR", "KM1NC"), new Wire("KM1NC", "KM2"), new Wire("KM2", "N2"),
                new Wire("L3", "KM1M"), new Wire("KM1M", "M1"), new Wire("M1", "N3"),
                new Wire("L4", "KM2M"), new Wire("KM2M", "M1")
        ));
        return c;
    }

    public static Circuit timerLamp() {
        Circuit c = new Circuit("تایمر تاخیر در وصل + لامپ");
        c.components.addAll(Arrays.asList(
                c("L1", ComponentType.POWER_L, 80, 200, "L"),
                c("S1", ComponentType.START_NO, 280, 200, "S1"),
                c("KT", ComponentType.TIMER_ON, 500, 200, "KT"),
                c("N1", ComponentType.POWER_N, 720, 200, "N"),
                c("L2", ComponentType.POWER_L, 80, 450, "L"),
                c("KTNO", ComponentType.TIMER_CONTACT_NO, 300, 450, "KT"),
                c("H1", ComponentType.LAMP, 520, 450, "H1"),
                c("N2", ComponentType.POWER_N, 720, 450, "N")
        ));
        c.wires.addAll(Arrays.asList(
                new Wire("L1", "S1"), new Wire("S1", "KT"), new Wire("KT", "N1"),
                new Wire("L2", "KTNO"), new Wire("KTNO", "H1"), new Wire("H1", "N2")
        ));
        return c;
    }

    public static Circuit simpleLamp() {
        Circuit c = new Circuit("لامپ با کلید");
        c.components.addAll(Arrays.asList(
                c("L1", ComponentType.POWER_L, 120, 300, "L"),
                c("SW1", ComponentType.SWITCH, 350, 300, "SW1"),
                c("H1", ComponentType.LAMP, 580, 300, "H1"),
                c("N1", ComponentType.POWER_N, 780, 300, "N")
        ));
        c.wires.addAll(Arrays.asList(
                new Wire("L1", "SW1"), new Wire("SW1", "H1"), new Wire("H1", "N1")
        ));
        return c;
    }

    public static Circuit elevator() {
        Circuit c = new Circuit("آسانسور — بالا / پایین");
        c.components.addAll(Arrays.asList(
                c("L1", ComponentType.POWER_L, 80, 150, "L"),
                c("S0", ComponentType.STOP_NC, 230, 150, "S0"),
                c("UP", ComponentType.START_NO, 390, 150, "UP"),
                c("KMU", ComponentType.CONTACTOR_COIL, 600, 150, "KMU"),
                c("N1", ComponentType.POWER_N, 790, 150, "N"),
                c("KMDNC", ComponentType.CONTACT_NC, 500, 150, "KMD"),
                c("L2", ComponentType.POWER_L, 80, 300, "L"),
                c("DN", ComponentType.START_NO, 390, 300, "DN"),
                c("KMD", ComponentType.CONTACTOR_COIL, 600, 300, "KMD"),
                c("N2", ComponentType.POWER_N, 790, 300, "N"),
                c("KMUNC", ComponentType.CONTACT_NC, 500, 300, "KMU"),
                c("LSU", ComponentType.LIMIT_NC, 230, 430, "LS بالا"),
                c("L3", ComponentType.POWER_L, 80, 430, "L"),
                c("KMUM", ComponentType.CONTACT_NO, 390, 430, "KMU"),
                c("M1", ComponentType.MOTOR, 600, 430, "M1"),
                c("N3", ComponentType.POWER_N, 790, 430, "N"),
                c("LSD", ComponentType.LIMIT_NC, 230, 580, "LS پایین"),
                c("L4", ComponentType.POWER_L, 80, 580, "L"),
                c("KMDM", ComponentType.CONTACT_NO, 390, 580, "KMD"),
                c("N4", ComponentType.POWER_N, 790, 580, "N"),
                c("H1", ComponentType.LAMP, 600, 580, "H1")
        ));
        c.wires.addAll(Arrays.asList(
                new Wire("L1", "S0"), new Wire("S0", "UP"), new Wire("UP", "KMDNC"), new Wire("KMDNC", "KMU"), new Wire("KMU", "N1"),
                new Wire("L2", "S0"), new Wire("S0", "DN"), new Wire("DN", "KMUNC"), new Wire("KMUNC", "KMD"), new Wire("KMD", "N2"),
                new Wire("L3", "LSU"), new Wire("LSU", "KMUM"), new Wire("KMUM", "M1"), new Wire("M1", "N3"),
                new Wire("L4", "LSD"), new Wire("LSD", "KMDM"), new Wire("KMDM", "H1"), new Wire("H1", "N4")
        ));
        return c;
    }

    public static Circuit gate() {
        Circuit c = new Circuit("درب اتوماتیک (گیت)");
        c.components.addAll(Arrays.asList(
                c("L1", ComponentType.POWER_L, 80, 180, "L"),
                c("S0", ComponentType.STOP_NC, 230, 180, "S0"),
                c("OPEN", ComponentType.START_NO, 390, 180, "OPEN"),
                c("KMO", ComponentType.CONTACTOR_COIL, 600, 180, "KMO"),
                c("N1", ComponentType.POWER_N, 790, 180, "N"),
                c("KMCNC", ComponentType.CONTACT_NC, 500, 180, "KMC"),
                c("LSO", ComponentType.LIMIT_NC, 230, 330, "LS باز"),
                c("L2", ComponentType.POWER_L, 80, 330, "L"),
                c("CLOSE", ComponentType.START_NO, 390, 330, "CLOSE"),
                c("KMC", ComponentType.CONTACTOR_COIL, 600, 330, "KMC"),
                c("N2", ComponentType.POWER_N, 790, 330, "N"),
                c("KMONC", ComponentType.CONTACT_NC, 500, 330, "KMO"),
                c("L3", ComponentType.POWER_L, 80, 500, "L"),
                c("KMOM", ComponentType.CONTACT_NO, 350, 500, "KMO"),
                c("M1", ComponentType.MOTOR_1PH, 600, 500, "M1"),
                c("N3", ComponentType.POWER_N, 790, 500, "N"),
                c("LSC", ComponentType.LIMIT_NC, 230, 650, "LS بسته"),
                c("L4", ComponentType.POWER_L, 80, 650, "L"),
                c("KMCM", ComponentType.CONTACT_NO, 350, 650, "KMC"),
                c("H1", ComponentType.LAMP, 600, 650, "H1"),
                c("N4", ComponentType.POWER_N, 790, 650, "N")
        ));
        c.wires.addAll(Arrays.asList(
                new Wire("L1", "S0"), new Wire("S0", "OPEN"), new Wire("OPEN", "KMCNC"), new Wire("KMCNC", "KMO"), new Wire("KMO", "N1"),
                new Wire("L2", "LSO"), new Wire("LSO", "CLOSE"), new Wire("CLOSE", "KMONC"), new Wire("KMONC", "KMC"), new Wire("KMC", "N2"),
                new Wire("L3", "KMOM"), new Wire("KMOM", "M1"), new Wire("M1", "N3"),
                new Wire("L4", "LSC"), new Wire("LSC", "KMCM"), new Wire("KMCM", "H1"), new Wire("H1", "N4")
        ));
        return c;
    }

    public static Circuit pumpFloat() {
        Circuit c = new Circuit("پمپ آب با فلوتر");
        c.components.addAll(Arrays.asList(
                c("L1", ComponentType.POWER_L, 80, 200, "L"),
                c("S0", ComponentType.STOP_NC, 230, 200, "S0"),
                c("FL", ComponentType.LIMIT_NO, 390, 200, "فلوتر"),
                c("KM1", ComponentType.CONTACTOR_COIL, 600, 200, "KM1"),
                c("N1", ComponentType.POWER_N, 790, 200, "N"),
                c("KM1H", ComponentType.CONTACT_NO, 390, 350, "KM1"),
                c("L2", ComponentType.POWER_L, 80, 500, "L"),
                c("KM1M", ComponentType.CONTACT_NO, 350, 500, "KM1"),
                c("M1", ComponentType.MOTOR_1PH, 600, 500, "M1"),
                c("N2", ComponentType.POWER_N, 790, 500, "N")
        ));
        c.wires.addAll(Arrays.asList(
                new Wire("L1", "S0"), new Wire("S0", "FL"), new Wire("FL", "KM1"), new Wire("KM1", "N1"),
                new Wire("S0", "KM1H"), new Wire("KM1H", "KM1"),
                new Wire("L2", "KM1M"), new Wire("KM1M", "M1"), new Wire("M1", "N2")
        ));
        return c;
    }

    public static Circuit sequentialMotors() {
        Circuit c = new Circuit("سه موتور ترتیبی (نوار نقاله)");
        c.components.addAll(Arrays.asList(
                c("L1", ComponentType.POWER_L, 80, 150, "L"),
                c("S0", ComponentType.STOP_NC, 230, 150, "S0"),
                c("S1", ComponentType.START_NO, 390, 150, "S1"),
                c("KM1", ComponentType.CONTACTOR_COIL, 600, 150, "KM1"),
                c("N1", ComponentType.POWER_N, 790, 150, "N"),
                c("KM1H", ComponentType.CONTACT_NO, 390, 280, "KM1"),
                c("KT1", ComponentType.TIMER_ON, 600, 280, "KT1"),
                c("L2", ComponentType.POWER_L, 80, 430, "L"),
                c("KT1NO", ComponentType.TIMER_CONTACT_NO, 300, 430, "KT1"),
                c("KM2", ComponentType.CONTACTOR_COIL, 550, 430, "KM2"),
                c("N2", ComponentType.POWER_N, 790, 430, "N"),
                c("KT2", ComponentType.TIMER_ON, 600, 560, "KT2"),
                c("KM2NO", ComponentType.CONTACT_NO, 300, 560, "KM2"),
                c("L3", ComponentType.POWER_L, 80, 560, "L"),
                c("N3", ComponentType.POWER_N, 790, 560, "N"),
                c("L4", ComponentType.POWER_L, 80, 680, "L"),
                c("KT2NO", ComponentType.TIMER_CONTACT_NO, 300, 680, "KT2"),
                c("KM3", ComponentType.CONTACTOR_COIL, 550, 680, "KM3"),
                c("N4", ComponentType.POWER_N, 790, 680, "N")
        ));
        c.wires.addAll(Arrays.asList(
                new Wire("L1", "S0"), new Wire("S0", "S1"), new Wire("S1", "KM1"), new Wire("KM1", "N1"),
                new Wire("S0", "KM1H"), new Wire("KM1H", "KM1"),
                new Wire("KM1H", "KT1"), new Wire("KT1", "N1"),
                new Wire("L2", "KT1NO"), new Wire("KT1NO", "KM2"), new Wire("KM2", "N2"),
                new Wire("L3", "KM2NO"), new Wire("KM2NO", "KT2"), new Wire("KT2", "N3"),
                new Wire("L4", "KT2NO"), new Wire("KT2NO", "KM3"), new Wire("KM3", "N4")
        ));
        return c;
    }

    public static Circuit motor1Phase() {
        Circuit c = new Circuit("موتور تک‌فاز با کلید");
        c.components.addAll(Arrays.asList(
                c("L1", ComponentType.POWER_L, 120, 200, "L"),
                c("Q1", ComponentType.SWITCH_3P, 350, 200, "Q1"),
                c("M1", ComponentType.MOTOR_1PH, 600, 200, "M1"),
                c("N1", ComponentType.POWER_N, 790, 200, "N"),
                c("L2", ComponentType.POWER_L, 120, 450, "L"),
                c("S1", ComponentType.START_NO, 350, 450, "S1"),
                c("KM1", ComponentType.CONTACTOR_COIL, 600, 450, "KM1"),
                c("N2", ComponentType.POWER_N, 790, 450, "N")
        ));
        c.wires.addAll(Arrays.asList(
                new Wire("L1", "Q1"), new Wire("Q1", "M1"), new Wire("M1", "N1"),
                new Wire("L2", "S1"), new Wire("S1", "KM1"), new Wire("KM1", "N2")
        ));
        return c;
    }

    public static Circuit resistanceStarter() {
        Circuit c = new Circuit("استارتر مقاومتی موتور");
        c.components.addAll(Arrays.asList(
                c("L1", ComponentType.POWER_L, 80, 180, "L"),
                c("S0", ComponentType.STOP_NC, 230, 180, "S0"),
                c("S1", ComponentType.START_NO, 390, 180, "S1"),
                c("KM1", ComponentType.CONTACTOR_COIL, 600, 180, "KM1"),
                c("N1", ComponentType.POWER_N, 790, 180, "N"),
                c("KM1H", ComponentType.CONTACT_NO, 390, 320, "KM1"),
                c("KT", ComponentType.TIMER_ON, 600, 320, "KT"),
                c("L2", ComponentType.POWER_L, 80, 480, "L"),
                c("KTNO", ComponentType.TIMER_CONTACT_NO, 320, 480, "KT"),
                c("KM2", ComponentType.CONTACTOR_COIL, 580, 480, "KM2"),
                c("N2", ComponentType.POWER_N, 790, 480, "N"),
                c("M1", ComponentType.MOTOR, 600, 620, "M1"),
                c("KM1M", ComponentType.CONTACT_NO, 390, 620, "KM1"),
                c("L3", ComponentType.POWER_L, 80, 620, "L"),
                c("N3", ComponentType.POWER_N, 790, 620, "N")
        ));
        c.wires.addAll(Arrays.asList(
                new Wire("L1", "S0"), new Wire("S0", "S1"), new Wire("S1", "KM1"), new Wire("KM1", "N1"),
                new Wire("S0", "KM1H"), new Wire("KM1H", "KM1"),
                new Wire("KM1H", "KT"), new Wire("KT", "N1"),
                new Wire("L2", "KTNO"), new Wire("KTNO", "KM2"), new Wire("KM2", "N2"),
                new Wire("L3", "KM1M"), new Wire("KM1M", "M1"), new Wire("M1", "N3")
        ));
        return c;
    }

    public static List<Circuit> all() {
        return Arrays.asList(
                dolStarter(), starDelta(), forwardReverse(), timerLamp(), simpleLamp(),
                elevator(), gate(), pumpFloat(), sequentialMotors(), motor1Phase(), resistanceStarter()
        );
    }

    public static Circuit byKeyword(String text) {
        if (text == null) return null;
        if (text.contains("آسانسور") || text.contains("اسانسور")) return elevator();
        if (text.contains("گیت") || text.contains("درب اتوماتیک") || text.contains("درب")) return gate();
        if (text.contains("پمپ") || text.contains("فلوتر") || text.contains("آب")) return pumpFloat();
        if (text.contains("ترتیبی") || text.contains("نوار نقاله") || text.contains("کانوایر") || text.contains("سه موتور")) return sequentialMotors();
        if (text.contains("تک‌فاز") || text.contains("تک فاز")) return motor1Phase();
        if (text.contains("مقاومتی")) return resistanceStarter();
        if (text.contains("ستاره") || text.contains("مثلث")) return starDelta();
        if (text.contains("چپ") || text.contains("راست") || text.contains("راستگرد") || text.contains("چپگرد")) return forwardReverse();
        if (text.contains("تایمر")) return timerLamp();
        if (text.contains("مستقیم") || text.contains("راه‌انداز") || text.contains("راه انداز") || text.contains("موتور")) return dolStarter();
        if (text.contains("لامپ") || text.contains("چراغ")) return simpleLamp();
        return null;
    }

    // ------------------------------------------------------------------ JSON

    public static class CircuitJson {
        public static String toJson(Circuit circuit) {
            try {
                JSONObject root = new JSONObject();
                root.put("name", circuit.name);
                JSONArray comps = new JSONArray();
                for (CircuitComponent c : circuit.components) {
                    JSONObject o = new JSONObject();
                    o.put("id", c.id);
                    o.put("type", c.type.name());
                    o.put("x", (double) c.x);
                    o.put("y", (double) c.y);
                    o.put("label", c.label);
                    o.put("value", c.value);
                    o.put("rotation", c.rotation);
                    o.put("delaySec", (double) c.delaySec);
                    o.put("color", c.color);
                    comps.put(o);
                }
                root.put("components", comps);
                JSONArray wires = new JSONArray();
                for (Wire w : circuit.wires) {
                    JSONObject o = new JSONObject();
                    o.put("from", w.fromId);
                    o.put("to", w.toId);
                    wires.put(o);
                }
                root.put("wires", wires);
                return root.toString();
            } catch (Exception e) {
                return "{\"name\":\"" + circuit.name + "\",\"components\":[],\"wires\":[]}";
            }
        }

        public static Circuit fromJson(String json) {
            try {
                JSONObject root = new JSONObject(json);
                String name = root.optString("name", "مدار هوش مصنوعی");
                Circuit circuit = new Circuit(name);
                JSONArray comps = root.optJSONArray("components");
                if (comps == null) return null;
                for (int i = 0; i < comps.length(); i++) {
                    JSONObject o = comps.getJSONObject(i);
                    ComponentType type = ComponentType.fromName(o.optString("type", ""));
                    if (type == null) continue;
                    String id = o.optString("id", "C" + i);
                    float x = (float) o.optDouble("x", 300);
                    float y = (float) o.optDouble("y", 300);
                    if (x < 40) x = 40; if (x > 960) x = 960;
                    if (y < 40) y = 40; if (y > 760) y = 760;
                    String label = o.optString("label", id);
                    CircuitComponent comp = new CircuitComponent(id, type, x, y, label);
                    comp.value = o.optString("value", "");
                    comp.rotation = o.optInt("rotation", 0);
                    comp.delaySec = (float) o.optDouble("delaySec", 3.0);
                    if (comp.delaySec < 0.5f) comp.delaySec = 0.5f;
                    if (comp.delaySec > 60f) comp.delaySec = 60f;
                    comp.color = o.optString("color", "");
                    circuit.components.add(comp);
                }
                JSONArray wires = root.optJSONArray("wires");
                if (wires != null) {
                    for (int i = 0; i < wires.length(); i++) {
                        JSONObject o = wires.getJSONObject(i);
                        String from = o.optString("from", "");
                        String to = o.optString("to", "");
                        if (!from.isEmpty() && !to.isEmpty()) circuit.wires.add(new Wire(from, to));
                    }
                }
                if (circuit.components.isEmpty()) return null;
                return circuit;
            } catch (Exception e) {
                return null;
            }
        }
    }
}
