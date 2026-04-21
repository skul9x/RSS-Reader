import requests
import json
import time

api_key = "AIzaSyCyqmBpNU0ydnbBIaBZ2JaEaMMtpAuebao"
model_name = "models/gemini-3.1-flash-lite-preview"
url = f"https://generativelanguage.googleapis.com/v1beta/{model_name}:generateContent?key={api_key}"

def test_config(name, config_obj):
    payload = {
        "contents": [{"parts": [{"text": "Hello"}]}],
        "generationConfig": config_obj
    }
    headers = {"Content-Type": "application/json"}
    
    start = time.time()
    try:
        response = requests.post(url, data=json.dumps(payload), headers=headers, timeout=60)
        end = time.time()
        print(f"Test: {name}")
        print(f"Status: {response.status_code}")
        print(f"Time: {end - start:.2f}s")
        if response.status_code == 200:
            # print(response.text[:100])
            pass
    except Exception as e:
        print(f"Error: {str(e)}")
    print("-" * 40)

test_config("No config", {})
test_config("thinkingBudget: 0", {"thinkingConfig": {"thinkingBudget": 0}})
test_config("thinkingLevel: minimal", {"thinkingConfig": {"thinkingLevel": "minimal"}})
test_config("thinkingLevel: MINIMAL", {"thinkingConfig": {"thinkingLevel": "MINIMAL"}})
test_config("thinkingLevel: low", {"thinkingConfig": {"thinkingLevel": "low"}})
