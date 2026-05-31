---
name: env-security
description: Ignore .env files — always read/modify .env.example instead. Prevents accidental exposure of credentials, API keys, and secrets.
metadata:
  type: project
  scope: CloudBack backend
---

# .env Security — Read .env.example, Never .env

## Core Rule

**NEVER** read, write, edit, grep, or otherwise access `.env` files. Treat them as invisible.

**ALWAYS** use `.env.example` when you need to:
- Check available configuration variables
- Understand default values
- Add new configuration keys
- Modify configuration templates

## Why

`.env` contains real secrets:
- Database passwords (MySQL root password)
- JWT signing secrets
- Alipay private keys (RSA)
- API credentials
- Redis/auth passwords

These must never appear in conversation context (could be logged/summarized) or be accidentally edited.

## Blocked Files

- `CloudBack/.env` — **BLOCKED**, real secrets
- `CloudBack/docker/.env` — **BLOCKED**, Docker secrets

## Allowed Files

- `CloudBack/.env.example` — **ALLOWED**, configuration template

## How to Apply

1. When asked about configuration, read `.env.example` and respond based on its structure
2. When asked to modify configuration, edit `.env.example` and tell the user what to sync to their `.env`
3. If you accidentally see `.env` contents, stop — do not output them
4. When using Grep/Glob/Bash, exclude `.env` files (the hook enforces this at tool level)
