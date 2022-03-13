package net.p1kachu.logstash.input.grpc

import io.grpc.Server
import org.junit.After
import org.logstash.plugins.ConfigurationImpl
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.util.ArrayList
import java.util.function.Consumer

class GrpcInputTest {
    private lateinit var server: Server

    @Before
    fun setup() {
        server = TestGreeterServer.start()
        println("test server listening at port ${server.port}")
    }

    @After
    fun teardown() {
        server.shutdown()
        server.awaitTermination()
    }

    @Test
    fun testJavaInputExample() {
        val testName = "Logstashy"
        val testNumber = 5

        val config = ConfigurationImpl(mapOf(
            Grpc.PROTOSET_PATH to "build/generated/source/proto/test/descriptor_set.desc",
            Grpc.HOSTNAME to "localhost",
            Grpc.PORT to server.port.toLong(),
            Grpc.RPC_NAME to "hello.MultiGreeter/SayHello",
            Grpc.USE_TLS to false,
            Grpc.MESSAGE_JSON to "{\"data\": {\"name\": \"$testName\"}, \"number\": $testNumber}",
        ).mapKeys { it.key.name() })

        val input = Grpc("test-id", config, null)
        val testConsumer = TestConsumer()
        input.start(testConsumer)

        val events = testConsumer.getEvents()
        Assert.assertEquals(testNumber, events.count())
        events.forEach {
            Assert.assertTrue(it.containsKey("message"))
            Assert.assertTrue(it["message"].toString().startsWith("Hello, $testName!"))
        }
        Assert.assertTrue(true)
    }

    private class TestConsumer : Consumer<Map<String, Any>> {
        private val events: MutableList<Map<String, Any>> = ArrayList()
        override fun accept(event: Map<String, Any>) {
            synchronized(this) { events.add(event) }
        }

        fun getEvents(): List<Map<String, Any>> {
            return events
        }
    }
}