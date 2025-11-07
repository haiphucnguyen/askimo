---
title: Using Recipes in Askimo
nav_order: 6
description: Learn how to create, list, delete, and run recipes in Askimo CLI using YAML templates.
---

# Using Recipes in Askimo

Recipes are a key feature of Askimo, designed to automate repetitive tasks and enable advanced customization. With recipes, you can define reusable workflows, create custom prompts, and parameterize both system and user messages to fit your needs.

> **Important:** Recipe management and execution commands (create, list, delete, run) are **only available in non-interactive mode** using CLI flags (`--create-recipe`, `--recipes`, `--delete-recipe`, `-r`).

## Why Use Recipes?
- **Automate Repetitive Tasks:** Save time by automating common actions, such as summarizing files, generating commit messages, or extracting information from documents.
- **Custom Prompts and Parameters:** Recipes can have parameters, allowing users to customize behavior, input, and output.
- **Consistent Workflows:** Ensure tasks are performed consistently, following your preferred format and rules.

## Quick Reference: Recipe Operations

| Operation           | Non-Interactive Mode         |
|---------------------|------------------------------|
| Create recipe       | ✅ `askimo --create-recipe`   |
| List recipes        | ✅ `askimo --recipes`         |
| Delete recipe       | ✅ `askimo --delete-recipe`   |
| Run recipe          | ✅ `askimo -r recipe args`    |

## Recipe Document Format

A recipe in Askimo is defined in a YAML file with the following sections:

- `name`: Unique identifier for the recipe.
- `version`: (Optional) Recipe version number.
- `description`: Short summary of what the recipe does.
- `allowedTools`: (Optional) List of tool names the recipe is allowed to use. If omitted, all tools are allowed.
- `vars`: (Optional) Variables computed using tools, often referencing external arguments.
- `system`: Instructions or context for the AI (system prompt).
- `userTemplate`: The main user prompt, can reference variables and external arguments.
- `postActions`: (Optional) Actions to perform after the main recipe execution.

### Section Explanations

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

## External Arguments (`arg1`, `arg2`, ...)

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

## Tool Access Control (`allowedTools`)
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

## Complete Example Recipe

```yaml
name: summarize
version: 1
description: "Summarize the content of a file concisely"
allowedTools: [] # All tools allowed (can omit this line)
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
postActions: []
```

**Usage:**
```bash
askimo -r summarize README.md
```
- `arg1` is `README.md`, used in `vars` and `userTemplate`.

## Parameterization and Customization
Recipes support parameters using the `{{argN}}` syntax, which allows users to pass arguments when running the recipe. These parameters can be used in any section, including `vars`, `system`, and `userTemplate`.

**Example: Custom File Search Recipe (Restricted Tools)**
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
postActions: []
```

**Usage:**
```bash
askimo -r searchFiles /projects "*.md"
```
- `arg1` is `/projects`
- `arg2` is `*.md`

## Customizing System and User Messages
- **System Message:** Sets the context, rules, or persona for the AI. You can make this dynamic by including parameters (e.g., `{{arg1}}`).
- **User Template:** Defines the main prompt, which can also use parameters and variables for maximum flexibility.

## Recipe Management Commands (Non-Interactive Only)
Recipe management commands (create, list, delete) are **only available in non-interactive mode**:
```bash
# Create a recipe
$ askimo --create-recipe myrecipe -f templates/myrecipe.yml
# List all recipes
$ askimo --recipes
# Delete a recipe
$ askimo --delete-recipe myrecipe
```

## Recipe Execution (Non-Interactive Only)
You can run a recipe using non-interactive mode, passing any number of arguments:
```bash
askimo -r <recipe_name> <arg1> <arg2> ... <argN>
```

**Examples:**
If your recipe expects one argument:
```bash
askimo -r summarize README.md
```
- `arg1` is `README.md`
If your recipe expects two arguments:
```bash
askimo -r searchFiles /projects "*.md"
```
- `arg1` is `/projects`
- `arg2` is `*.md`

## Recipe Template Example
Here’s a simplified example from `gitcommit.yml` (with explicit restriction):
```yaml
name: gitCommit
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
```

## Recipe YAML Template Structure
A recipe YAML file consists of several key sections. Here is a concrete example using `summarize.yml`:

```yaml
name: summarize                # Unique identifier for the recipe
version: 1                     # (Optional) Recipe version number
description: "Summarize the content of a file concisely"  # Short summary
allowedTools:
  - readFile                   # Restrict to only readFile tool
vars:
  file_content:                # Variable computed before running the recipe
    tool: readFile             # Uses the readFile tool
    args: ["{{arg1}}"]         # Uses the first external argument
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
postActions: []                # (Optional) Actions after execution
```

**Section breakdown:**
- `name`, `version`, `description`: Metadata for the recipe
- `allowedTools`: Restricts which tools can be used
- `vars`: Defines variables using tools and arguments
- `system`: Sets the AI's context and instructions
- `userTemplate`: Main prompt, can reference variables and arguments
- `postActions`: Actions after main execution (empty in this example)

## Default Bundled Recipes

Askimo comes bundled with several default recipes, including `gitcommit` and `summarize`. These are available out-of-the-box in every Askimo distribution.

- You can view the default recipe templates at:
  [src/main/resources/templates](https://github.com/haiphucnguyen/askimo/tree/main/src/main/resources/templates)

- For more advanced and custom examples, see:
  [samples/recipes](https://github.com/haiphucnguyen/askimo/tree/main/samples/recipes)