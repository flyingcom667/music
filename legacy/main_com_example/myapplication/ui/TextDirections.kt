package com.example.myapplication.ui

import androidx.compose.ui.text.style.TextDirection

/**
 * Paragraph direction used for song metadata (title, artist, album, file name).
 *
 * Persian and Arabic text has to be laid out with its own direction even when the app itself
 * runs left-to-right. Otherwise the Unicode bidirectional algorithm treats the title as a
 * right-to-left island inside a left-to-right paragraph and brackets, parentheses and
 * punctuation end up on the wrong side.
 *
 * `ContentOrLtr` keeps the direction of the content and falls back to left-to-right for text
 * without any strong character (Latin titles, file names, numbers).
 */
val MetadataTextDirection: TextDirection = TextDirection.ContentOrLtr
