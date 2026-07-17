package com.newideacase.platform.knowledge.application;

import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class DeterministicTextChunker {

    private final int maxCharacters;
    private final int overlapCharacters;

    public DeterministicTextChunker(
            @Value("${app.knowledge.chunk-size:1000}") int maxCharacters,
            @Value("${app.knowledge.chunk-overlap:100}") int overlapCharacters) {
        if (maxCharacters < 100 || overlapCharacters < 0 || overlapCharacters >= maxCharacters) {
            throw new IllegalArgumentException("Invalid knowledge chunk size or overlap");
        }
        this.maxCharacters = maxCharacters;
        this.overlapCharacters = overlapCharacters;
    }

    public List<TextChunk> split(String source) {
        String normalized = normalize(source);
        if (normalized.isBlank()) {
            return List.of();
        }

        List<TextChunk> chunks = new ArrayList<>();
        int start = 0;
        while (start < normalized.length()) {
            int end = Math.min(start + maxCharacters, normalized.length());
            if (end < normalized.length()) {
                end = findBoundary(normalized, start, end);
            }
            String content = normalized.substring(start, end).trim();
            if (!content.isEmpty()) {
                chunks.add(new TextChunk(chunks.size(), content, ContentChecksum.sha256(content)));
            }
            if (end == normalized.length()) {
                break;
            }
            start = Math.max(start + 1, end - overlapCharacters);
        }
        return List.copyOf(chunks);
    }

    private int findBoundary(String text, int start, int proposedEnd) {
        int minimumBoundary = start + (maxCharacters / 2);
        int paragraph = text.lastIndexOf('\n', proposedEnd);
        if (paragraph >= minimumBoundary) {
            return paragraph;
        }
        int whitespace = text.lastIndexOf(' ', proposedEnd);
        return whitespace >= minimumBoundary ? whitespace : proposedEnd;
    }

    private String normalize(String source) {
        String lineNormalized = source.replace("\r\n", "\n").replace('\r', '\n');
        StringBuilder result = new StringBuilder(lineNormalized.length());
        boolean previousSpace = false;
        for (int index = 0; index < lineNormalized.length(); index++) {
            char character = lineNormalized.charAt(index);
            if (character == '\n') {
                while (!result.isEmpty() && result.charAt(result.length() - 1) == ' ') {
                    result.setLength(result.length() - 1);
                }
                result.append('\n');
                previousSpace = false;
            } else if (Character.isWhitespace(character) || Character.isISOControl(character)) {
                if (!previousSpace && !result.isEmpty() && result.charAt(result.length() - 1) != '\n') {
                    result.append(' ');
                    previousSpace = true;
                }
            } else {
                result.append(character);
                previousSpace = false;
            }
        }
        return result.toString().trim();
    }
}
