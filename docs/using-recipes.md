---
title: Using Recipes in Askimo
nav_order: 6
description: Learn how to create, list, delete, and run recipes in Askimo CLI using YAML templates.
---

# Using Recipes in Askimo

Recipes are a key feature of Askimo, designed to automate repetitive tasks and enable advanced customization. With recipes, you can define reusable workflows, create custom prompts, and parameterize both system and user messages to fit your needs.

> **Important:** Recipe management and execution commands (create, list, delete, run) are **only available in non-interactive mode** using CLI flags (`--create-recipe`, `--recipes`, `--delete-recipe`, `-r`).

---

## 1. Recipe Lifecycle: Quick Reference

| Operation           | Non-Interactive Mode         |
|---------------------|------------------------------|
| Create recipe       | ✅ `askimo --create-recipe`   |
| List recipes        | ✅ `askimo --recipes`         |
| Delete recipe       | ✅ `askimo --delete-recipe`   |
| Run recipe          | ✅ `askimo -r recipe args`    |

---

## 2. Anatomy of a Recipe

A recipe in Askimo is defined in a YAML file with the following sections:

- `name`: Unique identifier for the recipe.
- `version`: (Optional) Recipe version number.
- `description`: Short summary of what the recipe does.
- `allowedTools`: (Optional) List of tool names the recipe is allowed to use. If omitted, all tools are allowed.
- `vars`: (Optional) Variables computed using tools, often referencing external arguments.
- `system`: Instructions or context for the AI (system prompt).
- `userTemplate`: The main user prompt, can reference variables and external arguments.
- `postActions`: (Optional) Actions to perform after the main recipe execution.

### Section-by-Section Explanation

#### `name`
A unique string to identify your recipe. Used to run the recipe from CLI or REPL.

#### `version`
(Optional) Integer or string to track changes to your recipe.

#### `description`
A short summary of the recipe's purpose. Shown in listings and help.

#### `allowedTools`
(Optional) List of tool names the recipe can use. If omitted or empty, all tools are allowed. See the next section for details.

#### `vars`
(Optional) Define variables that are computed before running the recipe. Each variable uses a tool and arguments. Arguments can reference external parameters (see below).

#### `system`
System prompt for the AI, setting context, rules, or persona.

#### `userTemplate`
Main user prompt, can reference variables and external arguments.

#### `postActions`
(Optional) List of actions to perform after the main recipe execution (e.g., save output).

---

## 3. Arguments and Parameterization

When you run a recipe, you can pass external arguments from the CLI. These are referenced in the recipe as `{{arg1}}`, `{{arg2}}`, etc. The mapping is positional:

- `arg1` = first argument after the recipe name
- `arg2` = second argument, and so on

**Example:**
```bash
askimo -r summarize README.md
```
- `arg1` will be `README.md`

In your recipe YAML, you can use `{{arg1}}` in `vars`, `system`, or `userTemplate`:
```yaml
vars:
  file_content:
    tool: readFile
    args: ["{{arg1}}"]
```

---

## 4. Using Piped Input (stdin)

Recipes can accept input from stdin (piped commands), making them powerful for analyzing logs, processing command output, or working with data streams. When stdin is available, it's automatically captured and made available as the `{{stdin}}` variable.

### Basic Usage

**Analyze log files:**
```bash
cat application.log | askimo -r analyze_log
```

**Process kubectl logs:**
```bash
kubectl logs pod-name | askimo -r analyze_log
```

**Analyze docker logs:**
```bash
docker logs container-name | askimo -r analyze_log
```

**Work with system logs:**
```bash
journalctl -u myservice -n 200 | askimo -r analyze_log
```

### Example: Log Analysis Recipe

```yaml
name: analyze_log
version: 1
description: "Analyze log output from stdin and provide insights"

allowedTools:
  - print

system: |
  You are a log analysis expert.
  Analyze the following log output and provide insights about:
  - Errors and warnings (categorize by severity)
  - Patterns or anomalies
  - Performance issues or bottlenecks
  - Root cause analysis for failures
  - Actionable recommendations

userTemplate: |
  Analyze the following log output:
  
  ====BEGIN LOG====
  {{stdin}}
  ====END LOG====
  
  Provide a comprehensive analysis with specific line numbers.

postActions:
  - call:
      tool: print
      args: ["{{output}}"]

defaults: {}
```

### Combining stdin with Arguments

You can use both stdin and positional arguments together:

```bash
echo "error data" | askimo -r process --set format=json extra-arg
```

In the recipe:
- `{{stdin}}` contains the piped input
- `{{arg1}}` contains "extra-arg"
- `{{format}}` contains "json" (from --set override)

### How stdin Detection Works

The system automatically detects when data is piped to Askimo:
- If stdin is available (pipe or redirect), it's read and stored in `{{stdin}}`
- If no stdin is available (interactive terminal), `{{stdin}}` is empty/undefined
- The recipe can check if stdin exists or provide a fallback value: `{{stdin|No input provided}}`

---

## 5. Tool Access Control (`allowedTools`)
By default, a recipe has access to ALL built‑in tools. You only need to specify `allowedTools:` when you want to restrict what the recipe may call.
You can view all available tools at any time by running:
- Non-interactive mode: `askimo --tools`

Ways to allow all tools:
- Omit the `allowedTools` field entirely.
- Or set it to an empty list: `allowedTools: []`

Restricting tools example:
```yaml
allowedTools:
  - readFile
  - writeFile
```
This limits the recipe to just those tools; any attempt to use others will fail with an error.

> Note: Tool names are the Kotlin method names (e.g. `writeFile`). If new tools are added you'll see them in `askimo --tools` or error messages listing available tools.

---

## 5. Handling Recipe Output (AI Response)

When a recipe runs, the AI's response is stored in a special variable called `output`. This variable is available for use in the `postActions` section. You can use `output` to decide what happens to the result—print it to the user, save it to a file, use it in a tool, or even perform multiple actions.

**Common output handling patterns:**

### 1. Display output to the user (stdout)
```yaml
postActions:
  - call:
      tool: print
      args: ["{{output}}"]
```

### 2. Save output to a file
```yaml
postActions:
  - call:
      tool: writeFile
      args: ["/path/to/file.txt", "{{output}}"]
```

### 3. Use output in a tool (e.g., git commit)
```yaml
postActions:
  - call:
      tool: commit
      args:
        message: "{{output}}"
```

### 4. Both save and display
```yaml
postActions:
  - call:
      tool: writeFile
      args: ["output.txt", "{{output}}"]
  - call:
      tool: print
      args: ["✅ Saved to output.txt\n\n{{output}}"]
```

### 5. Conditional output
```yaml
postActions:
  - when_: "{{verbose|false}} == true"
    call:
      tool: print
      args: ["{{output}}"]
```

> **Note:** Always add the tool you want to use (e.g., `print`, `writeFile`, `commit`) to `allowedTools`.

### Output Tools Reference

| Tool      | Purpose                  |
|-----------|--------------------------|
| `print`   | Display text to stdout   |
| `writeFile` | Write text to a file     |
| `commit`  | Git commit (no stdout)   |

---

## 7. Example Recipes

### Summarize Recipe (prints output)
```yaml
name: summarize
version: 1
description: "Summarize the content of a file concisely"
allowedTools:
  - readFile
  - print
vars:
  file_content:
    tool: readFile
    args: ["{{arg1}}"]
system: |
  You are an expert technical writer.
  Summarize the following file content in a concise and precise way.
  Output MUST be plain text only.
userTemplate: |
  File path: {{arg1}}
  Content:
  ====BEGIN====
  {{file_content}}
  ====END====
postActions:
  - call:
      tool: print
      args: ["{{output}}"]
defaults: {}
```
**Usage:**
```bash
askimo -r summarize README.md
```
- The summary will be printed to the terminal.

### Git Commit Recipe (uses output in a tool)
```yaml
name: gitCommit
version: 1
description: "Generate a Conventional Commit message from staged changes"
allowedTools:
  - stagedDiff
  - status
  - branch
  - commit
vars:
  diff:
    tool: stagedDiff
    args: ["--no-color", "--unified=0"]
system: |
  You are a senior engineer writing Conventional Commit messages.
userTemplate: |
  Generate the commit message in the exact plaintext format described above.
postActions:
  - call:
      tool: commit
      args:
        message: "{{output}}"
```
- The commit message is written to git, not printed to the terminal.

### File Search Recipe (restricted tools)
```yaml
name: searchFiles
version: 2
description: "Search for files by pattern in a directory"
allowedTools:
  - searchFilesByGlob
vars:
  results:
    tool: searchFilesByGlob
    args: ["{{arg1}}", "{{arg2}}"]
system: |
  You are a helpful assistant. List all files in the directory {{arg1}} matching the pattern {{arg2}}.
userTemplate: |
  Directory: {{arg1}}
  Pattern: {{arg2}}
  Results:
  {{results}}
postActions:
  - call:
      tool: print
      args: ["{{output}}"]
```

---

## 7. Best Practices & Tips

- The `output` variable always contains the AI's response.
- Use `postActions` to control what happens to `output`.
- For user-facing results, use the `print` tool.
- For file output, use the `writeFile` tool.
- For side effects (like git), use the appropriate tool (e.g., `commit`).
- You can chain multiple actions or use conditions for flexible workflows.
- Always add the tools you use in `postActions` to `allowedTools`.
- Test your recipe before committing.

---

## 8. Default Bundled Recipes & Further Resources

Askimo comes bundled with several default recipes, including `gitcommit` and `summarize`. These are available out-of-the-box in every Askimo distribution.

- You can view the default recipe templates at:
  [src/main/resources/templates](https://github.com/haiphucnguyen/askimo/tree/main/src/main/resources/templates)

- For more advanced and custom examples, see:
  [samples/recipes](https://github.com/haiphucnguyen/askimo/tree/main/samples/recipes)

---
