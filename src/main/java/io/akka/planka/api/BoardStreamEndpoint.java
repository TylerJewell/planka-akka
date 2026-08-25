package io.akka.planka.api;

import akka.NotUsed;
import akka.http.javadsl.model.HttpResponse;
import akka.japi.Pair;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.http.HttpResponses;
import akka.stream.javadsl.Source;
import io.akka.planka.application.BoardBroadcast;
import io.akka.planka.application.BoardEntity;
import java.util.Optional;

/**
 * The realtime feed for one board (SPEC-001 R5, R7, R8).
 *
 * <p>The first frame is the board's whole current state, so a client that has just connected —
 * or reconnected — never needs a second request to know what is there (R7). After that a frame
 * is pushed for each change, and because a change and the sibling repositions it caused are
 * one state rather than a message each, a watcher cannot see one without the other (R5).
 *
 * <p>Nothing here is on a timer. The stream waits on the board's own changes, so a board
 * nobody is changing produces no work, and a change is not held back by an interval.
 *
 * <p>The path names one board, so a client watching another board never shares a stream with
 * this one (R8).
 */
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
@HttpEndpoint("")
public class BoardStreamEndpoint {

  private final ComponentClient componentClient;

  public BoardStreamEndpoint(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  @Get("/boards/{boardId}/stream")
  public HttpResponse stream(String boardId) {
    var current = BoardResponse.of(
        componentClient.forEventSourcedEntity(boardId).method(BoardEntity::getBoard).invoke());

    Source<BoardResponse, NotUsed> updates =
        Source.lazySource(() -> {
              var subscriber = BoardBroadcast.subscribe(boardId);
              return Source.<BoardSubscription, BoardResponse>unfoldAsync(
                      new BoardSubscription(boardId, subscriber),
                      open -> open.subscriber().next()
                          .thenApply(state -> Optional.of(Pair.create(open,
                              BoardResponse.of(state)))))
                  .watchTermination((ignored, done) -> {
                    done.whenComplete((ok, failure) ->
                        BoardBroadcast.unsubscribe(boardId, subscriber));
                    return NotUsed.getInstance();
                  });
            })
            .mapMaterializedValue(ignored -> NotUsed.getInstance());

    // A change applied between reading the current state and subscribing would arrive as a
    // frame identical to the first one. Dropping a repeat is cheaper for a watcher than
    // rendering the same board twice, and no rule distinguishes them: a frame is a state.
    var frames = Source.single(current).concat(updates)
        .statefulMapConcat(() -> {
          var previous = new BoardResponse[1];
          return frame -> {
            if (frame.equals(previous[0])) {
              return java.util.List.of();
            }
            previous[0] = frame;
            return java.util.List.of(frame);
          };
        });

    return HttpResponses.serverSentEvents(frames);
  }

  /** The pair the unfold carries: which board, and this connection's own mailbox. */
  private record BoardSubscription(String boardId, BoardBroadcast.Subscriber subscriber) {}
}
