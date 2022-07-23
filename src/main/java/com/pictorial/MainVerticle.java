package com.pictorial;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.bridge.PermittedOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.sockjs.SockJSBridgeOptions;
import io.vertx.ext.web.handler.sockjs.SockJSHandler;
import io.vertx.redis.client.Redis;
import io.vertx.redis.client.RedisAPI;
import io.vertx.redis.client.RedisConnection;

import java.util.Arrays;
import java.util.UUID;

public class MainVerticle extends AbstractVerticle {

    UUID id;
    RedisAPI redis;
    RedisConnection redis_conn;

    @Override
    public void start(Promise<Void> startPromise) throws Exception {
        id = UUID.randomUUID();
        intiRedis()
            .onSuccess(client -> {
                redis_conn = client;
                redis = RedisAPI.api(client);
                Router router = setupRouter();
                vertx.createHttpServer()
                    .requestHandler(router)
                    .listen(Integer.parseInt(System.getenv("SERVER_PORT")))
                    .onSuccess(ar -> startPromise.complete())
                    .onFailure(startPromise::fail);
            }).onFailure(err -> {
                System.out.println("Error while connecting redis");
                startPromise.fail(err);
            });
    }

    @Override
    public void stop(Promise<Void> stopPromise) throws Exception {
        if (redis != null) {
            redis.close();
        }
        stopPromise.complete();
    }

    private Future<RedisConnection> intiRedis() {
        Promise<RedisConnection> promise = Promise.promise();

        String redis_host = System.getenv("REDIS_HOST");
        String redis_port = System.getenv("REDIS_PORT");
        String redis_connection_string = "redis://" + redis_host + ":" + redis_port;


        Redis.createClient(vertx, redis_connection_string)
            .connect()
            .onSuccess(promise::complete)
            .onFailure(promise::fail);
        return promise.future();
    }

    private Router setupRouter() {
        Router router = Router.router(vertx);
        webSocketHandler(router);

        router.get("/").handler(this::home);
        return router;
    }

    private void webSocketHandler(Router router) {

        SockJSHandler sockJSHandler = SockJSHandler.create(vertx);
        SockJSBridgeOptions options = new SockJSBridgeOptions();
        PermittedOptions inBoundPermitted = new PermittedOptions().setAddress("msg-back");
        PermittedOptions outBoundPermitted = new PermittedOptions().setAddress("msg");
        options.addInboundPermitted(inBoundPermitted).addOutboundPermitted(outBoundPermitted);

        router.route("/eventbus/*").subRouter(sockJSHandler.bridge(options));

        vertx.eventBus().consumer("msg-back", msg -> {
            JsonObject paylaod = new JsonObject();
            paylaod.put("message", msg.body());
            paylaod.put("server", id.toString());
            paylaod.put("timestamp", System.currentTimeMillis());
            redis.publish("msg-redis", paylaod.encode());
        });


        redis_conn.handler(message -> {
            switch (message.type()) {
                case PUSH:
                    String data = message.get(2).toString();
                    vertx.eventBus().publish("msg", data);
                    break;
                default:
                    break;
            }
        });

        redis.subscribe(Arrays.asList("msg-redis"));
    }


    /**
     * ###########################
     * ROUTE HANDLERS
     * ###########################
     */

    private void home(RoutingContext context) {
        context.response().end("hello from " + id);
    }
}
