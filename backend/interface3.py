import tkinter as tk
import numpy as np
import tensorflow as tf
import re
from datetime import datetime
import socket
import dns.resolver
import whois
import ssl
import requests
from urllib.parse import urlparse

# Load your trained model
interpreter = tf.lite.Interpreter(model_path='model3.tflite')
interpreter.allocate_tensors()

# Get input and output details
input_details = interpreter.get_input_details()
output_details = interpreter.get_output_details()

# List of feature columns matching the dataset
final_feature_columns = [
    "ID",
    "DOMAIN_NAME",
    "TS_RATE_CALC",
    "IP",
    "WHOIS_NAME",
    "WHOIS_ORG",
    "WHOIS_REGISTRAR",
    "CERT_ISSUER",
    "ROOT_DOMAIN",
    "SECOND_DOMAIN",
    "SUBDOMAINS_COUNT",
    "DOMAIN_LENGTH",
    "DOMAIN_AGE",
    "DNS_MX_COUNT",
    "DNS_TXT_COUNT",
    "DNS_A_COUNT",
    "DNS_NS_COUNT",
    "DNS_CNAME_COUNT",
    "FAVICON",
    "SYMS_DIGS",
    "SYMS_DASH",
    "SYMS_VOWELS",
    "SYMS_CONSONANTS"
]

def count_vowels(text):
    vowels = 'aeiouAEIOU'
    return sum(1 for char in text if char in vowels)

def count_consonants(text):
    consonants = 'bcdfghjklmnpqrstvwxyzBCDFGHJKLMNPQRSTVWXYZ'
    return sum(1 for char in text if char in consonants)

def extract_features(url):
    try:
        # Parse URL
        parsed_url = urlparse(url)
        domain = parsed_url.netloc
        
        # Basic domain features
        domain_length = len(domain)
        syms_digs = sum(c.isdigit() for c in domain)
        syms_dash = domain.count('-')
        syms_vowels = count_vowels(domain)
        syms_consonants = count_consonants(domain)
        
        # Domain parts
        parts = domain.split('.')
        root_domain = parts[-1] if len(parts) > 0 else ''
        second_domain = parts[-2] if len(parts) > 1 else ''
        subdomains_count = len(parts) - 2 if len(parts) > 2 else 0
        
        # Initialize features with default values
        features = {
            "ID": 0,
            "DOMAIN_NAME": domain,
            "TS_RATE_CALC": 0,
            "IP": 0,
            "WHOIS_NAME": 0,
            "WHOIS_ORG": 0,
            "WHOIS_REGISTRAR": 0,
            "CERT_ISSUER": 0,
            "ROOT_DOMAIN": 1 if root_domain else 0,
            "SECOND_DOMAIN": 1 if second_domain else 0,
            "SUBDOMAINS_COUNT": subdomains_count,
            "DOMAIN_LENGTH": domain_length,
            "DOMAIN_AGE": 0,
            "DNS_MX_COUNT": 0,
            "DNS_TXT_COUNT": 0,
            "DNS_A_COUNT": 0,
            "DNS_NS_COUNT": 0,
            "DNS_CNAME_COUNT": 0,
            "FAVICON": 0,
            "SYMS_DIGS": syms_digs,
            "SYMS_DASH": syms_dash,
            "SYMS_VOWELS": syms_vowels,
            "SYMS_CONSONANTS": syms_consonants
        }
        
        # Convert features to array in the correct order
        feature_array = [features[col] for col in final_feature_columns[:-1]]  # Exclude IS_PHISHING
        return np.array([feature_array], dtype=np.float32)
        
    except Exception as e:
        print(f"Error extracting features: {e}")
        # Return zero array if feature extraction fails
        return np.zeros((1, len(final_feature_columns)-1), dtype=np.float32)

def predict_url():
    url = url_entry.get()
    if not url:
        result_label.config(text="Please enter a URL")
        return
    
    try:
        features = extract_features(url)
        
        # Set input tensor
        interpreter.set_tensor(input_details[0]['index'], features)
        
        # Run inference
        interpreter.invoke()
        
        # Get prediction
        prediction_prob = interpreter.get_tensor(output_details[0]['index'])[0][0]
        prediction = int(prediction_prob > 0.5)
        result_label.config(text=f"Prediction: {'Phishing' if prediction == 1 else 'Legitimate'} ({prediction_prob:.3f})")
    except Exception as e:
        result_label.config(text=f"Error: {e}")

def reset_fields():
    url_entry.delete(0, tk.END)
    result_label.config(text="")

# Tkinter GUI setup
root = tk.Tk()
root.title("Phishing URL Detector")
root.geometry("450x200")

tk.Label(root, text="Enter URL:", font=("Arial", 12)).pack(pady=10)
url_entry = tk.Entry(root, width=60)
url_entry.pack()

button_frame = tk.Frame(root)
button_frame.pack(pady=15)

predict_button = tk.Button(button_frame, text="Predict", command=predict_url, width=15)
predict_button.grid(row=0, column=0, padx=10)

reset_button = tk.Button(button_frame, text="Reset", command=reset_fields, width=15)
reset_button.grid(row=0, column=1, padx=10)

result_label = tk.Label(root, text="", font=("Arial", 14, "bold"), fg="blue")
result_label.pack(pady=10)

root.mainloop()
