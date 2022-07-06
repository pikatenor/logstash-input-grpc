package me.dinowernli.grpc.polyglot.grpc;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Executors;

import javax.net.ssl.SSLException;

import com.google.common.base.Preconditions;
import com.google.common.net.HostAndPort;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.grpc.*;
import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.shaded.io.grpc.netty.NegotiationType;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContext;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContextBuilder;

import static com.google.common.util.concurrent.MoreExecutors.listeningDecorator;

/** Knows how to construct grpc channels. */
public class ChannelFactory {

  private final boolean useTls;
  private final String caPath;
  private final ListeningExecutorService authExecutor;

  public static ChannelFactory create(boolean useTls, String caPath) {
    ListeningExecutorService authExecutor = listeningDecorator(
        Executors.newCachedThreadPool(new ThreadFactoryBuilder().setDaemon(true).build()));
    return new ChannelFactory(useTls, caPath, authExecutor);
  }

  public ChannelFactory(
          boolean useTls, String caPath, ListeningExecutorService authExecutor) {
    this.useTls = useTls;
    this.caPath = caPath;
    this.authExecutor = authExecutor;
  }

  public ManagedChannel createChannel(HostAndPort endpoint) {
    NettyChannelBuilder nettyChannelBuilder = createChannelBuilder(endpoint);
    return nettyChannelBuilder.build();
  }

  private NettyChannelBuilder createChannelBuilder(HostAndPort endpoint) {
    if (!useTls) {
      return NettyChannelBuilder.forAddress(endpoint.getHost(), endpoint.getPort())
          .negotiationType(NegotiationType.PLAINTEXT)
          .intercept(metadataInterceptor());
    } else {
      return NettyChannelBuilder.forAddress(endpoint.getHost(), endpoint.getPort())
          .sslContext(createSslContext())
          .negotiationType(NegotiationType.TLS)
          .intercept(metadataInterceptor());
    }
  }

  private ClientInterceptor metadataInterceptor() {
    ClientInterceptor interceptor = new ClientInterceptor() {
      @Override
      public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
          final io.grpc.MethodDescriptor<ReqT, RespT> method, CallOptions callOptions, final Channel next) {
        return new ClientInterceptors.CheckedForwardingClientCall<ReqT, RespT>(next.newCall(method, callOptions)) {
          @Override
          protected void checkedStart(Listener<RespT> responseListener, Metadata headers)
              throws StatusException {
            // FIXME: implement this
            delegate().start(responseListener, headers);
          }
        };
      }
    };

    return interceptor;
  }

  private SslContext createSslContext() {
    SslContextBuilder resultBuilder = GrpcSslContexts.forClient();
    if (!caPath.isEmpty()) {
      resultBuilder.trustManager(loadFile(caPath));
    }
    // FIXME: implement TLS client authentication
    try {
      return resultBuilder.build();
    } catch (SSLException e) {
      throw new RuntimeException("Unable to build sslcontext for client call", e);
    }
  }

  private static File loadFile(String fileName) {
    Path filePath = Paths.get(fileName);
    Preconditions.checkArgument(Files.exists(filePath), "File " + fileName + " was not found");
    return filePath.toFile();
  }
}
