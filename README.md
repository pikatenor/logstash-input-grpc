# Logstash gRPC Input Plugin

This is a Logstash input plugin for gRPC.

This plugin send a RPC request to the other gRPC service and emits the corresponding response as a Logstash event.

Currently, this plugin only supports server-side streaming RPC.

## Configulation
```
input {
  grpc {

   # path to descriptorset binary. needs to be bundled all imports.
   protoset_path => "/path/to/proto.protoset"

   # remote server's hostname, ip address.
   host => "localhost"
   # remote server's port.
   port => 50051
   # whether to use TLS connection to server. (optional; default to true)
   use_tls => false
   # path to CA certificate to verify server. (optional)
   ca_path => "/path/to/ca.crt"

   # fully qualified method name to call. 'service/method' syntax.
   rpc => "hello.MultiGreeter/SayHello"

   # JSON-encoded RPC request.
   message_json => '{"data": {"name": "Logstash"}, number: 10}'
   # ... or Hash styled.
   message => {
     data => {
       name => "Logstash"
     }
     number => 10
   }

   # use lowerCamelCased field name instead of original proto field name. (optional; default to false)
   use_json_name => false
  }
}
```

## Building

### Build logstash-core

This plugin needs compiled Logstash .jar file for building. For more reference, see [Elastic official document](https://www.elastic.co/guide/en/logstash/current/java-input-plugin.html#_set_up_your_environment).

Clone [Logstash](https://github.com/elastic/logstash) to your working directory.

    git clone --branch 7.17 --single-branch https://github.com/elastic/logstash.git <target_folder>

Build .jar of Logstash Java plugin API.

    ./gradlw assemble

Once `logstash-core/build/libs/logstash-core-x.y.z.jar` generated, set the path to `gradle.properties` in this project.

    LOGSTASH_CORE_PATH=<target_folder>/logstash-core

### Build gem

To build plugin gem, run:

    ./gradlew gem

This generates `logstash-input-grpc-x.x.x.gem` on top of this project directory.

To install:

    logstash-plugin install --no-verify ./logstash-input-grpc-0.0.1.gem

## Acknowledgement

This plugin is heavily based on [Polyglot](https://github.com/grpc-ecosystem/polyglot) for making dynamic gRPC client and [logstash-input-java_input_example](https://github.com/logstash-plugins/logstash-input-java_input_example) for boilerplating project. Many thanks to the original authors.
