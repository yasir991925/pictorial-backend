package com.pictorial;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.bridge.PermittedOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.sockjs.SockJSBridgeOptions;
import io.vertx.ext.web.handler.sockjs.SockJSHandler;

import java.util.UUID;

public class MainVerticle extends AbstractVerticle {

    UUID id;

    @Override
    public void start(Promise<Void> startPromise) throws Exception {
        id = UUID.randomUUID();
        Router router = setupRouter();
        vertx.createHttpServer().requestHandler(router).listen(Integer.parseInt(System.getenv("SERVER_PORT")))
            .onSuccess(ar -> startPromise.complete())
            .onFailure(startPromise::fail);
    }

    private Router setupRouter() {
        Router router = Router.router(vertx);

        SockJSHandler sockJSHandler = SockJSHandler.create(vertx);
        SockJSBridgeOptions options = new SockJSBridgeOptions();
        PermittedOptions inBoundPermitted = new PermittedOptions().setAddress("msg-back");
        PermittedOptions outBoundPermitted = new PermittedOptions().setAddress("msg");
        options.addInboundPermitted(inBoundPermitted).addOutboundPermitted(outBoundPermitted);

        router.route("/eventbus/*").subRouter(sockJSHandler.bridge(options));

        router.get("/").handler(this::home);

        vertx.eventBus().consumer("msg-back", msg -> {
            JsonObject paylaod = new JsonObject();
            paylaod.put("message", msg.body());
            paylaod.put("server", id.toString());
            paylaod.put("timestamp", System.currentTimeMillis());
            vertx.eventBus().publish("msg", paylaod.encode());
        });

        return router;
    }

    private void home(RoutingContext context) {
        context.response().end("hello from " + id);
    }
}
