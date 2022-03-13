package net.p1kachu.logstash.input.grpc

import io.grpc.Server
import io.grpc.netty.NettyServerBuilder
import io.grpc.stub.StreamObserver
import net.p1kachu.proto.hellostreamingworld.Hello
import net.p1kachu.proto.hellostreamingworld.MultiGreeterGrpc
import java.net.ServerSocket

class TestGreeterServer : MultiGreeterGrpc.MultiGreeterImplBase() {
    override fun sayHello(request: Hello.HelloRequest, responseObserver: StreamObserver<Hello.HelloReply>) {
        repeat(request.number) {
            responseObserver.onNext(
                Hello.HelloReply.newBuilder().apply {
                    message = "Hello, ${request.data.name}!"
                }.build()
            )
        }
        responseObserver.onCompleted()
    }

    companion object {
        fun start(): Server {
            // find random available port
            val tmp = ServerSocket(0)
            val port = tmp.localPort
            tmp.close()

            return NettyServerBuilder
                .forPort(port)
                .addService(TestGreeterServer())
                .build()
                .start()
        }
    }
}