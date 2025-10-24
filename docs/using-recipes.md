---
title: Using Recipes in Askimo
nav_order: 8
description: Learn how to create, list, delete, and run recipes in Askimo CLI using YAML templates.
---

# Using Recipes in Askimo

Recipes are a key feature of Askimo, designed to automate repetitive tasks and enable advanced customization. With recipes, you can define reusable workflows, create custom prompts, and parameterize both system and user messages to fit your needs.

## Why Use Recipes?
- **Automate Repetitive Tasks:** Recipes let you save time by automating common actions, such as summarizing files, generating commit messages, or extracting information from documents.
- **Custom Prompts and Parameters:** You can create recipes with parameters, allowing users to customize the behavior, input, and output of each recipe. This makes recipes highly flexible for different scenarios.
- **Consistent Workflows:** Recipes ensure that tasks are performed consistently, following your preferred format and rules.

## Example: Custom Summarization Recipe
Suppose you frequently need to summarize different files. You can create a recipe that takes a file path as a parameter and customizes the system and user messages:

```yaml
name: summarize
version: 1
description: "Summarize the content of a file concisely"
allowedTools:
  - readFile
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

When you run this recipe, you can specify the file to summarize:
```
summarize README.md
```
You can further customize the `system` message to change the writing style, or the `userTemplate` to adjust the prompt format.

## Parameterization and Customization
Recipes support parameters using the `{{argN}}` syntax, which allows users to pass arguments when running the recipe. These parameters can be used in any section, including `vars`, `system`, and `userTemplate`.

**Example: Custom Commit Message Recipe**
```yaml
name: gitCommit
version: 5
description: "Generate a Conventional Commit message from staged changes"
allowedTools:
  - stagedDiff
  - status
  - branch
vars:
  diff:
    tool: stagedDiff
    args: ["--no-color", "--unified=0"]
system: |
  You are a senior engineer writing Conventional Commit messages.
  Please use the following style: {{arg1}}
userTemplate: |
  Current branch: {{branch}}
  Repo status:
  {{status}}
postActions: []
```
You can run this recipe and specify a style parameter:
```
gitCommit "feat(scope): summary"
```

## Customizing System and User Messages
- **System Message:** Sets the context, rules, or persona for the AI. You can make this dynamic by including parameters (e.g., `{{arg1}}`).
- **User Template:** Defines the main prompt, which can also use parameters and variables for maximum flexibility.

## Practical Scenarios
- **Project Onboarding:** Create a recipe to generate onboarding documentation for new team members.
- **Code Review:** Automate code review summaries by passing file paths and review criteria as parameters.
- **Data Extraction:** Build recipes to extract and format data from logs or reports, customizing the output format.

## Recipe Commands

### 1. Create a Recipe
Use the `:create-recipe` command to register a new recipe from a YAML template.

**Syntax:**
```
:create-recipe <path-to-yaml>
```
**Example:**
```
:create-recipe templates/gitcommit.yml
```
This registers the recipe for future use.

### 2. List Recipes
Use the `:recipes` command to list all registered recipes.

**Syntax:**
```
:recipes
```
**Example Output:**
```
> :recipes
summarize
gitCommit
```

### 3. Delete a Recipe
Use the `:delete-recipe` command to remove a recipe.

**Syntax:**
```
:delete-recipe <recipe-name>
```
**Example:**
```
:delete-recipe summarize
```

### 4. Run a Recipe
You can run a recipe using the CLI. Recipes are executed via the `RecipeExecutor` and can be invoked from `ChatCli`.

**Syntax:**
```
<recipe-name> <arguments>
```
**Example:**
```
summarize README.md
```
This runs the `summarize` recipe on the specified file.

## Recipe Template Example
Here’s a simplified example from `gitcommit.yml`:

```yaml
name: gitCommit
description: "Generate a Conventional Commit message from staged changes"
allowedTools:
  - stagedDiff
  - status
  - branch
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

A recipe YAML file consists of several key sections:

### `vars`
Defines variables that are computed or fetched before running the recipe. Each variable specifies a tool and arguments. These variables can be referenced in other sections.

**Example:**
```yaml
vars:
  file_content:
    tool: readFile
    args: ["{{arg1}}"]
```
This example (from `summarize.yml`) sets `file_content` to the result of reading a file whose path is provided as an argument.

### `system`
Provides system instructions or context for the AI. This is typically a prompt that sets the role, rules, or output format.

**Example:**
```yaml
system: |
  You are a senior engineer writing Conventional Commit messages.
  Output MUST be plain text only suitable for `git commit -F -`.
```
This example (from `gitcommit.yml`) instructs the AI to act as a commit message writer and specifies output requirements.

### `userTemplate`
Defines the user-facing prompt, often referencing variables. This is what the AI sees as the main task or question.

**Example:**
```yaml
userTemplate: |
  File path: {{arg1}}
  Content:
  ====BEGIN====
  {{file_content}}
  ====END====
```
This example (from `summarize.yml`) presents the file content to the AI for summarization.

### `postActions`
Specifies actions to perform after the main recipe execution, such as saving output or triggering another tool. This section is optional and can be an empty list if not needed.

**Example:**
```yaml
postActions: []
```
This example shows no post-actions are defined. You could add actions like saving the result to a file or running a follow-up command.

---

Refer to the `templates/gitcommit.yml` and `templates/summarize.yml` files for more advanced examples and inspiration.

## Best Practices
- Store your recipes in `~/.askimo/recipes` for easy access.
- Use descriptive names and document the purpose of each recipe in the YAML.
- Refer to `gitcommit.yml` and `summarize.yml` in the `templates/` folder for inspiration.
