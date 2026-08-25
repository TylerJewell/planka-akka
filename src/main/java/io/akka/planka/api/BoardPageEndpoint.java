package io.akka.planka.api;

import akka.http.javadsl.model.ContentTypes;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.StatusCodes;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * The board screen: one page whose every piece of server-owned state arrives on the stream.
 *
 * <p>This is not planka's own front end. Reusing that one means speaking the Sails socket.io
 * virtual-request protocol its client sends every call over, and serving the bootstrap payload
 * it loads first — users, projects, board memberships, labels, custom fields, notifications —
 * all of which this slice deliberately does not have. What is here instead is the smallest
 * page that shows this slice's state changing, so that the capability is reachable from
 * outside a test and comparable, region by region, against the original's own screen. The
 * difference is declared in `planka-port/gui/manifest.json` and in the README rather than
 * presented as a rebuild of the original's interface.
 *
 * <p>The page holds no fetch of its own. Its first render is the stream's first frame, which
 * is why a reconnect needs nothing but the browser reopening the connection.
 */
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
@HttpEndpoint("")
public class BoardPageEndpoint {

  private static final String PAGE = load();

  @Get("/ui/boards/{boardId}")
  public HttpResponse board(String boardId) {
    var page = PAGE.replace("<body>", "<body data-board-id=\"" + escape(boardId) + "\">");
    return HttpResponse.create()
        .withStatus(StatusCodes.OK)
        .withEntity(ContentTypes.TEXT_HTML_UTF8, page.getBytes(StandardCharsets.UTF_8));
  }

  /**
   * The id reaches the page as an HTML attribute, so anything that would end the attribute or
   * open a tag is encoded rather than trusted. Ids this service mints cannot contain those
   * characters; a caller can put anything it likes in the path.
   */
  private static String escape(String value) {
    return value.replace("&", "&amp;").replace("\"", "&quot;")
        .replace("<", "&lt;").replace(">", "&gt;");
  }

  private static String load() {
    try (var in = BoardPageEndpoint.class.getResourceAsStream("/board-page.html")) {
      if (in == null) {
        throw new IllegalStateException("board-page.html is not on the classpath");
      }
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
