package io.akka.planka.api;

import akka.http.javadsl.model.HttpResponse;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Patch;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.http.HttpException;
import akka.javasdk.http.HttpResponses;
import io.akka.planka.application.BoardEntity;
import io.akka.planka.application.BoardState;
import java.util.UUID;

/** The JSON API over one Board and its Lists and Cards (SPEC-001). */
@HttpEndpoint("")
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
public class BoardEndpoint {

  private final ComponentClient componentClient;

  public BoardEndpoint(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public record CreateBoardRequest(String name) {}

  public record CreateListRequest(String name, double position) {}

  public record CreateCardRequest(String name, String description, double position) {}

  public record MoveCardRequest(String listId, double position) {}

  public record RepositionListRequest(double position) {}

  @Post("/boards")
  public HttpResponse createBoard(CreateBoardRequest request) {
    var boardId = "board-" + UUID.randomUUID();
    call(() -> componentClient.forEventSourcedEntity(boardId).method(BoardEntity::createBoard)
        .invoke(new BoardEntity.CreateBoard(request.name())));
    return HttpResponses.created(BoardResponse.of(read(boardId)));
  }

  @Get("/boards/{boardId}")
  public BoardResponse getBoard(String boardId) {
    return BoardResponse.of(read(boardId));
  }

  @Post("/boards/{boardId}/lists")
  public HttpResponse createList(String boardId, CreateListRequest request) {
    var list = call(() ->
        componentClient.forEventSourcedEntity(boardId).method(BoardEntity::createList)
            .invoke(new BoardEntity.CreateList(request.name(), request.position())));
    return HttpResponses.created(BoardResponse.ListResponse.of(list));
  }

  @Patch("/lists/{listId}")
  public HttpResponse repositionList(String listId, RepositionListRequest request) {
    var list = call(() ->
        componentClient.forEventSourcedEntity(boardIdOf(listId))
            .method(BoardEntity::repositionList)
            .invoke(new BoardEntity.RepositionList(listId, request.position())));
    return HttpResponses.ok(BoardResponse.ListResponse.of(list));
  }

  @Post("/lists/{listId}/cards")
  public HttpResponse createCard(String listId, CreateCardRequest request) {
    var card = call(() ->
        componentClient.forEventSourcedEntity(boardIdOf(listId)).method(BoardEntity::createCard)
            .invoke(new BoardEntity.CreateCard(listId, request.name(), request.description(),
                request.position())));
    return HttpResponses.created(BoardResponse.CardResponse.of(card));
  }

  @Patch("/cards/{cardId}")
  public HttpResponse moveCard(String cardId, MoveCardRequest request) {
    var card = call(() ->
        componentClient.forEventSourcedEntity(boardIdOf(cardId)).method(BoardEntity::moveCard)
            .invoke(new BoardEntity.MoveCard(cardId, request.listId(), request.position())));
    return HttpResponses.ok(BoardResponse.CardResponse.of(card));
  }

  /**
   * The Board that owns a List or Card, read out of the child's own id.
   *
   * <p>No index is consulted: an id minted by {@link BoardEntity} carries its Board's id, so a
   * request naming only a child resolves here and now, and a record created by one request is
   * addressable by the very next one. An index built from the entity's events would be a
   * View, whose rows are keyed by the entity that emitted them and so cannot hold one row per
   * child at all (question-log row 15).
   */
  private String boardIdOf(String id) {
    var boardId = BoardEntity.boardIdOf(id);
    if (boardId == null) {
      throw HttpException.notFound();
    }
    return boardId;
  }

  private BoardState read(String boardId) {
    return call(() -> componentClient.forEventSourcedEntity(boardId)
        .method(BoardEntity::getBoard).invoke());
  }

  /**
   * Turns an entity's refusal into the status it means.
   *
   * <p>The entity answers "board not found", "list not found" and "card not found" with
   * {@code effects().error()}, which reaches a caller as a 400 carrying the message. Every one
   * of those is a missing record, and a caller that cannot tell a missing board from a
   * malformed request has to guess. The message itself is never echoed back: it is written
   * here rather than passed through, so nothing a caller sent can come back to them.
   */
  private <T> T call(java.util.function.Supplier<T> call) {
    try {
      return call.get();
    } catch (RuntimeException e) {
      var message = e.getMessage();
      if (message != null && message.contains("not found")) {
        throw HttpException.notFound();
      }
      throw e;
    }
  }
}
