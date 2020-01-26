package com.sopherapps.learn;

import org.slf4j.Logger;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.sql.SQLConnection;

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
    private static final Logger LOGGER = (Logger) LoggerFactory.getLogger(MainVerticle.class);

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
            if(asyncResult.failed()){
                LOGGER.error("Could not open JDBC connectiion", asyncResult.cause());
                promise.fail(asyncResult.cause());
            } else {
                SQLConnection connection = asyncResult.result();
                // if connection is successful, attempt to initialize table
                connection.execute(SQL_CREATE_PAGES_TABLE, create -> {
                    // close immediately to free up the pool for others
                    connection.close();
                    if(create.failed()){
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

        return promise.future();
    }
}