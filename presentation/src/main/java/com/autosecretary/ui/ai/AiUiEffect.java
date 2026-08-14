package com.autosecretary.ui.ai;

public sealed interface AiUiEffect
        permits AiUiEffect.OpenInstruction, AiUiEffect.OpenProposal, AiUiEffect.ShowError {
    long id();
    record OpenInstruction(long id) implements AiUiEffect { }
    record OpenProposal(long id) implements AiUiEffect { }
    record ShowError(long id, String message) implements AiUiEffect { }
}
