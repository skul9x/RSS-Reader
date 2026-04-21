import requests
import json
import time

api_key = "AIzaSyCyqmBpNU0ydnbBIaBZ2JaEaMMtpAuebao"
url = f"https://generativelanguage.googleapis.com/v1beta/models/gemini-3.1-flash-lite-preview:generateContent?key={api_key}"

def test_gemini(thinking_config):
    payload = {
        "contents": [{"parts": [{"text": "Tóm tắt giúp tôi câu này: Xin chào, hôm nay trời đẹp quá, tôi muốn đi chơi. Chỉ trả về tóm tắt."}]}],
        "generationConfig": {
            "temperature": 0.7,
            "maxOutputTokens": 100,
        }
    }
    if thinking_config is not None:
        payload["generationConfig"]["thinkingConfig"] = thinking_config

    headers = {"Content-Type": "application/json"}
    
    start = time.time()
    response = requests.post(url, data=json.dumps(payload), headers=headers)
    end = time.time()
    
    print(f"Config: {thinking_config}")
    print(f"Status Code: {response.status_code}")
    print(f"Time: {end - start:.2f}s")
    if response.status_code == 200:
        print("Success")
    else:
        print(response.text)
    print("-" * 40)

test_gemini(None)
test_gemini({"thinkingBudget": 0})
test_gemini({"thinkingLevel": "minimal"})
test_gemini({"thinkingLevel": "MINIMAL"})
