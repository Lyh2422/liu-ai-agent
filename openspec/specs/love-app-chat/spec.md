# LoveApp Chat Specification

## Purpose

Define the observable behavior of the LoveApp relationship-advice chat
capability exposed by the backend and consumed by the frontend.

## Requirements

### Requirement: Multi-turn relationship advice

The system SHALL accept a user message and conversation identifier, use the
conversation identifier to retain recent context, and return relationship
advice in the LoveApp voice.

#### Scenario: Continue an existing conversation

- **WHEN** a client sends a message to `/ai/love_app/chat/sync` with a
  previously used `chatId`
- **THEN** the system SHALL include the recent conversation context when
  generating the response
- **AND** SHALL return a non-empty text response

### Requirement: Streaming chat response

The system SHALL expose a streaming LoveApp chat endpoint using
`text/event-stream`.

#### Scenario: Stream a new answer

- **WHEN** a client sends a message to
  `/ai/love_app/chat/sse` with a `chatId`
- **THEN** the response SHALL use `text/event-stream`
- **AND** SHALL emit an acknowledgement event before generated answer deltas
- **AND** SHALL emit answer content as delta events

### Requirement: Hot-question response reuse

The system SHALL record incoming questions and MAY return a cached answer for
questions identified as hot, while preserving the streaming event contract.

#### Scenario: Cached hot question

- **WHEN** a hot question has a cached answer and the same question is streamed
  again
- **THEN** the system SHALL emit the acknowledgement event
- **AND** SHALL emit the cached answer as a delta event
- **AND** SHALL avoid starting a new model stream for that response

### Requirement: Knowledge-enhanced advice

The system SHALL support a LoveApp mode that enriches responses with the
configured relationship knowledge base or retrieval service.

#### Scenario: Relationship question uses retrieval

- **WHEN** the application invokes the LoveApp RAG flow for a relationship
  question
- **THEN** the response SHALL be generated with the configured retrieval
  advisor
- **AND** SHALL still use the conversation identifier for chat memory
