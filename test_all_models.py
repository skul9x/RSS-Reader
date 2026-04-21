import requests
import json
import time

api_key = "AIzaSyCyqmBpNU0ydnbBIaBZ2JaEaMMtpAuebao"

article_text = """
Cập nhật BCTC quý I/2026: Nhiều doanh nghiệp báo lãi tăng mạnh
Thủy Trúc • 21/04/2026 - 07:10

Nhiều doanh nghiệp đã bắt đầu công bố BCTC quý I/2026.

Bức tranh kết quả kinh doanh quý I/2026 của các doanh nghiệp niêm yết tiếp tục cho thấy sự phân hóa mạnh giữa các nhóm ngành, khi một số lĩnh vực hưởng lợi từ chu kỳ giá, trong khi nhiều doanh nghiệp vẫn chịu áp lực chi phí và đầu ra.

CTCP DAP – Vinachem (mã chứng khoán DDV) ghi nhận doanh thu thuần quý I/2026 đạt 1.842 tỷ đồng, tăng 59,3% so với cùng kỳ. Tuy nhiên, chi phí đầu vào tăng cao khiến lãi gộp chỉ đạt 201 tỷ đồng, tăng 6,8%.

Biên lợi nhuận gộp theo đó giảm từ 16,3% xuống còn 11%, cho thấy áp lực chi phí đang bào mòn hiệu quả kinh doanh. Sau khi trừ các chi phí, lợi nhuận sau thuế đạt 124 tỷ đồng, chỉ tăng nhẹ 2% so với cùng kỳ.

Trong bối cảnh giá thép tăng do chi phí logistics và nguyên vật liệu đầu vào leo thang, nhiều doanh nghiệp ngành thép ghi nhận kết quả tích cực.

CTCP Gang thép Thái Nguyên (Tisco – mã chứng khoán TIS) đạt doanh thu thuần 3.642 tỷ đồng trong quý I, tăng 28,5% so với cùng kỳ. Đáng chú ý, doanh nghiệp đã có lãi trở lại hơn 15 tỷ đồng, trong khi cùng kỳ năm ngoái lỗ hơn 9,2 tỷ đồng.

CTCP Âu Lạc (mã chứng khoán ALC) – doanh nghiệp hoạt động trong lĩnh vực vận tải nhiên liệu đường thủy – ghi nhận doanh thu thuần đạt 316 tỷ đồng, tăng 8,1% so với cùng kỳ.

Lợi nhuận sau thuế đạt hơn 59 tỷ đồng, tăng 24,7%, chủ yếu nhờ tiết giảm chi phí. Doanh thu của doanh nghiệp chủ yếu đến từ dịch vụ vận tải biển và các dịch vụ liên quan.
Screenshot 2026-04-21 at 00.00.16
Nguồn tổng hợp

Ở nhóm thủy điện, CTCP Thủy điện Bắc Hà (BHA) ghi nhận doanh thu quý I đạt 37,5 tỷ đồng, tăng 22,5% so với cùng kỳ.

Tuy nhiên, chi phí tăng cao, đặc biệt là giá vốn và chi phí quản lý doanh nghiệp, khiến công ty tiếp tục lỗ hơn 4,7 tỷ đồng. Dù vậy, mức lỗ đã cải thiện đáng kể so với hơn 10 tỷ đồng của cùng kỳ năm trước.

Trong lĩnh vực dược phẩm, kết quả kinh doanh tiếp tục phân hóa. CTCP Dược phẩm Trung ương 2 (DP2) ghi nhận doanh thu giảm 24% xuống còn 29 tỷ đồng. Giá vốn và các chi phí tăng cao khiến doanh nghiệp lỗ 7 tỷ đồng, gấp đôi cùng kỳ năm ngoái.

Ngược lại, CTCP Dược phẩm Agimexpharm (AGM) duy trì tăng trưởng ổn định với doanh thu đạt 208 tỷ đồng, tăng 3,3% và lợi nhuận sau thuế đạt 13,5 tỷ đồng, tăng 3,8%.

Trong khi đó, CTCP Hóa Dược phẩm Mekophar (MKP) ghi nhận doanh thu giảm 9,4% xuống 208 tỷ đồng, nhưng nhờ chi phí tài chính giảm mạnh, công ty có lãi 1,6 tỷ đồng, cải thiện đáng kể so với mức lỗ hơn 13 tỷ đồng cùng kỳ. Nguyên nhân chính do quý I năm ngoái doanh nghiệp phải trích lập dự phòng giảm giá đầu tư hơn 14,5 tỷ đồng.

Kết quả kinh doanh quý I/2026 cho thấy sự phân hóa rõ nét theo chu kỳ ngành. Nhóm doanh nghiệp hưởng lợi từ giá hàng hóa như thép, phân bón ghi nhận tăng trưởng doanh thu, trong khi các ngành chịu áp lực chi phí hoặc phụ thuộc điều kiện vận hành như thủy điện, dược phẩm vẫn gặp khó khăn.
"""

def build_prompt(content):
    return f"""
Bạn là trợ lý AI chuyên tóm tắt tin tức cho người lái xe. Nhiệm vụ của bạn là tóm tắt nội dung sau thành các ý chính quan trọng nhất, ngắn gọn, súc tích.

YÊU CẦU BẮT BUỘC:
1. Chỉ trả về nội dung tóm tắt dưới dạng danh sách đánh số (1. 2. 3...).
2. TUYỆT ĐỐI KHÔNG có bất kỳ câu dẫn dắt, chào hỏi, rào đón hay kết thúc nào.
3. Vào thẳng nội dung chính ngay lập tức.
4. Ngôn ngữ tự nhiên, dễ nghe khi đọc bằng giọng nói.

Nội dung cần tóm tắt:
{content}
"""

def test_all_models(models):
    url_base = "https://generativelanguage.googleapis.com/v1beta/"
    prompt = build_prompt(article_text)
    
    for model_name, config in models:
        url = f"{url_base}{model_name}:generateContent?key={api_key}"
        payload = {
            "contents": [{"parts": [{"text": prompt}]}],
            "generationConfig": {
                "temperature": 0.5,
                "maxOutputTokens": 800,
            }
        }
        if config:
            payload["generationConfig"]["thinkingConfig"] = config

        headers = {"Content-Type": "application/json"}
        
        start = time.time()
        try:
            response = requests.post(url, data=json.dumps(payload), headers=headers, timeout=60)
            end = time.time()
            print(f"Model: {model_name}")
            print(f"Config: {config}")
            print(f"Status: {response.status_code}")
            print(f"Time: {end - start:.2f}s")
            
            if response.status_code == 200:
                res_json = response.json()
                text = res_json['candidates'][0]['content']['parts'][0]['text']
                print("Summary:")
                print(text.strip())
            else:
                print(f"Error: {response.text}")
        except Exception as e:
            print(f"Exception: {str(e)}")
        print("=" * 70)

# The 4 models currently in GeminiApiClient.kt
models_to_test = [
    ("models/gemini-3.1-flash-lite-preview", {"thinkingLevel": "MINIMAL"}), # Gemini 3.1
    ("models/gemini-2.5-flash-lite", {"thinkingBudget": 0}),                 # Gemini 2.5 Lite
    ("models/gemini-3-flash-preview", {"thinkingLevel": "MINIMAL"}),         # Gemini 3 
    ("models/gemini-2.5-flash", {"thinkingBudget": 0})                       # Gemini 2.5 Normal
]

print("Starting test for all 4 models...\n" + "=" * 70)
test_all_models(models_to_test)
