package run_test_server

import net.p1kachu.logstash.input.grpc.TestGreeterServer

class Main {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            TestGreeterServer.start(50051).apply {
                println("server listening at port ${this.port}")
                this.awaitTermination()
            }
        }
    }
}