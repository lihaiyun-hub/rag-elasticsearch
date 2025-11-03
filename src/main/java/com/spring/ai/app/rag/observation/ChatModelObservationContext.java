package com.spring.ai.app.rag.observation;

import io.micrometer.observation.Observation;

public class ChatModelObservationContext extends Observation.Context {
    private String operationType;
    private String provider;
    private String request;
    private String response;

    public String getOperationType() {
        return operationType;
    }

    public void setOperationType(String operationType) {
        this.operationType = operationType;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getRequest() {
        return request;
    }

    public void setRequest(String request) {
        this.request = request;
    }

    public String getResponse() {
        return response;
    }

    public void setResponse(String response) {
        this.response = response;
    }
}
