# DevOps Kanban API Documentation

This document provides complete API documentation for the DevOps Kanban system, including all endpoints, request/response schemas, and data transfer objects (DTOs).

## Table of Contents

- [Projects](#projects)
- [Tasks](#tasks)
- [Task Sources](#task-sources)
- [Agents](#agents)
- [Executions](#executions)
- [Data Transfer Objects](#data-transfer-objects)

---

## Projects

### GET /api/projects

List all projects in the system.

**Request**

```
GET /api/projects
```

**Query Parameters**

| Parameter | Type   | Required | Description                    |
|-----------|--------|----------|--------------------------------|
| page      | int    | No       | Page number (default: 0)       |
| size      | int    | No       | Page size (default: 20)        |
| sort      | string | No       | Sort field (default: "name")   |

**Response** (200 OK)

```json
{
  "content": [
    {
      "id": "uuid-string",
      "name": "Project Name",
      "description": "Project description",
      "createdAt": "2026-03-03T10:00:00Z",
      "updatedAt": "2026-03-03T10:00:00Z"
    }
  ],
  "pageable": {
    "pageNumber": 0,
    "pageSize": 20,
    "totalElements": 1,
    "totalPages": 1
  }
}
```

---

### POST /api/projects

Create a new project.

**Request**

```
POST /api/projects
Content-Type: application/json
```

**Request Body**

```json
{
  "name": "New Project",
  "description": "Project description"
}
```

| Field       | Type   | Required | Description                    |
|-------------|--------|----------|--------------------------------|
| name        | string | Yes      | Project name (min: 1, max: 100)|
| description | string | No       | Project description            |

**Response** (201 Created)

```json
{
  "id": "uuid-string",
  "name": "New Project",
  "description": "Project description",
  "createdAt": "2026-03-03T10:00:00Z",
  "updatedAt": "2026-03-03T10:00:00Z"
}
```

**Error Response** (400 Bad Request)

```json
{
  "timestamp": "2026-03-03T10:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "errors": [
    {
      "field": "name",
      "message": "Project name is required"
    }
  ]
}
```

---

### DELETE /api/projects/{id}

Delete a project by ID.

**Request**

```
DELETE /api/projects/{id}
```

**Path Parameters**

| Parameter | Type   | Required | Description      |
|-----------|--------|----------|------------------|
| id        | string | Yes      | Project UUID     |

**Response** (204 No Content)

No response body.

**Error Response** (404 Not Found)

```json
{
  "timestamp": "2026-03-03T10:00:00Z",
  "status": 404,
  "error": "Not Found",
  "message": "Project not found with id: {id}"
}
```

---

## Tasks

### GET /api/tasks

List tasks filtered by project.

**Request**

```
GET /api/tasks?projectId={id}
```

**Query Parameters**

| Parameter | Type   | Required | Description                          |
|-----------|--------|----------|--------------------------------------|
| projectId | string | Yes      | Project UUID to filter tasks         |
| status    | string | No       | Filter by status (TODO, IN_PROGRESS, DONE, BLOCKED) |
| priority  | string | No       | Filter by priority (LOW, MEDIUM, HIGH, CRITICAL) |
| assignee  | string | No       | Filter by assignee ID                |
| page      | int    | No       | Page number (default: 0)             |
| size      | int    | No       | Page size (default: 20)              |
| sort      | string | No       | Sort field (default: "createdAt")    |

**Response** (200 OK)

```json
{
  "content": [
    {
      "id": "uuid-string",
      "projectId": "project-uuid",
      "title": "Task Title",
      "description": "Task description",
      "status": "TODO",
      "priority": "MEDIUM",
      "assigneeId": "user-uuid",
      "sourceId": "source-uuid",
      "externalId": "external-ticket-id",
      "tags": ["tag1", "tag2"],
      "dueDate": "2026-03-10T00:00:00Z",
      "createdAt": "2026-03-03T10:00:00Z",
      "updatedAt": "2026-03-03T10:00:00Z"
    }
  ],
  "pageable": {
    "pageNumber": 0,
    "pageSize": 20,
    "totalElements": 1,
    "totalPages": 1
  }
}
```

---

### POST /api/tasks

Create a new task.

**Request**

```
POST /api/tasks
Content-Type: application/json
```

**Request Body**

```json
{
  "projectId": "project-uuid",
  "title": "New Task",
  "description": "Task description",
  "status": "TODO",
  "priority": "MEDIUM",
  "assigneeId": "user-uuid",
  "sourceId": "source-uuid",
  "externalId": "external-ticket-id",
  "tags": ["tag1", "tag2"],
  "dueDate": "2026-03-10T00:00:00Z"
}
```

| Field       | Type     | Required | Description                                      |
|-------------|----------|----------|--------------------------------------------------|
| projectId   | string   | Yes      | Project UUID                                     |
| title       | string   | Yes      | Task title (min: 1, max: 200)                    |
| description | string   | No       | Task description                                 |
| status      | string   | No       | Task status (default: "TODO")                    |
| priority    | string   | No       | Task priority (default: "MEDIUM")                |
| assigneeId  | string   | No       | Assigned user UUID                               |
| sourceId    | string   | No       | Task source UUID                                 |
| externalId  | string   | No       | External system identifier                       |
| tags        | string[] | No       | Array of tags                                    |
| dueDate     | string   | No       | Due date (ISO 8601 format)                       |

**Response** (201 Created)

```json
{
  "id": "uuid-string",
  "projectId": "project-uuid",
  "title": "New Task",
  "description": "Task description",
  "status": "TODO",
  "priority": "MEDIUM",
  "assigneeId": "user-uuid",
  "sourceId": "source-uuid",
  "externalId": "external-ticket-id",
  "tags": ["tag1", "tag2"],
  "dueDate": "2026-03-10T00:00:00Z",
  "createdAt": "2026-03-03T10:00:00Z",
  "updatedAt": "2026-03-03T10:00:00Z"
}
```

---

### PUT /api/tasks/{id}

Update an existing task.

**Request**

```
PUT /api/tasks/{id}
Content-Type: application/json
```

**Path Parameters**

| Parameter | Type   | Required | Description  |
|-----------|--------|----------|--------------|
| id        | string | Yes      | Task UUID    |

**Request Body**

```json
{
  "title": "Updated Task Title",
  "description": "Updated description",
  "status": "IN_PROGRESS",
  "priority": "HIGH",
  "assigneeId": "user-uuid",
  "tags": ["tag1", "tag2", "tag3"],
  "dueDate": "2026-03-15T00:00:00Z"
}
```

| Field       | Type     | Required | Description                                      |
|-------------|----------|----------|--------------------------------------------------|
| title       | string   | No       | Task title (min: 1, max: 200)                    |
| description | string   | No       | Task description                                 |
| status      | string   | No       | Task status                                      |
| priority    | string   | No       | Task priority                                    |
| assigneeId  | string   | No       | Assigned user UUID                               |
| tags        | string[] | No       | Array of tags                                    |
| dueDate     | string   | No       | Due date (ISO 8601 format)                       |

**Response** (200 OK)

```json
{
  "id": "uuid-string",
  "projectId": "project-uuid",
  "title": "Updated Task Title",
  "description": "Updated description",
  "status": "IN_PROGRESS",
  "priority": "HIGH",
  "assigneeId": "user-uuid",
  "sourceId": "source-uuid",
  "externalId": "external-ticket-id",
  "tags": ["tag1", "tag2", "tag3"],
  "dueDate": "2026-03-15T00:00:00Z",
  "createdAt": "2026-03-03T10:00:00Z",
  "updatedAt": "2026-03-03T11:00:00Z"
}
```

---

### PATCH /api/tasks/{id}/status

Update only the status of a task.

**Request**

```
PATCH /api/tasks/{id}/status
Content-Type: application/json
```

**Path Parameters**

| Parameter | Type   | Required | Description  |
|-----------|--------|----------|--------------|
| id        | string | Yes      | Task UUID    |

**Request Body**

```json
{
  "status": "IN_PROGRESS"
}
```

| Field  | Type   | Required | Description                                           |
|--------|--------|----------|-------------------------------------------------------|
| status | string | Yes      | New status (TODO, IN_PROGRESS, DONE, BLOCKED, CANCELLED) |

**Response** (200 OK)

```json
{
  "id": "uuid-string",
  "projectId": "project-uuid",
  "title": "Task Title",
  "description": "Task description",
  "status": "IN_PROGRESS",
  "priority": "MEDIUM",
  "assigneeId": "user-uuid",
  "sourceId": "source-uuid",
  "externalId": "external-ticket-id",
  "tags": ["tag1", "tag2"],
  "dueDate": "2026-03-10T00:00:00Z",
  "createdAt": "2026-03-03T10:00:00Z",
  "updatedAt": "2026-03-03T12:00:00Z"
}
```

---

## Task Sources

### GET /api/task-sources

List task sources filtered by project.

**Request**

```
GET /api/task-sources?projectId={id}
```

**Query Parameters**

| Parameter | Type   | Required | Description                    |
|-----------|--------|----------|--------------------------------|
| projectId | string | Yes      | Project UUID to filter sources |
| type      | string | No       | Filter by source type          |
| page      | int    | No       | Page number (default: 0)       |
| size      | int    | No       | Page size (default: 20)        |

**Response** (200 OK)

```json
{
  "content": [
    {
      "id": "uuid-string",
      "projectId": "project-uuid",
      "name": "Jira Source",
      "type": "JIRA",
      "config": {
        "url": "https://company.atlassian.net",
        "projectKey": "PROJ",
        "issueTypes": ["Story", "Bug", "Task"]
      },
      "lastSyncAt": "2026-03-03T09:00:00Z",
      "syncStatus": "SUCCESS",
      "enabled": true,
      "createdAt": "2026-03-01T10:00:00Z",
      "updatedAt": "2026-03-03T09:00:00Z"
    }
  ],
  "pageable": {
    "pageNumber": 0,
    "pageSize": 20,
    "totalElements": 1,
    "totalPages": 1
  }
}
```

---

### POST /api/task-sources

Create a new task source.

**Request**

```
POST /api/task-sources
Content-Type: application/json
```

**Request Body**

```json
{
  "projectId": "project-uuid",
  "name": "GitHub Issues Source",
  "type": "GITHUB",
  "config": {
    "repository": "owner/repo",
    "labels": ["bug", "enhancement"],
    "state": "open"
  },
  "enabled": true
}
```

| Field     | Type     | Required | Description                                      |
|-----------|----------|----------|--------------------------------------------------|
| projectId | string   | Yes      | Project UUID                                     |
| name      | string   | Yes      | Source name (min: 1, max: 100)                   |
| type      | string   | Yes      | Source type (JIRA, GITHUB, GITLAB, TRELLO, AZURE_DEVOPS) |
| config    | object   | Yes      | Source-specific configuration                    |
| enabled   | boolean  | No       | Whether source is enabled (default: true)        |

**Response** (201 Created)

```json
{
  "id": "uuid-string",
  "projectId": "project-uuid",
  "name": "GitHub Issues Source",
  "type": "GITHUB",
  "config": {
    "repository": "owner/repo",
    "labels": ["bug", "enhancement"],
    "state": "open"
  },
  "lastSyncAt": null,
  "syncStatus": "PENDING",
  "enabled": true,
  "createdAt": "2026-03-03T10:00:00Z",
  "updatedAt": "2026-03-03T10:00:00Z"
}
```

---

### POST /api/task-sources/{id}/sync

Synchronize tasks from the external source.

**Request**

```
POST /api/task-sources/{id}/sync
```

**Path Parameters**

| Parameter | Type   | Required | Description      |
|-----------|--------|----------|------------------|
| id        | string | Yes      | Task source UUID |

**Request Body**

```json
{
  "fullSync": false
}
```

| Field     | Type     | Required | Description                                      |
|-----------|----------|----------|--------------------------------------------------|
| fullSync  | boolean  | No       | Perform full sync instead of incremental (default: false) |

**Response** (202 Accepted)

```json
{
  "id": "uuid-string",
  "projectId": "project-uuid",
  "name": "GitHub Issues Source",
  "type": "GITHUB",
  "config": {
    "repository": "owner/repo",
    "labels": ["bug", "enhancement"],
    "state": "open"
  },
  "lastSyncAt": null,
  "syncStatus": "IN_PROGRESS",
  "enabled": true,
  "createdAt": "2026-03-03T10:00:00Z",
  "updatedAt": "2026-03-03T10:00:00Z"
}
```

---

### GET /api/task-sources/{id}/test

Test the connection to the external task source.

**Request**

```
GET /api/task-sources/{id}/test
```

**Path Parameters**

| Parameter | Type   | Required | Description      |
|-----------|--------|----------|------------------|
| id        | string | Yes      | Task source UUID |

**Response** (200 OK)

```json
{
  "success": true,
  "message": "Connection successful",
  "details": {
    "reachable": true,
    "authenticated": true,
    "projectCount": 5,
    "lastChecked": "2026-03-03T10:00:00Z"
  }
}
```

**Error Response** (200 OK - Failed Test)

```json
{
  "success": false,
  "message": "Authentication failed",
  "details": {
    "reachable": true,
    "authenticated": false,
    "error": "Invalid API token",
    "lastChecked": "2026-03-03T10:00:00Z"
  }
}
```

---

## Agents

### GET /api/agents

List agents filtered by project.

**Request**

```
GET /api/agents?projectId={id}
```

**Query Parameters**

| Parameter | Type   | Required | Description                    |
|-----------|--------|----------|--------------------------------|
| projectId | string | Yes      | Project UUID to filter agents  |
| status    | string | No       | Filter by status               |
| page      | int    | No       | Page number (default: 0)       |
| size      | int    | No       | Page size (default: 20)        |

**Response** (200 OK)

```json
{
  "content": [
    {
      "id": "uuid-string",
      "projectId": "project-uuid",
      "name": "CI/CD Agent",
      "type": "GITHUB_ACTIONS",
      "config": {
        "workflow": "ci.yml",
        "environment": "staging"
      },
      "status": "IDLE",
      "lastExecutionAt": "2026-03-03T08:00:00Z",
      "createdAt": "2026-03-01T10:00:00Z",
      "updatedAt": "2026-03-03T08:00:00Z"
    }
  ],
  "pageable": {
    "pageNumber": 0,
    "pageSize": 20,
    "totalElements": 1,
    "totalPages": 1
  }
}
```

---

### POST /api/agents

Create a new agent.

**Request**

```
POST /api/agents
Content-Type: application/json
```

**Request Body**

```json
{
  "projectId": "project-uuid",
  "name": "Deployment Agent",
  "type": "JENKINS",
  "config": {
    "jobName": "deploy-production",
    "serverUrl": "https://jenkins.company.com"
  }
}
```

| Field     | Type   | Required | Description                                      |
|-----------|--------|----------|--------------------------------------------------|
| projectId | string | Yes      | Project UUID                                     |
| name      | string | Yes      | Agent name (min: 1, max: 100)                    |
| type      | string | Yes      | Agent type (GITHUB_ACTIONS, JENKINS, GITLAB_CI, AZURE_DEVOPS, CUSTOM) |
| config    | object | Yes      | Agent-specific configuration                     |

**Response** (201 Created)

```json
{
  "id": "uuid-string",
  "projectId": "project-uuid",
  "name": "Deployment Agent",
  "type": "JENKINS",
  "config": {
    "jobName": "deploy-production",
    "serverUrl": "https://jenkins.company.com"
  },
  "status": "IDLE",
  "lastExecutionAt": null,
  "createdAt": "2026-03-03T10:00:00Z",
  "updatedAt": "2026-03-03T10:00:00Z"
}
```

---

### DELETE /api/agents/{id}

Delete an agent by ID.

**Request**

```
DELETE /api/agents/{id}
```

**Path Parameters**

| Parameter | Type   | Required | Description  |
|-----------|--------|----------|--------------|
| id        | string | Yes      | Agent UUID   |

**Response** (204 No Content)

No response body.

**Error Response** (409 Conflict)

```json
{
  "timestamp": "2026-03-03T10:00:00Z",
  "status": 409,
  "error": "Conflict",
  "message": "Agent is currently running an execution and cannot be deleted"
}
```

---

## Executions

### POST /api/executions

Start a new execution.

**Request**

```
POST /api/executions
Content-Type: application/json
```

**Request Body**

```json
{
  "taskId": "task-uuid",
  "agentId": "agent-uuid"
}
```

| Field   | Type   | Required | Description      |
|---------|--------|----------|------------------|
| taskId  | string | Yes      | Task UUID        |
| agentId | string | Yes      | Agent UUID       |

**Response** (202 Accepted)

```json
{
  "id": "uuid-string",
  "taskId": "task-uuid",
  "agentId": "agent-uuid",
  "status": "PENDING",
  "startedAt": "2026-03-03T10:00:00Z",
  "completedAt": null,
  "exitCode": null,
  "output": "",
  "error": null,
  "metadata": {
    "triggeredBy": "user-uuid",
    "agentType": "JENKINS",
    "jobName": "deploy-production"
  },
  "createdAt": "2026-03-03T10:00:00Z",
  "updatedAt": "2026-03-03T10:00:00Z"
}
```

---

### GET /api/executions/{id}

Get execution status and details.

**Request**

```
GET /api/executions/{id}
```

**Path Parameters**

| Parameter | Type   | Required | Description     |
|-----------|--------|----------|-----------------|
| id        | string | Yes      | Execution UUID  |

**Response** (200 OK)

```json
{
  "id": "uuid-string",
  "taskId": "task-uuid",
  "agentId": "agent-uuid",
  "status": "RUNNING",
  "startedAt": "2026-03-03T10:00:00Z",
  "completedAt": null,
  "exitCode": null,
  "output": "Starting build...\nRunning tests...\n",
  "error": null,
  "metadata": {
    "triggeredBy": "user-uuid",
    "agentType": "JENKINS",
    "jobName": "deploy-production",
    "buildNumber": 123
  },
  "createdAt": "2026-03-03T10:00:00Z",
  "updatedAt": "2026-03-03T10:05:00Z"
}
```

**Status Values**

| Status    | Description                           |
|-----------|---------------------------------------|
| PENDING   | Execution is queued                   |
| RUNNING   | Execution is in progress              |
| SUCCESS   | Execution completed successfully      |
| FAILED    | Execution failed                      |
| CANCELLED | Execution was cancelled by user       |
| TIMEOUT   | Execution exceeded time limit         |

---

### POST /api/executions/{id}/stop

Stop a running execution.

**Request**

```
POST /api/executions/{id}/stop
```

**Path Parameters**

| Parameter | Type   | Required | Description     |
|-----------|--------|----------|-----------------|
| id        | string | Yes      | Execution UUID  |

**Request Body**

```json
{
  "reason": "Cancelled by user request"
}
```

| Field  | Type   | Required | Description              |
|--------|--------|----------|--------------------------|
| reason | string | No       | Reason for stopping      |

**Response** (202 Accepted)

```json
{
  "id": "uuid-string",
  "taskId": "task-uuid",
  "agentId": "agent-uuid",
  "status": "CANCELLED",
  "startedAt": "2026-03-03T10:00:00Z",
  "completedAt": "2026-03-03T10:03:00Z",
  "exitCode": 130,
  "output": "Starting build...\nRunning tests...\n[CANCELLED]",
  "error": "Cancelled by user request",
  "metadata": {
    "triggeredBy": "user-uuid",
    "agentType": "JENKINS",
    "jobName": "deploy-production"
  },
  "createdAt": "2026-03-03T10:00:00Z",
  "updatedAt": "2026-03-03T10:03:00Z"
}
```

---

### GET /api/executions/{id}/output

Server-Sent Events (SSE) endpoint for real-time execution output.

**Request**

```
GET /api/executions/{id}/output
Accept: text/event-stream
```

**Path Parameters**

| Parameter | Type   | Required | Description     |
|-----------|--------|----------|-----------------|
| id        | string | Yes      | Execution UUID  |

**Response** (200 OK)

```
Content-Type: text/event-stream
Cache-Control: no-cache
Connection: keep-alive
```

**Event Types**

```
event: output
data: {"timestamp":"2026-03-03T10:00:01Z","line":"Starting build process..."}

event: output
data: {"timestamp":"2026-03-03T10:00:02Z","line":"Installing dependencies..."}

event: output
data: {"timestamp":"2026-03-03T10:00:05Z","line":"Running tests..."}

event: status
data: {"timestamp":"2026-03-03T10:00:10Z","status":"RUNNING","progress":50}

event: output
data: {"timestamp":"2026-03-03T10:00:15Z","line":"All tests passed!"}

event: complete
data: {"timestamp":"2026-03-03T10:00:20Z","status":"SUCCESS","exitCode":0,"duration":20000}
```

**SSE Event Schema**

| Event Type | Description                              | Fields                                      |
|------------|------------------------------------------|---------------------------------------------|
| output     | Single line of output                    | timestamp, line                             |
| status     | Status update with progress              | timestamp, status, progress (0-100)         |
| error      | Error occurred                           | timestamp, error, stackTrace (optional)     |
| complete   | Execution finished                       | timestamp, status, exitCode, duration (ms)  |

---

## Data Transfer Objects

### ProjectDTO

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "title": "ProjectDTO",
  "type": "object",
  "required": ["id", "name", "createdAt", "updatedAt"],
  "properties": {
    "id": {
      "type": "string",
      "format": "uuid",
      "description": "Unique identifier for the project"
    },
    "name": {
      "type": "string",
      "minLength": 1,
      "maxLength": 100,
      "description": "Name of the project"
    },
    "description": {
      "type": "string",
      "maxLength": 1000,
      "description": "Optional description of the project"
    },
    "createdAt": {
      "type": "string",
      "format": "date-time",
      "description": "Timestamp when the project was created"
    },
    "updatedAt": {
      "type": "string",
      "format": "date-time",
      "description": "Timestamp when the project was last updated"
    }
  }
}
```

---

### TaskDTO

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "title": "TaskDTO",
  "type": "object",
  "required": ["id", "projectId", "title", "status", "priority", "createdAt", "updatedAt"],
  "properties": {
    "id": {
      "type": "string",
      "format": "uuid",
      "description": "Unique identifier for the task"
    },
    "projectId": {
      "type": "string",
      "format": "uuid",
      "description": "ID of the project this task belongs to"
    },
    "title": {
      "type": "string",
      "minLength": 1,
      "maxLength": 200,
      "description": "Title of the task"
    },
    "description": {
      "type": "string",
      "maxLength": 10000,
      "description": "Detailed description of the task"
    },
    "status": {
      "type": "string",
      "enum": ["TODO", "IN_PROGRESS", "DONE", "BLOCKED", "CANCELLED"],
      "default": "TODO",
      "description": "Current status of the task"
    },
    "priority": {
      "type": "string",
      "enum": ["LOW", "MEDIUM", "HIGH", "CRITICAL"],
      "default": "MEDIUM",
      "description": "Priority level of the task"
    },
    "assigneeId": {
      "type": "string",
      "format": "uuid",
      "description": "ID of the user assigned to this task"
    },
    "sourceId": {
      "type": "string",
      "format": "uuid",
      "description": "ID of the task source if imported"
    },
    "externalId": {
      "type": "string",
      "maxLength": 100,
      "description": "External system identifier (e.g., Jira ticket number)"
    },
    "tags": {
      "type": "array",
      "items": {
        "type": "string",
        "maxLength": 50
      },
      "description": "Array of tags for categorization"
    },
    "dueDate": {
      "type": "string",
      "format": "date-time",
      "description": "Due date for the task"
    },
    "createdAt": {
      "type": "string",
      "format": "date-time",
      "description": "Timestamp when the task was created"
    },
    "updatedAt": {
      "type": "string",
      "format": "date-time",
      "description": "Timestamp when the task was last updated"
    }
  }
}
```

---

### TaskSourceDTO

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "title": "TaskSourceDTO",
  "type": "object",
  "required": ["id", "projectId", "name", "type", "config", "syncStatus", "enabled", "createdAt", "updatedAt"],
  "properties": {
    "id": {
      "type": "string",
      "format": "uuid",
      "description": "Unique identifier for the task source"
    },
    "projectId": {
      "type": "string",
      "format": "uuid",
      "description": "ID of the project this source belongs to"
    },
    "name": {
      "type": "string",
      "minLength": 1,
      "maxLength": 100,
      "description": "Display name for the task source"
    },
    "type": {
      "type": "string",
      "enum": ["JIRA", "GITHUB", "GITLAB", "TRELLO", "AZURE_DEVOPS", "LINEAR", "ASANA"],
      "description": "Type of external task management system"
    },
    "config": {
      "type": "object",
      "description": "Source-specific configuration",
      "properties": {
        "url": {
          "type": "string",
          "format": "uri",
          "description": "Base URL of the external system"
        },
        "projectKey": {
          "type": "string",
          "description": "Project key or identifier"
        },
        "repository": {
          "type": "string",
          "description": "Repository in format 'owner/repo'"
        },
        "labels": {
          "type": "array",
          "items": {
            "type": "string"
          },
          "description": "Labels to filter by"
        },
        "issueTypes": {
          "type": "array",
          "items": {
            "type": "string"
          },
          "description": "Issue types to sync"
        }
      }
    },
    "lastSyncAt": {
      "type": "string",
      "format": "date-time",
      "description": "Timestamp of the last successful sync"
    },
    "syncStatus": {
      "type": "string",
      "enum": ["PENDING", "IN_PROGRESS", "SUCCESS", "FAILED"],
      "description": "Current sync status"
    },
    "enabled": {
      "type": "boolean",
      "description": "Whether automatic syncing is enabled"
    },
    "createdAt": {
      "type": "string",
      "format": "date-time",
      "description": "Timestamp when the source was created"
    },
    "updatedAt": {
      "type": "string",
      "format": "date-time",
      "description": "Timestamp when the source was last updated"
    }
  }
}
```

---

### AgentDTO

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "title": "AgentDTO",
  "type": "object",
  "required": ["id", "projectId", "name", "type", "config", "status", "createdAt", "updatedAt"],
  "properties": {
    "id": {
      "type": "string",
      "format": "uuid",
      "description": "Unique identifier for the agent"
    },
    "projectId": {
      "type": "string",
      "format": "uuid",
      "description": "ID of the project this agent belongs to"
    },
    "name": {
      "type": "string",
      "minLength": 1,
      "maxLength": 100,
      "description": "Display name for the agent"
    },
    "type": {
      "type": "string",
      "enum": ["GITHUB_ACTIONS", "JENKINS", "GITLAB_CI", "AZURE_DEVOPS", "CIRCLECI", "TRAVIS_CI", "CUSTOM"],
      "description": "Type of CI/CD system"
    },
    "config": {
      "type": "object",
      "description": "Agent-specific configuration",
      "properties": {
        "serverUrl": {
          "type": "string",
          "format": "uri",
          "description": "URL of the CI/CD server"
        },
        "jobName": {
          "type": "string",
          "description": "Name of the job or workflow"
        },
        "workflow": {
          "type": "string",
          "description": "Workflow file name"
        },
        "environment": {
          "type": "string",
          "description": "Target environment"
        },
        "credentialsId": {
          "type": "string",
          "description": "Reference to stored credentials"
        }
      }
    },
    "status": {
      "type": "string",
      "enum": ["IDLE", "BUSY", "OFFLINE", "ERROR"],
      "description": "Current status of the agent"
    },
    "lastExecutionAt": {
      "type": "string",
      "format": "date-time",
      "description": "Timestamp of the last execution"
    },
    "createdAt": {
      "type": "string",
      "format": "date-time",
      "description": "Timestamp when the agent was created"
    },
    "updatedAt": {
      "type": "string",
      "format": "date-time",
      "description": "Timestamp when the agent was last updated"
    }
  }
}
```

---

### ExecutionDTO

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "title": "ExecutionDTO",
  "type": "object",
  "required": ["id", "taskId", "agentId", "status", "startedAt", "createdAt", "updatedAt"],
  "properties": {
    "id": {
      "type": "string",
      "format": "uuid",
      "description": "Unique identifier for the execution"
    },
    "taskId": {
      "type": "string",
      "format": "uuid",
      "description": "ID of the task being executed"
    },
    "agentId": {
      "type": "string",
      "format": "uuid",
      "description": "ID of the agent running the execution"
    },
    "status": {
      "type": "string",
      "enum": ["PENDING", "RUNNING", "SUCCESS", "FAILED", "CANCELLED", "TIMEOUT"],
      "description": "Current status of the execution"
    },
    "startedAt": {
      "type": "string",
      "format": "date-time",
      "description": "Timestamp when execution started"
    },
    "completedAt": {
      "type": "string",
      "format": "date-time",
      "description": "Timestamp when execution completed"
    },
    "exitCode": {
      "type": "integer",
      "description": "Exit code of the execution (null if not completed)"
    },
    "output": {
      "type": "string",
      "description": "Execution output log"
    },
    "error": {
      "type": "string",
      "description": "Error message if execution failed"
    },
    "metadata": {
      "type": "object",
      "description": "Additional execution metadata",
      "properties": {
        "triggeredBy": {
          "type": "string",
          "format": "uuid",
          "description": "ID of user who triggered the execution"
        },
        "agentType": {
          "type": "string",
          "description": "Type of agent used"
        },
        "jobName": {
          "type": "string",
          "description": "Name of job or workflow"
        },
        "buildNumber": {
          "type": "integer",
          "description": "Build number from CI system"
        },
        "duration": {
          "type": "integer",
          "description": "Execution duration in milliseconds"
        }
      }
    },
    "createdAt": {
      "type": "string",
      "format": "date-time",
      "description": "Timestamp when execution record was created"
    },
    "updatedAt": {
      "type": "string",
      "format": "date-time",
      "description": "Timestamp when execution record was last updated"
    }
  }
}
```

---

## Error Handling

All endpoints follow a consistent error response format:

```json
{
  "timestamp": "2026-03-03T10:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Detailed error message",
  "path": "/api/tasks",
  "errors": [
    {
      "field": "title",
      "message": "Title is required"
    }
  ]
}
```

### Common HTTP Status Codes

| Status Code | Description                                      |
|-------------|--------------------------------------------------|
| 200         | OK - Request succeeded                           |
| 201         | Created - Resource created successfully          |
| 202         | Accepted - Request accepted for async processing |
| 204         | No Content - Successful with no response body    |
| 400         | Bad Request - Invalid request data               |
| 401         | Unauthorized - Authentication required           |
| 403         | Forbidden - Insufficient permissions             |
| 404         | Not Found - Resource does not exist              |
| 409         | Conflict - Resource state conflict               |
| 422         | Unprocessable Entity - Validation failed         |
| 500         | Internal Server Error - Server-side error        |

---

## Authentication

All API endpoints require authentication using Bearer token:

```
Authorization: Bearer <token>
```

---

## Rate Limiting

API requests are rate limited. Headers are included in responses:

```
X-RateLimit-Limit: 100
X-RateLimit-Remaining: 95
X-RateLimit-Reset: 1709463600
```

---

## Versioning

The API uses URL path versioning. The current version is v1, accessible at `/api/v1/...` or simply `/api/...` (default).

---

## Content Types

- **Request**: `application/json`
- **Response**: `application/json`
- **SSE Endpoint**: `text/event-stream`
