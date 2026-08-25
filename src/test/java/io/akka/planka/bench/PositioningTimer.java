package io.akka.planka.bench;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.akka.planka.domain.Positioning;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Times {@link Positioning#insert} on the port side, in the shape `toolkit/timing_check.py`
 * reads.
 *
 * <p>Three things this has to get right, all of them JIT problems rather than clock problems:
 *
 * <ul>
 *   <li>The window is sized from a pilot to run for tens of milliseconds, so the figure is a
 *       window divided by what was in it rather than a reading of the platform clock's own
 *       resolution.
 *   <li>The result is accumulated into a sink that is printed afterwards, so the call cannot
 *       be proven dead and deleted.
 *   <li>The arguments are cycled over every case in the workload rather than one repeated
 *       case, so the call cannot be proven loop-invariant and hoisted out. Reading the result
 *       is not enough on its own: a loop-invariant call folds just as freely as an unread one
 *       is deleted.
 * </ul>
 */
public final class PositioningTimer {

  private PositioningTimer() {}

  private static final ObjectMapper JSON = new ObjectMapper();

  /** Tens of milliseconds: long enough to outlast the platform clock's own resolution. */
  private static final long TARGET_NANOS = 50_000_000L;

  private static final int WINDOWS = 5;
  private static final int WARMUP_WINDOWS = 3;
  private static final int MAX_DOUBLINGS = 24;

  static double sink;

  @SuppressWarnings("unchecked")
  public static void main(String[] args) throws Exception {
    var document = (Map<String, Object>) JSON.readValue(
        new String(System.in.readAllBytes(), StandardCharsets.UTF_8), Map.class);

    var timing = new LinkedHashMap<String, Object>();
    for (var entry : ((Map<String, Object>) document.get("workloads")).entrySet()) {
      var cases = new ArrayList<List<Positioning.Sibling>>();
      var positions = new ArrayList<Double>();
      for (var each : (List<Map<String, Object>>) entry.getValue()) {
        cases.add(PositioningRunner.siblings(each));
        positions.add(((Number) each.get("position")).doubleValue());
      }
      timing.put(entry.getKey(), time(cases, positions));
    }

    var out = new LinkedHashMap<String, Object>();
    out.put("timing", timing);
    out.put("sink", sink);
    PositioningRunner.write(System.out, out);
  }

  private static Map<String, Object> time(List<List<Positioning.Sibling>> cases,
                                           List<Double> positions) {
    int repetitions = 1;
    long window = 0;
    for (int i = 0; i < MAX_DOUBLINGS && window < TARGET_NANOS; i++) {
      repetitions *= 2;
      window = runWindow(cases, positions, repetitions);
    }

    for (int i = 0; i < WARMUP_WINDOWS; i++) {
      runWindow(cases, positions, repetitions);
    }

    var readings = new long[WINDOWS];
    for (int i = 0; i < WINDOWS; i++) {
      readings[i] = runWindow(cases, positions, repetitions);
    }
    Arrays.sort(readings);
    long median = readings[WINDOWS / 2];

    var row = new LinkedHashMap<String, Object>();
    row.put("repetitions", repetitions);
    row.put("windows", WINDOWS);
    row.put("windowNanos", median);
    row.put("nanosPerRun", median / (double) repetitions);
    return row;
  }

  private static long runWindow(List<List<Positioning.Sibling>> cases, List<Double> positions,
                                 int repetitions) {
    double total = 0;
    long started = System.nanoTime();
    for (int i = 0; i < repetitions; i++) {
      int at = i % cases.size();
      total += Positioning.insert(cases.get(at), positions.get(at)).position();
    }
    long elapsed = System.nanoTime() - started;
    sink += total;
    return elapsed;
  }
}
