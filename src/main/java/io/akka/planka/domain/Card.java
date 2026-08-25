package io.akka.planka.domain;

/**
 * A Card belongs to one List. {@code boardId} is denormalized from the owning List so a
 * card's board is known without a join (SPEC-001 R5).
 */
public record Card(String id, String boardId, String listId, String name, String description,
                    double position) {
  public Card withPosition(double position) {
    return new Card(id, boardId, listId, name, description, position);
  }

  public Card movedTo(String listId, String boardId, double position) {
    return new Card(id, boardId, listId, name, description, position);
  }
}
