---
title: Getting Started with Askimo
nav_order: 2
description: Learn how to install Askimo on macOS, Linux, and Windows, and run your first AI CLI command.
---

# Getting Started with Askimo

After installing Askimo, choose a provider and a model, then start chatting.
Askimo saves your settings locally, so you won’t need to repeat these steps next time.

👉 If you don’t choose a model, Askimo will use the default for that provider (except Ollama).

## Quick start (works the same for any provider)

```bash
askimo> :setprovider <ollama|openai|gemini|xai>
askimo> :models            # see models available for that provider
askimo> :setparam model <model-id> # optional if a default exists
askimo> "Hello! Summarize this text."
```
---

## Using Ollama (local models)

1. Install Ollama (see [ollama.com](https://ollama.com/))
2. Pull a model, for example gpt-oss:20b:
```bash
ollama pull gpt-oss:20b
```
3. In askimo
```bash
askimo> :setprovider ollama
askimo> :models          # shows local models (e.g., llama3)
askimo> :setparam model gpt-oss:20b
askimo> "Explain Redis caching in simple terms."
```

If `:models` is empty, pull one with `ollama pull <name>` and try again.

---
## Using OpenAI
1. Get an API key → https://platform.openai.com/api-keys
2. Configure Askimo and chat:
```bash
askimo> :setprovider open_ai
askimo> :setparam api_key sk-...
askimo> :models          # e.g., gpt-4o, gpt-4o-mini
askimo> "Explain Redis caching in simple terms."
```
📌 Default model: gpt-4o

---
## Use Gemini (Google)
1. Get an API key → https://aistudio.google.com
2. Configure and chat:
```bash
askimo> :provider gemini
askimo> :setparam api_key <your-gemini-key>
askimo> :models                         # e.g., gemini-1.5-pro, gemini-1.5-flash
askimo> "Give me five CLI productivity tips."
```
📌 Default model: gemini-2.5-flash

---
## Use X AI (Grok)
1. Get an API key → https://x.ai
2. Configure and chat:
```bash
askimo> :setprovider x_ai
askimo> :setparam api_key <your-xai-key>
askimo> :models                         # e.g., grok-2, grok-2-mini (examples)
askimo> :setparam model grok-3-mini
askimo> "What's new in Java 21?"
```
📌 Default model: grok-4

---
## Switch any time
You can switch providers/models on the fly; Askimo remembers your last choices.
```bash
askimo> :setprovider ollama
askimo> :setparam model mistral 
askimo> :setprovider openai                # defaults to gpt-4o
```