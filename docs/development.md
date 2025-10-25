---
title: Development & Customization
nav_order: 7
has_children: true
description: "Guides for extending Askimo: custom commands, new model providers, recipes, and advanced configuration."
---

# 🛠️ Development & Customization

Welcome to the Askimo development hub. These guides show you how to extend and customize the CLI—whether you're adding a new interactive command, integrating a new AI model provider, or building reusable prompt recipes.

## What You Can Customize

- Commands: Add new `:yourcommand` interactions in the REPL.
- Model Providers: Plug in any chat model API via a factory.
- Recipes: Create reusable YAML-based prompt workflows.
- Projects & RAG: Index local codebases for retrieval-augmented chat.
- Parameters & Presets: Tune style, verbosity, and provider-specific settings.

## Prerequisites

Before extending Askimo, make sure you can build and run it locally:

```bash
git clone git@github.com:haiphucnguyen/askimo.git
cd askimo
./gradlew build
```

## Development Conventions

- Keep public APIs documented (KDoc for Kotlin classes).
- Use clear, emoji-enhanced user feedback (✅, ⚠️, ❌, 🧹, 📦).
- Favor small, focused handlers/factories.
- Fail fast with helpful messages when configuration is missing.
- Avoid hardcoding secrets—use parameters (e.g., `api_key`).

## Testing Your Changes

You can build a GraalVM Native Image of Askimo to distribute a single self-contained binary so end users do NOT need to install Java/JVM. This is ideal for CLI tools: ultra-fast cold startup, lower memory footprint, simpler installation (just drop the `askimo` executable in PATH), reduced attack surface (no dynamic classloading), and predictable runtime behavior.

Benefits at a glance:
- 🚀 Startup speed: native image avoids JVM warm-up.
- 📦 Single file distribution: easier for Homebrew, archives, Docker slim images.
- 🧠 Lower memory: only reachable code is compiled in; smaller RSS for short-lived commands.
- 🔒 Security: fewer moving parts (no runtime bytecode loading).

Build steps:
```bash
# Ensure you have a GraalVM distribution compatible with the Gradle build.
# (If using SDKMAN or Homebrew, set JAVA_HOME accordingly.)
./gradlew nativeImage
```

The resulting executable will appear here:
```bash
ls build/native/nativeCompile/askimo
./build/native/nativeCompile/askimo --help
```

Distribute it by copying `build/native/nativeCompile/askimo` into a release archive or a package manager formula. End users can run it directly without installing Java.

Troubleshooting:
- If the native build fails due to missing resources, check `src/main/resources` and reflection config (`graal-access-filter.json`).
- Increase memory for the build if needed:
  ```bash
  export JAVA_TOOL_OPTIONS="-Xmx4G"
  ./gradlew nativeImage
  ```
- On Linux distributions without `glibc`, consider building inside a container matching target libc.

For commands:
```bash
askimo> :yourcommand
```

For providers:
```bash
askimo> :set-provider YOUR_PROVIDER
askimo> :set-param api_key sk-***
askimo> :models
askimo> :set-param model my-model
askimo> Explain vector embeddings in 2 sentences.
```

Run the test suite
```bash
./gradlew test
```

## Feedback & Contribution

If you build a new provider or command that others may benefit from:
1. Fork the repo.
2. Add your feature + docs under this section.
3. Open a pull request following CONTRIBUTING.md.

Happy hacking! 🚀
