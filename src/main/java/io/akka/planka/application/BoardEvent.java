package io.akka.planka.application;

import akka.javasdk.annotations.TypeName;
import io.akka.planka.domain.BoardList;
import io.akka.planka.domain.Card;
import java.util.Map;

public sealed interface BoardEvent {
  @TypeName("board-created")
  record BoardCreated(String boardId, String name) implements BoardEvent {}

  /** {@code repositions} carries any sibling Lists renumbered by the same insert (R2/R4). */
  @TypeName("list-created")
  record ListCreated(BoardList list, Map<String, Double> repositions) implements BoardEvent {}

  @TypeName("list-repositioned")
  record ListRepositioned(String listId, double position, Map<String, Double> repositions)
      implements BoardEvent {}

  /** {@code repositions} carries any sibling Cards renumbered by the same insert (R2/R4). */
  @TypeName("card-created")
  record CardCreated(Card card, Map<String, Double> repositions) implements BoardEvent {}

  /**
   * Covers both a same-list reposition and a cross-list move: {@code listId}/{@code boardId}
   * are always the card's resulting list and board, even when unchanged (R5).
   */
  @TypeName("card-moved")
  record CardMoved(String cardId, String listId, String boardId, double position,
                    Map<String, Double> repositions) implements BoardEvent {}
}
