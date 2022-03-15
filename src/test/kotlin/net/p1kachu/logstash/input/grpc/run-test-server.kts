package net.p1kachu.logstash.input.grpc

var server = TestGreeterServer.start(50051)
println("server listening at port ${server.port}")
server.awaitTermination()