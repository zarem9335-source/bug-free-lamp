package com.ekts.app.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * بررسی خطا — مثل ETKS که «خطاهای احتمالی را پیدا می‌کند».
 */
public class CircuitValidator {

    public static class Issue {
        public boolean isError;
        public String textFa;
        public String componentId;

        public Issue(boolean isError, String textFa, String componentId) {
            this.isError = isError;
            this.textFa = textFa;
            this.componentId = componentId;
        }

        public Issue(boolean isError, String textFa) {
            this(isError, textFa, null);
        }
    }

    public static List<Issue> validate(CircuitModel.Circuit circuit) {
        List<Issue> issues = new ArrayList<>();
        if (circuit.components.isEmpty()) {
            issues.add(new Issue(true, "مدار خالی است — اول یک قطعه اضافه کن."));
            return issues;
        }
        boolean hasPower = false;
        boolean hasLoad = false;
        for (CircuitModel.CircuitComponent c : circuit.components) {
            if (c.type == CircuitModel.ComponentType.POWER_L
                    || c.type == CircuitModel.ComponentType.POWER_3PH) hasPower = true;
            if (c.type == CircuitModel.ComponentType.CONTACTOR_COIL
                    || c.type == CircuitModel.ComponentType.RELAY_COIL
                    || c.type == CircuitModel.ComponentType.TIMER_ON
                    || c.type == CircuitModel.ComponentType.TIMER_OFF
                    || c.type == CircuitModel.ComponentType.MOTOR
                    || c.type == CircuitModel.ComponentType.MOTOR_1PH
                    || c.type == CircuitModel.ComponentType.LAMP
                    || c.type == CircuitModel.ComponentType.BUZZER) hasLoad = true;
        }
        if (!hasPower) issues.add(new Issue(true, "تغذیه ندارد — یک «فاز L» یا «برق سه‌فاز» اضافه کن."));
        if (!hasLoad) issues.add(new Issue(true, "بار ندارد — یک بوبین، موتور یا لامپ اضافه کن."));

        Map<String, List<CircuitModel.CircuitComponent>> coilLabels = new LinkedHashMap<>();
        for (CircuitModel.CircuitComponent c : circuit.components) {
            if (c.type == CircuitModel.ComponentType.CONTACTOR_COIL
                    || c.type == CircuitModel.ComponentType.RELAY_COIL
                    || c.type == CircuitModel.ComponentType.TIMER_ON
                    || c.type == CircuitModel.ComponentType.TIMER_OFF) {
                List<CircuitModel.CircuitComponent> list = coilLabels.get(c.label);
                if (list == null) {
                    list = new ArrayList<>();
                    coilLabels.put(c.label, list);
                }
                list.add(c);
            }
        }
        for (Map.Entry<String, List<CircuitModel.CircuitComponent>> e : coilLabels.entrySet()) {
            if (e.getValue().size() > 1) {
                issues.add(new Issue(true, "نام «" + e.getKey() + "» تکراری است — هر بوبین/تایمر باید نام یکتا داشته باشد.", e.getValue().get(1).id));
            }
        }

        for (CircuitModel.CircuitComponent comp : circuit.components) {
            if (comp.type == CircuitModel.ComponentType.CONTACT_NO
                    || comp.type == CircuitModel.ComponentType.CONTACT_NC
                    || comp.type == CircuitModel.ComponentType.TIMER_CONTACT_NO
                    || comp.type == CircuitModel.ComponentType.TIMER_CONTACT_NC) {
                if (!coilLabels.containsKey(comp.label)) {
                    issues.add(new Issue(true, "کنتاکت «" + comp.label + "» بوبین هم‌نام ندارد — اول بوبین/تایمر " + comp.label + " را اضافه کن.", comp.id));
                }
            }
        }

        Set<String> wired = new HashSet<>();
        for (CircuitModel.Wire w : circuit.wires) {
            wired.add(w.fromId);
            wired.add(w.toId);
        }
        for (CircuitModel.CircuitComponent comp : circuit.components) {
            if (!wired.contains(comp.id)) {
                issues.add(new Issue(false, "«" + comp.label + "» (" + comp.type.fa + ") هیچ سیمی ندارد.", comp.id));
            }
        }

        for (CircuitModel.Wire w : circuit.wires) {
            CircuitModel.CircuitComponent a = circuit.component(w.fromId);
            CircuitModel.CircuitComponent b = circuit.component(w.toId);
            if (a != null && b != null) {
                boolean aIsPower = a.type == CircuitModel.ComponentType.POWER_L || a.type == CircuitModel.ComponentType.POWER_3PH;
                boolean bIsPower = b.type == CircuitModel.ComponentType.POWER_L || b.type == CircuitModel.ComponentType.POWER_3PH;
                if ((aIsPower && b.type == CircuitModel.ComponentType.POWER_N)
                        || (bIsPower && a.type == CircuitModel.ComponentType.POWER_N)) {
                    issues.add(new Issue(true, "اتصال کوتاه! فاز مستقیم به نول وصل شده — بین آن‌ها بار یا کلید بگذار.", a.id));
                }
            }
        }

        boolean hasStart = false;
        for (CircuitModel.CircuitComponent c : circuit.components) {
            if (c.type == CircuitModel.ComponentType.START_NO) hasStart = true;
        }
        if (hasStart) {
            for (String label : coilLabels.keySet()) {
                boolean hasHolding = false;
                for (CircuitModel.CircuitComponent c : circuit.components) {
                    if (c.type == CircuitModel.ComponentType.CONTACT_NO && c.label.equals(label)) hasHolding = true;
                }
                if (!hasHolding) {
                    issues.add(new Issue(false, "بوبین «" + label + "» کنتاکت خودنگهدار NO ندارد — بعد از رها کردن استارت خاموش می‌شود."));
                }
            }
        }

        if (issues.isEmpty()) {
            issues.add(new Issue(false, "خطایی پیدا نشد — مدار آماده اجراست."));
        }
        return issues;
    }

    /** لیست قطعات (BOM) — مثل «List Used Elements» در ETKS */
    public static String billOfMaterials(CircuitModel.Circuit circuit) {
        if (circuit.components.isEmpty()) return "مدار خالی است.";
        Map<String, List<CircuitModel.CircuitComponent>> grouped = new LinkedHashMap<>();
        for (CircuitModel.CircuitComponent c : circuit.components) {
            List<CircuitModel.CircuitComponent> list = grouped.get(c.type.fa);
            if (list == null) {
                list = new ArrayList<>();
                grouped.put(c.type.fa, list);
            }
            list.add(c);
        }
        StringBuilder sb = new StringBuilder();
        sb.append("مدار: ").append(circuit.name).append("\n");
        sb.append("تعداد قطعه: ").append(circuit.components.size())
                .append(" — تعداد سیم: ").append(circuit.wires.size()).append("\n\n");
        for (Map.Entry<String, List<CircuitModel.CircuitComponent>> e : grouped.entrySet()) {
            sb.append("• ").append(e.getKey()).append(": ").append(e.getValue().size()).append(" عدد — ");
            for (int i = 0; i < e.getValue().size(); i++) {
                if (i > 0) sb.append("، ");
                sb.append(e.getValue().get(i).label);
            }
            sb.append("\n");
        }
        return sb.toString();
    }
}
