package com.spring.ai.app.rag.flow;

public interface Action {
    String getName();
    String execute(String input);
}