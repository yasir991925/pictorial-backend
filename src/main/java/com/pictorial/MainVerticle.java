package com.pictorial;

import com.fasterxml.jackson.databind.ObjectMapper;
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
    static ObjectMapper objectMapper = new ObjectMapper();

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
        router.get("/game/data/:id").handler(this::getGameData);
        return router;
    }

    private void webSocketHandler(Router router) {

        SockJSHandler sockJSHandler = SockJSHandler.create(vertx);
        SockJSBridgeOptions options = new SockJSBridgeOptions();

        String outBounds_regexp = "^[0-9a-fA-F]{8}\\b-[0-9a-fA-F]{4}\\b-[0-9a-fA-F]{4}\\b-[0-9a-fA-F]{4}\\b-[0-9a-fA-F]{12}$";
        String outBounds_room_status = "^room.status.[0-9a-fA-F]{8}\\\\b-[0-9a-fA-F]{4}\\\\b-[0-9a-fA-F]{4}\\\\b-[0-9a-fA-F]{4}\\\\b-[0-9a-fA-F]{12}$";

        String outBounds_regexp_test = "([0-9])";
        PermittedOptions outBoundPermitted_test_reg = new PermittedOptions().setAddressRegex(outBounds_regexp_test);

        PermittedOptions inBoundPermitted = new PermittedOptions().setAddressRegex("msg.back");
        PermittedOptions outBoundPermitted_uuid = new PermittedOptions().setAddressRegex(outBounds_regexp);
        PermittedOptions outBoundPermitted_room_status = new PermittedOptions().setAddressRegex(outBounds_room_status);

        options
            .addInboundPermitted(inBoundPermitted)
            .addOutboundPermitted(outBoundPermitted_uuid)
            .addOutboundPermitted(outBoundPermitted_test_reg)
            .addOutboundPermitted(outBoundPermitted_room_status);

        router
            .route("/eventbus/*")
            .subRouter(sockJSHandler.bridge(options));


        vertx.eventBus().consumer("msg.back", msg -> {
            JsonObject in_msg = new JsonObject(msg.body().toString());
            JsonObject payload = new JsonObject();
            payload.put("message", in_msg.getString("message"));
            payload.put("server", id.toString());
            payload.put("timestamp", System.currentTimeMillis());
            payload.put("roomId", in_msg.getString("roomId"));
            redis.publish("msg.redis", payload.encode());
            redis.lpush(Arrays.asList("data." + in_msg.getString("roomId"), payload.encode()))
                .onFailure(err -> {
                    System.out.println("Error while pushing the data into list");
                    System.out.println(err.getMessage());
                });
        });


        redis_conn.handler(message -> {
            switch (message.type()) {
                case PUSH:
                    String data = message.get(2).toString();
                    String roomId = new JsonObject(data).getString("roomId");
                    vertx.eventBus().publish(roomId, data);
                    vertx.eventBus().publish("test.out", data);
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
        // [x] need to create a uuid
        // [_] need to create a store in redis to store the data for a room
        // [_] return the uuid to the user and create a url in frontend
        UUID id = UUID.randomUUID();
        redis.set(Arrays.asList(id.toString(), ""));
        context.response().end(id.toString());
    }

    private void joinGame(RoutingContext context) {
        // [x] need to check if the room is valid or not
        // [x] room become invalid if the it's not there in redis
        // [_] if the room's last_activity is > 30 mins
        // [_] allow the user to istablish ws connection
        // [_] return the room state stored in redis
        UUID roomId = UUID.fromString(context.request().getParam("id"));
        redis.get(roomId.toString()).onSuccess(res -> {
            if (res == null) {
                context.response().setStatusCode(404).end("No such room exists");
            } else {
                context.response().end(res.toString());
            }
        });
    }

    private void getGameData(RoutingContext context) {
        String roomId = "data." + context.pathParam("id");
        redis
            .lrange(roomId, "0", "-1")
            .onSuccess(ar -> {
                context.response().putHeader("Content-Type", "application/json").end(ar.toString());
            })
            .onFailure(ar -> {
                context.response().setStatusCode(500).end("something failed " + ar.getMessage());
            });
    }

}
