package com.sopherapps.learn;

import com.github.rjeschke.txtmark.Processor;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.logging.Logger;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.templ.freemarker.FreeMarkerTemplateEngine;
// import org.slf4j.Logger;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.sql.SQLConnection;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/*
 *   A Verticle is like an actor. It works independently of other
 *   verticles in reponse to events communicated to it
 *   by either other verticles or the receptive thread itself
 */
public class MainVerticle extends AbstractVerticle {
  /*
   * CRUD SQL queries for HSQLDB saved as class constants
   */
  private static final String SQL_CREATE_PAGES_TABLE = "create table if not exists Pages (Id integer identity primary Key, Name Varchar(255) unique, Content clob)";
  private static final String SQL_GET_PAGE = "select Id, Content from Pages where Name = ?";
  private static final String SQL_CREATE_PAGE = "insert into Pages values (NULL, ?, ?)";
  private static final String SQL_SAVE_PAGE = "update Pages set Content = ? where Id = ?";
  private static final String SQL_ALL_PAGES = "select Name from Pages";
  private static final String SQL_DELETE_PAGE = "delete from Pages where Id = ?";
  /*
   * Other properties
   */
  private JDBCClient dbClient;
  private static final Logger LOGGER =  LoggerFactory.getLogger(MainVerticle.class);
  private FreeMarkerTemplateEngine templateEngine;
  private static final String EMPTY_PAGE_MARKDOWN =
    "# A new page\n\nFeel free to write in Markdown!\n";

  /*
   * "throws Exception" ensures the caller code wraps this in a try-catch else the
   * compiler refuses to work
   */
  @Override
  public void start(Promise<Void> promise) throws Exception {

    // Since, the server can only start hen the database is fully
    // prepared, we use a composition that starts with the database
    // preparation then the starting of the server
    Future<Void> steps = prepareDatabase().compose(v -> startHttpServer());
    steps.setHandler(asyncResult -> {
      if (asyncResult.succeeded()) {

        // stop the waiting, tell the caller program that
        // promise has completed successfully
        promise.complete();
      } else {
        // tell the calling program that the promise has
        // failed with the given Throwable
        promise.fail(asyncResult.cause());
      }
    });
  }

  private Future<Void> prepareDatabase() {
    // In async, a promise will eventurely return a value
    // or an exception. It is the future() that eventually
    // has this value
    Promise<Void> promise = Promise.promise();

    // A JDBCClient shared across Verticles known to the vertx instance
    dbClient = JDBCClient.createShared(vertx, new JsonObject()
      .put("url", "jdbc:hsqldb:file:db/wiki")
      .put("driver_class", "org.hsqldb.jdbcDriver")
      .put("max_pool_size", 30));

    dbClient.getConnection(asyncResult -> {
      if (asyncResult.failed()) {
        LOGGER.error("Could not open JDBC connectiion", asyncResult.cause());
        promise.fail(asyncResult.cause());
      } else {
        SQLConnection connection = asyncResult.result();
        // if connection is successful, attempt to initialize table
        connection.execute(SQL_CREATE_PAGES_TABLE, create -> {
          // close immediately to free up the pool for others
          connection.close();
          if (create.failed()) {
            LOGGER.error("Database preparation error", create.cause());
            promise.fail(create.cause());
          } else {
            // Inform the calling program that the job has been successful
            promise.complete();
          }
        });
      }
    });

    return promise.future();
  }

  private Future<Void> startHttpServer() {
    // In async, a promise will eventurely return a value
    // or an exception. It is the future() that eventually
    // has this value
    Promise<Void> promise = Promise.promise();
    HttpServer server = vertx.createHttpServer();

    Router router = Router.router(vertx);
    router.get("/").handler(this::indexHandler);
    router.get("/wiki/:page").handler(this::pageRenderingHandler);
    router.post().handler(BodyHandler.create());
    router.post("/save").handler(this::pageUpdateHandler);
    router.post("/create").handler(this::pageCreateHandler);
    router.post("/delete").handler(this::pageDeletionHandler);

    templateEngine = FreeMarkerTemplateEngine.create(vertx);

    server.requestHandler(router).listen(8080, asyncResult -> {
      if (asyncResult.succeeded()) {
        LOGGER.info("Http server running on port 8080");
        promise.complete();
      } else {
        LOGGER.error("Could not start an HTTP server", asyncResult.cause());
        promise.fail(asyncResult.cause());
      }
    });

    return promise.future();
  }

  private void indexHandler(RoutingContext context) {
    dbClient.getConnection(cursor -> {
      if (cursor.succeeded()) {
        SQLConnection connection = cursor.result();

        connection.query(SQL_ALL_PAGES, response -> {
          // close the connection immediately to free u resources
          connection.close();

          if (response.succeeded()) {
            List<String> pages = response.result()
              .getResults()
              .stream()
              .map(json -> json.getString(0))
              .sorted()
              .collect(Collectors.toList());

            // Update the context
            context.put("title", "Wiki home");
            context.put("pages", pages);

            templateEngine.render(context.data(), "templates/index.ftl", asyncResult -> {
              if (asyncResult.succeeded()) {
                context.response().putHeader("Content-Type", "text/html");
                context.response().end(asyncResult.result());
              } else {
                context.fail(asyncResult.cause());
              }
            });
          } else {
            context.fail(response.cause());
          }
        });
      } else {
        context.fail(cursor.cause());
      }
    });
  }

  private void pageRenderingHandler(RoutingContext context){
    String page = context.request().getParam("page");

    dbClient.getConnection(cursor -> {
      if(cursor.succeeded()){

        SQLConnection connection = cursor.result();
        connection.queryWithParams(SQL_GET_PAGE, new JsonArray().add(page), fetch -> {
          // Free up the connection resources
          connection.close();

          if(fetch.succeeded()){
            JsonArray row = fetch.result().getResults()
              .stream()
              .findFirst()
              .orElseGet(() -> new JsonArray().add(-1).add(EMPTY_PAGE_MARKDOWN));

            Integer id = row.getInteger(0);
            String rawContent = row.getString(1);

            context.put("title", page);
            context.put("id", id);
            context.put("newPage", fetch.result().getResults().size() == 0? "yes": "no");
            context.put("rawContent", rawContent);
            context.put("content", Processor.process(rawContent));
            context.put("timestamp", new Date().toString());

            // rendering
            templateEngine.render(context.data(), "temlates/page.ftl", asyncResult -> {
              if(asyncResult.succeeded()){
                context.response().putHeader("Content-Type", "text/html");
                context.response().end(asyncResult.result());
              } else {
                context.fail(asyncResult.cause());
              }
            });
          } else {
            context.fail(fetch.cause());
          }
        });
      } else {
        context.fail(cursor.cause());
      }
    });
  }

  private void pageCreateHandler(RoutingContext context){
    String pageName = context.request().getParam("name");
    String location = "/wiki/" + pageName;

    if(pageName == null || pageName.isEmpty()){
      location = "/";
    }

    context.response().setStatusCode(303);
    context.response().putHeader("Location", location);
    context.response().end();
  }

  private  void pageUpdateHandler(RoutingContext context){
    String id = context.request().getParam("id");
    String title = context.request().getParam("title");
    String markdown = context.request().getParam("markdown");
    boolean newPage = "yes".equals(context.request().getParam("newPage"));

    dbClient.getConnection(cursor -> {
      if(cursor.succeeded()){
        SQLConnection connection = cursor.result();
        String sql = newPage? SQL_CREATE_PAGE: SQL_SAVE_PAGE;
        JsonArray params = new JsonArray();

        if(newPage){
          params.add(title).add(markdown);
        } else {
          params.add(markdown).add(id);
        }

        connection.updateWithParams(sql, params, response -> {
          // close the connection to free up connections
          connection.close();

          if(response.succeeded()){
            context.response().setStatusCode(303);
            context.response().putHeader("Location", "/wiki" + title);
            context.response().end();
          } else {
            context.fail(response.cause());
          }
        });
      } else {
        context.fail(cursor.cause());
      }
    });
  }

  private void pageDeletionHandler(RoutingContext context){
    String id = context.request().getParam("id");

    dbClient.getConnection(cursor -> {
      if(cursor.succeeded()){

        SQLConnection connection = cursor.result();

        connection.updateWithParams(SQL_DELETE_PAGE, new JsonArray().add(id), response -> {
          connection.close();

          if(response.succeeded()){
            context.response().setStatusCode(303);
            context.response().putHeader("Location", "/");
            context.response().end();
          } else {
            context.fail(response.cause());
          }
        });
      } else {
        context.fail(cursor.cause());
      }
    });
  }
}
