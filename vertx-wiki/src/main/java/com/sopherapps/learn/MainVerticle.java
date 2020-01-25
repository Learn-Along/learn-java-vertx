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
    *   "throws Exception" ensures the caller code wraps this in a try-catch
    *   else the compiler refuses to work
    */
    @Override 
    public void start(Promise<Void> promise) throws Exception{

        // Since, the server can only start hen the database is fully
        // prepared, we use a composition that starts with the database
        // preparation then the starting of the server
        Future<Void> steps = prepareDatabase().compose(v -> startHttpServer());
        steps.setHandler(asyncResult -> {
            if(asyncResult.succeeded()){

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

    private Future<Void> prepareDatabase(){
        // In async, a promise will eventurely return a value
        // or an exception. It is the future() that eventually
        // has this value
        Promise<Void> promise = Promise.promise();

        return promise.future();
    }

    private Future<Void> startHttpServer(){
        // In async, a promise will eventurely return a value
        // or an exception. It is the future() that eventually
        // has this value
        Promise<Void> promise = Promise.promise();

        return promise.future();
    }
}