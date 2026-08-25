package io.akka.planka.application;

import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Fan-out of board changes to the clients currently watching that board.
 *
 * <p>A subscriber waits on a promise rather than asking again on a timer, so a change reaches
 * a watcher as soon as it has been applied instead of at the end of whatever interval a poll
 * would have used, and a board nobody is changing costs nothing to watch.
 *
 * <p>The registry is process-local. Every watcher of a board and the consumer that publishes
 * to them must be in the same JVM, which holds for a single-node deployment and does not hold
 * across a cluster — a watcher connected to one node would not see a change applied on
 * another. This is declared in the README rather than hidden: the slice's rule is about what
 * a watcher sees, and this is the boundary within which the rebuild delivers it.
 */
public final class BoardBroadcast {

  private BoardBroadcast() {}

  private static final Map<String, Set<Subscriber>> BY_BOARD = new ConcurrentHashMap<>();

  /**
   * One connected watcher's mailbox.
   *
   * <p>Frames that arrive with nobody waiting are queued; a wait that finds the queue empty
   * parks a promise for the next publisher to complete. Both sides re-check after their own
   * step, because a frame can arrive between the check and the park.
   */
  public static final class Subscriber {
    private final Queue<BoardState> pending = new ConcurrentLinkedQueue<>();
    private final AtomicReference<CompletableFuture<BoardState>> waiting =
        new AtomicReference<>();

    void offer(BoardState frame) {
      pending.add(frame);
      var parked = waiting.getAndSet(null);
      if (parked != null) {
        var head = pending.poll();
        if (head != null) {
          parked.complete(head);
        } else {
          waiting.compareAndSet(null, parked);
        }
      }
    }

    public CompletionStage<BoardState> next() {
      var head = pending.poll();
      if (head != null) {
        return CompletableFuture.completedFuture(head);
      }
      var promise = new CompletableFuture<BoardState>();
      if (!waiting.compareAndSet(null, promise)) {
        // Only one wait per subscriber is live at a time; a second one means the stream was
        // driven concurrently, which the SSE source does not do.
        throw new IllegalStateException("this subscriber is already waiting");
      }
      var late = pending.poll();
      if (late != null && waiting.compareAndSet(promise, null)) {
        promise.complete(late);
      }
      return promise;
    }
  }

  public static Subscriber subscribe(String boardId) {
    var subscriber = new Subscriber();
    BY_BOARD.computeIfAbsent(boardId, id -> ConcurrentHashMap.newKeySet()).add(subscriber);
    return subscriber;
  }

  public static void unsubscribe(String boardId, Subscriber subscriber) {
    var watchers = BY_BOARD.get(boardId);
    if (watchers == null) {
      return;
    }
    watchers.remove(subscriber);
    if (watchers.isEmpty()) {
      BY_BOARD.remove(boardId, watchers);
    }
  }

  public static void publish(String boardId, BoardState frame) {
    var watchers = BY_BOARD.get(boardId);
    if (watchers == null) {
      return;
    }
    for (var watcher : watchers) {
      watcher.offer(frame);
    }
  }
}
