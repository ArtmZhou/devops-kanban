# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build and Run Commands

### Backend (Spring Boot)
```bash
# Run development server (port 8080)
mvn spring-boot:run

# Build JAR
mvn package

# Run tests
mvn test
```

### Frontend (Vue 3)
```bash
cd frontend

# Install dependencies
npm install

# Run development server (port 5173)
npm run dev

# Build for production
npm run build
```

## Architecture Overview

This is a DevOps Kanban board application for managing tasks with AI agent execution capabilities. The system allows users to manage projects, tasks, and execute tasks using AI coding agents in isolated Git worktrees.

### Backend Structure (`src/main/java/com/devops/kanban/`)

| Package | Purpose |
|---------|---------|
| `entity/` | Domain entities: Project, Task, TaskSource, Agent, Execution |
| `dto/` | Data Transfer Objects for API requests/responses |
| `repository/` | Repository interfaces with `impl/` containing file-based JSON implementations |
| `service/` | Business logic including GitService for worktree management |
| `controller/` | REST API endpoints |
| `spi/` | Service Provider Interfaces for extensibility |
| `adapter/` | SPI implementations for task sources and AI agents |

### Frontend Structure (`frontend/src/`)

| Directory | Purpose |
|-----------|---------|
| `views/` | Page components (KanbanView, AgentConfig, TaskSourceConfig) |
| `components/` | Reusable components (TaskCard, TaskDetail) |
| `api/` | Axios-based API client modules |
| `router/` | Vue Router configuration |

### Key Architectural Patterns

**SPI (Service Provider Interface)**: The system uses SPI interfaces for extensibility:
- `TaskSourceAdapter`: Implement to add new external task sources (GitHub, Jira, etc.)
- `AgentAdapter`: Implement to add new AI agent integrations (Claude, Codex, etc.)

New adapters are auto-discovered via Spring's `@Component` annotation and registered by type.

**File-Based Storage**: Data is stored as JSON files in `./data/` directory (configurable via `app.storage.path`). Each entity type has a dedicated file per project (e.g., `tasks_1.json`).

**Git Worktree Isolation**: Each task execution creates an isolated Git worktree to allow parallel agent execution without conflicts. See `GitService.createWorktree()`.

### Entity Relationships

```
Project (1) ─┬─ (N) Task
              ├─ (N) TaskSource (external task providers)
              └─ (N) Agent (AI execution agents)

Task (1) ────── (N) Execution (agent execution records)
```

### Task Status Flow

`TODO` → `IN_PROGRESS` → `DONE` (also: `BLOCKED`, `CANCELLED`)

## API Reference

See [API.md](API.md) for complete API documentation including all endpoints, request/response schemas, and DTOs.

## Configuration

Application configuration in `src/main/resources/application.yml`:
- Server port: 8080
- Storage path: `./data`
- CORS origins: `http://localhost:5173,http://localhost:3000`
