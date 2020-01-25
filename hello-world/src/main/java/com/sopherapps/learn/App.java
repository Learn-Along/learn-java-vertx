package com.sopherapps.learn;

import io.vertx.core.Vertx;

/**
 * Hello world!
 *
 */
public class App 
{
    public static void main( String[] args )
    {
        // System.out.println( "Hello World!" );
        Vertx.vertx().createHttpServer().requestHandler(req -> 
            req.response().end("Hello World!")).listen(8080);
    }
}
