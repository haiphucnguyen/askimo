/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.intent

import dev.langchain4j.agent.tool.ToolSpecification
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

class DetectUserIntentCommandTest {

    data class CategoryTestCase(
        val category: ToolCategory,
        val testMessage: String,
    )

    data class NoIntentTestCase(
        val message: String,
        val description: String,
    )

    data class EdgeCaseTestInput(
        val message: String,
        val expectedTools: Int,
        val description: String,
    )

    data class ResultPropertyTest(
        val message: String,
        val expectedConfidence: Int,
        val expectedToolCount: Int,
        val description: String,
    )

    companion object {
        @JvmStatic
        fun visualizationKeywords() = listOf(
            // Direct keywords
            "chart", "graph", "plot", "visualize", "visualization",
            "diagram", "draw",
            // Contextual phrases
            "display them in chart",
            "show data as graph",
            "present in chart",
            "render as graph",
            "create a chart",
            "generate chart",
            "make a graph",
            "using chart",
            "with graph",
        )

        @JvmStatic
        fun fileWriteKeywords() = listOf(
            // Direct keywords
            "create file", "write file", "save to file", "save as",
            "generate file", "make a file", "write to disk",
            "save this", "write this", "export to", "output to",
            // Contextual phrases
            "save data to csv",
            "write results in file",
            "export as json",
            "output to txt",
            "create a csv",
            "generate json file",
            "store in file",
            "persist to disk",
        )

        @JvmStatic
        fun fileReadKeywords() = listOf(
            // Direct keywords
            "read file", "open file", "show file", "get file",
            "list files", "display file", "view file", "cat",
            "read from", "load file",
            // Contextual phrases
            "read data from csv",
            "open the json",
            "show me the file",
            "display csv content",
            "view json data",
            "load from txt",
            "get data from file",
            "fetch the csv",
            "retrieve from file",
        )

        @JvmStatic
        fun executionKeywords() = listOf(
            // Direct keywords
            "run", "execute", "install", "build", "compile", "test",
            "run the", "execute this", "install package", "deploy",
            "start", "launch", "trigger",
            // Contextual phrases
            "run the tests",
            "execute npm command",
            "install dependencies",
            "build the project",
            "compile source code",
            "test the application",
            "deploy to production",
            "start the server",
            "launch application",
            "trigger build process",
        )

        @JvmStatic
        fun databaseKeywords() = listOf(
            // Direct keywords
            "query", "database", "sql", "select from", "insert into",
            "update", "delete from", "create table", "drop table",
            "connect to database", "db query", "run query",
            // Contextual phrases
            "query the database",
            "fetch from table",
            "get data from db",
            "retrieve from database",
            "insert data into table",
            "update the record",
            "select users from table",
            "connect to mysql",
            "run sql query",
            "execute query on postgres",
        )

        @JvmStatic
        fun networkKeywords() = listOf(
            // Direct keywords
            "http", "api", "request", "call api", "fetch from",
            "get request", "post request", "webhook", "rest api",
            "download", "upload", "curl",
            // Contextual phrases
            "call the api",
            "send request to server",
            "fetch data from url",
            "get from endpoint",
            "post to api",
            "make api call",
            "invoke the service",
            "download from url",
            "upload to endpoint",
            "use rest api",
        )

        @JvmStatic
        fun searchKeywords() = listOf(
            // Direct keywords
            "search", "find", "lookup", "query for", "search for",
            "find files", "search github", "search code", "grep",
            "locate", "discover",
            // Contextual phrases
            "search for the file",
            "find code in project",
            "lookup in repository",
            "locate the file",
            "discover files in repo",
            "grep for pattern",
            "look for the code",
            "query github repository",
            "find in codebase",
            "search in github",
        )

        @JvmStatic
        fun transformKeywords() = listOf(
            // Direct keywords
            "convert", "transform", "parse", "format", "process",
            "convert to", "transform data", "parse json", "parse xml",
            "encode", "decode", "serialize", "deserialize",
            // Contextual phrases
            "convert to json",
            "transform data to csv",
            "parse xml file",
            "format as yaml",
            "process the data",
            "encode to base64",
            "decode from json",
            "serialize to xml",
            "deserialize from yaml",
            "convert format to csv",
        )

        @JvmStatic
        fun versionControlKeywords() = listOf(
            // Direct keywords
            "git", "commit", "push", "pull", "merge", "branch",
            "checkout", "clone", "git commit", "create branch",
            "merge branch", "pull request", "pr",
            // Contextual phrases
            "commit the changes",
            "push to remote",
            "pull from origin",
            "merge into main",
            "create new branch",
            "checkout feature branch",
            "clone the repository",
            "rebase onto master",
            "tag the release",
            "stash changes",
        )

        @JvmStatic
        fun communicationKeywords() = listOf(
            // Direct keywords
            "send email", "email", "notify", "message", "send message",
            "slack", "discord", "teams", "send notification",
            "alert", "post to", "send to",
            // Contextual phrases
            "send email to user",
            "notify the team",
            "post message to slack",
            "alert the users",
            "message to team",
            "email user about update",
            "post to slack channel",
            "inform the team",
            "broadcast notification",
            "send to teams",
        )

        @JvmStatic
        fun monitoringKeywords() = listOf(
            // Direct keywords
            "log", "monitor", "track", "watch", "observe",
            "log event", "track metric", "monitor performance",
            "alert on", "check status", "health check",
            // Contextual phrases
            "monitor the system",
            "track performance metrics",
            "watch service status",
            "observe system behavior",
            "log error events",
            "check health status",
            "track usage metrics",
            "monitor server performance",
            "check service status",
            "alert when errors occur",
        )

        @JvmStatic
        fun categoryTestCases() = listOf(
            CategoryTestCase(ToolCategory.DATABASE, "Run a query on the database"),
            CategoryTestCase(ToolCategory.NETWORK, "Make an HTTP request to the API"),
            CategoryTestCase(ToolCategory.FILE_READ, "Read file from disk"),
            CategoryTestCase(ToolCategory.FILE_WRITE, "Save to file named output.txt"),
            CategoryTestCase(ToolCategory.VISUALIZE, "Create a chart for the data"),
            CategoryTestCase(ToolCategory.EXECUTE, "Run the build command"),
            CategoryTestCase(ToolCategory.SEARCH, "Search for files in the project"),
            CategoryTestCase(ToolCategory.TRANSFORM, "Convert the data to JSON format"),
            CategoryTestCase(ToolCategory.VERSION_CONTROL, "Git commit the changes"),
            CategoryTestCase(ToolCategory.COMMUNICATION, "Send email notification to users"),
            CategoryTestCase(ToolCategory.MONITORING, "Monitor the application performance"),
        )

        @JvmStatic
        fun noIntentMessages() = listOf(
            NoIntentTestCase("What is the weather today?", "generic question"),
            NoIntentTestCase("Hello, how are you?", "conversational message"),
            NoIntentTestCase("Tell me about machine learning", "informational request"),
            NoIntentTestCase("Can you explain quantum physics?", "explanation request"),
            NoIntentTestCase("The sky is blue", "statement"),
            NoIntentTestCase("Good morning!", "greeting"),
        )

        @JvmStatic
        fun edgeCaseInputs() = listOf(
            EdgeCaseTestInput("", 0, "empty message"),
            EdgeCaseTestInput("   ", 0, "whitespace-only message"),
            EdgeCaseTestInput("CrEaTe A ChArT", 1, "mixed case keywords"),
            EdgeCaseTestInput("The battery discharge rate is high", 0, "keyword substring false positive"),
        )

        @JvmStatic
        fun resultPropertyCases() = listOf(
            ResultPropertyTest("Create a chart", 85, 1, "matched intent"),
            ResultPropertyTest("Hello there", 0, 0, "no matched intent"),
        )
    }

    private fun createMockToolSpec(name: String): ToolSpecification = ToolSpecification.builder()
        .name(name)
        .description("Mock tool for testing")
        .build()

    private fun createToolConfig(
        name: String,
        category: ToolCategory,
        strategy: Int = ToolStrategy.INTENT_BASED,
    ): ToolConfig = ToolConfig(
        specification = createMockToolSpec(name),
        category = category,
        strategy = strategy,
        source = ToolSource.ASKIMO_BUILTIN,
    )

    @Nested
    @DisplayName("Parameterized Keyword Tests - All Categories")
    inner class ParameterizedKeywordTests {

        @ParameterizedTest
        @MethodSource("io.askimo.core.intent.DetectUserIntentCommandTest#visualizationKeywords")
        @DisplayName("Should detect each visualization keyword")
        fun testVisualizationKeywords(keyword: String) {
            val tool = createToolConfig("visualize_tool", ToolCategory.VISUALIZE)
            val result = DetectUserIntentCommand.execute(
                "Please $keyword the data",
                listOf(tool),
            )

            assertEquals(1, result.tools.size, "Failed to detect keyword: $keyword")
            assertEquals(ToolCategory.VISUALIZE, result.tools[0].category)
        }

        @ParameterizedTest
        @MethodSource("io.askimo.core.intent.DetectUserIntentCommandTest#fileWriteKeywords")
        @DisplayName("Should detect each file write keyword")
        fun testFileWriteKeywords(keyword: String) {
            val tool = createToolConfig("file_write_tool", ToolCategory.FILE_WRITE)
            val result = DetectUserIntentCommand.execute(
                "Please $keyword the data",
                listOf(tool),
            )

            assertEquals(1, result.tools.size, "Failed to detect keyword: $keyword")
            assertEquals(ToolCategory.FILE_WRITE, result.tools[0].category)
        }

        @ParameterizedTest
        @MethodSource("io.askimo.core.intent.DetectUserIntentCommandTest#fileReadKeywords")
        @DisplayName("Should detect each file read keyword")
        fun testFileReadKeywords(keyword: String) {
            val tool = createToolConfig("file_read_tool", ToolCategory.FILE_READ)
            val result = DetectUserIntentCommand.execute(
                "Please $keyword now",
                listOf(tool),
            )

            assertEquals(1, result.tools.size, "Failed to detect keyword: $keyword")
            assertEquals(ToolCategory.FILE_READ, result.tools[0].category)
        }

        @ParameterizedTest
        @MethodSource("io.askimo.core.intent.DetectUserIntentCommandTest#executionKeywords")
        @DisplayName("Should detect each execution keyword")
        fun testExecutionKeywords(keyword: String) {
            val tool = createToolConfig("execute_tool", ToolCategory.EXECUTE)
            val result = DetectUserIntentCommand.execute(
                "Please $keyword the command",
                listOf(tool),
            )

            assertTrue(result.tools.isNotEmpty(), "Failed to detect keyword: $keyword")
            assertTrue(result.tools.any { it.category == ToolCategory.EXECUTE })
        }

        @ParameterizedTest
        @MethodSource("io.askimo.core.intent.DetectUserIntentCommandTest#databaseKeywords")
        @DisplayName("Should detect each database keyword")
        fun testDatabaseKeywords(keyword: String) {
            val tool = createToolConfig("database_tool", ToolCategory.DATABASE)
            val result = DetectUserIntentCommand.execute(
                "Please $keyword the data",
                listOf(tool),
            )

            assertTrue(result.tools.isNotEmpty(), "Failed to detect keyword: $keyword")
            assertTrue(result.tools.any { it.category == ToolCategory.DATABASE })
        }

        @ParameterizedTest
        @MethodSource("io.askimo.core.intent.DetectUserIntentCommandTest#networkKeywords")
        @DisplayName("Should detect each network keyword")
        fun testNetworkKeywords(keyword: String) {
            val tool = createToolConfig("network_tool", ToolCategory.NETWORK)
            val result = DetectUserIntentCommand.execute(
                "Please $keyword the endpoint",
                listOf(tool),
            )

            assertTrue(result.tools.isNotEmpty(), "Failed to detect keyword: $keyword")
            assertTrue(result.tools.any { it.category == ToolCategory.NETWORK })
        }

        @ParameterizedTest
        @MethodSource("io.askimo.core.intent.DetectUserIntentCommandTest#searchKeywords")
        @DisplayName("Should detect each search keyword")
        fun testSearchKeywords(keyword: String) {
            val tool = createToolConfig("search_tool", ToolCategory.SEARCH)
            val result = DetectUserIntentCommand.execute(
                "Please $keyword the files",
                listOf(tool),
            )

            assertTrue(result.tools.isNotEmpty(), "Failed to detect keyword: $keyword")
            assertTrue(result.tools.any { it.category == ToolCategory.SEARCH })
        }

        @ParameterizedTest
        @MethodSource("io.askimo.core.intent.DetectUserIntentCommandTest#transformKeywords")
        @DisplayName("Should detect each transform keyword")
        fun testTransformKeywords(keyword: String) {
            val tool = createToolConfig("transform_tool", ToolCategory.TRANSFORM)
            val result = DetectUserIntentCommand.execute(
                "Please $keyword the data",
                listOf(tool),
            )

            assertTrue(result.tools.isNotEmpty(), "Failed to detect keyword: $keyword")
            assertTrue(result.tools.any { it.category == ToolCategory.TRANSFORM })
        }

        @ParameterizedTest
        @MethodSource("io.askimo.core.intent.DetectUserIntentCommandTest#versionControlKeywords")
        @DisplayName("Should detect each version control keyword")
        fun testVersionControlKeywords(keyword: String) {
            val tool = createToolConfig("version_control_tool", ToolCategory.VERSION_CONTROL)
            val result = DetectUserIntentCommand.execute(
                "Please $keyword the changes",
                listOf(tool),
            )

            assertTrue(result.tools.isNotEmpty(), "Failed to detect keyword: $keyword")
            assertTrue(result.tools.any { it.category == ToolCategory.VERSION_CONTROL })
        }

        @ParameterizedTest
        @MethodSource("io.askimo.core.intent.DetectUserIntentCommandTest#communicationKeywords")
        @DisplayName("Should detect each communication keyword")
        fun testCommunicationKeywords(keyword: String) {
            val tool = createToolConfig("communication_tool", ToolCategory.COMMUNICATION)
            val result = DetectUserIntentCommand.execute(
                "Please $keyword the notification",
                listOf(tool),
            )

            assertTrue(result.tools.isNotEmpty(), "Failed to detect keyword: $keyword")
            assertTrue(result.tools.any { it.category == ToolCategory.COMMUNICATION })
        }

        @ParameterizedTest
        @MethodSource("io.askimo.core.intent.DetectUserIntentCommandTest#monitoringKeywords")
        @DisplayName("Should detect each monitoring keyword")
        fun testMonitoringKeywords(keyword: String) {
            val tool = createToolConfig("monitoring_tool", ToolCategory.MONITORING)
            val result = DetectUserIntentCommand.execute(
                "Please $keyword the metrics",
                listOf(tool),
            )

            assertTrue(result.tools.isNotEmpty(), "Failed to detect keyword: $keyword")
            assertTrue(result.tools.any { it.category == ToolCategory.MONITORING })
        }
    }

    @Nested
    @DisplayName("Category Coverage Tests - Parameterized")
    inner class CategoryCoverageTests {

        @ParameterizedTest
        @MethodSource("io.askimo.core.intent.DetectUserIntentCommandTest#categoryTestCases")
        @DisplayName("Should detect tool category from user message")
        fun testCategoryDetection(testCase: CategoryTestCase) {
            val tool = createToolConfig("${testCase.category.name.lowercase()}_tool", testCase.category)
            val result = DetectUserIntentCommand.execute(
                testCase.testMessage,
                listOf(tool),
            )

            assertTrue(result.tools.isNotEmpty(), "Failed to detect category: ${testCase.category}")
            assertTrue(
                result.tools.any { it.category == testCase.category },
                "Expected category ${testCase.category} but got ${result.tools.map { it.category }}",
            )
        }
    }

    // Note: Visualization, File Operation, and Execution tests are now covered by
    // parameterized keyword tests in ParameterizedKeywordTests class above

    @Nested
    @DisplayName("Multiple Tool Detection")
    inner class MultipleToolDetectionTests {

        private val visualizeTool = createToolConfig("create_chart", ToolCategory.VISUALIZE)
        private val fileWriteTool = createToolConfig("write_file", ToolCategory.FILE_WRITE)
        private val executeTool = createToolConfig("run_command", ToolCategory.EXECUTE)
        private val tools = listOf(visualizeTool, fileWriteTool, executeTool)

        @Test
        @DisplayName("Should detect multiple intents in one message")
        fun detectMultipleIntents() {
            val result = DetectUserIntentCommand.execute(
                "Create a chart and save to file",
                tools,
            )

            assertEquals(2, result.tools.size)
            assertTrue(result.tools.any { it.category == ToolCategory.VISUALIZE })
            assertTrue(result.tools.any { it.category == ToolCategory.FILE_WRITE })
        }

        @Test
        @DisplayName("Should detect all three types of intents")
        fun detectAllIntents() {
            val result = DetectUserIntentCommand.execute(
                "Run the script, generate a chart, and write file with results",
                tools,
            )

            assertEquals(3, result.tools.size)
            assertTrue(result.tools.any { it.category == ToolCategory.VISUALIZE })
            assertTrue(result.tools.any { it.category == ToolCategory.FILE_WRITE })
            assertTrue(result.tools.any { it.category == ToolCategory.EXECUTE })
        }
    }

    @Nested
    @DisplayName("No Intent Detection - Parameterized")
    inner class NoIntentDetectionTests {

        private val tools = listOf(
            createToolConfig("create_chart", ToolCategory.VISUALIZE),
            createToolConfig("write_file", ToolCategory.FILE_WRITE),
            createToolConfig("run_command", ToolCategory.EXECUTE),
        )

        @ParameterizedTest
        @MethodSource("io.askimo.core.intent.DetectUserIntentCommandTest#noIntentMessages")
        @DisplayName("Should return empty tools for non-intent messages")
        fun testNoIntentDetection(testCase: NoIntentTestCase) {
            val result = DetectUserIntentCommand.execute(
                testCase.message,
                tools,
            )

            assertEquals(0, result.tools.size, "Expected no tools for ${testCase.description}: ${testCase.message}")
            assertEquals(0, result.confidence)
            assertTrue(result.reasoning.contains("No specific tool intent"))
        }
    }

    @Nested
    @DisplayName("Tool Strategy Filtering")
    inner class ToolStrategyFilteringTests {

        @Test
        @DisplayName("Should only include INTENT_BASED tools")
        fun onlyIntentBasedTools() {
            val intentBasedTool = createToolConfig("chart1", ToolCategory.VISUALIZE, ToolStrategy.INTENT_BASED)
            val followUpTool = createToolConfig("chart2", ToolCategory.VISUALIZE, ToolStrategy.FOLLOW_UP_BASED)
            val bothTool = createToolConfig("chart3", ToolCategory.VISUALIZE, ToolStrategy.BOTH)
            val tools = listOf(intentBasedTool, followUpTool, bothTool)

            val result = DetectUserIntentCommand.execute(
                "Create a chart",
                tools,
            )

            // Should include first match from INTENT_BASED and BOTH (find returns first match)
            // Only the first tool of category VISUALIZE is added
            assertEquals(1, result.tools.size)
            assertTrue(result.tools.any { it.specification.name() == "chart1" })
            assertTrue(result.tools.none { it.specification.name() == "chart2" })
        }

        @Test
        @DisplayName("Should include BOTH strategy tools")
        fun includeBothStrategyTools() {
            val bothTool = createToolConfig("multi_tool", ToolCategory.VISUALIZE, ToolStrategy.BOTH)
            val tools = listOf(bothTool)

            val result = DetectUserIntentCommand.execute(
                "Make a graph",
                tools,
            )

            assertEquals(1, result.tools.size)
            assertEquals("multi_tool", result.tools[0].specification.name())
        }

        @Test
        @DisplayName("Should filter out FOLLOW_UP_BASED only tools")
        fun filterFollowUpOnlyTools() {
            val followUpTool = createToolConfig("follow_up_chart", ToolCategory.VISUALIZE, ToolStrategy.FOLLOW_UP_BASED)
            val tools = listOf(followUpTool)

            val result = DetectUserIntentCommand.execute(
                "Create a chart",
                tools,
            )

            assertEquals(0, result.tools.size)
        }
    }

    @Nested
    @DisplayName("MCP Tools Integration")
    inner class McpToolsIntegrationTests {

        @Test
        @DisplayName("Should include MCP tools with INTENT_BASED strategy")
        fun includeMcpTools() {
            val builtinTool = createToolConfig("builtin_execute", ToolCategory.EXECUTE)
            val mcpTool = ToolConfig(
                specification = createMockToolSpec("mcp_execute"),
                category = ToolCategory.EXECUTE,
                strategy = ToolStrategy.INTENT_BASED,
                source = ToolSource.MCP_EXTERNAL,
            )

            val result = DetectUserIntentCommand.execute(
                "Run the command",
                listOf(builtinTool),
                listOf(mcpTool),
            )

            assertEquals(2, result.tools.size)
            assertTrue(result.tools.any { it.source == ToolSource.ASKIMO_BUILTIN })
            assertTrue(result.tools.any { it.source == ToolSource.MCP_EXTERNAL })
        }

        @Test
        @DisplayName("Should filter MCP tools by strategy")
        fun filterMcpToolsByStrategy() {
            val mcpFollowUpTool = ToolConfig(
                specification = createMockToolSpec("mcp_tool"),
                category = ToolCategory.VISUALIZE,
                strategy = ToolStrategy.FOLLOW_UP_BASED,
                source = ToolSource.MCP_EXTERNAL,
            )

            val result = DetectUserIntentCommand.execute(
                "Create chart",
                emptyList(),
                listOf(mcpFollowUpTool),
            )

            assertEquals(0, result.tools.size)
        }
    }

    @Nested
    @DisplayName("Result Properties - Parameterized")
    inner class ResultPropertiesTests {

        private val tools = listOf(
            createToolConfig("create_chart", ToolCategory.VISUALIZE),
        )

        @Test
        @DisplayName("Should set USER_INPUT stage")
        fun correctStage() {
            val result = DetectUserIntentCommand.execute(
                "Create a chart",
                tools,
            )

            assertEquals(IntentStage.USER_INPUT, result.stage)
        }

        @ParameterizedTest
        @MethodSource("io.askimo.core.intent.DetectUserIntentCommandTest#resultPropertyCases")
        @DisplayName("Should set correct confidence based on match")
        fun testConfidenceLevels(testCase: ResultPropertyTest) {
            val result = DetectUserIntentCommand.execute(
                testCase.message,
                tools,
            )

            assertEquals(testCase.expectedConfidence, result.confidence, "Wrong confidence for ${testCase.description}")
            assertEquals(testCase.expectedToolCount, result.tools.size, "Wrong tool count for ${testCase.description}")
        }

        @Test
        @DisplayName("Should provide reasoning when tools matched")
        fun reasoningWhenMatched() {
            val result = DetectUserIntentCommand.execute(
                "Create a chart",
                tools,
            )

            assertNotNull(result.reasoning)
            assertTrue(result.reasoning.contains("Detected intent"))
            assertTrue(result.reasoning.contains("VISUALIZE"))
        }

        @Test
        @DisplayName("Should provide reasoning when no tools matched")
        fun reasoningWhenNotMatched() {
            val result = DetectUserIntentCommand.execute(
                "Hello there",
                tools,
            )

            assertNotNull(result.reasoning)
            assertTrue(result.reasoning.contains("No specific tool intent"))
        }
    }

    @Nested
    @DisplayName("Edge Cases - Parameterized")
    inner class EdgeCasesTests {

        @ParameterizedTest
        @MethodSource("io.askimo.core.intent.DetectUserIntentCommandTest#edgeCaseInputs")
        @DisplayName("Should handle various edge case inputs")
        fun testEdgeCaseInputs(testCase: EdgeCaseTestInput) {
            val tools = listOf(createToolConfig("chart", ToolCategory.VISUALIZE))
            val result = DetectUserIntentCommand.execute(
                testCase.message,
                tools,
            )

            assertEquals(testCase.expectedTools, result.tools.size, "Failed for ${testCase.description}")
        }

        @Test
        @DisplayName("Should handle empty tools list")
        fun handleEmptyToolsList() {
            val result = DetectUserIntentCommand.execute(
                "Create a chart",
                emptyList(),
            )

            assertEquals(0, result.tools.size)
            assertEquals(0, result.confidence)
        }
    }

    @Nested
    @DisplayName("Real-world Scenarios")
    inner class RealWorldScenariosTests {

        private val allTools = listOf(
            createToolConfig("create_chart", ToolCategory.VISUALIZE),
            createToolConfig("write_file", ToolCategory.FILE_WRITE),
            createToolConfig("read_file", ToolCategory.FILE_READ),
            createToolConfig("run_command", ToolCategory.EXECUTE),
            createToolConfig("query_database", ToolCategory.DATABASE),
            createToolConfig("http_request", ToolCategory.NETWORK),
            createToolConfig("search_files", ToolCategory.SEARCH),
            createToolConfig("transform_data", ToolCategory.TRANSFORM),
            createToolConfig("git_operation", ToolCategory.VERSION_CONTROL),
            createToolConfig("send_notification", ToolCategory.COMMUNICATION),
            createToolConfig("track_metrics", ToolCategory.MONITORING),
        )

        @Test
        @DisplayName("GDP data visualization request")
        fun gdpVisualizationRequest() {
            val result = DetectUserIntentCommand.execute(
                "Give me the data of US GDP from 1990 to 2000 and create a chart",
                allTools,
            )

            assertTrue(result.tools.isNotEmpty())
            assertTrue(result.tools.any { it.category == ToolCategory.VISUALIZE })
        }

        @Test
        @DisplayName("GDP data with contextual visualization request")
        fun gdpDataWithContextualVisualization() {
            val result = DetectUserIntentCommand.execute(
                "Give the the data of US GDP from 1990 to 2000, display them in the chart",
                allTools,
            )

            assertTrue(result.tools.isNotEmpty(), "Should detect visualization intent from 'display them in the chart'")
            assertTrue(
                result.tools.any { it.category == ToolCategory.VISUALIZE },
                "Should match VISUALIZE category for contextual pattern",
            )
        }

        @Test
        @DisplayName("Data export request")
        fun dataExportRequest() {
            val result = DetectUserIntentCommand.execute(
                "Save this data as a CSV file",
                allTools,
            )

            assertEquals(1, result.tools.size)
            assertEquals(ToolCategory.FILE_WRITE, result.tools[0].category)
        }

        @Test
        @DisplayName("Contextual file export - export results to csv")
        fun contextualFileExportToCsv() {
            val result = DetectUserIntentCommand.execute(
                "Export the results to csv format",
                allTools,
            )

            assertTrue(result.tools.isNotEmpty(), "Should detect file write intent from 'export...to csv'")
            assertTrue(
                result.tools.any { it.category == ToolCategory.FILE_WRITE },
                "Should match FILE_WRITE category for contextual pattern",
            )
        }

        @Test
        @DisplayName("Contextual file write - save data in file")
        fun contextualSaveDataInFile() {
            val result = DetectUserIntentCommand.execute(
                "Can you save the data in a file for me?",
                allTools,
            )

            assertTrue(result.tools.isNotEmpty(), "Should detect file write intent from 'save...in...file'")
            assertTrue(
                result.tools.any { it.category == ToolCategory.FILE_WRITE },
                "Should match FILE_WRITE category for contextual pattern",
            )
        }

        @Test
        @DisplayName("Contextual file write - write output to json")
        fun contextualWriteOutputToJson() {
            val result = DetectUserIntentCommand.execute(
                "Write the output to json please",
                allTools,
            )

            assertTrue(result.tools.isNotEmpty(), "Should detect file write intent from 'write...to json'")
            assertTrue(
                result.tools.any { it.category == ToolCategory.FILE_WRITE },
                "Should match FILE_WRITE category for contextual pattern",
            )
        }

        @Test
        @DisplayName("Contextual file read - read data from csv")
        fun contextualReadDataFromCsv() {
            val result = DetectUserIntentCommand.execute(
                "Read the data from csv file",
                allTools,
            )

            assertTrue(result.tools.isNotEmpty(), "Should detect file read intent from 'read...from csv'")
            assertTrue(
                result.tools.any { it.category == ToolCategory.FILE_READ },
                "Should match FILE_READ category for contextual pattern",
            )
        }

        @Test
        @DisplayName("Contextual file read - load json data")
        fun contextualLoadJsonData() {
            val result = DetectUserIntentCommand.execute(
                "Load the configuration from json",
                allTools,
            )

            assertTrue(result.tools.isNotEmpty(), "Should detect file read intent from 'load...from json'")
            assertTrue(
                result.tools.any { it.category == ToolCategory.FILE_READ },
                "Should match FILE_READ category for contextual pattern",
            )
        }

        @Test
        @DisplayName("Contextual file read - show me the file")
        fun contextualShowMeTheFile() {
            val result = DetectUserIntentCommand.execute(
                "Show me the contents of the file",
                allTools,
            )

            assertTrue(result.tools.isNotEmpty(), "Should detect file read intent from 'show me...file'")
            assertTrue(
                result.tools.any { it.category == ToolCategory.FILE_READ },
                "Should match FILE_READ category for contextual pattern",
            )
        }

        @Test
        @DisplayName("Contextual file read - get data from file")
        fun contextualGetDataFromFile() {
            val result = DetectUserIntentCommand.execute(
                "Get the data from the text file",
                allTools,
            )

            assertTrue(result.tools.isNotEmpty(), "Should detect file read intent from 'get...from...file'")
            assertTrue(
                result.tools.any { it.category == ToolCategory.FILE_READ },
                "Should match FILE_READ category for contextual pattern",
            )
        }

        @Test
        @DisplayName("Command execution request")
        fun commandExecutionRequest() {
            val result = DetectUserIntentCommand.execute(
                "Run npm install to install dependencies",
                allTools,
            )

            assertTrue(result.tools.any { it.category == ToolCategory.EXECUTE })
        }

        @Test
        @DisplayName("Contextual execution - run the tests")
        fun contextualRunTheTests() {
            val result = DetectUserIntentCommand.execute(
                "Please run the tests for me",
                allTools,
            )

            assertTrue(result.tools.isNotEmpty(), "Should detect execution intent from 'run the tests'")
            assertTrue(
                result.tools.any { it.category == ToolCategory.EXECUTE },
                "Should match EXECUTE category for contextual pattern",
            )
        }

        @Test
        @DisplayName("Contextual execution - build the project")
        fun contextualBuildTheProject() {
            val result = DetectUserIntentCommand.execute(
                "Build the project before deploying",
                allTools,
            )

            assertTrue(result.tools.isNotEmpty(), "Should detect execution intent from 'build the project'")
            assertTrue(
                result.tools.any { it.category == ToolCategory.EXECUTE },
                "Should match EXECUTE category for contextual pattern",
            )
        }

        @Test
        @DisplayName("Contextual execution - install dependencies")
        fun contextualInstallDependencies() {
            val result = DetectUserIntentCommand.execute(
                "Install all the dependencies first",
                allTools,
            )

            assertTrue(result.tools.isNotEmpty(), "Should detect execution intent from 'install...dependencies'")
            assertTrue(
                result.tools.any { it.category == ToolCategory.EXECUTE },
                "Should match EXECUTE category for contextual pattern",
            )
        }

        @Test
        @DisplayName("Contextual execution - start the server")
        fun contextualStartTheServer() {
            val result = DetectUserIntentCommand.execute(
                "Start the development server on port 3000",
                allTools,
            )

            assertTrue(result.tools.isNotEmpty(), "Should detect execution intent from 'start the server'")
            assertTrue(
                result.tools.any { it.category == ToolCategory.EXECUTE },
                "Should match EXECUTE category for contextual pattern",
            )
        }

        @Test
        @DisplayName("Contextual database - query the database")
        fun contextualQueryTheDatabase() {
            val result = DetectUserIntentCommand.execute(
                "Query the database for all users",
                allTools,
            )

            assertTrue(result.tools.isNotEmpty(), "Should detect database intent from 'query the database'")
            assertTrue(
                result.tools.any { it.category == ToolCategory.DATABASE },
                "Should match DATABASE category for contextual pattern",
            )
        }

        @Test
        @DisplayName("Contextual database - fetch from table")
        fun contextualFetchFromTable() {
            val result = DetectUserIntentCommand.execute(
                "Fetch all records from the users table",
                allTools,
            )

            assertTrue(result.tools.isNotEmpty(), "Should detect database intent from 'fetch...from table'")
            assertTrue(
                result.tools.any { it.category == ToolCategory.DATABASE },
                "Should match DATABASE category for contextual pattern",
            )
        }

        @Test
        @DisplayName("Contextual database - connect to mysql")
        fun contextualConnectToMysql() {
            val result = DetectUserIntentCommand.execute(
                "Connect to mysql database and show tables",
                allTools,
            )

            assertTrue(result.tools.isNotEmpty(), "Should detect database intent from 'connect to mysql'")
            assertTrue(
                result.tools.any { it.category == ToolCategory.DATABASE },
                "Should match DATABASE category for contextual pattern",
            )
        }

        @Test
        @DisplayName("Contextual database - get data from db")
        fun contextualGetDataFromDb() {
            val result = DetectUserIntentCommand.execute(
                "Get the customer data from db",
                allTools,
            )

            assertTrue(result.tools.isNotEmpty(), "Should detect database intent from 'get...from db'")
            assertTrue(
                result.tools.any { it.category == ToolCategory.DATABASE },
                "Should match DATABASE category for contextual pattern",
            )
        }

        @Test
        @DisplayName("Contextual network - call the api")
        fun contextualCallTheApi() {
            val result = DetectUserIntentCommand.execute(
                "Call the API to get user data",
                allTools,
            )

            assertTrue(result.tools.isNotEmpty(), "Should detect network intent from 'call the api'")
            assertTrue(
                result.tools.any { it.category == ToolCategory.NETWORK },
                "Should match NETWORK category for contextual pattern",
            )
        }

        @Test
        @DisplayName("Contextual network - fetch from url")
        fun contextualFetchFromUrl() {
            val result = DetectUserIntentCommand.execute(
                "Fetch the data from the url endpoint",
                allTools,
            )

            assertTrue(result.tools.isNotEmpty(), "Should detect network intent from 'fetch...from url'")
            assertTrue(
                result.tools.any { it.category == ToolCategory.NETWORK },
                "Should match NETWORK category for contextual pattern",
            )
        }

        @Test
        @DisplayName("Contextual network - post to api")
        fun contextualPostToApi() {
            val result = DetectUserIntentCommand.execute(
                "Post the form data to api endpoint",
                allTools,
            )

            assertTrue(result.tools.isNotEmpty(), "Should detect network intent from 'post to api'")
            assertTrue(
                result.tools.any { it.category == ToolCategory.NETWORK },
                "Should match NETWORK category for contextual pattern",
            )
        }

        @Test
        @DisplayName("Contextual network - make request to server")
        fun contextualMakeRequest() {
            val result = DetectUserIntentCommand.execute(
                "Make a request to the server and get response",
                allTools,
            )

            assertTrue(result.tools.isNotEmpty(), "Should detect network intent from 'make request'")
            assertTrue(
                result.tools.any { it.category == ToolCategory.NETWORK },
                "Should match NETWORK category for contextual pattern",
            )
        }

        @Test
        @DisplayName("Contextual search - search for files")
        fun contextualSearchForFiles() {
            val result = DetectUserIntentCommand.execute(
                "Search for all configuration files in the project",
                allTools,
            )

            assertTrue(result.tools.isNotEmpty(), "Should detect search intent from 'search for...files'")
            assertTrue(
                result.tools.any { it.category == ToolCategory.SEARCH },
                "Should match SEARCH category for contextual pattern",
            )
        }

        @Test
        @DisplayName("Contextual search - find code in repository")
        fun contextualFindCodeInRepo() {
            val result = DetectUserIntentCommand.execute(
                "Find the authentication code in the repository",
                allTools,
            )

            assertTrue(result.tools.isNotEmpty(), "Should detect search intent from 'find code in repo'")
            assertTrue(
                result.tools.any { it.category == ToolCategory.SEARCH },
                "Should match SEARCH category for contextual pattern",
            )
        }

        @Test
        @DisplayName("Contextual search - lookup in github")
        fun contextualLookupInGithub() {
            val result = DetectUserIntentCommand.execute(
                "Lookup the function definition in github",
                allTools,
            )

            assertTrue(result.tools.isNotEmpty(), "Should detect search intent from 'lookup in github'")
            assertTrue(
                result.tools.any { it.category == ToolCategory.SEARCH },
                "Should match SEARCH category for contextual pattern",
            )
        }

        @Test
        @DisplayName("Contextual search - locate file")
        fun contextualLocateFile() {
            val result = DetectUserIntentCommand.execute(
                "Locate the main configuration file",
                allTools,
            )

            assertTrue(result.tools.isNotEmpty(), "Should detect search intent from 'locate...file'")
            assertTrue(
                result.tools.any { it.category == ToolCategory.SEARCH },
                "Should match SEARCH category for contextual pattern",
            )
        }

        @Test
        @DisplayName("Contextual transform - convert to json")
        fun contextualConvertToJson() {
            val result = DetectUserIntentCommand.execute(
                "Convert the data to json format",
                allTools,
            )

            assertTrue(result.tools.isNotEmpty(), "Should detect transform intent from 'convert to json'")
            assertTrue(
                result.tools.any { it.category == ToolCategory.TRANSFORM },
                "Should match TRANSFORM category for contextual pattern",
            )
        }

        @Test
        @DisplayName("Contextual transform - parse xml file")
        fun contextualParseXmlFile() {
            val result = DetectUserIntentCommand.execute(
                "Parse the xml file and extract data",
                allTools,
            )

            assertTrue(result.tools.isNotEmpty(), "Should detect transform intent from 'parse xml file'")
            assertTrue(
                result.tools.any { it.category == ToolCategory.TRANSFORM },
                "Should match TRANSFORM category for contextual pattern",
            )
        }

        @Test
        @DisplayName("Contextual transform - transform data to csv")
        fun contextualTransformDataToCsv() {
            val result = DetectUserIntentCommand.execute(
                "Transform the data to csv format",
                allTools,
            )

            assertTrue(result.tools.isNotEmpty(), "Should detect transform intent from 'transform data to csv'")
            assertTrue(
                result.tools.any { it.category == ToolCategory.TRANSFORM },
                "Should match TRANSFORM category for contextual pattern",
            )
        }

        @Test
        @DisplayName("Contextual transform - serialize to yaml")
        fun contextualSerializeToYaml() {
            val result = DetectUserIntentCommand.execute(
                "Serialize the configuration to yaml",
                allTools,
            )

            assertTrue(result.tools.isNotEmpty(), "Should detect transform intent from 'serialize to yaml'")
            assertTrue(
                result.tools.any { it.category == ToolCategory.TRANSFORM },
                "Should match TRANSFORM category for contextual pattern",
            )
        }

        @Test
        @DisplayName("Contextual version control - commit the changes")
        fun contextualCommitTheChanges() {
            val result = DetectUserIntentCommand.execute(
                "Commit the changes to the repository",
                allTools,
            )

            assertTrue(result.tools.isNotEmpty(), "Should detect version control intent from 'commit the changes'")
            assertTrue(
                result.tools.any { it.category == ToolCategory.VERSION_CONTROL },
                "Should match VERSION_CONTROL category for contextual pattern",
            )
        }

        @Test
        @DisplayName("Contextual version control - push to remote")
        fun contextualPushToRemote() {
            val result = DetectUserIntentCommand.execute(
                "Push the code to remote branch",
                allTools,
            )

            assertTrue(result.tools.isNotEmpty(), "Should detect version control intent from 'push to remote'")
            assertTrue(
                result.tools.any { it.category == ToolCategory.VERSION_CONTROL },
                "Should match VERSION_CONTROL category for contextual pattern",
            )
        }

        @Test
        @DisplayName("Contextual version control - create new branch")
        fun contextualCreateNewBranch() {
            val result = DetectUserIntentCommand.execute(
                "Create a new branch for the feature",
                allTools,
            )

            assertTrue(result.tools.isNotEmpty(), "Should detect version control intent from 'create new branch'")
            assertTrue(
                result.tools.any { it.category == ToolCategory.VERSION_CONTROL },
                "Should match VERSION_CONTROL category for contextual pattern",
            )
        }

        @Test
        @DisplayName("Contextual version control - merge into main")
        fun contextualMergeIntoMain() {
            val result = DetectUserIntentCommand.execute(
                "Merge the feature branch into main",
                allTools,
            )

            assertTrue(result.tools.isNotEmpty(), "Should detect version control intent from 'merge into main'")
            assertTrue(
                result.tools.any { it.category == ToolCategory.VERSION_CONTROL },
                "Should match VERSION_CONTROL category for contextual pattern",
            )
        }

        @Test
        @DisplayName("Contextual communication - send email to user")
        fun contextualSendEmailToUser() {
            val result = DetectUserIntentCommand.execute(
                "Send an email to the user about the update",
                allTools,
            )

            assertTrue(result.tools.isNotEmpty(), "Should detect communication intent from 'send email to user'")
            assertTrue(
                result.tools.any { it.category == ToolCategory.COMMUNICATION },
                "Should match COMMUNICATION category for contextual pattern",
            )
        }

        @Test
        @DisplayName("Contextual communication - notify the team")
        fun contextualNotifyTheTeam() {
            val result = DetectUserIntentCommand.execute(
                "Notify the team about the deployment",
                allTools,
            )

            assertTrue(result.tools.isNotEmpty(), "Should detect communication intent from 'notify the team'")
            assertTrue(
                result.tools.any { it.category == ToolCategory.COMMUNICATION },
                "Should match COMMUNICATION category for contextual pattern",
            )
        }

        @Test
        @DisplayName("Contextual communication - post to slack")
        fun contextualPostToSlack() {
            val result = DetectUserIntentCommand.execute(
                "Post a message to the slack channel",
                allTools,
            )

            assertTrue(result.tools.isNotEmpty(), "Should detect communication intent from 'post to slack'")
            assertTrue(
                result.tools.any { it.category == ToolCategory.COMMUNICATION },
                "Should match COMMUNICATION category for contextual pattern",
            )
        }

        @Test
        @DisplayName("Contextual communication - alert users")
        fun contextualAlertUsers() {
            val result = DetectUserIntentCommand.execute(
                "Alert the users about the system maintenance",
                allTools,
            )

            assertTrue(result.tools.isNotEmpty(), "Should detect communication intent from 'alert the users'")
            assertTrue(
                result.tools.any { it.category == ToolCategory.COMMUNICATION },
                "Should match COMMUNICATION category for contextual pattern",
            )
        }

        @Test
        @DisplayName("Contextual monitoring - monitor the system")
        fun contextualMonitorTheSystem() {
            val result = DetectUserIntentCommand.execute(
                "Monitor the system performance continuously",
                allTools,
            )

            assertTrue(result.tools.isNotEmpty(), "Should detect monitoring intent from 'monitor the system'")
            assertTrue(
                result.tools.any { it.category == ToolCategory.MONITORING },
                "Should match MONITORING category for contextual pattern",
            )
        }

        @Test
        @DisplayName("Contextual monitoring - track performance metrics")
        fun contextualTrackPerformanceMetrics() {
            val result = DetectUserIntentCommand.execute(
                "Track the performance metrics for the API",
                allTools,
            )

            assertTrue(result.tools.isNotEmpty(), "Should detect monitoring intent from 'track performance metrics'")
            assertTrue(
                result.tools.any { it.category == ToolCategory.MONITORING },
                "Should match MONITORING category for contextual pattern",
            )
        }

        @Test
        @DisplayName("Contextual monitoring - check health status")
        fun contextualCheckHealthStatus() {
            val result = DetectUserIntentCommand.execute(
                "Check the health status of all services",
                allTools,
            )

            assertTrue(result.tools.isNotEmpty(), "Should detect monitoring intent from 'check health status'")
            assertTrue(
                result.tools.any { it.category == ToolCategory.MONITORING },
                "Should match MONITORING category for contextual pattern",
            )
        }

        @Test
        @DisplayName("Contextual monitoring - log error events")
        fun contextualLogErrorEvents() {
            val result = DetectUserIntentCommand.execute(
                "Log all error events from the application",
                allTools,
            )

            assertTrue(result.tools.isNotEmpty(), "Should detect monitoring intent from 'log error events'")
            assertTrue(
                result.tools.any { it.category == ToolCategory.MONITORING },
                "Should match MONITORING category for contextual pattern",
            )
        }

        @Test
        @DisplayName("Complex multi-step request")
        fun complexMultiStepRequest() {
            val result = DetectUserIntentCommand.execute(
                "Build the project, run tests, and generate a report chart",
                allTools,
            )

            assertTrue(result.tools.size >= 2)
            assertTrue(result.tools.any { it.category == ToolCategory.EXECUTE })
            assertTrue(result.tools.any { it.category == ToolCategory.VISUALIZE })
        }

        @Test
        @DisplayName("Simple informational question")
        fun simpleInformationalQuestion() {
            val result = DetectUserIntentCommand.execute(
                "What is the capital of France?",
                allTools,
            )

            assertEquals(0, result.tools.size)
            assertEquals(0, result.confidence)
        }
    }
}
