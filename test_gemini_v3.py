import requests
import json
import time

api_key = "AIzaSyCyqmBpNU0ydnbBIaBZ2JaEaMMtpAuebao"
model_name = "models/gemini-3.1-flash-lite-preview"
url = f"https://generativelanguage.googleapis.com/v1beta/{model_name}:generateContent?key={api_key}"

def test_config(name, config_update):
    payload = {
        "contents": [{"parts": [{"text": "Tóm tắt giúp tôi câu này: Xin chào, hôm nay trời đẹp quá, tôi muốn đi chơi. Chỉ trả về tóm tắt."}]}],
        "generationConfig": {
            "temperature": 0.5,
            "maxOutputTokens": 200,
            "topP": 0.95,
            "topK": 40,
            "thinkingConfig": {
                "thinkingLevel": "MINIMAL"
            }
        }
    }
    
    # Apply updates
    if "thinkingLevel" in config_update:
        payload["generationConfig"]["thinkingConfig"]["thinkingLevel"] = config_update["thinkingLevel"]
    
    if "system_instruction" in config_update:
        payload["system_instruction"] = {"parts": [{"text": config_update["system_instruction"]}]}
        # Update contents to just be the text
        payload["contents"] = [{"parts": [{"text": "Xin chào, hôm nay trời đẹp quá, tôi muốn đi chơi."}]}]

    if "response_mime_type" in config_update:
        payload["generationConfig"]["responseMimeType"] = config_update["response_mime_type"]

    headers = {"Content-Type": "application/json"}
    
    start = time.time()
    try:
        response = requests.post(url, data=json.dumps(payload), headers=headers, timeout=60)
        end = time.time()
        print(f"Test: {name}")
        print(f"Status: {response.status_code}")
        print(f"Time: {end - start:.2f}s")
        if response.status_code == 200:
            res_json = response.json()
            # Check if there are thinking tokens/parts
            # print(f"Response: {response.text[:100]}...")
            pass
    except Exception as e:
        print(f"Exception: {str(e)}")
    print("-" * 40)

# 1. Base (previous best)
test_config("MINIMAL only", {})

# 2. MINIMAL + text/plain
test_config("MINIMAL + text/plain", {"response_mime_type": "text/plain"})

# 3. MINIMAL + system_instruction
test_config("MINIMAL + system_instruction", {"system_instruction": "Bạn là trợ lý tóm tắt văn bản ngắn gọn."})

# 4. low level
test_config("level low", {"thinkingLevel": "low"})

# 5. ALL optimizations
test_config("ALL", {
    "thinkingLevel": "MINIMAL",
    "response_mime_type": "text/plain",
    "system_instruction": "Bạn là trợ lý tóm tắt văn bản ngắn gọn."
})
