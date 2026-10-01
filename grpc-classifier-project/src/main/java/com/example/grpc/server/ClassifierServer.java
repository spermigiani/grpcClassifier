package com.example.grpc.server;

import io.grpc.Server;
import io.grpc.ServerBuilder;

import java.io.BufferedReader;
import java.io.IOException;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/** Boots the ClassifierService gRPC server and blocks until it's shut down. */
public class ClassifierServer {

    private static final Logger logger = Logger.getLogger(ClassifierServer.class.getName());

    private static final int DEFAULT_PORT = 50051;

    private Server server;
    private Process externalProcess;   // <-- track the child process
    private static final long PROCESS_TIMEOUT_SECONDS = 260; // adjust as needed

    private void start(int port) throws IOException {
        server = ServerBuilder.forPort(port)
                .addService(new ClassifierServiceImpl())
                .build()
                .start();

        logger.info(() -> "ClassifierServer started, listening on port " + port);

        //runExternalProcess(); // Start the external process once the server is up.

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

    /**
     * Launches an external process Fandango and streams its output into the server logsCall.
     * @throws IOException if an I/O error occurs.
     */
    private void runExternalProcess() throws IOException {
        try {
            String fanFilePath = "python_client/busquedaSecuencial.fan"; //Path to .fan file.
            int genNumber = 2; //number of inputs we want to have (-n).
            List<String> command = Arrays.asList(
                    "/opt/anaconda3/bin/fandango", "fuzz",
                    "-f", fanFilePath,
                    "-n", String.valueOf(genNumber)
            );

            ProcessBuilder pb = new ProcessBuilder(command).redirectErrorStream(true); // merge stderr into stdout;
            externalProcess = pb.start();
            // Drain the process's stdout on a background thread so it doesn't block
            // (and so the pipe doesn't fill up and stall the child process).
            /*Thread reader = new Thread(() -> {
                try (var in =new java.io.BufferedReader(
                        new java.io.InputStreamReader(externalProcess.getInputStream()))) {
                    String line;
                    while ((line = in.readLine()) != null) {
                        logger.info("[external process] " + line);
                    }
                } catch (IOException e) {
                    logger.warning("External process output stream closed: " + e.getMessage());
                }
            }, "external-process-reader");
            reader.setDaemon(true);
            reader.start();*/

            //alternativa al thread
            logger.info(() -> "[external process] started, pid=" + externalProcess.pid());

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(externalProcess.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    logger.info("[external process] " + line);
                }
                stopProcess(externalProcess);
            }

            boolean finishedInTime = externalProcess.waitFor(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            if (finishedInTime) {
                int exitCode = externalProcess.exitValue();
                logger.info(() -> "[external process] finished, exit code " + exitCode);
                if (exitCode != 0) {
                    logger.warning(() -> "[external process] exited with non-zero code " + exitCode);
                }
            } else {
                logger.warning(() -> "[external process] did not finish within "
                        + PROCESS_TIMEOUT_SECONDS + "s, stopping it");
                stopProcess(externalProcess);
            }

            logger.info(() -> "Started external process, pid=" + externalProcess.pid());
        } catch (IOException e) {
            logger.severe("Failed to start external process: " + e.getMessage());
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warning("[external process] interrupted while waiting, stopping it");
            if (externalProcess != null) {
                stopProcess(externalProcess);
            }
        }
    }

    /** Stops a process gracefully (SIGTERM), escalating to SIGKILL if it won't exit. */
    private void stopProcess(Process process) {
        if (!process.isAlive()) {
            return;
        }
        process.destroy(); // SIGTERM
        try {
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                logger.warning(() -> "[external process] ignored SIGTERM, forcing kill");
                process.destroyForcibly(); // SIGKILL
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        }
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
