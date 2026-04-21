import requests
import json
import time

api_key = "AIzaSyCyqmBpNU0ydnbBIaBZ2JaEaMMtpAuebao"

article_text = """
genk.vn
Tôi đã thay đổi 3 cài đặt ẩn này trên điện thoại Android và pin trên máy tôi "bỗng tăng vọt": Làm thử ngay
Quốc Vinh
4–5 minutes

Chỉ với vào mẹo tinh chỉnh dưới đây mà không cần cài đặt thêm bất kỳ phần mềm bên thứ ba nào, bạn sẽ dễ dàng kéo dài thời lượng pin trên điện thoại Android thêm 10%. Dưới đây là cách làm của cây bút Digvijay Kumar từ MakeUseOf.

1. Tắt tính năng sạc pin tối ưu: Hãy để pin đạt mức 100%

Nhiều dòng smartphone gần đây thường mặc định kích hoạt tính năng giới hạn sạc ở mức 80% thông qua các công cụ như Battery protection (Bảo vệ pin) trên Samsung hay Charging optimization (Tối ưu hóa sạc) trên Google Pixel. Lý do là việc sạc đầy 100% thường xuyên có thể làm giảm tuổi thọ pin nếu bạn có ý định sử dụng thiết bị trên hai năm.

Tuy nhiên, các tính năng này đôi khi gây phiền toái cho những lần sạc nhanh ngẫu hứng, khiến bạn bị kẹt ở mức 80% trừ khi chủ động tắt đi. Nếu bạn thuộc nhóm người dùng có thói quen nâng cấp điện thoại hàng năm, việc "cưng nựng" viên pin quá mức là không thực sự cần thiết. Hãy tìm đến mục cài đặt tối ưu hóa sạc, vô hiệu hóa nó để tận dụng tối đa dung lượng viên pin mỗi khi cắm sạc.

2. Vô hiệu hóa Always On Display (AOD): "Kẻ sát nhân thầm lặng"

Always On Display là tính năng mặc định trên hầu hết các dòng Android đời mới. Tuy nhiên, việc để màn hình liên tục hiển thị giờ giấc hay thông báo sẽ tiêu tốn một lượng pin đáng kể mỗi ngày. Trên một số thiết bị, AOD có thể rút ngắn thời gian chờ của máy một cách rõ rệt.

Để tiết kiệm năng lượng, bạn nên tắt tính năng này trong phần Cài đặt -> Hiển thị (hoặc tìm kiếm từ khóa "AOD"). Thay vì duy trì màn hình luôn bật, bạn có thể sử dụng thao tác chạm hai lần (double-tap) để xem nhanh màn hình khóa khi cần. Đây là cách làm hiệu quả để bảo toàn dung lượng pin mà không ảnh hưởng nhiều đến trải nghiệm.

3. Tối ưu hóa trong Tùy chọn cho nhà phát triển

Đây là nơi chứa những tinh chỉnh "thay da đổi thịt" cho chiếc điện thoại của bạn, giúp chứa được nhiều thông tin hơn trên màn hình, làm hoạt ảnh mượt mà hơn và giảm tải cho phần cứng. Để bắt đầu, hãy kích hoạt Developer options bằng cách vào Cài đặt -> Giới thiệu -> Chạm 7 lần vào "Số bản dựng" (Build number) .

    Tăng mật độ hiển thị (Smallest Width): Việc tăng thông số này giúp nội dung hiển thị nhỏ gọn hơn, cho phép bạn xem được nhiều dữ liệu hơn mà không phải cuộn trang quá nhiều. Thông thường, giá trị mặc định nằm trong khoảng 360–440 dp. Bạn có thể thử nghiệm nâng lên mức 450, 500 hoặc 600 để tìm ra điểm cân bằng phù hợp với thị lực.
    Rút ngắn thời gian hoạt ảnh: Bạn hãy tìm các mục Window animation scale , Transition animation scale và Animator duration scale , sau đó điều chỉnh từ 1.0x xuống 0.5x . Thay đổi này sẽ giúp các hiệu ứng chuyển cảnh nhanh gấp đôi, tạo cảm giác giao diện phản hồi tức thì.
    Giảm độ phân giải màn hình: Đây là yếu tố tác động lớn nhất đến pin. Việc hạ độ phân giải xuống mức tương đương 1080p (ví dụ từ 1280x2856 xuống 1080x2410 trên Pixel 10 Pro) sẽ giúp GPU hoạt động nhẹ nhàng hơn. Ở khoảng cách sử dụng thông thường, mắt người rất khó nhận ra sự khác biệt về độ sắc nét, nhưng hiệu quả tiết kiệm pin là rất rõ ràng.

Tổng kết

Dù một số lời khuyên trên có vẻ đi ngược lại với các thiết lập bảo vệ thiết bị tiêu chuẩn, nhưng chúng lại cực kỳ hữu ích nếu bạn ưu tiên hiệu suất thực tế và sự linh hoạt hàng ngày. Việc ngừng "nâng niu" viên pin ở mức 80% và loại bỏ những tính năng thừa thãi như AOD sẽ giúp bạn khai thác tối đa sức mạnh của chiếc điện thoại.

Hãy thử áp dụng ngay những bước trên để cảm nhận một chiếc Android "trâu" hơn và nhanh hơn đáng kể.
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

def test_article_summarize(model_name, thinking_config):
    url = f"https://generativelanguage.googleapis.com/v1beta/{model_name}:generateContent?key={api_key}"
    prompt = build_prompt(article_text)
    
    payload = {
        "contents": [{"parts": [{"text": prompt}]}],
        "generationConfig": {
            "temperature": 0.5,
            "maxOutputTokens": 800,
        }
    }
    if thinking_config:
        payload["generationConfig"]["thinkingConfig"] = thinking_config

    headers = {"Content-Type": "application/json"}
    
    start = time.time()
    try:
        response = requests.post(url, data=json.dumps(payload), headers=headers, timeout=60)
        end = time.time()
        print(f"Model: {model_name}")
        print(f"Config: {thinking_config}")
        print(f"Status: {response.status_code}")
        print(f"Time: {end - start:.2f}s")
        if response.status_code == 200:
            res_json = response.json()
            text = res_json['candidates'][0]['content']['parts'][0]['text']
            print("Summary Content:")
            print(text.strip())
        else:
            print(f"Error: {response.text}")
    except Exception as e:
        print(f"Exception: {str(e)}")
    print("-" * 60)

# 1. Test 2.5 Flash Lite
test_article_summarize("models/gemini-2.5-flash-lite", {"thinkingBudget": 0})

# 2. Test 3.1 Flash Lite
test_article_summarize("models/gemini-3.1-flash-lite-preview", {"thinkingLevel": "MINIMAL"})
