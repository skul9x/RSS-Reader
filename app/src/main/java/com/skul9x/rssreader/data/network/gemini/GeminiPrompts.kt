package com.skul9x.rssreader.data.network.gemini

/**
 * Helper object for constructing prompts for Gemini API.
 */
object GeminiPrompts {

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
     * Build translation prompt.
     */
    fun buildTranslationPrompt(text: String): String {
        return """
Dịch đoạn text sau sang tiếng Việt. Chỉ trả về bản dịch, không giải thích hay thêm bất cứ điều gì khác.

Text: $text
""".trim()
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
