package com.example.grpc.server;

import io.grpc.Server;
import io.grpc.ServerBuilder;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/** Boots the ClassifierService gRPC server and blocks until it's shut down. */
public class ClassifierServer {

    private static final Logger logger = Logger.getLogger(ClassifierServer.class.getName());

    private static final int DEFAULT_PORT = 50051;

    private Server server;

    private void start(int port) throws IOException {
        server = ServerBuilder.forPort(port)
                .addService(new ClassifierServiceImpl())
                .build()
                .start();

        logger.info(() -> "ClassifierServer started, listening on port " + port);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutting down gRPC server since JVM is shutting down");
            try {
                ClassifierServer.this.stop();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            logger.info("Server shut down.");
        }));
    }

    private void stop() throws InterruptedException {
        if (server != null) {
            server.shutdown().awaitTermination(30, TimeUnit.SECONDS);
        }
    }

    private void blockUntilShutdown() throws InterruptedException {
        if (server != null) {
            server.awaitTermination();
        }
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        int port = DEFAULT_PORT;
        if (args.length > 0) {
            port = Integer.parseInt(args[0]);
        } else {
            String envPort = System.getenv("CLASSIFIER_SERVER_PORT");
            if (envPort != null && !envPort.isEmpty()) {
                port = Integer.parseInt(envPort);
            }
        }

        final ClassifierServer server = new ClassifierServer();
        server.start(port);
        server.blockUntilShutdown();
    }
}
