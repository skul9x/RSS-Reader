package com.skul9x.rssreader.data.network.gemini

import android.util.Log
import kotlinx.serialization.json.*

/**
 * Helper object for processing Gemini API responses and managing JSON serialization.
 */
object GeminiResponseHelper {
    private const val TAG = "GeminiResponseHelper"

    /**
     * Build JSON Request Body for Gemini API.
     */
    fun buildRequestBody(prompt: String, temperature: Double = 0.7, maxOutputTokens: Int = 4096): String {
        val json = buildJsonObject {
            putJsonArray("contents") {
                addJsonObject {
                    putJsonArray("parts") {
                        addJsonObject {
                            put("text", prompt)
                        }
                    }
                }
            }
            putJsonObject("generationConfig") {
                put("temperature", temperature)
                put("maxOutputTokens", maxOutputTokens)
                put("topP", 0.95)
                // Disable thinking to avoid thinking tokens in response
                // Gemini 2.5/3 models have thinking enabled by default
                putJsonObject("thinkingConfig") {
                    put("thinkingBudget", 0)
                }
            }
            putJsonArray("safetySettings") {
                addJsonObject {
                    put("category", "HARM_CATEGORY_HARASSMENT")
                    put("threshold", "BLOCK_NONE")
                }
                addJsonObject {
                    put("category", "HARM_CATEGORY_HATE_SPEECH")
                    put("threshold", "BLOCK_NONE")
                }
                addJsonObject {
                    put("category", "HARM_CATEGORY_SEXUALLY_EXPLICIT")
                    put("threshold", "BLOCK_NONE")
                }
                addJsonObject {
                    put("category", "HARM_CATEGORY_DANGEROUS_CONTENT")
                    put("threshold", "BLOCK_NONE")
                }
            }
        }
        return json.toString()
    }

    /**
     * Extract text from Gemini JSON response.
     * Skips thinking parts (thought: true) that Gemini 2.5/3 models may return.
     * Iterates from last part to first since actual content comes after thinking parts.
     */
    fun extractText(responseBody: String): String {
        return try {
            val json = Json.parseToJsonElement(responseBody).jsonObject
            val candidates = json["candidates"]?.jsonArray ?: return ""
            if (candidates.isEmpty()) return ""
            
            val firstCandidate = candidates[0].jsonObject
            val content = firstCandidate["content"]?.jsonObject ?: return ""
            val parts = content["parts"]?.jsonArray ?: return ""
            if (parts.isEmpty()) return ""
            
            // Collect all non-thinking text parts (in order)
            val result = StringBuilder()
            for (part in parts) {
                val partObj = part.jsonObject
                // Skip thinking parts (Gemini 2.5/3 thinking feature)
                val isThought = partObj["thought"]?.jsonPrimitive?.booleanOrNull == true
                if (!isThought) {
                    val text = partObj["text"]?.jsonPrimitive?.content
                    if (!text.isNullOrBlank()) {
                        result.append(text)
                    }
                }
            }
            result.toString().trim()
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting text from response", e)
            ""
        }
    }

    /**
     * Clean AI response for TTS: remove markdown formatting but keep numbering.
     * Uses regex for robust pattern matching with safeguards against infinite loops.
     */
    fun cleanForTts(text: String): String {
        // Log.d(TAG, "cleanForTts input length: ${text.length}")
        
        var result = text
        
        // Remove bold markers: **text** -> text (using regex for safety)
        result = result.replace(Regex("""\*\*([^*]*)\*\*""")) { it.groupValues[1] }
        // Remove any remaining orphan ** markers
        result = result.replace("**", "")
        
        // Remove italic markers: *text* -> text (but not ** which is already handled)
        result = result.replace(Regex("""\*([^*]+)\*""")) { it.groupValues[1] }
        // Remove any remaining orphan * markers at word boundaries
        result = result.replace(Regex("""(?<!\*)\*(?!\*)"""), "")
        
        // Remove heading markers at start of lines: # ## ### etc
        result = result.lines().joinToString("\n") { line ->
            line.trimStart().let { trimmed ->
                if (trimmed.startsWith("#")) {
                    trimmed.dropWhile { it == '#' }.trimStart()
                } else {
                    line
                }
            }
        }
        
        // Remove inline code backticks: `code` -> code (using regex)
        result = result.replace(Regex("""`([^`]*)`""")) { it.groupValues[1] }
        // Remove any remaining orphan backticks
        result = result.replace("`", "")
        
        // Remove blockquote markers: > at start of line
        result = result.lines().joinToString("\n") { line ->
            if (line.trimStart().startsWith(">")) {
                line.trimStart().drop(1).trimStart()
            } else {
                line
            }
        }
        
        // Remove bullet points: - or * at start of line (but keep numbered lists)
        result = result.lines().joinToString("\n") { line ->
            val trimmed = line.trimStart()
            if ((trimmed.startsWith("- ") || trimmed.startsWith("* ")) && 
                trimmed.firstOrNull()?.isDigit() != true) {
                trimmed.drop(2)
            } else {
                line
            }
        }
        
        // Clean up multiple blank lines (using regex for efficiency)
        result = result.replace(Regex("""\n{3,}"""), "\n\n")
        
        // Clean up multiple spaces (using regex for efficiency)
        result = result.replace(Regex(""" {2,}"""), " ")
        
        return result.trim()
    }
}
