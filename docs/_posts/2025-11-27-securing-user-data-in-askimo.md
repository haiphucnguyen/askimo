---
title: "How Askimo Protects Your Data: Simple, Local, and Encrypted"
description: "A clear, non-technical look at how Askimo handles your API keys and settings—never stored in plain text, encrypted on your device, and designed with practical safeguards."
date: 2025-11-27
author: "Hai Nguyen"
tags: ["Askimo", "Security", "Encryption", "Privacy", "Product"]
keywords: ["Askimo security", "Askimo encryption", "protect API keys", "local security", "never plain text"]
canonical_url: "https://haiphucnguyen.github.io/askimo/blog/2025/11/27/securing-user-data-in-askimo/"
---

## The short version

- Askimo never stores your sensitive data (like API keys) in plain text.  
- Your data is saved locally on your device and encrypted.  
- We use the same protection across Askimo—both the CLI and the Desktop app.  
- Our goal is practical security: protect what matters without exaggeration.

## What Askimo keeps (and what it doesn’t)

Askimo needs a few things to work well:

- API keys for the AI providers you choose (e.g., OpenAI, local Ollama).  
- Optional preferences and command recipes that help you work faster.  

Askimo does not upload your files or send analytics in the background. It only connects to services you choose, when you ask it to.

## Encryption at rest: what that means

When Askimo saves sensitive information, it encrypts it first—so it’s unreadable without a key. If someone opens the file, they’ll see gibberish, not your actual keys.

- We use a modern encryption method (AES‑256‑GCM) that includes integrity protection.  
- A small, installation-specific key on your device is used to encrypt/decrypt your data.  
- This key is stored in your Askimo directory with strict file permissions.

If a system keychain is available, Askimo prefers it. Otherwise, we use the encrypted local file approach described above. Either way, we do not store your keys in plain text.

## Where it lives

- Everything is kept under your user’s Askimo folder.  
- We set restrictive permissions on the encryption key file (owner-only).  
- Encrypted blobs can’t be read without that local key—moving them to another machine won’t decrypt them.

## Applies to the whole product

This protection model is shared across Askimo—both the command-line tools and the Desktop app use the same local, encrypted storage approach. The experience should feel consistent no matter how you use Askimo.

## A practical threat model (in plain words)

What this aims to protect:

- Your saved credentials from casual access (like a simple file open or accidental commit).  
- Shared machines where file permissions matter.

What it doesn’t promise:

- If your computer account is fully compromised (malware running as you), local files and memory may be accessible.  
- If you install untrusted plugins or scripts, they can do harmful things you permit.  
- Provider-side incidents (e.g., issues at OpenAI) are outside our control.

We prefer honest boundaries over big claims. The goal is to make everyday use safer by default.

## Tips we recommend

- Use your OS keychain when available.  
- Keep the Askimo folder private and out of source control.  
- Rotate your API keys if you think they were exposed.  
- Avoid hardcoding secrets in scripts—use environment variables instead.

## A note for curious developers

If you want the details: the encryption code uses AES‑256‑GCM with a random IV and an authentication tag to prevent tampering. Keys are generated once per installation and stored with owner-only permissions. If loading fails, Askimo fails safely instead of returning partial data.

## In closing

Security in Askimo aims to be simple and respectful: your secrets are yours, kept locally, and never stored in plain text. We’ll keep improving this, step by step, and welcome feedback from the community.
