package za.proto.mock

import com.google.protobuf.*
import io.ktor.application.call
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.response.respondBytes
import io.ktor.routing.post
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import java.io.File

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        printHelp()
        return
    }

    val argsMap = args.fold(Pair(emptyMap<String, List<String>>(), "")) { (map, lastKey), elem ->
        if (elem.startsWith("-")) Pair(map + (elem to emptyList()), elem)
        else Pair(map + (lastKey to map.getOrDefault(lastKey, emptyList()) + elem), lastKey)
    }.first

    startMockServer(argsMap["-m"], argsMap["-f"], argsMap["-d"], argsMap["-p"])
}

fun printHelp() {
    println("Usage: -p <port> (optional, default 8080) -m <Protobuf message full type name> -f <file> -d <Descriptor file 1> .. <Descriptor file N>")
}

fun startMockServer(messageTypeNames: List<String>?, files: List<String>?, descriptorFiles: List<String>?, ports: List<String>?) {
    val messageTypeName = messageTypeNames?.first()
    val file = files?.first()

    if (messageTypeName == null || file == null || descriptorFiles.isNullOrEmpty()) {
        printHelp()
        return
    }

    val text = File(file).readText()

    if (text.isEmpty()) {
        println("File $file not exists or empty!")
        return
    }

    val map =
        getFileDescriptorProtos(descriptorFiles.map { descriptorFile -> DescriptorProtos.FileDescriptorSet.parseFrom(File(descriptorFile).readBytes()) })

    val descriptor = createMessageDescriptor(messageTypeName, map, null)

    val dynamicMessageBuilder = DynamicMessage.newBuilder(descriptor)

    TextFormat.getParser().merge(text, dynamicMessageBuilder)

    startServer(dynamicMessageBuilder.build(), ports)
}

fun startServer(response: DynamicMessage, ports: List<String>?) {
    val port = ports?.first()?.toInt() ?: 8080
    embeddedServer(Netty, port) {
        routing {
            post("/") {
                call.respondBytes(response.toByteArray(), ContentType("application", "x-protobuf; messageType=\"${response.descriptorForType.fullName}\"", emptyList()), HttpStatusCode.OK)
            }
        }
    }.start(wait = true)
}

fun getFileDescriptorProtos(fileDescSets: List<DescriptorProtos.FileDescriptorSet>) =
    fileDescSets.flatMap { fileDescSet -> fileDescSet.fileList.map { fileDescProto -> fileDescProto } }
        .map { fileDescProto -> getProtoFileName(fileDescProto.name) to fileDescProto }.toMap()

fun getProtoFileName(fullPath: String): String = fullPath.split("/").last()

fun createMessageDescriptor(
    messageTypeName: String,
    map: Map<String, DescriptorProtos.FileDescriptorProto>,
    parentMessageDescriptor: Descriptors.Descriptor? = null
): Descriptors.Descriptor? {
    val fileDescriptorProto =
        getProtoFileForMessage(messageTypeName, map) ?: throw Exception("Message $messageTypeName not found!")

    println("Proto file ${fileDescriptorProto.name} contains message type $messageTypeName")

    val dependencies = getDependencies(map, fileDescriptorProto)

    val fileDescriptor = Descriptors.FileDescriptor.buildFrom(fileDescriptorProto, dependencies)

    return fileDescriptor.findMessageTypeByNameOrFullName(messageTypeName) ?: findMessageDescriptorInHierarchy(
        messageTypeName,
        parentMessageDescriptor
    )
}

fun getProtoFileForMessage(messageTypeName: String, map: Map<String, DescriptorProtos.FileDescriptorProto>): DescriptorProtos.FileDescriptorProto? =
    map.values.firstOrNull { fileDescriptorProto ->
        messageInList(
            fileDescriptorProto.messageTypeList,
            fileDescriptorProto.`package`,
            messageTypeName
        )
    }

fun messageInList(
    messageTypeList: List<DescriptorProtos.DescriptorProto>,
    packageName: String,
    messageTypeName: String
): Boolean {
    return messageTypeList.any {
        messageTypeName == it.name || messageTypeName == "$packageName.${it.name}" || messageInList(
            it.nestedTypeList,
            packageName,
            messageTypeName
        )
    }
}

fun getDependencies(
    fileDescProtoMap: Map<String, DescriptorProtos.FileDescriptorProto>,
    fileDescProto: DescriptorProtos.FileDescriptorProto
): Array<Descriptors.FileDescriptor> =
    fileDescProto.dependencyList.filter { fileDescProtoMap.containsKey(getProtoFileName(it)) }
        .map {
            Descriptors.FileDescriptor.buildFrom(
                fileDescProtoMap[getProtoFileName(it)],
                getDependencies(fileDescProtoMap, fileDescProtoMap[getProtoFileName(it)]!!)
            )
        }.toTypedArray()

fun findMessageDescriptorInHierarchy(
    messageTypeName: String,
    parent: Descriptors.Descriptor?
): Descriptors.Descriptor? =
    parent?.findNestedTypeByName(messageTypeName) ?: findMessageDescriptorInHierarchy(
        messageTypeName,
        parent?.containingType
    )

// EXTENSIONS

fun Descriptors.FileDescriptor.findMessageTypeByNameOrFullName(name: String): Descriptors.Descriptor? {
    return this.findMessageTypeByName(name) ?: this.findMessageTypeByName(name.replace("${this.`package`}.", ""))
}