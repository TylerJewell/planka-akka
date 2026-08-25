package io.akka.planka.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import akka.javasdk.testkit.TestKitSupport;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Through the real HTTP JSON layer, not ComponentClient — PIPELINE.md warns that a
 * non-path parameter left unread from the request body compiles clean and passes any test
 * that calls the entity directly, so R5's cross-list move is exercised here as a real PATCH.
 */
public class BoardEndpointIntegrationTest extends TestKitSupport {

  @Test
  public void movesACardAcrossListsThroughRealHttp() {
    var board = httpClient.POST("/boards").withRequestBody(Map.of("name", "Sprint board"))
        .responseBodyAs(Map.class).invoke().body();
    var boardId = (String) board.get("id");

    var todo = httpClient.POST("/boards/" + boardId + "/lists")
        .withRequestBody(Map.of("name", "To Do", "position", 0))
        .responseBodyAs(Map.class).invoke().body();
    var doing = httpClient.POST("/boards/" + boardId + "/lists")
        .withRequestBody(Map.of("name", "Doing", "position", 100))
        .responseBodyAs(Map.class).invoke().body();

    var card = httpClient.POST("/lists/" + todo.get("id") + "/cards")
        .withRequestBody(Map.of("name", "Ship it", "description", "", "position", 0))
        .responseBodyAs(Map.class).invoke().body();

    var moved = httpClient.PATCH("/cards/" + card.get("id"))
        .withRequestBody(Map.of("listId", doing.get("id"), "position", 50))
        .responseBodyAs(Map.class).invoke().body();

    assertEquals(doing.get("id"), moved.get("listId"));
    assertEquals(boardId, moved.get("boardId"));
    assertEquals(50.0, ((Number) moved.get("position")).doubleValue());

    var refreshed = httpClient.GET("/boards/" + boardId)
        .responseBodyAs(Map.class).invoke().body();
    var lists = (List<Map>) refreshed.get("lists");
    var cards = (List<Map>) refreshed.get("cards");
    assertEquals(2, lists.size());
    assertEquals(1, cards.size());
    assertEquals(doing.get("id"), cards.get(0).get("listId"));
  }

  @Test
  public void readsBackABoard() {
    var board = httpClient.POST("/boards").withRequestBody(Map.of("name", "Readback board"))
        .responseBodyAs(Map.class).invoke().body();
    var boardId = (String) board.get("id");

    var read = httpClient.GET("/boards/" + boardId).responseBodyAs(Map.class).invoke().body();

    assertEquals(boardId, read.get("id"));
    assertEquals("Readback board", read.get("name"));
    assertTrue(read.containsKey("lists"));
    assertTrue(read.containsKey("cards"));
  }
}
