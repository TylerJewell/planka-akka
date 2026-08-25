package io.akka.planka.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * SPEC-001 R1-R4, over the values plankanban/planka's own helper produced when run.
 *
 * <p>Every expected value here was read off `probes/probe_01.py`'s run of
 * `server/api/helpers/utils/insert-to-positionables.js` rather than derived from a reading of
 * it — three of the five cases below answer differently from what a description of the
 * algorithm predicts.
 */
public class PositioningTest {

  private static Positioning.Sibling at(String id, double position) {
    return new Positioning.Sibling(id, position);
  }

  @Test
  public void insertWithClearanceLeavesEveryoneAlone() {
    // R1: exactly MIN_GAP of clearance is enough clearance. One sibling sits MIN_GAP above
    // the requested position and does not move.
    var result = Positioning.insert(List.of(at("a", 1.125)), 1.0);

    assertEquals(1.0, result.position());
    assertTrue(result.repositions().isEmpty());
  }

  @Test
  public void tightRunIsPushedAlongInGapSteps() {
    // R2: four siblings 0.03 apart with one well clear of them. The run to the right of the
    // insert is pushed along in GAP steps; the new record is inside that run, so it does not
    // keep the position it asked for; `e` at 500000 never moves.
    var result = Positioning.insert(
        List.of(at("a", 0.0), at("b", 0.03), at("c", 0.06), at("d", 0.09), at("e", 500000)),
        0.03);

    assertEquals(49152.0, result.position());
    assertEquals(16384.0, result.repositions().get("a"));
    assertEquals(32768.0, result.repositions().get("b"));
    assertEquals(65536.0, result.repositions().get("c"));
    assertEquals(282768.0, result.repositions().get("d"));
    assertTrue(!result.repositions().containsKey("e"));
  }

  @Test
  public void aWideEnoughStraddleTakesAMidpoint() {
    // R2: where the next two positions straddle a space at least twice MIN_GAP wide, the run
    // stops there and that sibling takes the midpoint rather than the next GAP step. A
    // description of the algorithm as "renumber at multiples of GAP" has no such case.
    var result = Positioning.insert(List.of(at("a", 1.0), at("b", 1.05), at("c", 40000)), 1.0);

    assertEquals(16385.0, result.position());
    assertEquals(28192.5, result.repositions().get("b"));
    assertTrue(!result.repositions().containsKey("c"));
  }

  @Test
  public void ceilingRenumbersTheWholeSet() {
    // R3: a cluster at MAX_POSITION cannot be pushed along, so every sibling is renumbered
    // from scratch at GAP multiples in existing order — including `a` and `b`, which had all
    // the room they needed.
    var ceiling = 1125899906842623.0;
    var result = Positioning.insert(
        List.of(at("a", 1000), at("b", 2000), at("c", ceiling), at("d", ceiling + 0.05)),
        ceiling);

    assertEquals(81920.0, result.position());
    assertEquals(16384.0, result.repositions().get("a"));
    assertEquals(32768.0, result.repositions().get("b"));
    assertEquals(49152.0, result.repositions().get("c"));
    assertEquals(65536.0, result.repositions().get("d"));
  }

  @Test
  public void tiedSiblingsAreServedFromTheRight() {
    // R2/R4: three siblings sharing one position. Which of them lands where is decided by the
    // order they arrive in, not by their positions — the positions are the tie. The last
    // record to arrive takes the highest of the replacements minted for that position.
    var result = Positioning.insert(
        List.of(at("a", 0.05), at("b", 0.05), at("c", 0.05)), 0.05);

    assertEquals(65536.0, result.position());
    assertEquals(16384.0, result.repositions().get("a"));
    assertEquals(32768.0, result.repositions().get("b"));
    assertEquals(49152.0, result.repositions().get("c"));
  }
}
