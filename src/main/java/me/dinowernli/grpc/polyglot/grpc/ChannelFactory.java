package me.dinowernli.grpc.polyglot.grpc;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Executors;
import java.util.*;

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

  private static final Map<String, String> defaultServiceConfigMethodName = Collections.unmodifiableMap(
          new HashMap<String, String>() {{ put("service", ""); put("method", ""); }}
  );
  private final boolean useTls;
  private final String caPath;
  private final Map<String, Object> retryServiceConfig;
  private final ListeningExecutorService authExecutor;

  public static ChannelFactory create(boolean useTls, String caPath, Map<String, Object> retryPolicy) {
    ListeningExecutorService authExecutor = listeningDecorator(
        Executors.newCachedThreadPool(new ThreadFactoryBuilder().setDaemon(true).build()));

    if (retryPolicy != null) {
      // service config parser only accepts Double or String, convert to string.
      retryPolicy.put("maxAttempts", retryPolicy.get("maxAttempts").toString());
      Map<String, Object> methodConfig = new HashMap<>();
      methodConfig.put("name", Collections.singletonList(defaultServiceConfigMethodName));
      methodConfig.put("retryPolicy", retryPolicy);
      Map<String, Object> serviceConfig = new HashMap<>();
      serviceConfig.put("methodConfig", Collections.singletonList(methodConfig));
      return new ChannelFactory(useTls, caPath, serviceConfig, authExecutor);
    } else {
      return new ChannelFactory(useTls, caPath, null, authExecutor);
    }
  }

  private ChannelFactory(
          boolean useTls, String caPath, Map<String, Object> retryServiceConfig, ListeningExecutorService authExecutor) {
    this.useTls = useTls;
    this.caPath = caPath;
    this.retryServiceConfig = retryServiceConfig;
    this.authExecutor = authExecutor;
  }

  public ManagedChannel createChannel(HostAndPort endpoint) {
    NettyChannelBuilder nettyChannelBuilder = createChannelBuilder(endpoint);
    return nettyChannelBuilder.build();
  }

  private NettyChannelBuilder createChannelBuilder(HostAndPort endpoint) {
    NettyChannelBuilder builder = NettyChannelBuilder.forAddress(endpoint.getHost(), endpoint.getPort())
            .intercept(metadataInterceptor());

    if (this.retryServiceConfig != null) {
      builder.defaultServiceConfig(retryServiceConfig);
      builder.enableRetry();
    }
    if (this.useTls) {
      builder.sslContext(createSslContext());
      builder.negotiationType(NegotiationType.TLS);
    } else {
      builder.negotiationType(NegotiationType.PLAINTEXT);
    }
    return builder;
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
