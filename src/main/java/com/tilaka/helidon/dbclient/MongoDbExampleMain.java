package com.tilaka.helidon.dbclient;

/**
 * @author mohd rully k
 */

import com.tilaka.helidon.dbclient.endpoint.WebSocketEndpoint;
import com.tilaka.helidon.dbclient.service.PokemonService;
import com.tilaka.helidon.dbclient.service.SendingService;
import io.helidon.config.Config;
import io.helidon.dbclient.DbClient;
import io.helidon.dbclient.DbStatementType;
import io.helidon.dbclient.health.DbClientHealthCheck;
import io.helidon.dbclient.metrics.DbClientMetrics;
import io.helidon.dbclient.tracing.DbClientTracing;
import io.helidon.health.HealthSupport;
import io.helidon.media.jsonb.JsonbSupport;
import io.helidon.media.jsonp.JsonpSupport;
import io.helidon.metrics.MetricsSupport;
import io.helidon.webserver.Routing;
import io.helidon.webserver.StaticContentSupport;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.cors.CorsSupport;
import io.helidon.webserver.cors.CrossOriginConfig;
import io.helidon.webserver.tyrus.TyrusSupport;

import javax.websocket.server.ServerEndpointConfig;
import java.io.IOException;
import java.util.logging.LogManager;
import java.util.logging.Logger;


public final class MongoDbExampleMain {

    /**
     * Cannot be instantiated.
     */
    private MongoDbExampleMain() {
    }

    /**
     * Application main entry point.
     *
     * @param args command line arguments.
     * @throws java.io.IOException if there are problems reading logging properties
     */
    public static void main(final String[] args) throws IOException {
        startServer();
    }

    /**
     * Start the server.
     *
     * @return the created {@link io.helidon.webserver.WebServer} instance
     * @throws java.io.IOException if there are problems reading logging properties
     */
    static WebServer startServer() throws IOException {

        // load logging configuration
        LogManager.getLogManager().readConfiguration(
                MongoDbExampleMain.class.getResourceAsStream("/logging.properties"));

        // By default this will pick up application.yaml from the classpath
        Config config = Config.create();

        SendingService sendingService = new SendingService(config);

        WebServer server = WebServer.builder(createRouting(sendingService, config))
                .config(config.get("server"))
                .addMediaSupport(JsonpSupport.create())
                .addMediaSupport(JsonbSupport.create())
                .build();

        // Start the server and print some info.
        server.start()
                .thenAccept(ws -> {
                    System.out.println(
                            "WEB server is up! http://localhost:" + ws.port());
                    ws.whenShutdown().thenRun(()
                            -> {
                        // Stop messaging properly
                        sendingService.shutdown();
                        System.out.println("WEB server is DOWN. Good bye!");
                    });
                })
                .exceptionally(t -> {
                    System.err.println("Startup failed: " + t.getMessage());
                    t.printStackTrace(System.err);
                    return null;
                });

        // Server threads are not daemon. No need to block. Just react.
        return server;

    }

    private static Routing createRouting(SendingService sendingService, Config config) {

        Config dbConfig = config.get("db");

        DbClient dbClient = DbClient.builder(dbConfig)
                // add an interceptor to named statement(s)
                .addService(DbClientMetrics.counter().statementNames("select-all", "select-one"))
                // add an interceptor to statement type(s)
                .addService(DbClientMetrics.timer()
                        .statementTypes(DbStatementType.DELETE, DbStatementType.UPDATE, DbStatementType.INSERT))
                // add an interceptor to all statements
                .addService(DbClientTracing.create())
                .build();

        MetricsSupport metrics = MetricsSupport.create();

        HealthSupport health = HealthSupport.builder()
                .addLiveness(DbClientHealthCheck.create(dbClient))
                .build();

        return Routing.builder()
                .register(health)   // Health at "/health"
                .register(metrics)  // Metrics at "/metrics"
                .register("/db", corsSupportForPokemonService(config), new PokemonService(dbClient))
//                 register static content support (on "/")
                .register(StaticContentSupport.builder("/WEB").welcomeFileName("index.html"))
                // register rest endpoint for sending to Kafka
                .register("/rest/messages", sendingService)
                // register WebSocket endpoint to push messages coming from Kafka to client
                .register("/ws",
                        TyrusSupport.builder().register(
                                ServerEndpointConfig.Builder.create(
                                        WebSocketEndpoint.class, "/messages")
                                        .build())
                                .build())
                .build();
    }

    private static CorsSupport corsSupportForPokemonService(Config config) {

        // The default CorsSupport object (obtained using CorsSupport.create()) allows sharing for any HTTP method and with any
        // origin. Using CorsSupport.create(Config) with a missing config node yields a default CorsSupport, which might not be
        // what you want. This example warns if either expected config node is missing and then continues with the default.

        Config restrictiveConfig = config.get("restrictive-cors");
        if (!restrictiveConfig.exists()) {
            Logger.getLogger(MongoDbExampleMain.class.getName())
                    .warning("Missing restrictive config; continuing with default CORS support");
        }

        CorsSupport.Builder corsBuilder = CorsSupport.builder();

        // Use possible overrides first.
        config.get("cors")
                .ifExists(c -> {
                    Logger.getLogger(MongoDbExampleMain.class.getName()).info("Using the override configuration");
                    corsBuilder.mappedConfig(c);
                });
        corsBuilder
                .config(restrictiveConfig) // restricted sharing for PUT, DELETE
                .addCrossOrigin(CrossOriginConfig.create()) // open sharing for other methods
                .build();

        return corsBuilder.build();
    }

}