package io.akka.planka.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import akka.javasdk.testkit.TestKitSupport;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/** SPEC-001 R4, R6, R7, over the real SSE endpoint. */
public class BoardStreamIntegrationTest extends TestKitSupport {

  private static final Duration WAIT = Duration.ofSeconds(20);

  private String createBoard(String name) {
    var board = httpClient.POST("/boards").withRequestBody(Map.of("name", name))
        .responseBodyAs(Map.class).invoke().body();
    return (String) board.get("id");
  }

  @Test
  public void subscriberReceivesFullStateFirstThenTheMutation() throws Exception {
    var boardId = createBoard("Streamed board");

    var frames = CompletableFuture.supplyAsync(() ->
        testKit.getSelfSseRouteTester()
            .receiveFirstN("/boards/" + boardId + "/stream", 2, WAIT));

    Thread.sleep(500);
    httpClient.POST("/boards/" + boardId + "/lists")
        .withRequestBody(Map.of("name", "To Do", "position", 0)).invoke();

    var received = frames.get(30, TimeUnit.SECONDS);
    assertEquals(2, received.size());

    var first = received.get(0).getData();
    var second = received.get(1).getData();
    assertTrue(first.contains(boardId), "R6: the first frame already carries the board");
    assertTrue(second.contains("To Do"), "R4: the change arrived without asking again");
  }

  @Test
  public void reconnectingClientSeesCurrentStateNotADiff() {
    var boardId = createBoard("Rejoin board");
    httpClient.POST("/boards/" + boardId + "/lists")
        .withRequestBody(Map.of("name", "Backlog", "position", 0)).invoke();

    var frames = testKit.getSelfSseRouteTester()
        .receiveFirstN("/boards/" + boardId + "/stream", 1, WAIT);

    assertEquals(1, frames.size());
    assertTrue(frames.get(0).getData().contains("Backlog"));
  }

  @Test
  public void subscriberToADifferentBoardNeverSeesThisBoardsEvents() throws Exception {
    var boardA = createBoard("Board A");
    var boardB = createBoard("Board B");

    var framesB = CompletableFuture.supplyAsync(() ->
        testKit.getSelfSseRouteTester()
            .receiveFirstN("/boards/" + boardB + "/stream", 1, WAIT));

    Thread.sleep(500);
    httpClient.POST("/boards/" + boardA + "/lists")
        .withRequestBody(Map.of("name", "Only on A", "position", 0)).invoke();

    var received = framesB.get(30, TimeUnit.SECONDS);
    assertEquals(1, received.size(), "board B's stream should only carry its own state");
    assertTrue(received.get(0).getData().contains(boardB));
    assertTrue(!received.get(0).getData().contains("Only on A"));
  }
}
