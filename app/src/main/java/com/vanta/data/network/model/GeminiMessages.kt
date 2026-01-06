package com.vanta.data.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Gemini Live API message models.
 * 
 * Based on official Vertex AI documentation:
 * https://cloud.google.com/vertex-ai/generative-ai/docs/model-reference/multimodal-live
 * 
 * Key points:
 * - Video processed at 1 FPS
 * - Server-side VAD (Voice Activity Detection) is default
 * - 10 minute max session duration
 * - Session configuration sent as first message
 */

// ============================================================================
// Client Messages
// ============================================================================

/**
 * Wrapper for all client→server messages.
 * Exactly ONE of these fields should be set per message.
 */
@Serializable
data class ClientMessage(
    val setup: BidiGenerateContentSetup? = null,
    val clientContent: BidiGenerateContentClientContent? = null,
    val realtimeInput: BidiGenerateContentRealtimeInput? = null,
    val toolResponse: BidiGenerateContentToolResponse? = null
)

// ============================================================================
// Setup Message (First message only)
// ============================================================================

@Serializable
data class BidiGenerateContentSetup(
    val model: String,
    @SerialName("generation_config")
    val generationConfig: GenerationConfig? = null,
    @SerialName("system_instruction")
    val systemInstruction: Content? = null,
    val tools: List<Tool>? = null,
    @SerialName("realtime_input_config")
    val realtimeInputConfig: RealtimeInputConfig? = null
)

@Serializable
data class GenerationConfig(
    @SerialName("candidate_count")
    val candidateCount: Int? = null,
    @SerialName("max_output_tokens")
    val maxOutputTokens: Int? = null,
    val temperature: Float? = null,
    @SerialName("top_p")
    val topP: Float? = null,
    @SerialName("top_k")
    val topK: Int? = null,
    @SerialName("response_modalities")
    val responseModalities: List<String>? = null,
    @SerialName("speech_config")
    val speechConfig: SpeechConfig? = null
)

@Serializable
data class SpeechConfig(
    @SerialName("voice_config")
    val voiceConfig: VoiceConfig
)

@Serializable
data class VoiceConfig(
    @SerialName("prebuilt_voice_config")
    val prebuiltVoiceConfig: PrebuiltVoiceConfig
)

@Serializable
data class PrebuiltVoiceConfig(
    @SerialName("voice_name")
    val voiceName: String = "Kore"
)

@Serializable
data class Content(
    val role: String? = null,
    val parts: List<Part>
)

@Serializable
data class Part(
    val text: String? = null,
    @SerialName("inline_data")
    val inlineData: Blob? = null
)

@Serializable
data class Blob(
    @SerialName("mime_type")
    val mimeType: String,
    val data: String  // Base64 encoded
) {
    companion object {
        fun audioPcm(base64Data: String) = Blob(
            mimeType = "audio/pcm",
            data = base64Data
        )
        
        fun imageJpeg(base64Data: String) = Blob(
            mimeType = "image/jpeg",
            data = base64Data
        )
    }
}

@Serializable
data class Tool(
    @SerialName("function_declarations")
    val functionDeclarations: List<FunctionDeclaration>? = null
)

@Serializable
data class FunctionDeclaration(
    val name: String,
    val description: String? = null,
    val parameters: Map<String, String>? = null
)

@Serializable
data class RealtimeInputConfig(
    @SerialName("automatic_activity_detection")
    val automaticActivityDetection: AutomaticActivityDetection? = null
)

@Serializable
data class AutomaticActivityDetection(
    val disabled: Boolean = false
)

// ============================================================================
// Client Content (Incremental updates)
// ============================================================================

@Serializable
data class BidiGenerateContentClientContent(
    val turns: List<Content>? = null,
    @SerialName("turn_complete")
    val turnComplete: Boolean = false
)

// ============================================================================
// Realtime Input (Continuous streaming)
// ============================================================================

@Serializable
data class BidiGenerateContentRealtimeInput(
    @SerialName("media_chunks")
    val mediaChunks: List<Blob>? = null,
    @SerialName("activity_start")
    val activityStart: ActivityStart? = null,
    @SerialName("activity_end")
    val activityEnd: ActivityEnd? = null
)

@Serializable
class ActivityStart  // Empty - just marks start of user activity

@Serializable
class ActivityEnd    // Empty - just marks end of user activity

// ============================================================================
// Tool Response
// ============================================================================

@Serializable
data class BidiGenerateContentToolResponse(
    @SerialName("function_responses")
    val functionResponses: List<FunctionResponse>
)

@Serializable
data class FunctionResponse(
    val name: String,
    val response: Map<String, String>
)

// ============================================================================
// Server Messages
// ============================================================================

/**
 * Wrapper for all server→client messages.
 * Exactly ONE of these fields will be set per message.
 */
@Serializable
data class ServerMessage(
    val setupComplete: BidiGenerateContentSetupComplete? = null,
    val serverContent: BidiGenerateContentServerContent? = null,
    val toolCall: BidiGenerateContentToolCall? = null,
    val toolCallCancellation: BidiGenerateContentToolCallCancellation? = null,
    val usageMetadata: UsageMetadata? = null,
    val goAway: GoAway? = null,
    val sessionResumptionUpdate: SessionResumptionUpdate? = null,
    val inputTranscription: BidiGenerateContentTranscription? = null,
    val outputTranscription: BidiGenerateContentTranscription? = null
)

@Serializable
data class BidiGenerateContentSetupComplete(
    val model: String? = null
)

@Serializable
data class BidiGenerateContentServerContent(
    @SerialName("model_turn")
    val modelTurn: ModelTurn? = null,
    @SerialName("turn_complete")
    val turnComplete: Boolean = false,
    val interrupted: Boolean = false
)

@Serializable
data class ModelTurn(
    val parts: List<Part>? = null
)

@Serializable
data class BidiGenerateContentToolCall(
    @SerialName("function_calls")
    val functionCalls: List<FunctionCall>? = null
)

@Serializable
data class FunctionCall(
    val name: String,
    val args: Map<String, String>? = null,
    val id: String? = null
)

@Serializable
data class BidiGenerateContentToolCallCancellation(
    val ids: List<String>? = null
)

@Serializable
data class UsageMetadata(
    @SerialName("prompt_token_count")
    val promptTokenCount: Int? = null,
    @SerialName("candidates_token_count")
    val candidatesTokenCount: Int? = null,
    @SerialName("total_token_count")
    val totalTokenCount: Int? = null
)

@Serializable
data class GoAway(
    @SerialName("time_left")
    val timeLeft: String? = null  // Duration format
)

@Serializable
data class SessionResumptionUpdate(
    val token: String? = null,
    val resumable: Boolean = false
)

@Serializable
data class BidiGenerateContentTranscription(
    val text: String? = null
)
