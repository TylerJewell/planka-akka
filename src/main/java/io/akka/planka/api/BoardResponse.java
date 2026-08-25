package io.akka.planka.api;

import io.akka.planka.application.BoardState;
import io.akka.planka.domain.BoardList;
import io.akka.planka.domain.Card;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * What a caller of this service sees. Kept separate from the domain records so a change to
 * either can be made without silently changing the other.
 *
 * <p>Lists and cards come out ordered by position, which is what the position field is for:
 * a caller that had to sort them itself would be reimplementing the ordering rule this port
 * exists to hold.
 */
public record BoardResponse(String id, String name, List<ListResponse> lists,
                             List<CardResponse> cards) {

  public record ListResponse(String id, String boardId, String name, double position,
                              String type) {
    static ListResponse of(BoardList list) {
      return new ListResponse(list.id(), list.boardId(), list.name(), list.position(), "active");
    }
  }

  public record CardResponse(String id, String boardId, String listId, String name,
                              Optional<String> description, double position) {
    static CardResponse of(Card card) {
      return new CardResponse(card.id(), card.boardId(), card.listId(), card.name(),
          Optional.ofNullable(card.description()), card.position());
    }
  }

  public static BoardResponse of(BoardState state) {
    return new BoardResponse(
        state.id(),
        state.name(),
        state.lists().values().stream()
            .sorted(Comparator.comparingDouble(BoardList::position))
            .map(ListResponse::of).toList(),
        state.cards().values().stream()
            .sorted(Comparator.comparingDouble(Card::position))
            .map(CardResponse::of).toList());
  }
}
