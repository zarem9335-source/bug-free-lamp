package com.ekts.app.model;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * شبیه‌ساز مدار فرمان (Ladder) — جاوا
 * تایمر وصل: بعد از delaySec ثانیه عمل می‌کند.
 * تایمر قطع: تا وقتی برق دارد بسته است و بعد از قطع هم تا delaySec ثانیه بسته می‌ماند.
 */
public class SimulationEngine {

    public static class Result {
        public Map<String, Boolean> coilOn = new HashMap<>();
        public Map<String, Boolean> loadOn = new HashMap<>();
        public Set<String> energizedIds = new HashSet<>();
        public Set<String> timerDone = new HashSet<>();
        public String statusFa = "";
    }

    private final Map<String, Long> timerStartMs = new HashMap<>();
    private final Map<String, Long> timerOffEndMs = new HashMap<>();

    public void reset() {
        timerStartMs.clear();
        timerOffEndMs.clear();
    }

    private boolean isLoad(CircuitModel.ComponentType type) {
        return type == CircuitModel.ComponentType.CONTACTOR_COIL
                || type == CircuitModel.ComponentType.RELAY_COIL
                || type == CircuitModel.ComponentType.TIMER_ON
                || type == CircuitModel.ComponentType.TIMER_OFF
                || type == CircuitModel.ComponentType.MOTOR
                || type == CircuitModel.ComponentType.MOTOR_1PH
                || type == CircuitModel.ComponentType.LAMP
                || type == CircuitModel.ComponentType.BUZZER;
    }

    private boolean isClosed(CircuitModel.CircuitComponent comp, Map<String, Boolean> coilOn,
                             Set<String> timerActive, Set<String> pressed) {
        switch (comp.type) {
            case POWER_L:
            case POWER_N:
            case POWER_3PH:
            case FUSE:
                return true;
            case START_NO:
            case JOG_NO:
            case LIMIT_NO:
                return pressed.contains(comp.id);
            case STOP_NC:
            case OVERLOAD_NC:
            case LIMIT_NC:
                return !pressed.contains(comp.id);
            case SWITCH:
            case SWITCH_3P:
                return pressed.contains(comp.id);
            case CONTACT_NO:
                return Boolean.TRUE.equals(coilOn.get(comp.label));
            case CONTACT_NC:
                return !Boolean.TRUE.equals(coilOn.get(comp.label));
            case TIMER_CONTACT_NO:
                return timerActive.contains(comp.label);
            case TIMER_CONTACT_NC:
                return !timerActive.contains(comp.label);
            default:
                return false;
        }
    }

    public Result evaluate(CircuitModel.Circuit circuit, Set<String> pressed, long nowMs) {
        Map<String, Boolean> coilOn = new HashMap<>();
        Set<String> timerDone = new HashSet<>();
        Set<String> timerOff = new HashSet<>();
        Map<String, Boolean> loadOn = new HashMap<>();
        Set<String> energized = new HashSet<>();

        for (int iter = 0; iter < 8; iter++) {
            Set<String> visited = new HashSet<>();
            ArrayDeque<String> queue = new ArrayDeque<>();
            Map<String, Boolean> loads = new HashMap<>();
            Set<String> timerActive = new HashSet<>(timerDone);
            timerActive.addAll(timerOff);

            for (CircuitModel.CircuitComponent src : circuit.components) {
                if (src.type == CircuitModel.ComponentType.POWER_L
                        || src.type == CircuitModel.ComponentType.POWER_3PH) {
                    if (visited.add(src.id)) queue.add(src.id);
                }
            }

            while (!queue.isEmpty()) {
                String curId = queue.removeFirst();
                for (CircuitModel.Wire wire : circuit.wires) {
                    String nextId;
                    if (curId.equals(wire.fromId)) nextId = wire.toId;
                    else if (curId.equals(wire.toId)) nextId = wire.fromId;
                    else continue;
                    if (visited.contains(nextId)) continue;
                    CircuitModel.CircuitComponent next = circuit.component(nextId);
                    if (next == null) continue;
                    if (next.type == CircuitModel.ComponentType.POWER_N) {
                        visited.add(nextId);
                    } else if (isLoad(next.type)) {
                        loads.put(next.id, true);
                        visited.add(nextId);
                    } else if (isClosed(next, coilOn, timerActive, pressed)) {
                        visited.add(nextId);
                        queue.add(nextId);
                    }
                }
            }

            energized = visited;
            loadOn = loads;

            Map<String, Boolean> newCoilOn = new HashMap<>();
            for (Map.Entry<String, Boolean> e : loads.entrySet()) {
                CircuitModel.CircuitComponent comp = circuit.component(e.getKey());
                if (comp != null && e.getValue()
                        && (comp.type == CircuitModel.ComponentType.CONTACTOR_COIL
                        || comp.type == CircuitModel.ComponentType.RELAY_COIL)) {
                    newCoilOn.put(comp.label, true);
                }
            }

            Set<String> newTimerDone = new HashSet<>();
            Set<String> newTimerOff = new HashSet<>();
            for (CircuitModel.CircuitComponent comp : circuit.components) {
                long delayMs = Math.max(500L, (long) (comp.delaySec * 1000f));
                boolean on = Boolean.TRUE.equals(loads.get(comp.id));
                if (comp.type == CircuitModel.ComponentType.TIMER_ON) {
                    if (on) {
                        Long start = timerStartMs.get(comp.label);
                        if (start == null) {
                            start = nowMs;
                            timerStartMs.put(comp.label, start);
                        }
                        if (nowMs - start >= delayMs) newTimerDone.add(comp.label);
                    } else {
                        timerStartMs.remove(comp.label);
                    }
                } else if (comp.type == CircuitModel.ComponentType.TIMER_OFF) {
                    if (on) {
                        newTimerOff.add(comp.label);
                        timerOffEndMs.put(comp.label, nowMs + delayMs);
                    } else {
                        Long end = timerOffEndMs.get(comp.label);
                        if (end != null && nowMs < end) newTimerOff.add(comp.label);
                        else timerOffEndMs.remove(comp.label);
                    }
                }
            }

            boolean changed = !newCoilOn.equals(coilOn)
                    || !newTimerDone.equals(timerDone)
                    || !newTimerOff.equals(timerOff);
            coilOn = newCoilOn;
            timerDone = newTimerDone;
            timerOff = newTimerOff;
            if (!changed) break;
        }

        boolean motors = false, lamps = false, buzzers = false;
        for (Map.Entry<String, Boolean> e : loadOn.entrySet()) {
            if (!e.getValue()) continue;
            CircuitModel.CircuitComponent comp = circuit.component(e.getKey());
            if (comp == null) continue;
            if (comp.type == CircuitModel.ComponentType.MOTOR
                    || comp.type == CircuitModel.ComponentType.MOTOR_1PH) motors = true;
            if (comp.type == CircuitModel.ComponentType.LAMP) lamps = true;
            if (comp.type == CircuitModel.ComponentType.BUZZER) buzzers = true;
        }

        List<String> timerList = new ArrayList<>(timerDone);
        timerList.addAll(timerOff);
        StringBuilder status = new StringBuilder();
        if (!coilOn.isEmpty()) status.append("بوبین روشن: ").append(join(coilOn.keySet())).append("  •  ");
        if (motors) status.append("موتور در حال کار است  •  ");
        if (lamps) status.append("لامپ روشن است  •  ");
        if (buzzers) status.append("آژیر روشن است  •  ");
        if (!timerList.isEmpty()) status.append("تایمر عمل کرد: ").append(join(timerList)).append("  •  ");
        if (coilOn.isEmpty() && !motors && !lamps && !buzzers) status.append("مدار خاموش است — شستی استارت را نگه دار");

        Result r = new Result();
        r.coilOn = coilOn;
        r.loadOn = loadOn;
        r.energizedIds = energized;
        r.timerDone = new HashSet<>(timerDone);
        r.timerDone.addAll(timerOff);
        String s = status.toString().trim();
        if (s.endsWith("•")) s = s.substring(0, s.length() - 1).trim();
        r.statusFa = s.isEmpty() ? "مدار خاموش است" : s;
        return r;
    }

    private String join(Iterable<String> items) {
        StringBuilder sb = new StringBuilder();
        for (String s : items) {
            if (sb.length() > 0) sb.append("، ");
            sb.append(s);
        }
        return sb.toString();
    }
}
