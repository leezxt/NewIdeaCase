package com.newideacase.platform.knowledge.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class DeterministicTextChunkerTest {

    private final DeterministicTextChunker chunker = new DeterministicTextChunker(100, 20);

    @Test
    void producesDeterministicBoundedChunksWithOverlap() {
        String source = "A".repeat(250);

        List<TextChunk> first = chunker.split(source);
        List<TextChunk> second = chunker.split(source);

        assertThat(first).isEqualTo(second);
        assertThat(first).hasSize(3);
        assertThat(first).extracting(chunk -> chunk.content().length())
                .containsExactly(100, 100, 90);
        assertThat(first).extracting(TextChunk::index).containsExactly(0, 1, 2);
        assertThat(first).allSatisfy(chunk -> assertThat(chunk.checksum()).hasSize(64));
    }

    @Test
    void normalizesLineEndingsWhitespaceAndControlCharacters() {
        List<TextChunk> chunks = chunker.split(" First\r\nline\t\u0000  value \r\n\r\n Second ");

        assertThat(chunks).singleElement()
                .extracting(TextChunk::content)
                .isEqualTo("First\nline value\n\nSecond");
    }
}
