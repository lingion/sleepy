package com.lingion.sleepy.ui.glass

enum class GlassTopologyNodeRole {
    Producer,
    Consumer,
    ProducerAndConsumer;

    val canProduce: Boolean get() = this != Consumer
    val canConsume: Boolean get() = this != Producer
}

data class GlassTopologyNode(
    val id: String,
    val domain: GlassBackdropDomain,
    val role: GlassTopologyNodeRole
)

data class GlassSamplingLink(
    val producerId: String,
    val consumerId: String
)

enum class GlassTopologyViolationKind {
    DuplicateNode,
    MissingProducer,
    MissingConsumer,
    InvalidProducerRole,
    InvalidConsumerRole,
    DomainMismatch,
    SelfSampling,
    SamplingCycle
}

data class GlassTopologyViolation(
    val kind: GlassTopologyViolationKind,
    val message: String
)

/** Pure validator used by tests and debug scene registration. */
object GlassSceneTopology {
    fun validate(
        nodes: List<GlassTopologyNode>,
        links: List<GlassSamplingLink>
    ): List<GlassTopologyViolation> {
        val violations = mutableListOf<GlassTopologyViolation>()
        val duplicates = nodes.groupBy(GlassTopologyNode::id).filterValues { it.size > 1 }
        duplicates.keys.forEach { id ->
            violations += GlassTopologyViolation(
                GlassTopologyViolationKind.DuplicateNode,
                "Glass node '$id' is registered more than once."
            )
        }
        val byId = nodes.associateBy(GlassTopologyNode::id)
        val validEdges = mutableListOf<GlassSamplingLink>()
        links.forEach { link ->
            val producer = byId[link.producerId]
            val consumer = byId[link.consumerId]
            if (producer == null) {
                violations += GlassTopologyViolation(
                    GlassTopologyViolationKind.MissingProducer,
                    "Glass producer '${link.producerId}' is not registered."
                )
            }
            if (consumer == null) {
                violations += GlassTopologyViolation(
                    GlassTopologyViolationKind.MissingConsumer,
                    "Glass consumer '${link.consumerId}' is not registered."
                )
            }
            if (producer == null || consumer == null) return@forEach
            if (!producer.role.canProduce) {
                violations += GlassTopologyViolation(
                    GlassTopologyViolationKind.InvalidProducerRole,
                    "Glass node '${producer.id}' cannot act as a provider."
                )
            }
            if (!consumer.role.canConsume) {
                violations += GlassTopologyViolation(
                    GlassTopologyViolationKind.InvalidConsumerRole,
                    "Glass node '${consumer.id}' cannot consume a backdrop."
                )
            }
            if (producer.id == consumer.id) {
                violations += GlassTopologyViolation(
                    GlassTopologyViolationKind.SelfSampling,
                    "Glass node '${producer.id}' samples its own output."
                )
            }
            if (producer.domain !in consumer.domain.sourceDomains) {
                violations += GlassTopologyViolation(
                    GlassTopologyViolationKind.DomainMismatch,
                    "${consumer.domain} cannot sample ${producer.domain}."
                )
            }
            if (
                producer.role.canProduce &&
                consumer.role.canConsume &&
                producer.id != consumer.id
            ) {
                validEdges += link
            }
        }

        val graph = validEdges.groupBy(GlassSamplingLink::producerId)
            .mapValues { (_, edges) -> edges.map(GlassSamplingLink::consumerId) }
        val visiting = mutableSetOf<String>()
        val visited = mutableSetOf<String>()
        fun hasCycle(node: String): Boolean {
            if (node in visiting) return true
            if (!visited.add(node)) return false
            visiting += node
            val cyclic = graph[node].orEmpty().any(::hasCycle)
            visiting -= node
            return cyclic
        }
        if (byId.keys.any(::hasCycle)) {
            violations += GlassTopologyViolation(
                GlassTopologyViolationKind.SamplingCycle,
                "Glass sampling graph contains a provider/consumer cycle."
            )
        }
        return violations.distinct()
    }
}
