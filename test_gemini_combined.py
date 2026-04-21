import requests
import json
import time

api_key = "AIzaSyCyqmBpNU0ydnbBIaBZ2JaEaMMtpAuebao"
url = f"https://generativelanguage.googleapis.com/v1beta/models/gemini-3.1-flash-lite-preview:generateContent?key={api_key}"

payload = {
    "contents": [{"parts": [{"text": "Hello"}]}],
    "generationConfig": {
        "thinkingConfig": {
            "thinkingBudget": 0,
            "thinkingLevel": "minimal"
        }
    }
}
headers = {"Content-Type": "application/json"}
response = requests.post(url, data=json.dumps(payload), headers=headers)
print(f"Status Code: {response.status_code}")
print(response.text)
