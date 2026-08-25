package io.akka.planka.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import akka.javasdk.testkit.EventSourcedTestKit;
import org.junit.jupiter.api.Test;

/**
 * SPEC-001 over the entity: creating lists and cards, moving a card between lists, and the
 * sibling repositions an insert produces.
 *
 * <p>Every position asserted here is what plankanban/planka's own helper answered when the same
 * sequence was run through it — see `planka-port/probes/probe_01.py` and the cumulative
 * workload in `planka-port/bench/workloads.json`. Asking for position 0 does not give position
 * 0: the helper answers 16384 for the first record and then halves the space below the record
 * ahead of it, which is the rule this port exists to hold and is invisible in any single case.
 */
public class BoardEntityTest {

  static EventSourcedTestKit<BoardState, BoardEvent, BoardEntity> seeded() {
    var kit = EventSourcedTestKit.of("board-1", BoardEntity::new);
    kit.method(BoardEntity::createBoard).invoke(new BoardEntity.CreateBoard("Test board"));
    return kit;
  }

  @Test
  public void createsAList() {
    var kit = seeded();
    var list = kit.method(BoardEntity::createList)
        .invoke(new BoardEntity.CreateList("To Do", 0)).getReply();

    assertEquals("To Do", list.name());
    assertEquals("board-1", list.boardId());
    assertEquals(16384.0, list.position());
    assertTrue(kit.getState().lists().containsKey(list.id()));
  }

  @Test
  public void createsACardInAList() {
    var kit = seeded();
    var list = kit.method(BoardEntity::createList)
        .invoke(new BoardEntity.CreateList("To Do", 0)).getReply();

    var card = kit.method(BoardEntity::createCard)
        .invoke(new BoardEntity.CreateCard(list.id(), "Write tests", "desc", 100))
        .getReply();

    assertEquals(list.id(), card.listId());
    assertEquals("board-1", card.boardId());
    // 100 is kept: the list is empty, so there is no neighbour to be too close to.
    assertEquals(100.0, card.position());
  }

  @Test
  public void movesACardAcrossListsUpdatingListAndBoardId() {
    var kit = seeded();
    var todo = kit.method(BoardEntity::createList)
        .invoke(new BoardEntity.CreateList("To Do", 0)).getReply();
    var doing = kit.method(BoardEntity::createList)
        .invoke(new BoardEntity.CreateList("Doing", 100)).getReply();
    var card = kit.method(BoardEntity::createCard)
        .invoke(new BoardEntity.CreateCard(todo.id(), "Ship it", null, 0)).getReply();

    var moved = kit.method(BoardEntity::moveCard)
        .invoke(new BoardEntity.MoveCard(card.id(), doing.id(), 50)).getReply();

    assertEquals(doing.id(), moved.listId());
    assertEquals("board-1", moved.boardId());
    // The destination list is empty, so the requested position stands.
    assertEquals(50.0, moved.position());
  }

  @Test
  public void insertingBetweenTwoCardsHalvesTheSpaceRatherThanMovingThem() {
    var kit = seeded();
    var list = kit.method(BoardEntity::createList)
        .invoke(new BoardEntity.CreateList("To Do", 0)).getReply();
    var c1 = kit.method(BoardEntity::createCard)
        .invoke(new BoardEntity.CreateCard(list.id(), "A", null, 0)).getReply();
    var c2 = kit.method(BoardEntity::createCard)
        .invoke(new BoardEntity.CreateCard(list.id(), "B", null, 0.1)).getReply();

    // A asked for 0 and got 16384; B asked for 0.1 and got the midpoint below A, 8192. C now
    // asks for 0.05, which is below both, and gets the midpoint below B: 4096. Nothing moves —
    // the space below the lowest record is what absorbs the insert, and this is the case a
    // reading of the algorithm as "renumber the neighbours" predicts wrongly.
    var c3 = kit.method(BoardEntity::createCard)
        .invoke(new BoardEntity.CreateCard(list.id(), "C", null, 0.05)).getReply();

    var state = kit.getState();
    assertEquals(16384.0, c1.position());
    assertEquals(8192.0, c2.position());
    assertEquals(4096.0, c3.position());
    assertEquals(16384.0, state.cards().get(c1.id()).position());
    assertEquals(8192.0, state.cards().get(c2.id()).position());
  }
}
