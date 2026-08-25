package io.akka.planka.application;

import akka.javasdk.annotations.Component;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import io.akka.planka.domain.BoardList;
import io.akka.planka.domain.Card;
import io.akka.planka.domain.Positioning;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.UUID;

/**
 * One Board, holding its Lists and Cards as part of its own state. Kept as a single
 * aggregate rather than separate entities per List/Card because a sibling reposition
 * triggered by an insert (R2/R4) must land in the same event as the insert itself, and an
 * event sourced entity can only persist one atomic event per command.
 */
@Component(id = "board")
public class BoardEntity extends EventSourcedEntity<BoardState, BoardEvent> {

  public record CreateBoard(String name) {}

  public record CreateList(String name, double position) {}

  public record CreateCard(String listId, String name, String description, double position) {}

  public record MoveCard(String cardId, String listId, double position) {}

  public record RepositionList(String listId, double position) {}

  /**
   * A List or Card id carries its Board's id ahead of a {@value #ID_SEPARATOR}. An endpoint
   * whose route names only the child ({@code PATCH /cards/{cardId}}) has to address the Board
   * entity that owns it, and the id is the only thing such a request carries.
   */
  public static final String ID_SEPARATOR = "~";

  public static String boardIdOf(String childId) {
    int at = childId.indexOf(ID_SEPARATOR);
    return at < 0 ? null : childId.substring(0, at);
  }

  private String childId() {
    return currentState().id() + ID_SEPARATOR + UUID.randomUUID();
  }


  /**
   * Siblings in the order the algorithm is entitled to see them: by position, then by id.
   *
   * <p>This is not cosmetic. The positioning algorithm reads its record list in order and its
   * answer depends on that order wherever two records share a position (SPEC-001 R4), so the
   * order is an input. The source fixes it in the query it hands the helper —
   * `Card.qm.getByListId` and `List.qm.getByBoardId` both sort `['position', 'id']` — and an
   * entity holding its records in a map would otherwise present them in whatever order they
   * were created.
   */
  private static List<Positioning.Sibling> ordered(Stream<Positioning.Sibling> siblings) {
    return siblings
        .sorted(Comparator.comparingDouble(Positioning.Sibling::position)
            .thenComparing(Positioning.Sibling::id))
        .toList();
  }

  @Override
  public BoardState emptyState() {
    return BoardState.empty();
  }

  public Effect<String> createBoard(CreateBoard command) {
    if (currentState().exists()) {
      return effects().error("board already exists");
    }
    return effects()
        .persist(new BoardEvent.BoardCreated(commandContext().entityId(), command.name()))
        .thenReply(BoardState::id);
  }

  public ReadOnlyEffect<BoardState> getBoard() {
    if (!currentState().exists()) {
      return effects().error("board not found");
    }
    return effects().reply(currentState());
  }

  public Effect<BoardList> createList(CreateList command) {
    if (!currentState().exists()) {
      return effects().error("board not found");
    }
    var siblings = ordered(currentState().lists().values().stream()
        .map(l -> new Positioning.Sibling(l.id(), l.position())));
    var result = Positioning.insert(siblings, command.position());
    var list = new BoardList(childId(), currentState().id(), command.name(),
        result.position());
    return effects()
        .persist(new BoardEvent.ListCreated(list, result.repositions()))
        .thenReply(s -> s.lists().get(list.id()));
  }

  public Effect<BoardList> repositionList(RepositionList command) {
    var list = currentState().lists().get(command.listId());
    if (list == null) {
      return effects().error("list not found");
    }
    var siblings = ordered(currentState().lists().values().stream()
        .filter(l -> !l.id().equals(command.listId()))
        .map(l -> new Positioning.Sibling(l.id(), l.position())));
    var result = Positioning.insert(siblings, command.position());
    return effects()
        .persist(new BoardEvent.ListRepositioned(command.listId(), result.position(),
            result.repositions()))
        .thenReply(s -> s.lists().get(command.listId()));
  }

  public Effect<Card> createCard(CreateCard command) {
    var list = currentState().lists().get(command.listId());
    if (list == null) {
      return effects().error("list not found");
    }
    var siblings = ordered(currentState().cards().values().stream()
        .filter(c -> c.listId().equals(command.listId()))
        .map(c -> new Positioning.Sibling(c.id(), c.position())));
    var result = Positioning.insert(siblings, command.position());
    var card = new Card(childId(), list.boardId(), list.id(), command.name(),
        command.description(), result.position());
    return effects()
        .persist(new BoardEvent.CardCreated(card, result.repositions()))
        .thenReply(s -> s.cards().get(card.id()));
  }

  /** R5: listId and boardId (denormalized from the target list) land in the same event. */
  public Effect<Card> moveCard(MoveCard command) {
    var card = currentState().cards().get(command.cardId());
    if (card == null) {
      return effects().error("card not found");
    }
    var targetList = currentState().lists().get(command.listId());
    if (targetList == null) {
      return effects().error("list not found");
    }
    var siblings = ordered(currentState().cards().values().stream()
        .filter(c -> c.listId().equals(command.listId()) && !c.id().equals(command.cardId()))
        .map(c -> new Positioning.Sibling(c.id(), c.position())));
    var result = Positioning.insert(siblings, command.position());
    return effects()
        .persist(new BoardEvent.CardMoved(command.cardId(), targetList.id(), targetList.boardId(),
            result.position(), result.repositions()))
        .thenReply(s -> s.cards().get(command.cardId()));
  }

  @Override
  public BoardState applyEvent(BoardEvent event) {
    var state = currentState();
    return switch (event) {
      case BoardEvent.BoardCreated e ->
          new BoardState(e.boardId(), e.name(), Map.of(), Map.of());

      case BoardEvent.ListCreated e -> {
        var lists = state.mutableLists();
        applyListRepositions(lists, e.repositions());
        lists.put(e.list().id(), e.list());
        yield new BoardState(state.id(), state.name(), lists, state.cards());
      }

      case BoardEvent.ListRepositioned e -> {
        var lists = state.mutableLists();
        applyListRepositions(lists, e.repositions());
        lists.computeIfPresent(e.listId(), (id, l) -> l.withPosition(e.position()));
        yield new BoardState(state.id(), state.name(), lists, state.cards());
      }

      case BoardEvent.CardCreated e -> {
        var cards = state.mutableCards();
        applyCardRepositions(cards, e.repositions());
        cards.put(e.card().id(), e.card());
        yield new BoardState(state.id(), state.name(), state.lists(), cards);
      }

      case BoardEvent.CardMoved e -> {
        var cards = state.mutableCards();
        applyCardRepositions(cards, e.repositions());
        cards.computeIfPresent(e.cardId(),
            (id, c) -> c.movedTo(e.listId(), e.boardId(), e.position()));
        yield new BoardState(state.id(), state.name(), state.lists(), cards);
      }
    };
  }

  private void applyListRepositions(Map<String, BoardList> lists,
                                     Map<String, Double> repositions) {
    repositions.forEach((id, pos) -> lists.computeIfPresent(id, (i, l) -> l.withPosition(pos)));
  }

  private void applyCardRepositions(Map<String, Card> cards, Map<String, Double> repositions) {
    repositions.forEach((id, pos) -> cards.computeIfPresent(id, (i, c) -> c.withPosition(pos)));
  }
}
