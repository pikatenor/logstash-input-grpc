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
            Assert.assertEquals("Hello, $testName!", it["message"])

            Assert.assertEquals(2.0, it["double_value"])
            Assert.assertEquals(3.0F, it["float_value"])
            Assert.assertEquals(4, it["int32_value"])
            Assert.assertEquals(5.toLong(), it["int64_value"])
            Assert.assertEquals(6, it["uint32_value"])
            Assert.assertEquals(7.toLong(), it["uint64_value"])
            Assert.assertEquals(-8, it["sint32_value"])
            Assert.assertEquals((-9).toLong(), it["sint64_value"])
            Assert.assertEquals(10, it["fixed32_value"])
            Assert.assertEquals(11.toLong(), it["fixed64_value"])
            Assert.assertEquals(-12, it["sfixed32_value"])
            Assert.assertEquals((-13).toLong(), it["sfixed64_value"])
            Assert.assertEquals(true, it["boolean_value"])
            Assert.assertEquals("string", it["string_value"])
            Assert.assertEquals("16", it["bytes_value"])
            Assert.assertEquals("ONE", it["enum_value"])

            Assert.assertTrue(it["nested"] is Map<*, *>)
            Assert.assertEquals("nested_string", (it["nested"] as Map<String, Any>)["nested_string"])

            Assert.assertEquals(testNumber, (it["repeated_string"] as List<*>).count())
            Assert.assertEquals(testNumber, (it["repeated_nested"] as List<*>).count())
            (it["repeated_string"] as List<String>).forEach { str ->
                Assert.assertEquals("repeated_string", str)
            }
            (it["repeated_nested"] as List<Map<String, Any>>).forEach { map ->
                Assert.assertEquals("repeated_nested_string", map["nested_string"])
            }
        }
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