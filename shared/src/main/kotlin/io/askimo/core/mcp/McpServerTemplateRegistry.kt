/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2026 Askimo
 */
package io.askimo.core.mcp

/**
 * Built-in catalog of popular MCP server definitions.
 *
 * Each entry is a fully-specified [McpServerDefinition] whose `stdioConfig`/`httpConfig`
 * may contain `{{key}}` placeholders that correspond to entries in [McpServerDefinition.parameters].
 * The UI resolves those placeholders with user-supplied values before saving.
 *
 * ## Adding a new template
 * 1. Create a new [McpServerDefinition] value below.
 * 2. Add `"popular"` to [tags] if it should appear in the Popular filter.
 * 3. Add a category tag matching one of [CATEGORIES] (e.g. `"dev-tools"`, `"databases"`).
 * 4. Declare every user-configurable placeholder as a [Parameter].
 */
object McpServerTemplateRegistry {

    val CATEGORIES = listOf("All", "Popular", "Dev Tools", "Productivity", "Databases", "Search", "Utilities")

    val templates: List<McpServerDefinition> = listOf(

        // ── Dev Tools ─────────────────────────────────────────────────────────

        McpServerDefinition(
            id = "template-github",
            name = "GitHub",
            description = "Access repositories, issues, pull requests, and file contents via the official GitHub remote MCP server. No local tools required.",
            transportType = TransportType.HTTP,
            httpConfig = HttpConfig(
                urlTemplate = "https://api.githubcopilot.com/mcp/",
                headersTemplate = mapOf("Authorization" to "Bearer {{githubToken}}"),
            ),
            parameters = listOf(
                Parameter(
                    key = "githubToken",
                    label = "GitHub Personal Access Token",
                    type = ParameterType.SECRET,
                    description = "Create at github.com/settings/tokens. Requires repo scope.",
                    placeholder = "ghp_...",
                    location = ParameterLocation.HTTP_HEADER,
                ),
            ),
            tags = listOf("popular", "dev-tools"),
            author = "GitHub",
        ),

        McpServerDefinition(
            id = "template-github-enterprise",
            name = "GitHub Enterprise",
            description = "Access GitHub Enterprise Cloud (ghe.com) repositories and resources via the remote MCP server.",
            transportType = TransportType.HTTP,
            httpConfig = HttpConfig(
                urlTemplate = "https://copilot-api.{{gheHost}}/mcp",
                headersTemplate = mapOf("Authorization" to "Bearer {{githubToken}}"),
            ),
            parameters = listOf(
                Parameter(
                    key = "gheHost",
                    label = "GitHub Enterprise Host",
                    type = ParameterType.STRING,
                    description = "Your GHE host, e.g. octocorp.ghe.com",
                    placeholder = "octocorp.ghe.com",
                    location = ParameterLocation.HTTP_URL,
                ),
                Parameter(
                    key = "githubToken",
                    label = "GitHub Personal Access Token",
                    type = ParameterType.SECRET,
                    description = "PAT with appropriate scopes for your GitHub Enterprise organization.",
                    placeholder = "ghp_...",
                    location = ParameterLocation.HTTP_HEADER,
                ),
            ),
            tags = listOf("dev-tools"),
            author = "GitHub",
        ),

        McpServerDefinition(
            id = "template-puppeteer",
            name = "Puppeteer",
            description = "Browser automation — navigate web pages, take screenshots, fill forms, and run scripts.",
            transportType = TransportType.STDIO,
            stdioConfig = StdioConfig(
                commandTemplate = listOf("npx", "-y", "@modelcontextprotocol/server-puppeteer"),
            ),
            parameters = emptyList(),
            tags = listOf("popular", "dev-tools"),
            author = "Anthropic",
        ),

        // ── Productivity ──────────────────────────────────────────────────────

        McpServerDefinition(
            id = "template-notion",
            name = "Notion",
            description = "Read and write Notion pages, databases, and blocks via the Notion API.",
            transportType = TransportType.STDIO,
            stdioConfig = StdioConfig(
                commandTemplate = listOf("npx", "-y", "@notionhq/notion-mcp-server"),
                envTemplate = mapOf(
                    "OPENAPI_MCP_HEADERS" to """{"Authorization": "Bearer {{notionToken}}", "Notion-Version": "2022-06-28"}""",
                ),
            ),
            parameters = listOf(
                Parameter(
                    key = "notionToken",
                    label = "Notion Integration Token",
                    type = ParameterType.SECRET,
                    description = "Create an integration at notion.so/my-integrations and copy the token.",
                    placeholder = "ntn_...",
                    location = ParameterLocation.ENVIRONMENT,
                ),
            ),
            tags = listOf("popular", "productivity"),
            author = "Notion",
        ),

        McpServerDefinition(
            id = "template-slack",
            name = "Slack",
            description = "Read messages, post to channels, list users, and manage Slack workspaces.",
            transportType = TransportType.STDIO,
            stdioConfig = StdioConfig(
                commandTemplate = listOf("npx", "-y", "@modelcontextprotocol/server-slack"),
                envTemplate = mapOf(
                    "SLACK_BOT_TOKEN" to "{{slackBotToken}}",
                    "SLACK_TEAM_ID" to "{{slackTeamId}}",
                ),
            ),
            parameters = listOf(
                Parameter(
                    key = "slackBotToken",
                    label = "Slack Bot Token",
                    type = ParameterType.SECRET,
                    description = "OAuth token for your Slack app bot user (starts with xoxb-).",
                    placeholder = "xoxb-...",
                    location = ParameterLocation.ENVIRONMENT,
                ),
                Parameter(
                    key = "slackTeamId",
                    label = "Slack Team ID",
                    type = ParameterType.STRING,
                    description = "Your Slack workspace Team ID. Found in the workspace URL or admin panel.",
                    placeholder = "T01234567",
                    location = ParameterLocation.ENVIRONMENT,
                ),
            ),
            tags = listOf("popular", "productivity"),
            author = "Anthropic",
        ),

        // ── Search ────────────────────────────────────────────────────────────

        McpServerDefinition(
            id = "template-brave-search",
            name = "Brave Search",
            description = "Search the web and retrieve results using the Brave Search API.",
            transportType = TransportType.STDIO,
            stdioConfig = StdioConfig(
                commandTemplate = listOf("npx", "-y", "@modelcontextprotocol/server-brave-search"),
                envTemplate = mapOf("BRAVE_API_KEY" to "{{braveApiKey}}"),
            ),
            parameters = listOf(
                Parameter(
                    key = "braveApiKey",
                    label = "Brave Search API Key",
                    type = ParameterType.SECRET,
                    description = "Get a free API key at brave.com/search/api.",
                    placeholder = "BSAxxxxxxxx...",
                    location = ParameterLocation.ENVIRONMENT,
                ),
            ),
            tags = listOf("popular", "search"),
            author = "Anthropic",
        ),

        // ── Utilities ─────────────────────────────────────────────────────────

        McpServerDefinition(
            id = "template-filesystem",
            name = "Filesystem",
            description = "Read and write files within a specific directory on your local machine.",
            transportType = TransportType.STDIO,
            stdioConfig = StdioConfig(
                commandTemplate = listOf("npx", "-y", "@modelcontextprotocol/server-filesystem", "{{rootPath}}"),
            ),
            parameters = listOf(
                Parameter(
                    key = "rootPath",
                    label = "Allowed Root Directory",
                    type = ParameterType.PATH,
                    description = "The AI will only be able to access files inside this directory.",
                    placeholder = "/Users/you/projects",
                    location = ParameterLocation.COMMAND,
                ),
            ),
            tags = listOf("popular", "utilities"),
            author = "Anthropic",
        ),

        McpServerDefinition(
            id = "template-fetch",
            name = "Fetch",
            description = "Fetch any URL and convert the response to Markdown for easy reading.",
            transportType = TransportType.STDIO,
            stdioConfig = StdioConfig(
                commandTemplate = listOf("uvx", "mcp-server-fetch"),
            ),
            parameters = emptyList(),
            tags = listOf("popular", "utilities"),
            author = "Anthropic",
        ),

        McpServerDefinition(
            id = "template-memory",
            name = "Memory",
            description = "Persistent knowledge graph memory — lets the AI remember facts across sessions.",
            transportType = TransportType.STDIO,
            stdioConfig = StdioConfig(
                commandTemplate = listOf("npx", "-y", "@modelcontextprotocol/server-memory"),
            ),
            parameters = emptyList(),
            tags = listOf("popular", "utilities"),
            author = "Anthropic",
        ),
    )

    /** Returns templates matching the given category label (case-insensitive). "All" returns everything. */
    fun getByCategory(category: String): List<McpServerDefinition> = when (category.lowercase()) {
        "all" -> templates
        "popular" -> templates.filter { "popular" in it.tags }
        else -> templates.filter { category.lowercase().replace(" ", "-") in it.tags }
    }

    /**
     * Resolves all `{{key}}` placeholders in a template's command and env templates
     * with the supplied [paramValues], producing a ready-to-use [McpServerDefinition].
     */
    fun resolve(template: McpServerDefinition, instanceId: String, instanceName: String, paramValues: Map<String, String>): McpServerDefinition {
        fun String.resolve() = paramValues.entries.fold(this) { acc, (k, v) -> acc.replace("{{$k}}", v) }

        val resolvedStdio = template.stdioConfig?.let { cfg ->
            StdioConfig(
                commandTemplate = cfg.commandTemplate.map { it.resolve() }.filter { it.isNotBlank() },
                envTemplate = cfg.envTemplate.mapValues { (_, v) -> v.resolve() },
                workingDirectory = cfg.workingDirectory?.resolve(),
            )
        }
        val resolvedHttp = template.httpConfig?.let { cfg ->
            HttpConfig(
                urlTemplate = cfg.urlTemplate.resolve(),
                headersTemplate = cfg.headersTemplate.mapValues { (_, v) -> v.resolve() },
                timeoutMs = cfg.timeoutMs,
            )
        }
        return template.copy(
            id = instanceId,
            name = instanceName,
            stdioConfig = resolvedStdio,
            httpConfig = resolvedHttp,
            parameters = emptyList(), // resolved — no longer needed
            tags = template.tags + "global",
        )
    }
}
