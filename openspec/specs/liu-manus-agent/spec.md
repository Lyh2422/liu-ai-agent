# LiuManus Agent Specification

## Purpose

Define the observable behavior of the LiuManus tool-using agent exposed by the
backend.

## Requirements

### Requirement: Tool-assisted task execution

The system SHALL accept a natural-language task and allow LiuManus to select
and invoke the registered tool callbacks needed to complete it.

#### Scenario: Execute a task with tools

- **WHEN** a client sends a task to `/ai/manus/chat`
- **THEN** LiuManus SHALL be initialized with the registered tool callbacks
- **AND** SHALL stream execution progress or results through the HTTP response

### Requirement: Bounded agent execution

The system SHALL enforce a maximum number of agent steps for one LiuManus run.

#### Scenario: Agent reaches the step limit

- **WHEN** a task requires more iterations than the configured maximum
- **THEN** the agent SHALL stop selecting additional steps
- **AND** SHALL return the best available progress or termination result

### Requirement: Explicit termination

The system SHALL provide LiuManus with a termination path that ends a run
without requiring additional tool calls.

#### Scenario: Agent completes a task

- **WHEN** LiuManus determines that the task is complete
- **THEN** it SHALL be able to invoke the termination tool
- **AND** the HTTP stream SHALL complete after the termination result is sent
