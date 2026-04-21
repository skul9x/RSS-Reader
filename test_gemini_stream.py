import requests
import json
import time

api_key = "AIzaSyCyqmBpNU0ydnbBIaBZ2JaEaMMtpAuebao"

def test_stream(model_name, thinking_level):
    url = f"https://generativelanguage.googleapis.com/v1beta/{model_name}:streamGenerateContent?key={api_key}"
    payload = {
        "contents": [{"parts": [{"text": "Tóm tắt giúp tôi câu này: Xin chào, hôm nay trời đẹp quá, tôi muốn đi chơi. Chỉ trả về tóm tắt."}]}],
        "generationConfig": {
            "temperature": 0.5,
            "maxOutputTokens": 200,
            "thinkingConfig": {
                "thinkingLevel": thinking_level
            }
        }
    }
    
    headers = {"Content-Type": "application/json"}
    
    start = time.time()
    first_token_time = None
    
    try:
        response = requests.post(url, data=json.dumps(payload), headers=headers, stream=True, timeout=60)
        for line in response.iter_lines():
            if line:
                if first_token_time is None:
                    first_token_time = time.time()
                    print(f"First chunk arrived in: {first_token_time - start:.2f}s")
        
        end = time.time()
        print(f"Total time for {model_name} ({thinking_level}): {end - start:.2f}s")
    except Exception as e:
        print(f"Exception: {str(e)}")
    print("-" * 40)

test_stream("models/gemini-3.1-flash-lite-preview", "MINIMAL")
test_stream("models/gemini-2.5-flash-lite", "MINIMAL")
