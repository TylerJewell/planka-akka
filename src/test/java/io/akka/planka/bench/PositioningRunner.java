package io.akka.planka.bench;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.akka.planka.domain.Positioning;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The port side of the comparison in bench/REPORT.md §1, in the same shape the source-side
 * probe writes: a JSON array of cases in, a JSON array of answers out.
 *
 * <p>Lives under test sources because it is measuring apparatus, not part of the service. It
 * calls {@link Positioning} directly, which is the class the source's own helper is being
 * compared against — nothing stands in for it.
 */
public final class PositioningRunner {

  private PositioningRunner() {}

  private static final ObjectMapper JSON = new ObjectMapper();

  public static void main(String[] args) throws Exception {
    var cases = read(System.in);
    var answers = new ArrayList<Map<String, Object>>();
    for (var each : cases) {
      answers.add(answer(each));
    }
    write(System.out, answers);
  }

  @SuppressWarnings("unchecked")
  static List<Map<String, Object>> read(InputStream in) throws Exception {
    return JSON.readValue(new String(in.readAllBytes(), StandardCharsets.UTF_8), List.class);
  }

  static void write(PrintStream out, Object value) throws Exception {
    out.print(JSON.writeValueAsString(value));
    out.flush();
  }

  @SuppressWarnings("unchecked")
  static Map<String, Object> answer(Map<String, Object> each) {
    var result = Positioning.insert(siblings(each), ((Number) each.get("position")).doubleValue());
    var repositions = new ArrayList<Map<String, Object>>();
    result.repositions().forEach((id, position) ->
        repositions.add(Map.of("id", id, "position", position)));
    return Map.of("position", result.position(), "repositions", repositions);
  }

  @SuppressWarnings("unchecked")
  static List<Positioning.Sibling> siblings(Map<String, Object> each) {
    var records = new ArrayList<Positioning.Sibling>();
    for (var record : (List<Map<String, Object>>) each.get("records")) {
      records.add(new Positioning.Sibling((String) record.get("id"),
          ((Number) record.get("position")).doubleValue()));
    }
    return records;
  }
}
