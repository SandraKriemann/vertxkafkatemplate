package verticles;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.AsyncResultHandler;
import org.vertx.java.core.Handler;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.http.RouteMatcher;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.platform.Verticle;
import util.NetworkUtil;

/**
 * Most important verticle.
 * It contains a webserver to make this application controlable via HTTP Requests.
 * <p>
 * Created by sandra.kriemann on 04.06.2015.
 */

public class HttpControllerVerticle extends Verticle {
    private static final Logger LOGGER = LoggerFactory.getLogger(HttpControllerVerticle.class);

    EventBus eventBus;
    private boolean startOnHold = false;
    private boolean stopOnHold = false;
    private boolean connectWorkerIsRunning = false;
    private String deployIdKafkaConWorVer;


    public void start() {
        eventBus = vertx.eventBus();
        RouteMatcher routeMatcher = new RouteMatcher();

        routeMatcher.get("/deploy/start", new Handler<HttpServerRequest>() {
            @Override
            public void handle(HttpServerRequest request) {
                JsonObject jsonObject = new JsonObject();
                jsonObject.putString("Data", "1234");


                if (!startOnHold) {
                    startTheConsumer(request, jsonObject);
                } else {
                    request.response().end("Start Load Tests: System is busy - please try again in some seconds!");
                }
                startOnHold = true;
                stopOnHold = false;
            }
        });

        routeMatcher.get("/deploy/stop", new Handler<HttpServerRequest>() {
            @Override
            public void handle(final HttpServerRequest request) {
                if (!stopOnHold) {
                    stopOnHold = false;
                    LOGGER.info(" ---> Try stopping " + KafkaConsumerWorkerVerticle.class.getSimpleName());
                    container.undeployVerticle(deployIdKafkaConWorVer, new AsyncResultHandler<Void>() {
                        @Override
                        public void handle(AsyncResult<Void> asyncResult) {
                            if (asyncResult.succeeded()) {
                                startOnHold = false;
                                connectWorkerIsRunning = false;
                                LOGGER.info(KafkaConsumerWorkerVerticle.class.getSimpleName() + " has been undeployed.");
                                request.response().end("Undeployed Worker Verticle!");
                            }
                        }
                    });

                } else {
                    request.response().end("Stop Deployment: System is busy - please try again in some seconds!");
                }
                startOnHold = true;
                stopOnHold = false;
            }
        });

        vertx.createHttpServer().requestHandler(routeMatcher).listen(8091, NetworkUtil.getMyIPAddress());
        LOGGER.info("Workerverticle started, listening on port: 8091");
    }

    private void startTheConsumer(final HttpServerRequest request, final JsonObject json) {
        connectWorkerIsRunning = true;
        container.deployWorkerVerticle(KafkaConsumerWorkerVerticle.class.getName(), null, 1, false, new AsyncResultHandler<String>() {
            @Override
            public void handle(AsyncResult<String> asyncResult) {
                if (asyncResult.succeeded()) {
                    deployIdKafkaConWorVer = asyncResult.result();
                    LOGGER.info(KafkaConsumerWorkerVerticle.class.getSimpleName() + " has been deployed.");
                    eventBus.send(KafkaConsumerWorkerVerticle.class.getSimpleName(), json);
                    request.response().end("verticles.ConnectWorkerVerticle has been started.");
                } else {
                    LOGGER.error("AsyncResultError: " + asyncResult.toString());
                }

            }
        });
    }
}
