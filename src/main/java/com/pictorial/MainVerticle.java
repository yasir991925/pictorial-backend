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
        router.post("/game").handler(this::createGame);
        router.get("/game/:id").handler(this::joinGame);
        return router;
    }

    private void webSocketHandler(Router router) {

        SockJSHandler sockJSHandler = SockJSHandler.create(vertx);
        SockJSBridgeOptions options = new SockJSBridgeOptions();

        String inBounds_regexp = "(([a-zA-Z0-9]+)-){4}(([a-zA-Z0-9]+)-*)(back)*";
        String outBounds_regexp = "(([a-zA-Z0-9]+)-){4}([a-zA-Z0-9]+)";

        PermittedOptions inBoundPermitted_test = new PermittedOptions().setAddress("test_back");

        PermittedOptions inBoundPermitted = new PermittedOptions().setAddressRegex("msg.back");
        PermittedOptions inBoundPermitted_uuid = new PermittedOptions().setAddressRegex(inBounds_regexp);
        PermittedOptions outBoundPermitted_uuid = new PermittedOptions().setAddress(outBounds_regexp);
        options
            .addInboundPermitted(inBoundPermitted_test)
            .addInboundPermitted(inBoundPermitted)
            .addOutboundPermitted(outBoundPermitted_uuid);

        router.route("/eventbus/*").subRouter(sockJSHandler.bridge(options));

        vertx.eventBus().consumer("msg.back", msg -> {
            JsonObject in_msg = new JsonObject(msg.body().toString());
            JsonObject payload = new JsonObject();
            payload.put("message", msg.body());
            payload.put("server", id.toString());
            payload.put("timestamp", System.currentTimeMillis());
            payload.put("roomId", in_msg.getString("roomId"));
            redis.publish("msg.redis", payload.encode());
        });


        redis_conn.handler(message -> {
            switch (message.type()) {
                case PUSH:
                    String data = message.get(2).toString();
                    String roomId = new JsonObject(message.get(2).toString()).getString("roomId");
                    vertx.eventBus().publish(roomId, data);
                    break;
                default:
                    break;
            }
        });

        redis.subscribe(Arrays.asList("msg.redis"));
    }


    /**
     * ###########################
     * ROUTE HANDLERS
     * ###########################
     */

    private void home(RoutingContext context) {
        context.response().end("hello from " + id);
    }

    private void createGame(RoutingContext context) {
        // need to create a uuid // done
        // need to create a store in redis to store the data for a room // done
        // return the uuid to the user and create a url in frontend // front-end work
        UUID id = UUID.randomUUID();
        redis.set(Arrays.asList(id.toString(), ""));
        context.response().end(id.toString());
    }

    private void joinGame(RoutingContext context) {
        // need to check if the room is valid or not
        // room become invalid if the it's not there in redis
        // if the room's last_activity is > 30 mins
        // allow the user to istablish ws connection
        // return the room state stored in redis

    }

}
