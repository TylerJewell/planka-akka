package io.akka.planka.application;

import akka.Done;
import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.consumer.Consumer;

/**
 * Turns each change to a Board into one frame for everyone watching that board (SPEC-001 R5).
 *
 * <p>The frame is the board's whole state rather than the event, so a watcher cannot observe
 * an insert without the sibling repositions the same command produced: they are one state, not
 * a message each. The source reaches the same end state as a sequence of one message per
 * repositioned record followed by one for the insert, which is a difference this port declares.
 */
@Component(id = "board-broadcast")
@Consume.FromEventSourcedEntity(BoardEntity.class)
public class BoardBroadcastConsumer extends Consumer {

  private final ComponentClient componentClient;

  public BoardBroadcastConsumer(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public Effect onEvent(BoardEvent event) {
    var boardId = messageContext().eventSubject().orElseThrow();
    var state = componentClient.forEventSourcedEntity(boardId).method(BoardEntity::getBoard)
        .invoke();
    BoardBroadcast.publish(boardId, state);
    return effects().done();
  }
}
