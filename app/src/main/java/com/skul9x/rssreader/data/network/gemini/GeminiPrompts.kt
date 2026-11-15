package com.skul9x.rssreader.data.network.gemini

/**
 * Helper object for constructing prompts for Gemini API.
 */
object GeminiPrompts {

    const val TRANSLATION_SYSTEM_INSTRUCTION =
        "Bạn là chuyên gia dịch thuật tin tức sang tiếng Việt. Dịch chính xác, tự nhiên, ngắn gọn. TUYỆT ĐỐI không thêm giải thích hay câu chào."

    const val BATCH_TRANSLATION_SYSTEM_INSTRUCTION =
        "Bạn là chuyên gia dịch thuật. Dịch các giá trị tiêu đề trong JSON object sau sang tiếng Việt. Giữ nguyên toàn bộ ID làm keys. Chỉ dịch phần text của values. Trả về đúng định dạng JSON object."

    /**
     * Build prompt for summarizing news content.
     */
    fun buildSummarizationPrompt(content: String): String {
        return """
Bạn là trợ lý AI chuyên tóm tắt tin tức cho người lái xe. Nhiệm vụ của bạn là tóm tắt nội dung sau thành các ý chính quan trọng nhất, ngắn gọn, súc tích.

YÊU CẦU BẮT BUỘC:
1. Chỉ trả về nội dung tóm tắt dưới dạng danh sách đánh số (1. 2. 3...).
2. TUYỆT ĐỐI KHÔNG có bất kỳ câu dẫn dắt, chào hỏi, rào đón hay kết thúc nào (Ví dụ: KHÔNG viết "Dưới đây là tóm tắt...", "Thưa giám đốc...", "Chào bạn...", "Tuyệt vời...").
3. Vào thẳng nội dung chính ngay lập tức.
4. Ngôn ngữ tự nhiên, dễ nghe khi đọc bằng giọng nói.

Nội dung cần tóm tắt:
$content
""".trim()
    }

    /**
     * Build translation prompt providing clean user text input without prompt bloat.
     */
    fun buildTranslationPrompt(text: String): String {
        return "Text: $text"
    }

    /**
     * Build prompt for suggesting content class.
     */
    fun buildSuggestClassPrompt(rawHtml: String): String {
        return """
Bạn là chuyên gia phân tích HTML. Nhiệm vụ của bạn là phân tích HTML sau và tìm CSS class hoặc selector tốt nhất để lấy nội dung chính của bài viết (article content).

YÊU CẦU BẮT BUỘC:
1. Chỉ trả về ĐÚNG 1 CSS selector duy nhất (ví dụ: .article-content, #main-body, div.post-body, article.content).
2. TUYỆT ĐỐI KHÔNG trả về giải thích, chỉ trả về selector duy nhất.
3. Ưu tiên class chứa nội dung bài viết chính, bỏ qua sidebar, menu, header, footer, quảng cáo, comments.
4. Selector phải tồn tại trong HTML được cung cấp.
5. Nếu không tìm được class phù hợp, chỉ trả về: NOT_FOUND

HTML để phân tích:
$rawHtml
""".trim()
    }
}
