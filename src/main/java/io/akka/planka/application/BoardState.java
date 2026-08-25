package io.akka.planka.application;

import io.akka.planka.domain.BoardList;
import io.akka.planka.domain.Card;
import java.util.LinkedHashMap;
import java.util.Map;

public record BoardState(String id, String name, Map<String, BoardList> lists,
                          Map<String, Card> cards) {

  public static BoardState empty() {
    return new BoardState(null, null, Map.of(), Map.of());
  }

  public boolean exists() {
    return id != null;
  }

  public Map<String, BoardList> mutableLists() {
    return new LinkedHashMap<>(lists);
  }

  public Map<String, Card> mutableCards() {
    return new LinkedHashMap<>(cards);
  }
}
