package net.p1kachu.logstash.input.grpc.util

import com.google.common.collect.ImmutableList
import com.google.gson.Gson
import com.google.protobuf.ByteString
import com.google.protobuf.Descriptors.Descriptor
import com.google.protobuf.Descriptors.FieldDescriptor
import com.google.protobuf.DynamicMessage
import com.google.protobuf.MapEntry
import com.google.protobuf.util.JsonFormat
import com.google.protobuf.util.JsonFormat.TypeRegistry
import java.nio.charset.Charset
import java.util.Base64

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

fun DynamicMessage.toMap(): Map<String, Any> = this.allFields.entries.associate {
    it.key.name to convertProtoValue(it.key, it.value)
}

private fun convertProtoValue(descriptor: FieldDescriptor, value: Any): Any {
    if(descriptor.isRepeated) {
        return (value as Collection<*>).map { convertSingleValue(descriptor, it!!) }
    }
    return convertSingleValue(descriptor, value)
}

private fun convertSingleValue(descriptor: FieldDescriptor, value: Any): Any {
    @Suppress("WHEN_ENUM_CAN_BE_NULL_IN_JAVA")
    return when(descriptor.javaType) {
        FieldDescriptor.JavaType.INT,
        FieldDescriptor.JavaType.LONG,
        FieldDescriptor.JavaType.FLOAT,
        FieldDescriptor.JavaType.DOUBLE,
        FieldDescriptor.JavaType.BOOLEAN,
        FieldDescriptor.JavaType.STRING ->
            value
        FieldDescriptor.JavaType.BYTE_STRING ->
            Base64.getEncoder().encodeToString((value as ByteString).toByteArray())
        FieldDescriptor.JavaType.ENUM ->
            value.toString()
        FieldDescriptor.JavaType.MESSAGE ->
            (value as DynamicMessage).toMap()
    }
}