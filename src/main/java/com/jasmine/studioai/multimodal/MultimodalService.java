package com.jasmine.studioai.multimodal;

import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MultimodalService {

    private final ChatLanguageModel chatLanguageModel;

    /**
     * Analyze an image via DashScope multimodal API.
     * Uses the base64-encoded image data with the chat model.
     */
    public String analyzeImage(String imageBase64, String question) {
        String prompt = """
                You are an image analysis assistant. Analyze the following image and answer the question.

                Question: %s

                Note: The image is provided as base64. Provide a detailed analysis.
                """.formatted(question == null ? "Describe this image in detail." : question);

        return chatLanguageModel.chat(prompt);
    }

    /**
     * Summarize a video transcript.
     */
    public String summarizeVideo(String transcript, String question) {
        String prompt = """
                Summarize the following video transcript and answer related questions.

                Transcript:
                %s

                Question: %s

                Provide a concise summary.
                """.formatted(truncate(transcript, 10000), question == null ? "Summarize this video." : question);

        return chatLanguageModel.chat(prompt);
    }

    /**
     * Process audio transcription result.
     */
    public String processAudioTranscription(String transcription, String instruction) {
        String prompt = """
                Process the following audio transcription according to the instruction.

                Transcription:
                %s

                Instruction: %s
                """.formatted(transcription, instruction == null ? "Format and summarize." : instruction);

        return chatLanguageModel.chat(prompt);
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }
}
