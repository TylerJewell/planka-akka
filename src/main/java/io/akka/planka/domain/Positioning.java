package io.akka.planka.domain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Assigns a position to a record being inserted among existing siblings, and says which
 * siblings had to move to make room (SPEC-001 R1-R3).
 *
 * <p>Positions are gap-based rather than ranks: a record's position is any real number, and
 * ordering is by comparison. A requested position with at least {@link #MIN_GAP} clearance is
 * taken as it stands. Where it is too close to its neighbours, the tight run to the right of
 * the insertion point is pushed along in steps of {@link #GAP}, stopping as soon as a sibling
 * is reached that already has room, or as soon as two siblings straddle a space wide enough to
 * take a midpoint. Where pushing would carry a position past {@link #MAX_POSITION}, every
 * sibling is renumbered from scratch at multiples of {@link #GAP} in their existing order.
 *
 * <p>A position may be held by more than one sibling, so the working map keyed by position
 * holds a list of replacements rather than one, handed out right-to-left over the records.
 *
 * <p>Pure logic: no entity or framework dependency, so it is unit-testable on its own and is
 * what the benchmark drives on this side.
 */
public final class Positioning {

  public static final double GAP = 16384; // 2^14
  public static final double MIN_GAP = 0.125;
  public static final double MAX_POSITION = 1125899906842624.0; // 2^50

  private Positioning() {}

  public record Sibling(String id, double position) {}

  public record Result(double position, Map<String, Double> repositions) {}

  public static Result insert(List<Sibling> records, double position) {
    var lowers = new ArrayList<Double>();
    var uppers = new ArrayList<Double>();
    for (var record : records) {
      (record.position() <= position ? lowers : uppers).add(record.position());
    }

    var beginnings = findBeginnings(lowers, position);

    var candidate = new ArrayList<Double>(beginnings);
    candidate.addAll(uppers);
    var map = repositionsMap(candidate);
    if (map == null) {
      var all = new ArrayList<Double>(lowers);
      all.add(position);
      all.addAll(uppers);
      map = fullRepositionsMap(all);
    }

    double assigned = pop(map, position, position);

    // Right-to-left, because a replacement list for one position was filled left-to-right and
    // is handed back from its end: the rightmost record of a tie takes the largest new value.
    var reversed = new ArrayList<Map.Entry<String, Double>>();
    for (int i = records.size() - 1; i >= 0; i--) {
      var record = records.get(i);
      var pending = map.get(record.position());
      if (pending == null || pending.isEmpty()) {
        continue;
      }
      reversed.add(Map.entry(record.id(), pending.remove(pending.size() - 1)));
    }
    var repositions = new LinkedHashMap<String, Double>();
    for (int i = reversed.size() - 1; i >= 0; i--) {
      repositions.put(reversed.get(i).getKey(), reversed.get(i).getValue());
    }

    return new Result(assigned, repositions);
  }

  private static double pop(Map<Double, List<Double>> map, double key, double fallback) {
    var pending = map.get(key);
    if (pending == null || pending.isEmpty()) {
      return fallback;
    }
    return pending.remove(pending.size() - 1);
  }

  /**
   * The run of positions at or below the insertion point that is packed too tightly to leave
   * the new record where it asked to go, ending with the requested position itself.
   *
   * <p>A zero is prepended as a floor, so a set whose every member is tight bottoms out there
   * rather than running off the start of the list.
   */
  private static List<Double> findBeginnings(List<Double> lowers, double position) {
    var walked = new ArrayList<Double>();
    walked.add(0.0);
    walked.addAll(lowers);

    double previous = position;
    var beginnings = new ArrayList<Double>();
    beginnings.add(position);
    for (int i = walked.size() - 1; i >= 0; i--) {
      double each = walked.get(i);
      if (previous - MIN_GAP >= each) {
        break;
      }
      previous = each;
      beginnings.add(0, each);
    }
    return beginnings;
  }

  /** Null means no arrangement fits under MAX_POSITION and the whole set must be renumbered. */
  private static Map<Double, List<Double>> repositionsMap(List<Double> positions) {
    var map = new LinkedHashMap<Double, List<Double>>();
    if (positions.size() <= 1) {
      if (!positions.isEmpty() && positions.get(0) > MAX_POSITION) {
        return null;
      }
      return map;
    }

    double previous = positions.get(0);
    for (int i = 1; i < positions.size(); i++) {
      double each = positions.get(i);
      Double next = i + 1 < positions.size() ? positions.get(i + 1) : null;

      if (previous + MIN_GAP <= each) {
        break;
      }
      if (next != null && previous + MIN_GAP * 2 <= next) {
        map.computeIfAbsent(each, k -> new ArrayList<>()).add(previous + (next - previous) / 2);
        break;
      }
      previous += GAP;
      if (previous > MAX_POSITION) {
        return null;
      }
      map.computeIfAbsent(each, k -> new ArrayList<>()).add(previous);
    }
    return map;
  }

  private static Map<Double, List<Double>> fullRepositionsMap(List<Double> positions) {
    var map = new LinkedHashMap<Double, List<Double>>();
    for (int i = 0; i < positions.size(); i++) {
      map.computeIfAbsent(positions.get(i), k -> new ArrayList<>()).add(GAP * (i + 1));
    }
    return map;
  }
}
