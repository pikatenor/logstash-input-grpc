package net.p1kachu.logstash.input.grpc

import co.elastic.logstash.api.*
import org.apache.commons.lang3.StringUtils
import java.util.concurrent.CountDownLatch
import kotlin.jvm.Volatile
import kotlin.Throws
import java.lang.InterruptedException
import java.util.function.Consumer

// class name must match plugin name
@LogstashPlugin(name = "grpc")
class Grpc(private val id: String, config: Configuration, context: Context?) : Input {
    private val count: Long
    private val prefix: String
    private val done = CountDownLatch(1)

    @Volatile
    private var stopped = false

    // all plugins must provide a constructor that accepts id, Configuration, and Context
    init {
        // constructors should validate configuration options
        count = config.get(EVENT_COUNT_CONFIG)
        prefix = config.get(PREFIX_CONFIG)
    }

    override fun start(consumer: Consumer<Map<String, Any>>) {

        // The start method should push Map<String, Object> instances to the supplied QueueWriter
        // instance. Those will be converted to Event instances later in the Logstash event
        // processing pipeline.
        //
        // Inputs that operate on unbounded streams of data or that poll indefinitely for new
        // events should loop indefinitely until they receive a stop request. Inputs that produce
        // a finite sequence of events should loop until that sequence is exhausted or until they
        // receive a stop request, whichever comes first.
        var eventCount = 0
        try {
            while (!stopped && eventCount < count) {
                eventCount++
                consumer.accept(mapOf(Pair("message", "$prefix  $eventCount of $count")))
            }
        } finally {
            stopped = true
            done.countDown()
        }
    }

    override fun stop() {
        stopped = true // set flag to request cooperative stop of input
    }

    @Throws(InterruptedException::class)
    override fun awaitStop() {
        done.await() // blocks until input has stopped
    }

    override fun configSchema(): Collection<PluginConfigSpec<*>> {
        // should return a list of all configuration options for this plugin
        return listOf<PluginConfigSpec<*>>(EVENT_COUNT_CONFIG, PREFIX_CONFIG)
    }

    override fun getId(): String {
        return id
    }

    companion object {
        @JvmField
        val EVENT_COUNT_CONFIG: PluginConfigSpec<Long> = PluginConfigSpec.numSetting("count", 3)
        @JvmField
        val PREFIX_CONFIG: PluginConfigSpec<String> = PluginConfigSpec.stringSetting("prefix", "message")
    }
}