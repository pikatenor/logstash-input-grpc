package net.p1kachu.logstash.input.grpc.util

import com.google.common.collect.ImmutableList
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.protobuf.Descriptors.Descriptor
import com.google.protobuf.DynamicMessage
import com.google.protobuf.util.JsonFormat
import com.google.protobuf.util.JsonFormat.TypeRegistry

class DynamicMessageBuilder(private val descriptor: Descriptor, registry: TypeRegistry) {
    private val jsonParser: JsonFormat.Parser = JsonFormat.parser().usingTypeRegistry(registry)
    private var jsonString: String? = null
    private var jsonMap: Map<String, Any?>? = null

    fun forString(string: String): DynamicMessageBuilder = apply { jsonString = string }
    fun forHashMap(map: Map<String, Any?>) = apply { jsonMap = map }

    fun build(): ImmutableList<DynamicMessage> {
        val builder = DynamicMessage.newBuilder(descriptor)
        if (jsonMap != null) {
            jsonParser.merge(Gson().toJson(jsonMap), builder)
        }
        if (jsonString != null) {
            jsonParser.merge(jsonString, builder)
        }
        return ImmutableList.of(builder.build())
    }
}