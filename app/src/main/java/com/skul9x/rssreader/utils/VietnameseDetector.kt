package com.skul9x.rssreader.utils

/**
 * Utility for detecting Vietnamese text based on characteristic diacritical marks.
 * Vietnamese uses Latin characters with unique diacritics that other languages don't have.
 */
object VietnameseDetector {
    
    // Vietnamese-specific characters (letters with diacritical marks)
    // These characters are unique to Vietnamese and don't appear in other common languages
    private val VIETNAMESE_CHARS = setOf(
        // Đ (unique to Vietnamese)
        'đ', 'Đ',
        // Ă (with breve)
        'ă', 'Ă', 'ằ', 'Ằ', 'ắ', 'Ắ', 'ẳ', 'Ẳ', 'ẵ', 'Ẵ', 'ặ', 'Ặ',
        // Â (with circumflex) + tones
        'ấ', 'Ấ', 'ầ', 'Ầ', 'ẩ', 'Ẩ', 'ẫ', 'Ẫ', 'ậ', 'Ậ',
        // Ê (with circumflex) + tones
        'ế', 'Ế', 'ề', 'Ề', 'ể', 'Ể', 'ễ', 'Ễ', 'ệ', 'Ệ',
        // Ô (with circumflex) + tones
        'ố', 'Ố', 'ồ', 'Ồ', 'ổ', 'Ổ', 'ỗ', 'Ỗ', 'ộ', 'Ộ',
        // Ơ (with horn) - unique to Vietnamese
        'ơ', 'Ơ', 'ờ', 'Ờ', 'ớ', 'Ớ', 'ở', 'Ở', 'ỡ', 'Ỡ', 'ợ', 'Ợ',
        // Ư (with horn) - unique to Vietnamese
        'ư', 'Ư', 'ừ', 'Ừ', 'ứ', 'Ứ', 'ử', 'Ử', 'ữ', 'Ữ', 'ự', 'Ự',
        // Y with tones (common in Vietnamese)
        'ỳ', 'Ỳ', 'ý', 'Ý', 'ỷ', 'Ỷ', 'ỹ', 'Ỹ', 'ỵ', 'Ỵ',
        // Additional vowels with tones
        'à', 'À', 'á', 'Á', 'ả', 'Ả', 'ã', 'Ã', 'ạ', 'Ạ',
        'è', 'È', 'é', 'É', 'ẻ', 'Ẻ', 'ẽ', 'Ẽ', 'ẹ', 'Ẹ',
        'ì', 'Ì', 'í', 'Í', 'ỉ', 'Ỉ', 'ĩ', 'Ĩ', 'ị', 'Ị',
        'ò', 'Ò', 'ó', 'Ó', 'ỏ', 'Ỏ', 'õ', 'Õ', 'ọ', 'Ọ',
        'ù', 'Ù', 'ú', 'Ú', 'ủ', 'Ủ', 'ũ', 'Ũ', 'ụ', 'Ụ'
    )
    
    // High-certainty Vietnamese characters (horn and đ are 100% Vietnamese)
    private val DEFINITE_VIETNAMESE_CHARS = setOf(
        'đ', 'Đ',
        'ơ', 'Ơ', 'ờ', 'Ờ', 'ớ', 'Ớ', 'ở', 'Ở', 'ỡ', 'Ỡ', 'ợ', 'Ợ',
        'ư', 'Ư', 'ừ', 'Ừ', 'ứ', 'Ứ', 'ử', 'Ử', 'ữ', 'Ữ', 'ự', 'Ự',
        'ă', 'Ă', 'ằ', 'Ằ', 'ắ', 'Ắ', 'ẳ', 'Ẳ', 'ẵ', 'Ẵ', 'ặ', 'Ặ'
    )
    
    /**
     * Check if the text contains Vietnamese characters.
     * Returns true if any Vietnamese-specific character is found.
     * 
     * @param text The text to check
     * @return true if Vietnamese, false otherwise
     */
    fun isVietnamese(text: String): Boolean {
        if (text.isBlank()) return false
        
        // First check for definite Vietnamese characters (fastest path)
        for (char in text) {
            if (char in DEFINITE_VIETNAMESE_CHARS) {
                return true
            }
        }
        
        // Then check broader set
        for (char in text) {
            if (char in VIETNAMESE_CHARS) {
                return true
            }
        }
        
        return false
    }
    
    /**
     * Check if the text is likely NOT Vietnamese.
     * Useful for determining if translation is needed.
     */
    fun needsTranslation(text: String): Boolean {
        return !isVietnamese(text)
    }
}
