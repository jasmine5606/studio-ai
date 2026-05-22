package com.jasmine.studioai.localmodel;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic local embedding model (non-semantic) for development without external services.
 * <p>
 * Not intended for production quality retrieval.
 */
public class HashEmbeddingModel implements EmbeddingModel {

    private final int dimension;

    public HashEmbeddingModel(int dimension) {
        this.dimension = dimension;
    }

    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {
        List<Embedding> out = new ArrayList<>(textSegments.size());
        for (TextSegment segment : textSegments) {
            out.add(embedOne(segment == null ? "" : segment.text()));
        }
        return Response.from(out);
    }

    @Override
    public int dimension() {
        return dimension;
    }

    private Embedding embedOne(String text) {
        float[] vec = new float[dimension];
        byte[] seed = sha256(text == null ? "" : text);
        ByteBuffer buf = ByteBuffer.wrap(seed);
        int i = 0;
        while (i < vec.length) {
            if (buf.remaining() < 4) {
                seed = sha256(seed);
                buf = ByteBuffer.wrap(seed);
            }
            int v = buf.getInt();
            // Map int bits into [-1, 1]
            vec[i] = (v / (float) Integer.MAX_VALUE);
            i++;
        }
        Embedding embedding = Embedding.from(vec);
        embedding.normalize();
        return embedding;
    }

    private static byte[] sha256(String s) {
        return sha256(s.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] sha256(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return md.digest(bytes);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}

