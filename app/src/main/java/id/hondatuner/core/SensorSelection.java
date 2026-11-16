package id.hondatuner.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** User choices survive changing ECU layouts; presentation never resets samples. */
public final class SensorSelection {
    private final List<String> savedOrder;
    private final Set<String> hidden;

    public SensorSelection(Collection<String> order, Collection<String> hidden) {
        savedOrder = clean(order);
        this.hidden = new LinkedHashSet<>(clean(hidden));
    }
    private static List<String> clean(Collection<String> fields) {
        Set<String> unique = new LinkedHashSet<>();
        for (String field : fields) if (field != null && !field.trim().isEmpty()) unique.add(field);
        return new ArrayList<>(unique);
    }
    public List<String> order(Collection<String> available) {
        List<String> remaining = clean(available);
        remaining.sort(Comparator.comparingInt(SensorInfo::priority));
        List<String> result = new ArrayList<>();
        for (String field : savedOrder) if (remaining.remove(field)) result.add(field);
        result.addAll(remaining);
        return result;
    }
    public List<String> visible(Collection<String> available) {
        List<String> result = order(available);
        result.removeAll(hidden);
        return result;
    }
    public boolean isVisible(String field) { return !hidden.contains(field); }
    public void setVisible(String field, boolean visible) {
        if (visible) hidden.remove(field); else hidden.add(field);
    }
    public void move(String field, int direction, Collection<String> available) {
        List<String> current = order(available);
        int index = current.indexOf(field), next = index + direction;
        if (index < 0 || next < 0 || next >= current.size()) return;
        Collections.swap(current, index, next);
        for (String old : savedOrder) if (!current.contains(old)) current.add(old);
        savedOrder.clear(); savedOrder.addAll(current);
    }
    public List<String> savedOrder() { return new ArrayList<>(savedOrder); }
    public Set<String> hidden() { return new LinkedHashSet<>(hidden); }
    public void reset(Collection<String> available) {
        savedOrder.removeAll(available); hidden.removeAll(available);
    }
}
