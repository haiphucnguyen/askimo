/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2026 Hai Nguyen
 */
package io.askimo.core.intent

import dev.langchain4j.agent.tool.ToolSpecification
import dev.langchain4j.model.embedding.EmbeddingModel
import dev.langchain4j.model.openai.OpenAiEmbeddingModel
import io.askimo.test.extensions.AskimoTestHome
import io.askimo.test.extensions.RetryOnFailure
import io.askimo.testcontainers.SharedOllama
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable
import org.testcontainers.junit.jupiter.Testcontainers

/**
 * Integration tests for [DetectUserIntentCommand] exercising the full [ToolVectorIndex]
 * pipeline with real embeddings from Ollama `nomic-embed-text` via Testcontainers.
 *
 * Using a real embedding model (instead of mocks) lets us test:
 *  - Whether semantically related queries actually match tools
 *  - Whether unrelated queries stay below the similarity threshold
 *  - Deduplication between the keyword layer and vector layer
 *  - Confidence levels (55 = vector-only low score, 70 = vector-only high score,
 *    85 = keyword match, regardless of vector)
 *
 * Model weights are cached in the `askimo-ollama-models-cache` Docker volume so
 * subsequent runs skip the pull step. Set `DISABLE_DOCKER_TESTS=true` to skip
 * this suite in environments without Docker.
 */
@DisabledIfEnvironmentVariable(
    named = "DISABLE_DOCKER_TESTS",
    matches = "(?i)true|1|yes",
)
@Testcontainers
@TestInstance(Lifecycle.PER_CLASS)
@AskimoTestHome
class DetectUserIntentCommandVectorIndexTest {

    companion object {
        private const val EMBEDDING_MODEL = "nomic-embed-text"
    }

    private lateinit var embeddingModel: EmbeddingModel

    @BeforeAll
    fun setUp() {
        val ollama = SharedOllama.container
        assertTrue(ollama.isRunning, "Ollama container should be running")
        SharedOllama.ensureModelPulled(EMBEDDING_MODEL)

        val baseUrl = "http://${ollama.host}:${ollama.getMappedPort(11434)}/v1"
        embeddingModel = OpenAiEmbeddingModel.builder()
            .apiKey("not-needed")
            .baseUrl(baseUrl)
            .modelName(EMBEDDING_MODEL)
            .build()

        println("Embedding model ready at $baseUrl using $EMBEDDING_MODEL")
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun spec(name: String, description: String): ToolSpecification = ToolSpecification.builder().name(name).description(description).build()

    private fun mcpTool(
        name: String,
        category: ToolCategory,
        description: String,
        strategy: Int = ToolStrategy.INTENT_BASED,
    ): ToolConfig = ToolConfig(
        specification = spec(name, description),
        category = category,
        strategy = strategy,
        source = ToolSource.MCP_EXTERNAL,
    )

    private fun buildIndex(
        tools: List<ToolConfig>,
        minScore: Double = 0.75,
    ): ToolVectorIndex = ToolVectorIndex(embeddingModel, minScore = minScore).also { it.index(tools) }

    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Null index — no-op behaviour")
    inner class NullIndexTests {

        @Test
        @DisplayName("Null toolVectorIndex does not break keyword detection")
        fun nullVectorIndexDoesNotBreakKeywordDetection() {
            val builtinTool = ToolConfig(
                specification = spec("create_chart", "Creates a chart from data"),
                category = ToolCategory.VISUALIZE,
                strategy = ToolStrategy.INTENT_BASED,
                source = ToolSource.ASKIMO_BUILTIN,
            )

            val result = DetectUserIntentCommand.execute(
                userMessage = "Create a chart for the data",
                availableTools = listOf(builtinTool),
                mcpTools = emptyList(),
                toolVectorIndex = null,
            )

            assertEquals(1, result.tools.size)
            assertEquals(ToolCategory.VISUALIZE, result.tools[0].category)
            assertEquals(85, result.confidence)
        }
    }

    @Nested
    @DisplayName("Vector-only matches — semantic intent without keywords")
    inner class VectorOnlyMatchTests {

        @Test
        @RetryOnFailure(maxAttempts = 3)
        @DisplayName("Semantically relevant query matches tool via vector search")
        fun semanticQueryMatchesToolViaVector() {
            val tool = mcpTool(
                name = "github_copilot_assign",
                category = ToolCategory.NETWORK,
                description = "Assign Copilot coding agent to a GitHub issue to automatically generate a pull request with code changes",
            )
            val index = buildIndex(listOf(tool))

            val result = DetectUserIntentCommand.execute(
                // semantically related but no keyword-layer keywords
                userMessage = "let the AI agent automatically fix this open ticket",
                availableTools = emptyList(),
                mcpTools = listOf(tool),
                toolVectorIndex = index,
            )

            assertTrue(
                result.tools.isNotEmpty(),
                "Semantically relevant query should match via vector search",
            )
            assertEquals("github_copilot_assign", result.tools[0].specification.name())
            assertTrue(
                result.confidence in setOf(55, 70),
                "Vector-only match should yield confidence 55 or 70, got ${result.confidence}",
            )
        }
    }

    @Nested
    @DisplayName("Deduplication — keyword + vector union")
    inner class DeduplicationTests {

        @Test
        @RetryOnFailure(maxAttempts = 3)
        @DisplayName("Tool matched by both keyword and vector appears exactly once")
        fun keywordToolNotDuplicatedByVector() {
            val tool = mcpTool(
                name = "github_code_search",
                category = ToolCategory.SEARCH,
                description = "Searches GitHub repositories for code, issues, and pull requests using text queries",
            )
            val index = buildIndex(listOf(tool))

            val result = DetectUserIntentCommand.execute(
                // "search" triggers keyword layer AND tool is semantically relevant
                userMessage = "search for authentication code in the repository",
                availableTools = emptyList(),
                mcpTools = listOf(tool),
                toolVectorIndex = index,
            )

            val names = result.tools.map { it.specification.name() }
            assertEquals(
                names.distinct(),
                names,
                "Each tool name must appear at most once — keyword + vector match must deduplicate",
            )
            assertTrue(
                result.tools.size <= 1,
                "Deduplication must prevent the same tool appearing twice",
            )
        }

        @Test
        @RetryOnFailure(maxAttempts = 3)
        @DisplayName("Vector-only MCP tool is appended after keyword-matched builtin tool")
        fun vectorOnlyToolAppendedAfterKeywordMatch() {
            val keywordTool = ToolConfig(
                specification = spec("create_chart", "Creates a data visualisation chart"),
                category = ToolCategory.VISUALIZE,
                strategy = ToolStrategy.INTENT_BASED,
                source = ToolSource.ASKIMO_BUILTIN,
            )
            val vectorTool = mcpTool(
                name = "copilot_issue_resolver",
                category = ToolCategory.NETWORK,
                description = "Assigns GitHub Copilot to automatically resolve a GitHub issue by writing and committing code",
            )
            val index = buildIndex(listOf(keywordTool, vectorTool))

            val result = DetectUserIntentCommand.execute(
                // "chart" triggers keyword; "copilot fix issue" relies on vector
                userMessage = "create a chart and let copilot automatically fix the open issue",
                availableTools = listOf(keywordTool),
                mcpTools = listOf(vectorTool),
                toolVectorIndex = index,
            )

            assertTrue(
                result.tools.any { it.specification.name() == "create_chart" },
                "Keyword-matched builtin tool should be present",
            )
            assertTrue(
                result.tools.any { it.specification.name() == "copilot_issue_resolver" },
                "Vector-only MCP tool should be appended",
            )
            assertEquals(2, result.tools.size)
            // Keyword match always dominates confidence
            assertEquals(90, result.confidence)
        }
    }

    @Nested
    @DisplayName("Strategy filtering with vector index")
    inner class StrategyFilteringTests {

        @Test
        @RetryOnFailure(maxAttempts = 3)
        @DisplayName("FOLLOW_UP_BASED MCP tool excluded even when vector matches")
        fun followUpBasedToolExcludedFromVectorResults() {
            val followUpTool = mcpTool(
                name = "generate_report_chart",
                category = ToolCategory.VISUALIZE,
                description = "Generates a visualisation chart from structured data returned by the AI",
                strategy = ToolStrategy.FOLLOW_UP_BASED,
            )
            val index = buildIndex(listOf(followUpTool))

            val result = DetectUserIntentCommand.execute(
                userMessage = "show me a chart of the results",
                availableTools = emptyList(),
                mcpTools = listOf(followUpTool),
                toolVectorIndex = index,
            )

            assertEquals(
                0,
                result.tools.size,
                "FOLLOW_UP_BASED tool must not appear in Stage-1 detection even if vector matches",
            )
        }

        @Test
        @RetryOnFailure(maxAttempts = 3)
        @DisplayName("BOTH strategy MCP tool included via vector search")
        fun bothStrategyToolIncludedViaVector() {
            val bothTool = mcpTool(
                name = "cross_repo_search",
                category = ToolCategory.SEARCH,
                description = "Searches across all connected repositories for files, code, and documentation",
                strategy = ToolStrategy.BOTH,
            )
            val index = buildIndex(listOf(bothTool))

            val result = DetectUserIntentCommand.execute(
                userMessage = "look across all connected repos for the config file",
                availableTools = emptyList(),
                mcpTools = listOf(bothTool),
                toolVectorIndex = index,
            )

            assertTrue(
                result.tools.isNotEmpty(),
                "BOTH strategy tool should appear in Stage-1 results via vector match",
            )
            assertEquals("cross_repo_search", result.tools[0].specification.name())
        }
    }

    @Nested
    @DisplayName("Result metadata")
    inner class ResultMetadataTests {

        @Test
        @RetryOnFailure(maxAttempts = 3)
        @DisplayName("Reasoning mentions 'vector search' for vector-only matched tools")
        fun reasoningMentionsVectorSearch() {
            val tool = mcpTool(
                name = "webhook_trigger",
                category = ToolCategory.NETWORK,
                description = "Sends an HTTP POST request to a configured webhook endpoint to trigger an external workflow",
            )
            val index = buildIndex(listOf(tool))

            val result = DetectUserIntentCommand.execute(
                userMessage = "trigger the external workflow integration",
                availableTools = emptyList(),
                mcpTools = listOf(tool),
                toolVectorIndex = index,
            )

            if (result.tools.isNotEmpty()) {
                assertTrue(
                    result.reasoning.contains("vector search"),
                    "Reasoning must mention 'vector search' for vector-only matches. Got: ${result.reasoning}",
                )
            }
        }

        @Test
        @DisplayName("Stage is always USER_INPUT regardless of detection layer")
        fun stageIsAlwaysUserInput() {
            val tool = mcpTool(
                name = "any_tool",
                category = ToolCategory.OTHER,
                description = "A tool that can be matched semantically",
            )
            val index = buildIndex(listOf(tool))

            val result = DetectUserIntentCommand.execute(
                userMessage = "do something useful",
                availableTools = emptyList(),
                mcpTools = listOf(tool),
                toolVectorIndex = index,
            )

            assertEquals(IntentStage.USER_INPUT, result.stage)
        }

        @Test
        @RetryOnFailure(maxAttempts = 3)
        @DisplayName("distinctBy name prevents duplicate tool entries in result")
        fun distinctByNamePreventsResultDuplication() {
            // Two ToolConfig with identical names — distinctBy must collapse them
            val tool = mcpTool(
                "duplicate_name",
                ToolCategory.SEARCH,
                "Searches files in the project repository",
            )
            val toolDuplicate = mcpTool(
                "duplicate_name",
                ToolCategory.SEARCH,
                "Searches files in the project repository — duplicate entry",
            )

            val index = buildIndex(listOf(tool, toolDuplicate))

            val result = DetectUserIntentCommand.execute(
                userMessage = "search the codebase for authentication logic",
                availableTools = emptyList(),
                mcpTools = listOf(tool, toolDuplicate),
                toolVectorIndex = index,
            )

            val names = result.tools.map { it.specification.name() }
            assertEquals(
                names.distinct(),
                names,
                "Each tool name must appear at most once — distinctBy must deduplicate",
            )
        }
    }

    @Nested
    @DisplayName("Re-index — previous state is fully cleared")
    inner class ReIndexTests {

        @Test
        @RetryOnFailure(maxAttempts = 3)
        @DisplayName("Re-indexing with a new tool set replaces the old index")
        fun reIndexReplacesOldTools() {
            val oldTool = mcpTool(
                name = "old_database_tool",
                category = ToolCategory.DATABASE,
                description = "Manages legacy database schema migrations for the old system",
            )
            val newTool = mcpTool(
                name = "new_webhook_tool",
                category = ToolCategory.NETWORK,
                description = "Triggers a webhook to notify external systems about deployment events",
            )

            val index = ToolVectorIndex(embeddingModel, minScore = 0.50)
            index.index(listOf(oldTool))

            // Re-index with only newTool — oldTool must be gone
            index.index(listOf(newTool))

            val result = DetectUserIntentCommand.execute(
                userMessage = "notify the external system about the new deployment",
                availableTools = emptyList(),
                mcpTools = listOf(oldTool, newTool),
                toolVectorIndex = index,
            )

            assertFalse(
                result.tools.any { it.specification.name() == "old_database_tool" },
                "old_database_tool must not appear after re-index removed it from the index",
            )
            assertTrue(
                result.tools.any { it.specification.name() == "new_webhook_tool" },
                "new_webhook_tool should be findable after re-index",
            )
        }
    }
}
