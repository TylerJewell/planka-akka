package io.akka.planka.domain;

/** A List belongs to one Board and holds Cards. This slice's Lists are always {@code active}. */
public record BoardList(String id, String boardId, String name, double position) {
  public BoardList withPosition(double position) {
    return new BoardList(id, boardId, name, position);
  }
}
