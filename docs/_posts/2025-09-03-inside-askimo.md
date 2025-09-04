---
title: "Inside Askimo: My Daily Journey with an AI CLI"
description: "How building and using Askimo every day has taught me to apply AI in real work - from custom commands to cross-platform workflows in the terminal."
date: 2025-09-03
author: "Hai Nguyen"
tags: ["Askimo", "AI CLI", "Open Source", "Developer Tools", "Cross Platform", "Productivity"]
keywords: ["Askimo CLI", "AI command line tool", "OpenAI CLI", "Ollama CLI", "cross platform AI tool", "AI automation terminal", "developer workflow AI"]
canonical_url: "https://haiphucnguyen.github.io/askimo/blog/2025/09/03/inside-askimo/"
---

## Inside Askimo

When I first started tinkering with Askimo, I wasn’t trying to create a big project. I just wanted something simple to make my day easier. I live in the terminal and bounce between AI tools—OpenAI for some things, Ollama locally, Copilot at work. Switching between them felt clunky, and being tied to one vendor didn’t make sense.

Then it clicked: what if I had one CLI that could talk to all of them, and let me automate the boring parts? Not just cross-platform, but repeatable. I often need to run the same command with different inputs—a set of messages, a list of files, variations of a prompt—pipe data in, script it, and reuse it later. That’s how Askimo began.

## A Tool I Actually Use Every Day

Askimo isn’t just a side project that I work on in spare time - it has become something I rely on daily. I use it to summarize long documents, generate quick drafts, or even suggest names for functions when I’m stuck. Because it lives in the terminal, it feels natural - just another command, like git or docker.

I didn’t build Askimo for show. I built it for myself first. But once it became part of my routine, I realized it might be useful for others too.

## What Askimo Can Do (Right Now)

Even though it’s still early, Askimo already fits neatly into my workflow:

* Runs everywhere - Homebrew on macOS/Linux, binaries for Windows, or Docker if I don’t want to install anything.

* Feels consistent - the same commands work whether I’m on my laptop or a server.

* Local file context - I can ask questions about a file in my project.

* Multiple providers - I can switch between OpenAI, Ollama, Gemini, or X AI without leaving the CLI.

These weren’t “features” I brainstormed - they were gaps I ran into while working. Each one exists because I personally needed it.

## A Journey of Learning

Askimo has also been my way of learning how to apply AI, not just read about it. Building it forced me to experiment: to test prompts, to break things, to see where AI adds value and where it doesn’t.

I’ve come to realize that AI doesn’t replace my work - it extends it. Sometimes it saves me from tedious repetition. Other times, it pushes me to think differently about automation. Each step of Askimo’s development has been a reflection of how I’m learning to work with AI rather than around it.

## Getting Started

If you want to try it out, installation is simple - Homebrew, binaries, or Docker. I keep the instructions here:
[👉 Installation Guide](/installation)

## What’s Next

I’ve got plenty of ideas for where to take it:

* Chaining commands into more powerful workflows.

* Custom commands - I can turn repeated prompts into shortcuts, so I don’t waste time retyping.

* Indexing projects so Askimo understands my real workspace - source code, database schemas/migrations, configuration files, API specs, docs, and even build logs - not just isolated files.

The vision isn’t just a CLI for chat - I want Askimo to grow into a programmable AI environment that feels at home in the terminal.

## Moving Forward

What excites me most isn’t just the tool itself, but what it represents. Askimo started as a weekend hack, but it’s grown into both a part of my daily workflow and a mirror of my own journey learning to apply AI.

For me, it’s proof that AI can be practical, lightweight, and personal. And as I keep building, I’ll keep sharing the lessons I learn along the way - because Askimo isn’t just about what AI can do, it’s about how we, as developers, can shape it into something that fits naturally into our work.

If you try Askimo, I’d love to hear how it fits into your routine.

I’ve made the project open source because I believe tools like this get better when they’re shaped by a community, not just by one developer’s perspective. If you’re curious, want to contribute, or simply want to star the project to follow its progress, you can find it here:
[👉 Askimo on GitHub](https://github.com/haiphucnguyen/askimo)