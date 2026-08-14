package com.autosecretary.ui.editor;

public sealed interface EditorUiEffect
        permits EditorUiEffect.Saved, EditorUiEffect.Deleted, EditorUiEffect.Error {
    long id();
    record Saved(long id) implements EditorUiEffect { }
    record Deleted(long id) implements EditorUiEffect { }
    record Error(long id, String message) implements EditorUiEffect { }
}
