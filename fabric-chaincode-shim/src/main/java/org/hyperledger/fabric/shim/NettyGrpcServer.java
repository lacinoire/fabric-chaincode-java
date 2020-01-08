/*
 * Copyright 2020 IBM All Rights Reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.hyperledger.fabric.shim;


import io.grpc.Server;
import io.grpc.netty.NettyServerBuilder;
import io.netty.handler.ssl.SslContextBuilder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

/**
 * implementation grpc server with NettyGrpcServer.
 */
public class NettyGrpcServer implements GrpcServer {

    private final Server server;
    /**
     * init netty grpc server.
     *
     * @param chaincodeBase       - chaincode implementation (invoke, init)
     * @throws IOException
     */
    NettyGrpcServer(final ChaincodeBase chaincodeBase, final GrpcServerSetting grpcServerSetting) throws IOException {
        if (chaincodeBase == null) {
            throw new IOException("chaincode must be specified");
        }

        final NettyServerBuilder serverBuilder = NettyServerBuilder.forPort(grpcServerSetting.getPortChaincodeServer())
                .addService(new ChatChaincodeWithPeer(chaincodeBase))
                .keepAliveTime(grpcServerSetting.getKeepAliveTimeMinutes(), TimeUnit.MINUTES)
                .keepAliveTimeout(grpcServerSetting.getKeepAliveTimeoutSeconds(), TimeUnit.SECONDS)
                .permitKeepAliveTime(grpcServerSetting.getPermitKeepAliveTimeMinutes(), TimeUnit.MINUTES)
                .permitKeepAliveWithoutCalls(grpcServerSetting.isPermitKeepAliveWithoutCalls())
                .maxConnectionAge(grpcServerSetting.getMaxConnectionAgeSeconds(), TimeUnit.SECONDS)
                .maxInboundMetadataSize(grpcServerSetting.getMaxInboundMetadataSize())
                .maxInboundMessageSize(grpcServerSetting.getMaxInboundMessageSize());

        if (grpcServerSetting.isTlsEnabled()) {
            final File keyCertChainFile = Paths.get(grpcServerSetting.getKeyCertChainFile()).toFile();
            final File keyFile = Paths.get(grpcServerSetting.getKeyFile()).toFile();

            if (grpcServerSetting.getKeyPassword() == null || grpcServerSetting.getKeyPassword().isEmpty()) {
                serverBuilder.sslContext(SslContextBuilder.forServer(keyCertChainFile, keyFile).build());
            } else {
                serverBuilder.sslContext(SslContextBuilder.forServer(keyCertChainFile, keyFile, grpcServerSetting.getKeyPassword()).build());
            }
        }

        this.server = serverBuilder.build();
    }

    /**
     * start grpc server.
     *
     * @throws IOException
     */
    public void start() throws IOException {
        Runtime.getRuntime()
                .addShutdownHook(
                        new Thread(() -> {
                            // Use stderr here since the logger may have been reset by its JVM shutdown hook.
                            System.err.println("*** shutting down gRPC server since JVM is shutting down");
                            NettyGrpcServer.this.stop();
                            System.err.println("*** server shut down");
                        }));
        server.start();
    }

    /**
     * Await termination on the main thread since the grpc library uses daemon threads.
     *
     * @throws InterruptedException
     */
    public void blockUntilShutdown() throws InterruptedException {
        server.awaitTermination();
    }

    /**
     * shutdown now grpc server.
     */
    public void stop() {
        server.shutdownNow();
    }
}
