package io.askimo.commands

import com.fasterxml.jackson.annotation.JsonIgnoreProperties


@JsonIgnoreProperties(ignoreUnknown = true)
data class CommandDef(
    val name: String,
    val version: Int = 3,
    val description: String? = null,
    val allowed_tools: List<String> = emptyList(),
    val vars: Map<String, VarCall> = emptyMap(),
    val system: String,
    val user_template: String,
    val post_actions: List<PostAction> = emptyList(),
    val defaults: Map<String, String> = emptyMap(),
)

data class VarCall(
    val tool: String,
    val args: Any? = null // List<Any>/Map<String,Any>/String
)

data class PostAction(
    val when_: String? = null, // named 'when' in YAML; map it manually if needed
    val call: VarCall
)