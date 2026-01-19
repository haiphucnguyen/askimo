/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.tools.chart

import dev.langchain4j.agent.tool.Tool
import io.askimo.core.util.JsonUtils.json
import io.askimo.tools.ToolResponseBuilder
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject

/**
 * Tools for generating diagrams and visualizations using Mermaid.
 * Supports ALL Mermaid diagram types: flowcharts, sequence diagrams, class diagrams,
 * state machines, ER diagrams, Gantt charts, pie charts, bar/line charts (xychart-beta),
 * user journeys, git graphs, mind maps, requirement diagrams, treemaps, sankey diagrams,
 * block diagrams, packet diagrams, kanban boards, architecture diagrams, quadrant charts,
 * and C4 diagrams.
 *
 * NOTE: Radar/spider charts are not supported by Mermaid CLI.
 */
object ChartTools {

    /**
     * Generate a Mermaid diagram for visualizations.
     *
     * @param title Diagram title
     * @param diagram Mermaid diagram definition
     * @param theme Theme name (default, dark, forest, neutral)
     * @return JSON response with diagram data
     */
    @Tool(
        """You are a Mermaid diagram generator.

Generate Mermaid diagrams to visualize data, systems, workflows, and relationships. Supports ALL diagram types: flowcharts, sequence diagrams, class/UML diagrams, state machines, ER diagrams, Gantt charts, pie charts, bar/line charts, user journeys, and git graphs.

ONLY use this tool when the user explicitly asks for diagrams, charts, graphs, architecture diagrams, timelines, user journeys, git workflows, or any visual representation.

OUTPUT FORMAT (MANDATORY):
Always return the result as a Markdown JSON code block with EXACTLY this structure:

{
  "title": "<human-readable title>",
  "diagram": "<mermaid diagram as a single string>",
  "theme": "default"
}

STRICT OUTPUT RULES:
- Output MUST be valid JSON
- Output MUST be wrapped in a json code block
- Do NOT include any text outside the JSON block
- Use \\n for line breaks inside the diagram string

DIAGRAM TYPE SELECTION:
- Charts (bar, line) → xychart-beta
- Pie charts → pie
- Priority matrices / quadrant analysis → quadrantChart
- Treemaps / hierarchical data → treemap
- Block diagrams / system architecture → block-beta
- Architecture diagrams → architecture-beta
- Flowcharts / process flows → flowchart or graph
- Flow diagrams / data flow → sankey-beta
- Timelines / project schedules → gantt
- Kanban boards / task management → kanban
- User journeys / customer experience → journey
- Git workflows / version control → gitGraph
- Requirements / specifications → requirementDiagram
- Architecture / UML → classDiagram, sequenceDiagram, stateDiagram, erDiagram
- Network packets / protocol diagrams → packet-beta
- Proportions / distributions → pie
- Mind maps → mindmap
- C4 diagrams → C4Context

NOTE: Mermaid CLI does not support radar/spider charts. For multi-axis comparison, consider using bar charts (xychart-beta) or creating a custom table visualization.


CRITICAL MERMAID SYNTAX RULES (DO NOT VIOLATE):
- Do NOT add colons after diagram keywords (graph, flowchart, sequenceDiagram, etc.)
- For xychart-beta: use x-axis and y-axis (with hyphens), NOT xaxis/yaxis
- For xychart-beta, titles MUST be quoted: title "My Chart"
- For xychart-beta, axis labels MUST be quoted: x-axis "Label"
- For xychart-beta, use line or bar to define data, NOT data keyword
- For sankey-beta: data format is Source,Target,Value (one per line)
- For block-beta: define blocks and connections with arrows
- For architecture-beta: format is service name(icon)[Label], connections use position indicators (e.g., serviceA:R --> L:serviceB for right-to-left)
- For quadrantChart: define quadrants and plot points with x,y coordinates
- For packet-beta: define packet structure with bit ranges (e.g., 0-15: "Field Name")
- For kanban: define columns and cards
- For treemap: use indentation to show hierarchy, format is "Label: value" (use actual data values)
- For journey/pie/gantt, titles must NOT be quoted: title My Chart
- For gitGraph, use simple commands (e.g., commit, branch name, checkout name, merge name, tag)
- For requirementDiagram, use block syntax: requirement name { } and element name { }
- For requirementDiagram, do NOT use title keyword (not supported)
- For pie charts, use format: "Label" : value
- For journey diagrams, use format: section Title\n  Task: score: Actor
- ER diagram attributes MUST include a type
- ER diagram relationships MUST use valid cardinality (e.g., ||--o{, }o--||, ||--|{)
- classDiagram stereotypes (e.g., <<interface>>, <<abstract>>, <<component>>) MUST be inside the class body

XYCHART-BETA CRITICAL RULES:
1. For categorical x-axis (years, months, names, categories), ALWAYS use array syntax:
   x-axis [2010, 2011, 2012, 2013, 2014, 2015]
   x-axis ["Jan", "Feb", "Mar", "Apr", "May"]
   x-axis ["Product A", "Product B", "Product C"]

2. For continuous numeric x-axis with label only (rare):
   x-axis "Time (ms)"

3. The number of x-axis categories MUST match the number of data points in line/bar
4. Y-axis always uses range syntax: y-axis "Label" min --> max

XYCHART-BETA EXAMPLE WITH CATEGORICAL X-AXIS (MOST COMMON):
xychart-beta
    title "Stock Price 2010-2015"
    x-axis [2010, 2011, 2012, 2013, 2014, 2015]
    y-axis "Price (USD)" 0 --> 1000
    line [320, 520, 650, 800, 470, 850]

XYCHART-BETA EXAMPLE WITH MONTH LABELS:
xychart-beta
    title "Monthly Sales"
    x-axis ["Jan", "Feb", "Mar", "Apr", "May", "Jun"]
    y-axis "Revenue" 0 --> 1000
    line [100, 200, 150, 300, 250, 280]
    bar [80, 180, 120, 280, 230, 260]

WRONG (DO NOT DO THIS):
xychart-beta
    title "Stock Price 2010-2015"
    x-axis "Year"  ❌ Missing category array! This will render incorrectly.
    y-axis "Price (USD)" 0 --> 1000
    line [320, 520, 650, 800, 470, 850]


BLOCK-BETA EXAMPLE (VALID):
block-beta
    columns 3
    A["Clock Input"]:1
    B["Oscillator"]:1
    C["Divider"]:1
    D["Counter"]:1
    E["Display Driver"]:1
    F["7-Segment Display"]:1
    A --> B
    B --> C
    C --> D
    D --> E
    E --> F

PACKET-BETA EXAMPLE (VALID):
packet-beta
    0-15: "Source Port"
    16-31: "Destination Port"
    32-63: "Sequence Number"
    64-95: "Acknowledgment Number"

KANBAN EXAMPLE (VALID):
kanban
    Todo
        Task 1
        Task 2
    In Progress
        Task 3
    Done
        Task 4

ARCHITECTURE-BETA EXAMPLE (VALID):
architecture-beta
    service api(cloud)[API Gateway]
    service web(server)[Web Server]
    service db(database)[Database]
    api:R --> L:web
    web:R --> L:db

NOTE:
- Icon types are optional. Common icons include: cloud, server, database, disk, internet. Default: server
- Connections require position indicators: L (left), R (right), T (top), B (bottom)
- Format: serviceA:R --> L:serviceB means "from right side of A to left side of B"

QUADRANTCHART EXAMPLE (VALID):
quadrantChart
    title "Product Analysis"
    x-axis "Low Priority" --> "High Priority"
    y-axis "Low Effort" --> "High Effort"
    quadrant-1 "Quick Wins"
    quadrant-2 "Major Projects"
    quadrant-3 "Fill Ins"
    quadrant-4 "Time Wasters"
    Feature A: [0.3, 0.6]
    Feature B: [0.7, 0.8]
    Feature C: [0.2, 0.2]

TREEMAP EXAMPLE (VALID):
treemap
    "Company"
        "Engineering: 4000"
            "Frontend: 2000"
            "Backend: 1500"
            "DevOps: 500"
        "Sales: 3000"
            "North: 1500"
            "South: 1500"
        "Marketing: 2000"

SANKEY-BETA EXAMPLE (VALID):
sankey-beta
    Sales,Online,500
    Sales,Retail,300
    Online,Processing,200
    Retail,Processing,200
    Processing,Delivery,400

GITGRAPH EXAMPLE (VALID):
gitGraph
    commit
    commit
    branch develop
    commit
    checkout main
    commit
    merge develop
    commit tag: "v1.0"

REQUIREMENTDIAGRAM EXAMPLE (VALID):
requirementDiagram
    requirement UserAuth {
    }
    requirement DataSecurity {
    }
    element LoginSystem {
    }
    element SecurityModule {
    }
    LoginSystem - satisfies -> UserAuth
    SecurityModule - satisfies -> DataSecurity

JOURNEY DIAGRAM EXAMPLE (VALID):
journey
    title Customer Journey
    section Awareness
      Research: 5: Customer
      Compare: 3: Customer
    section Purchase
      Buy Product: 5: Customer

CLASSDIAGRAM STEREOTYPE EXAMPLE (VALID):
      Compare: 3: Customer
    section Purchase
      Buy Product: 5: Customer

CLASSDIAGRAM STEREOTYPE EXAMPLE (VALID):
classDiagram
    class MyClass {
        <<component>>
        +method()
    }

INVALID:
classDiagram
    class MyClass <<component>> {
        +method()
    }

QUALITY REQUIREMENTS:
- Prefer clarity over complexity
- Use meaningful labels
- Ensure the diagram renders correctly in Mermaid CLI (mmdc), GitHub Markdown, and Mermaid Live
        """,
    )
    fun generateMermaidDiagram(
        title: String,
        diagram: String,
        theme: String = "default",
    ): String = try {
        require(diagram.isNotBlank()) { "Diagram definition cannot be empty" }

        val chartData = MermaidChartData(
            title = title,
            diagram = diagram,
            theme = theme,
        )

        val chartJson = json.encodeToJsonElement(chartData).jsonObject

        val diagramType = when {
            diagram.trim().startsWith("sequenceDiagram") -> "sequence"
            diagram.trim().startsWith("classDiagram") -> "class"
            diagram.trim().startsWith("stateDiagram") -> "state"
            diagram.trim().startsWith("erDiagram") -> "er"
            diagram.trim().startsWith("gantt") -> "gantt"
            diagram.trim().startsWith("pie") -> "pie"
            diagram.trim().startsWith("journey") -> "journey"
            diagram.trim().startsWith("treemap") -> "treemap"
            diagram.trim().startsWith("block") -> "block"
            diagram.trim().startsWith("architecture") -> "architecture"
            diagram.trim().startsWith("quadrantChart") -> "quadrant"
            diagram.trim().startsWith("packet") -> "packet"
            diagram.trim().startsWith("kanban") -> "kanban"
            diagram.trim().startsWith("sankey") -> "sankey"
            diagram.trim().startsWith("xychart") -> "chart"
            diagram.trim().startsWith("graph") || diagram.trim().startsWith("flowchart") -> "flowchart"
            else -> "diagram"
        }

        ToolResponseBuilder.successWithData(
            output = "Generated Mermaid $diagramType: '$title' (theme: $theme)",
            data = mapOf(
                "chartData" to chartJson.toString(),
                "type" to "mermaid",
                "diagramType" to diagramType,
                "theme" to theme,
            ),
        )
    } catch (e: Exception) {
        ToolResponseBuilder.failure(
            error = "Failed to generate Mermaid diagram: ${e.message}",
            metadata = mapOf(
                "title" to title,
                "exception" to (e::class.simpleName ?: "Exception"),
            ),
        )
    }
}
