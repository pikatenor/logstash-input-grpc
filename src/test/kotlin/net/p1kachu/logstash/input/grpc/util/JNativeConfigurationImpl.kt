package net.p1kachu.logstash.input.grpc.util

import co.elastic.logstash.api.Configuration
import co.elastic.logstash.api.PluginConfigSpec
import java.net.URI
import java.net.URISyntaxException

// This is a very limited implementation of Logstash's ConfigurationImpl which requires JRuby that I don't want to have it
class JNativeConfigurationImpl(private val raw: Map<String, Any>) : Configuration {

    @Suppress("UNCHECKED_CAST")
    override fun <T> get(configSpec: PluginConfigSpec<T>): T =
        if (contains(configSpec)) {
            getRawValue(configSpec) as T
        } else {
            configSpec.defaultValue()
        }

    override fun getRawValue(configSpec: PluginConfigSpec<*>): Any? = raw[configSpec.name()]

    override fun contains(configSpec: PluginConfigSpec<*>): Boolean =  raw.containsKey(configSpec.name())

    override fun allKeys(): Collection<String> = raw.keys
}
