package net.p1kachu.logstash.input.grpc

import co.elastic.logstash.api.*
import com.google.common.collect.ImmutableList
import com.google.common.net.HostAndPort
import com.google.protobuf.DescriptorProtos.FileDescriptorSet
import com.google.protobuf.DynamicMessage
import com.google.protobuf.util.JsonFormat.TypeRegistry
import io.grpc.CallOptions
import io.grpc.Deadline
import io.grpc.ManagedChannel
import io.grpc.stub.StreamObserver
import me.dinowernli.grpc.polyglot.grpc.ChannelFactory
import me.dinowernli.grpc.polyglot.grpc.DynamicGrpcClient
import me.dinowernli.grpc.polyglot.protobuf.ProtoMethodName
import me.dinowernli.grpc.polyglot.protobuf.ServiceResolver
import net.p1kachu.logstash.input.grpc.util.DynamicMessageBuilder
import net.p1kachu.logstash.input.grpc.util.toMap
import org.apache.logging.log4j.LogManager
import kotlin.Throws
import java.lang.InterruptedException
import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.TimeUnit
import java.util.function.Consumer

// class name must match plugin name
@LogstashPlugin(name = "grpc")
class Grpc(private val id: String, config: Configuration, context: Context?) : Input {
    private val logger = context?.getLogger(this) ?: LogManager.getLogger(Grpc::class.java)

    private val useJsonName: Boolean
    private val deadlineMs: Long?

    private val protoset: FileDescriptorSet
    private val channel: ManagedChannel
    private val client: DynamicGrpcClient
    private val message: ImmutableList<DynamicMessage>
    private val callOptions: CallOptions
        get() {
            var callOptions = CallOptions.DEFAULT
            if (deadlineMs != null) {
                callOptions = callOptions.withDeadlineAfter(deadlineMs, TimeUnit.MILLISECONDS)
            }
            return callOptions
        }

    init {
        // constructors should validate configuration options
        val protosetPath = Paths.get(
            config.get(PROTOSET_PATH) ?: throw IllegalArgumentException("parameter ${PROTOSET_PATH.name()} is required")
        )
        val host = HostAndPort.fromParts(
            config.get(HOSTNAME) ?: throw IllegalArgumentException("parameter ${HOSTNAME.name()} is required"),
            config.get(PORT)?.toInt() ?: throw IllegalArgumentException("parameter ${PORT.name()} is required")
        )
        val grpcMethodName = ProtoMethodName.parseFullGrpcMethodName(
            config.get(RPC_NAME) ?: throw IllegalArgumentException("parameter ${RPC_NAME.name()} is required")
        )
        val messageHash = config.get(MESSAGE)
        val messageJson = config.get(MESSAGE_JSON)
        if (messageJson == null && messageHash == null) {
            throw IllegalArgumentException("parameter ${MESSAGE.name()} or ${MESSAGE_JSON.name()} is required")
        }
        val useTls = config.get(USE_TLS)
        val caPath = config.get(CA_PATH)
        useJsonName = config.get(USE_JSON_NAME)
        val retryPolicy = config.get(RETRY_POLICY)
        deadlineMs = config.get(DEADLINE_MS)

        logger.debug("reading protoset from $protosetPath")
        protoset = FileDescriptorSet.parseFrom(Files.readAllBytes(protosetPath))

        logger.info("creating channel to $host, ${grpcMethodName.fullServiceName}")
        val channelFactory = ChannelFactory.create(useTls, caPath, retryPolicy)
        channel = channelFactory.createChannel(host)

        logger.info("creating dynamic grpc client")
        val serviceResolver = ServiceResolver.fromFileDescriptorSet(protoset)
        val methodDescriptor = serviceResolver.resolveServiceMethod(grpcMethodName)
        if (!methodDescriptor.isServerStreaming) {
            throw RuntimeException("currently this plugin only supports server streaming rpc")
        }
        client = DynamicGrpcClient.create(methodDescriptor, channel)

        val registry = TypeRegistry.newBuilder().apply {
            add(serviceResolver.listMessageTypes())
        }.build()

        logger.info("building rpc message")
        val builder = DynamicMessageBuilder(methodDescriptor.inputType, registry)
        messageJson?.let { builder.forString(it) }
        messageHash?.let { builder.forHashMap(it) }
        message = builder.build()
    }


    override fun start(consumer: Consumer<Map<String, Any>>) {
        val observer = object : StreamObserver<DynamicMessage> {
            override fun onNext(value: DynamicMessage) {
                consumer.accept(value.toMap(useJsonName))
            }
            override fun onError(t: Throwable) {
                logger.error(t)
            }
            override fun onCompleted() {
                logger.info("rpc call ended successfully")
            }
        }
        client.call(message, observer, callOptions).get()
        channel.shutdown()
    }

    override fun stop() {
        logger.info("closing channel")
        channel.shutdownNow()
    }

    @Throws(InterruptedException::class)
    override fun awaitStop() {
        logger.info("waiting channel to be closed")
        channel.awaitTermination(5, TimeUnit.SECONDS)
    }

    override fun configSchema(): Collection<PluginConfigSpec<*>> {
        // should return a list of all configuration options for this plugin
        return listOf(
            PROTOSET_PATH,
            HOSTNAME,
            PORT,
            USE_TLS,
            CA_PATH,
            RPC_NAME,
            MESSAGE,
            MESSAGE_JSON,
            USE_JSON_NAME,
            DEADLINE_MS,
            RETRY_POLICY,
        )
    }

    override fun getId(): String {
        return id
    }

    companion object {
        @JvmField
        val PROTOSET_PATH: PluginConfigSpec<String?> = PluginConfigSpec.requiredStringSetting("protoset_path")
        @JvmField
        val HOSTNAME: PluginConfigSpec<String?> = PluginConfigSpec.requiredStringSetting("host")
        @JvmField
        val PORT: PluginConfigSpec<Long?> = PluginConfigSpec.numSetting("port")
        @JvmField
        val USE_TLS: PluginConfigSpec<Boolean> = PluginConfigSpec.booleanSetting("use_tls", true)
        @JvmField
        val CA_PATH: PluginConfigSpec<String?> = PluginConfigSpec.stringSetting("ca_path", "")
        @JvmField
        val RPC_NAME: PluginConfigSpec<String?> = PluginConfigSpec.requiredStringSetting("rpc")
        @JvmField
        val MESSAGE: PluginConfigSpec<MutableMap<String, Any?>?> = PluginConfigSpec.hashSetting("message")
        @JvmField
        val MESSAGE_JSON: PluginConfigSpec<String?> = PluginConfigSpec.stringSetting("message_json")
        @JvmField
        val USE_JSON_NAME: PluginConfigSpec<Boolean> = PluginConfigSpec.booleanSetting("use_json_name", false)
        @JvmField
        val DEADLINE_MS: PluginConfigSpec<Long?> = PluginConfigSpec.numSetting("deadline_ms")
        @JvmField
        val RETRY_POLICY: PluginConfigSpec<MutableMap<String, Any?>?> = PluginConfigSpec.hashSetting("retry_policy")
    }
}