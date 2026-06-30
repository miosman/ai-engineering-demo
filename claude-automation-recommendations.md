# Claude Code Automation Recommendations

## Codebase Profile
- **Type**: Java 21, Maven
- **Framework**: Spring Boot 3.5.11, Spring AI 1.1.3
- **Key Libraries**: PGvector, Apache Tika, OpenAI-compatible client (LM Studio)
- **Patterns**: RAG with QuestionAnswerAdvisor, @Tool calling, document ingestion
- **Existing**: Playwright MCP already configured in settings.local.json

---

## MCP Servers

### context7
**Why**: Spring AI 1.1.3 is rapidly evolving — `QuestionAnswerAdvisor`, `@Tool`, `VectorStore` APIs change frequently between minor versions. context7 gives Claude live, version-accurate docs instead of relying on training data that may reflect older APIs.

**Install**: `claude mcp add context7 -- npx -y @upstash/context7-mcp`

**Usage**: When asking Claude to work with Spring AI, prefix with "use context7" (e.g., "use context7 to show me how to add a new Advisor in Spring AI 1.1.3").

---

## Skills

### add-spring-ai-tool (create this)
**Why**: Adding new `@Tool` methods is a repeatable pattern in this codebase — you'll likely extend beyond weather to other tools. A skill that scaffolds a new tool class following your exact conventions (Open-Meteo-style HTTP client, city validation, error handling) would speed this up.

**Create**: `.claude/skills/add-spring-ai-tool/SKILL.md`
**Invocation**: User-only (`/add-spring-ai-tool`)

```yaml
---
name: add-spring-ai-tool
description: Scaffold a new Spring AI @Tool class following project conventions
disable-model-invocation: false
---

Create a new Spring AI tool following these conventions:
1. Place in `src/main/java/org/example/aiengineeringdemo/tools/`
2. Annotate with `@Component` and `@Tool` on methods
3. Use RestClient or WebClient for HTTP calls
4. Wire into ChatService.toolChat() via `.tools(newTool)`
5. Document supported inputs in CLAUDE.md under "Tool supported inputs"

Ask the user: what external API should this tool call, and what inputs should it accept?
```

---

## Hooks

### Block edits to application.properties
**Why**: `application.properties` contains DB credentials and the LM Studio API key. An accidental edit (wrong model name, wrong port) silently breaks the entire app. A pre-edit hook that requires confirmation protects against this.

**Where**: `.claude/settings.json`

```json
{
  "hooks": {
    "PreToolUse": [
      {
        "matcher": "Edit|Write",
        "hooks": [
          {
            "type": "command",
            "command": "echo '⚠️ Editing application.properties — verify LM Studio/PGvector settings are correct' && exit 0"
          }
        ]
      }
    ]
  }
}
```

A stricter version can use `exit 2` to block the edit entirely and force the user to approve manually via the permission prompt.

---

## Subagents

### spring-ai-reviewer
**Why**: Spring AI has subtle misconfiguration traps — wrong vector dimensions (must match embedding model output), incorrect `SearchRequest` topK, tool registration order. A specialized reviewer that knows these patterns would catch issues before runtime.

**Where**: `.claude/agents/spring-ai-reviewer.md`

```markdown
---
name: spring-ai-reviewer
description: Reviews Spring AI configuration for common misconfigurations
---

You are a Spring AI expert. When reviewing code in this project, check:

1. **PGvector dimensions**: Must match the embedding model's output (384 for granite-embedding-107m). Flag any mismatch with the model being used.
2. **QuestionAnswerAdvisor**: Ensure SearchRequest topK is set and similarity threshold is appropriate.
3. **@Tool methods**: Must be on Spring-managed beans; check that new tools are wired into ChatService.
4. **Embedding model vs chat model**: They must both be configured in application.properties and not share the same bean name.
5. **TokenTextSplitter**: Chunk size should align with the model's context window.

Report any issues with file:line references.
```
