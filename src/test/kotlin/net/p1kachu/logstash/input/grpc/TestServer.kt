package net.p1kachu.logstash.input.grpc

import com.google.protobuf.ByteString
import io.grpc.Server
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder
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

                    doubleValue = 2.0
                    floatValue = 3.0F
                    int32Value = 4
                    int64Value = 5
                    uint32Value = 6
                    uint64Value = 7
                    sint32Value = -8
                    sint64Value = -9
                    fixed32Value = 10
                    fixed64Value = 11
                    sfixed32Value = -12
                    sfixed64Value = -13
                    booleanValue = true
                    stringValue = "string"
                    bytesValue = ByteString.copyFrom(byteArrayOf(0x31, 0x36))
                    enumValue = Hello.HelloReply.Enum.ONE
                    nested = nestedBuilder.apply {
                        nestedString = "nested_string"
                    }.build()
                    repeat(request.number) {
                        addRepeatedString("repeated_string")
                        addRepeatedNested(Hello.HelloReply.Data.newBuilder().apply {
                            nestedString = "repeated_nested_string"
                        }.build())
                    }
                }.build()
            )
        }
        responseObserver.onCompleted()
    }

    companion object {
        fun start(port: Int? = null): Server {
            // find random available port
            if (port == null) {
                val tmp = ServerSocket(0)
                val availablePort = tmp.localPort
                tmp.close()
                return start(availablePort)
            }

            return NettyServerBuilder
                .forPort(port)
                .addService(TestGreeterServer())
                .build()
                .start()
        }
    }
}