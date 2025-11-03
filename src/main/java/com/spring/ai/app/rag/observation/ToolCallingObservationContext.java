package com.spring.ai.app.rag.observation;

import io.micrometer.observation.Observation;

public class ToolCallingObservationContext extends Observation.Context {
    private String toolName;
    private String toolArguments;
    private String toolResult;

    public String getToolName() {
        return toolName;
    }

    public void setToolName(String toolName) {
        this.toolName = toolName;
    }

    public String getToolArguments() {
        return toolArguments;
    }

    public void setToolArguments(String toolArguments) {
        this.toolArguments = toolArguments;
    }

    public String getToolResult() {
        return toolResult;
    }

    public void setToolResult(String toolResult) {
        this.toolResult = toolResult;
    }
}
