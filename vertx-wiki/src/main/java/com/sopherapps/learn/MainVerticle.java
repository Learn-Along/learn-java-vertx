package com.sopherapps.learn;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;

/*
*   A Verticle is like an actor. It works independently of other
*   verticles in reponse to events communicated to it
*   by either other verticles or the receptive thread itself
*/
public class MainVerticle extends AbstractVerticle {
    /*
     * CRUD SQL queries for HSQLDB saved as class constants
     */
    private static final String SQL_CREATE_PAGES_TABLE = 
        "create table if not exists Pages (Id integer identity primary Key, Name Varchar(255) unique, Content clob)";
    private static final String SQL_GET_PAGE = "select Id, Content from Pages where Name = ?";
    private static final String SQL_CREATE_PAGE = "insert into Pages values (NULL, ?, ?)";
    private static final String SQL_SAVE_PAGE = "update Pages set Content = ? where Id = ?";
    private static final String SQL_ALL_PAGES = "select Name from Pages";
    private static final String SQL_DELETE_PAGE = "delete from Pages where Id = ?";


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