import requests
import json
import time

api_key = "AIzaSyCyqmBpNU0ydnbBIaBZ2JaEaMMtpAuebao"

def test_model(model_name, thinking_config):
    url = f"https://generativelanguage.googleapis.com/v1beta/{model_name}:generateContent?key={api_key}"
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
    try:
        response = requests.post(url, data=json.dumps(payload), headers=headers, timeout=60)
        end = time.time()
        print(f"Model: {model_name}")
        print(f"Config: {thinking_config}")
        print(f"Status: {response.status_code}")
        print(f"Time: {end - start:.2f}s")
        if response.status_code != 200:
            print(f"Error Body: {response.text}")
    except Exception as e:
        print(f"Exception: {str(e)}")
    print("-" * 40)

# Test Gemini 2.5 Flash Lite
test_model("models/gemini-2.5-flash-lite", {"thinkingBudget": 0})
test_model("models/gemini-2.5-flash-lite", None)

# Test Gemini 3.1 Flash Lite (for comparison)
test_model("models/gemini-3.1-flash-lite-preview", {"thinkingLevel": "minimal"})
