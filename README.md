# Chengxin AI Agent

Chengxin AI is a campus mental-health companion system for student support, counselor follow-up, and local demonstration of a multimodal AI workflow.

- Dynamic intent routing: classifies each message as `CHAT`, `CONSULT`, or `RISK`; ordinary chat avoids RAG, while counseling and risk messages enter retrieval-augmented support.
- SSE streaming chat: `/api/chat/stream` returns `text/event-stream` so the frontend can render a live companion-style response.
- Background mental-state assessment: records emotion labels, emotion scores, risk levels, and confidence for counselor review without exposing the assessment directly to students.
- Closed-loop risk workflow: counseling and risk messages are stored in the database; high-risk reports are written to Excel first, then trigger counselor alerts through log, SMTP, HTTP, or MCP-style tools.
- Spring AI model integration: the default route uses Ollama with the project model; `openai` is also supported; `mock` is available for offline demos without a large model.
- Replaceable knowledge base: local lightweight retrieval is enabled by default, with optional Chroma mirroring and querying.

The LoRA fine-tuning, GGUF conversion, and Ollama import workflow is documented in [docs/qwen25-7b-lora-finetune-guide.md](docs/qwen25-7b-lora-finetune-guide.md).

## Project Structure

```text
src/main/java/com/multimodalAgent/agent
├── config                 # Configuration, security, AI and MCP beans
├── controller             # Chat, knowledge, report and status APIs
├── domain                 # JPA entities and enums
├── dto                    # Request and response objects
├── repository             # Spring Data JPA repositories
├── security               # Current user model and authentication lookup
└── service
    ├── ai                 # Spring AI adapter, mock client and prompts
    ├── knowledge          # Chunking, retrieval and Chroma gateway
    ├── mcp                # Excel and alert delivery tools
    └── multimodal         # Text, audio, image/video analysis and fusion
```

## Quick Start

This workspace already includes a local JDK 17 and Maven setup. Ollama can be started by the project scripts when the local model is available.

```bash
cd multimodalAgent
./scripts/run-dev.sh
```

Open the application after startup:

```text
http://localhost:8080
```

For a two-step manual launch, start Ollama first:

```bash
cd multimodalAgent
./scripts/start-ollama.sh
```

Then start the Spring Boot backend:

```bash
cd multimodalAgent
JAVA_HOME="$PWD/.tools/amazon-corretto-17.jdk/Contents/Home" \
  .tools/apache-maven-3.9.9/bin/mvn -Dmaven.repo.local=.m2/repository spring-boot:run
```

You can also run the packaged jar:

```bash
cd multimodalAgent
JAVA_HOME="$PWD/.tools/amazon-corretto-17.jdk/Contents/Home" \
  .tools/amazon-corretto-17.jdk/Contents/Home/bin/java \
  -jar target/multimodalAgent-agent-0.1.0.jar --server.address=127.0.0.1 --server.port=8080
```

By default, the project uses an H2 file database, Ollama as the model provider, a local Excel file, and log-based alerts. The frontend status area shows whether the current runtime is a real model or mock mode. On first startup, the system creates two demo accounts:

```text
admin / admin123
student / student123
```

## Offline Demo Mode

Use mock mode when the large local model is not available or when you need a stable classroom demo:

```bash
cd multimodalAgent
AI_PROVIDER=mock \
USE_CHROMA=false \
MCP_EXCEL_MODE=local \
MCP_EMAIL_MODE=log \
MANAGEMENT_HEALTH_REDIS_ENABLED=false \
JAVA_HOME="$PWD/.tools/amazon-corretto-17.jdk/Contents/Home" \
  .tools/apache-maven-3.9.9/bin/mvn -Dmaven.repo.local=.m2/repository spring-boot:run
```

For a clean English demo database and report file, run with separate local paths:

```bash
DB_URL='jdbc:h2:file:./data/multimodalAgent-en;MODE=MySQL;DATABASE_TO_LOWER=TRUE' \
MCP_EXCEL_LOCAL_PATH=./data/multimodalAgent-en-reports.xlsx
```

## API Examples

Student support conversation:

```bash
curl -N -u student:student123 \
  -H 'Content-Type: application/json' \
  -d '{"message":"I have been under a lot of pressure lately and cannot sleep at night."}' \
  http://localhost:8080/api/chat/stream
```

High-risk example, which triggers report generation, Excel writing, and alert delivery:

```bash
curl -N -u student:student123 \
  -H 'Content-Type: application/json' \
  -d '{"message":"I feel hopeless and I cannot keep going."}' \
  http://localhost:8080/api/chat/stream
```

Counselor report list:

```bash
curl -u admin:admin123 http://localhost:8080/api/admin/reports
```

Current model and runtime status:

```bash
curl -u student:student123 http://localhost:8080/api/agent/status
```

Add knowledge as an administrator:

```bash
curl -u admin:admin123 \
  -H 'Content-Type: application/json' \
  -d '{"source":"sleep-guide","content":"For insomnia, students can keep a fixed wake-up time, reduce screen exposure before bed, and contact the campus counseling center if sleep problems persist."}' \
  http://localhost:8080/api/admin/knowledge
```

## Local RAG Knowledge Base

Bundled knowledge files live in:

```text
src/main/resources/knowledge/
```

On first startup, if the database has no knowledge chunks, the application imports every file under that folder. If an existing H2 database already contains knowledge chunks, editing these Markdown files will not automatically overwrite the stored chunks. Use a fresh `DB_URL`, clear the knowledge table, or upload a revised file through the admin knowledge endpoint when you need the updated content to take effect.

## Ollama and LoRA Model

The default local model name is:

```text
multimodalAgent-qwen2.5-7b-ft:latest
```

It is created from this GGUF weight file:

```text
models/multimodalAgent-qwen2.5-7b-ft/multimodalAgent-qwen2.5-7b-ft-q4_k_m.gguf
```

Create or re-import the local Ollama model:

```bash
cd multimodalAgent
./scripts/create-finetuned-model.sh
```

Then start the application:

```bash
cd multimodalAgent
./scripts/run-dev.sh
```

If the terminal reports `ollama: command not found`, the command-line link is missing. The project scripts can still call `/Applications/Ollama.app/Contents/Resources/ollama` directly.

Manual Ollama startup:

```bash
cd multimodalAgent
AI_PROVIDER=ollama \
OLLAMA_BASE_URL=http://localhost:11434 \
OLLAMA_MODEL=multimodalAgent-qwen2.5-7b-ft:latest \
JAVA_HOME="$PWD/.tools/amazon-corretto-17.jdk/Contents/Home" \
  .tools/apache-maven-3.9.9/bin/mvn -Dmaven.repo.local=.m2/repository spring-boot:run
```

## Release Package

The model file is large and should usually be distributed separately:

```text
models/multimodalAgent-qwen2.5-7b-ft/multimodalAgent-qwen2.5-7b-ft-q4_k_m.gguf
```

Build an application archive without model weights:

```bash
cd multimodalAgent
./scripts/package-release.sh
```

The script generates `dist/multimodalAgent-app-<timestamp>.tar.gz`. The package includes source code, Dockerfile, docker-compose, scripts, documentation, `models/multimodalAgent-qwen2.5-7b-ft/Modelfile`, and the `data/lora/psychqa.jsonl` dataset. It excludes model weights, model zip files, runtime databases, Excel outputs, logs, PDF documents, `target/`, `.m2/`, `.tools/`, and IDE metadata.

When another machine receives the project, place the model files under:

```text
multimodalAgent/models/multimodalAgent-qwen2.5-7b-ft/
```

Then run:

```bash
cd multimodalAgent
./scripts/create-finetuned-model.sh
./scripts/run-dev.sh
```

For Docker-based dependencies:

```bash
docker compose up -d mysql redis chroma mailpit
./scripts/create-finetuned-model.sh
./scripts/run-dev.sh
```

On non-macOS systems, or when Ollama, JDK 17, and Maven are installed in custom paths, set `OLLAMA_BIN`, `JAVA_HOME`, and `MAVEN_BIN` accordingly.

## OpenAI Provider

```bash
cd multimodalAgent
AI_PROVIDER=openai \
OPENAI_API_KEY=your_api_key \
OPENAI_MODEL=gpt-4o-mini \
JAVA_HOME="$PWD/.tools/amazon-corretto-17.jdk/Contents/Home" \
  .tools/apache-maven-3.9.9/bin/mvn -Dmaven.repo.local=.m2/repository spring-boot:run
```

## MySQL, Chroma, and SMTP

Start dependencies:

```bash
docker compose up -d mysql redis chroma mailpit
```

Run with the MySQL profile:

```bash
AI_PROVIDER=ollama \
USE_CHROMA=true \
MCP_EMAIL_MODE=smtp \
ALERT_MAIL_RECIPIENTS=counselor@example.com \
mvn spring-boot:run -Dspring-boot.run.profiles=mysql
```

Mailpit dashboard:

```text
http://localhost:8025
```

## MCP Tool Modes

Excel tool:

- `MCP_EXCEL_MODE=local`: writes to `./data/multimodalAgent-reports.xlsx` by default.
- `MCP_EXCEL_MODE=http`: calls `MCP_EXCEL_URL/write`.

Alert tool:

- `MCP_EMAIL_MODE=log`: writes alert delivery events to logs for local demos.
- `MCP_EMAIL_MODE=smtp`: sends email through Spring Mail.
- `MCP_EMAIL_MODE=http`: calls `MCP_EMAIL_URL/send`.

The high-risk workflow is implemented as: create report -> write Excel -> send alert after Excel succeeds -> update tool status.
