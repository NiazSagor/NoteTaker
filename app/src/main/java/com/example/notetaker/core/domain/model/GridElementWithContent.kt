package com.example.notetaker.core.domain.model

data class GridElementWithContent(
    val element: GridElement,
    val note: Note?
)
