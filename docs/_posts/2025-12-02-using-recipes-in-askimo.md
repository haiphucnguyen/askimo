---
title: "Askimo Recipes: A Simple Guide to Repeatable AI Commands"
description: "A friendly, non-technical guide to using Askimo recipes—save a prompt once, reuse it anytime, and share it with your team. Includes practical examples for work, coding, and everyday tasks."
date: 2025-12-02
author: "Hai Nguyen"
tags: ["Askimo", "Recipes", "Productivity", "CLI", "User Guide"]
keywords: ["Askimo recipes", "AI CLI recipes", "how to use recipes", "automate prompts", "repeatable AI commands"]
canonical_url: "https://haiphucnguyen.github.io/askimo/blog/2025/12/02/using-recipes-in-askimo/"
---

## Why recipes?

Recipes in Askimo are like reusable shortcuts for AI tasks. Instead of retyping the same prompt, model, and options every time, you save them once in a small file (YAML). Then you run that recipe with a single command—fast, repeatable, and shareable.

Use recipes when you:

- Repeat a task often (summarize a report, review a log, draft a note).
- Want consistent output across your team.
- Prefer simple commands over long prompts.

## What a recipe looks like

A recipe is a tiny YAML file that describes:

- The prompt to send to the model.
- Optional inputs (text, files, parameters).
- Where to save or display the result.

Note: Recipes don’t include a model inside the YAML. Askimo uses your configured provider/model defaults or whatever you pass on the command line when you run the recipe.

You can keep recipes in your project (e.g., `recipes/`) or a shared folder. Askimo comes with samples in `samples/recipes/`—you can start there and modify.

## How to run a recipe

- From a recipe file on disk (ad-hoc, no install needed):
  - `askimo -f ./recipes/my-recipe.yml`
- From an installed recipe by name:
  - `askimo -r my-recipe`
- Using the Desktop app:
  - Open Askimo Desktop → Recipes → Pick a recipe → Run.

If your recipe expects inputs, you can pass them as additional arguments after the recipe name or file path.

## Example 1: Summarize a PDF report (non‑technical)

Goal: Get a short, clear summary of a PDF report to share with your team.

1) Create a recipe `recipes/summarize_report.yml`:

- Prompt: "Summarize the attached report in 5 bullet points with one sentence of takeaway."
- Inputs: A file path to your report.
- Output: Print to screen and optionally save to a file.

2) Run it:

- `askimo -f recipes/summarize_report.yml /path/to/quarterly-report.pdf`

3) Share it:

- Commit the recipe to your repo or send the YAML to a teammate—they'll get the same result format.

Why it's useful: No need to remember the prompt. Everyone uses the same format.

## Example 2: Analyze web access logs (technical)

Goal: Scan an access log or CSV to spot spikes, slow endpoints, or suspicious patterns.

Start from the sample: `samples/recipes/analyze_log.yml` (included with Askimo). It asks the model to analyze a web log for common issues.

Quick run:

- `askimo -f samples/recipes/analyze_log.yml samples/recipes/http_access_analyze.csv`

Adapt it:

- Change the prompt to focus on errors (`status >= 500`), latency, or a specific endpoint.
- Add parameters like date range or top N endpoints.
- Save a version in your project (e.g., `ops/recipes/log_analyze.yml`).

Automation idea: Turn this into a small pipeline that runs at an interval (e.g., every hour), summarizes recent activity, and emails the team—no human interaction needed, and no one has to open a dashboard to see system health.

Why it's useful: Fast insights when you don't have time to build a dashboard.

## Example 3: Draft a polite follow‑up email (non‑technical)

Goal: Turn a few notes into a ready‑to‑send message.

Recipe idea (`recipes/follow_up_email.yml`):

- Prompt: "Write a friendly follow‑up email that thanks the recipient, summarizes the last conversation, and proposes one clear next step. Keep it short (120–150 words)."
- Inputs: A short note (your context), recipient name, and tone (e.g., "warm, professional").
- Output: Plain text.

Run it:

- `askimo -f recipes/follow_up_email.yml --set context="Spoke on Thursday about Q4 budget—waiting on approval." --set recipient="Alicia" --set tone="warm"`

Why it's useful: Removes the friction of writing from scratch while keeping your voice.

## Example 4: Code review checklist (technical)

Goal: Get a focused review on a diff or file using a standard checklist.

Recipe idea (`recipes/code_review.yml`):

- Prompt: "Review the provided code against this checklist: correctness, readability, performance, security basics, and test coverage. Return findings as bullet points with file:line when possible."
- Inputs: The file or `git diff` output.
- Output: Markdown for easy paste into a PR comment.

Run it:

- `git diff HEAD~1 | askimo -f recipes/code_review.yml`

Why it's useful: Consistent reviews and faster feedback.

## Tips for better results

- Be specific: Add concrete instructions (length, format, checklist). 
- Use inputs: Pass files or short context rather than huge prompts.
- Save outputs: Point results to a file if you want a record.
- Share recipes: Keep them in version control for your team.

## Where to learn more

- Full guide: [Using Recipes](/using-recipes)
- Samples to copy: `samples/recipes/`
- Desktop app: Run and tweak recipes without the terminal.

## Closing thought

Recipes make AI practical. Save the work once—run it anytime. Whether you’re summarizing a report, analyzing logs, drafting emails, or reviewing code, Askimo recipes give you reliable, repeatable results with one simple command.
