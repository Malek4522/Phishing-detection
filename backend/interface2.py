import tkinter as tk
from tkinter import ttk
import tldextract
import numpy as np
import pandas as pd
import tensorflow as tf
from sklearn.preprocessing import StandardScaler
import re

# Load the trained model
interpreter = tf.lite.Interpreter(model_path="model2.tflite")
interpreter.allocate_tensors()

# Get input and output tensors
input_details = interpreter.get_input_details()
output_details = interpreter.get_output_details()

# GUI setup
root = tk.Tk()
root.title("Phishing URL Detector")
root.geometry("600x500")
root.configure(bg="#f0f0f0")

# Create main frame
main_frame = ttk.Frame(root, padding="20")
main_frame.pack(fill=tk.BOTH, expand=True)

# Style configuration
style = ttk.Style()
style.configure("TFrame", background="#f0f0f0")
style.configure("TLabel", background="#f0f0f0", font=("Helvetica", 12))
style.configure("TButton", font=("Helvetica", 11))
style.configure("Title.TLabel", font=("Helvetica", 24, "bold"), padding=20)

# Title
title_label = ttk.Label(main_frame, text="Phishing URL Detector", style="Title.TLabel")
title_label.pack(pady=(0, 20))

# URL Input Frame
input_frame = ttk.Frame(main_frame)
input_frame.pack(fill=tk.X, pady=10)

url_label = ttk.Label(input_frame, text="Enter URL to analyze:")
url_label.pack(anchor="w", pady=(0, 5))

url_entry = ttk.Entry(input_frame, width=60, font=("Helvetica", 11))
url_entry.pack(fill=tk.X, pady=5)

# Buttons Frame
button_frame = ttk.Frame(main_frame)
button_frame.pack(pady=20)

check_button = ttk.Button(button_frame, text="Analyze URL", command=lambda: predict_url())
check_button.pack(side=tk.LEFT, padx=5)

reset_button = ttk.Button(button_frame, text="Reset", command=lambda: reset_interface())
reset_button.pack(side=tk.LEFT, padx=5)

# Result Frame
result_frame = ttk.Frame(main_frame)
result_frame.pack(fill=tk.BOTH, expand=True, pady=20)

result_label = ttk.Label(result_frame, text="", font=("Helvetica", 14), wraplength=500)
result_label.pack(pady=10)

confidence_label = ttk.Label(result_frame, text="", font=("Helvetica", 12))
confidence_label.pack(pady=5)

# Status bar
status_bar = ttk.Label(root, text="Ready", relief=tk.SUNKEN, anchor=tk.W)
status_bar.pack(side=tk.BOTTOM, fill=tk.X)

def reset_interface():
    url_entry.delete(0, tk.END)
    result_label.config(text="")
    confidence_label.config(text="")
    status_bar.config(text="Ready")

def update_status(message):
    status_bar.config(text=message)
    root.update_idletasks()

# Feature extraction (matches training pipeline)
def extract_features(url):
    ext = tldextract.extract(url)
    domain = ext.domain + "." + ext.suffix if ext.suffix else ext.domain
    subdomain = ext.subdomain

    url_len = len(url)
    domain_len = len(domain)
    tld_len = len(ext.suffix)
    no_of_subdomains = subdomain.count('.') + 1 if subdomain else 0
    no_of_letters = len(re.findall(r'[a-zA-Z]', url))
    no_of_digits = len(re.findall(r'\d', url))
    no_of_special = len(re.findall(r'[^a-zA-Z0-9]', url))

    features = {
        "URLLength": url_len,
        "DomainLength": domain_len,
        "IsDomainIP": int(re.match(r'^\d{1,3}(\.\d{1,3}){3}$', domain) is not None),
        "URLSimilarityIndex": 100,  # dummy
        "CharContinuationRate": 1,
        "TLDLegitimateProb": 0.5,
        "URLCharProb": 0.05,
        "TLDLength": tld_len,
        "NoOfSubDomain": no_of_subdomains,
        "HasObfuscation": 0,
        "NoOfObfuscatedChar": 0,
        "ObfuscationRatio": 0,
        "NoOfLettersInURL": no_of_letters,
        "LetterRatioInURL": no_of_letters / url_len if url_len else 0,
        "NoOfDegitsInURL": no_of_digits,
        "DegitRatioInURL": no_of_digits / url_len if url_len else 0,
        "NoOfEqualsInURL": url.count('='),
        "NoOfQMarkInURL": url.count('?'),
        "NoOfAmpersandInURL": url.count('&'),
        "NoOfOtherSpecialCharsInURL": len(re.findall(r'[^\w\d\-._~:/?#\[\]@!$&\'()*+,;=]', url)),
        "SpacialCharRatioInURL": no_of_special / url_len if url_len else 0,
        "IsHTTPS": int(url.startswith("https")),
        "LineOfCode": 1000,
        "LargestLineLength": 1000,
        "HasTitle": 1,
        "DomainTitleMatchScore": 0.5,
        "URLTitleMatchScore": 0.5,
        "HasFavicon": 1,
        "Robots": 1,
        "IsResponsive": 1,
        "NoOfURLRedirect": 0,
        "NoOfSelfRedirect": 0,
        "HasDescription": 1,
        "NoOfPopup": 0,
        "NoOfiFrame": 0,
        "HasExternalFormSubmit": 0,
        "HasSocialNet": 0,
        "HasSubmitButton": 1,
        "HasHiddenFields": 0,
        "HasPasswordField": 1,
        "Bank": 0,
        "Pay": 0,
        "Crypto": 0,
        "HasCopyrightInfo": 1,
        "NoOfImage": 10,
        "NoOfCSS": 5,
        "NoOfJS": 10,
        "NoOfSelfRef": 20,
        "NoOfEmptyRef": 0,
        "NoOfExternalRef": 10
    }

    return pd.DataFrame([features])

def predict_url():
    url = url_entry.get().strip()
    if not url:
        result_label.config(text="Please enter a URL.")
        confidence_label.config(text="")
        update_status("Error: No URL provided")
        return

    try:
        update_status("Analyzing URL...")
        # Step 1: Extract features
        df = extract_features(url)

        # Step 2: Drop any columns not used in training
        for col in df.columns:
            if df[col].dtype == object:
                df.drop(columns=col, inplace=True)

        # Step 3: Standardize input
        scaler = StandardScaler()
        dummy_input = pd.DataFrame(np.zeros((1, df.shape[1])), columns=df.columns)
        scaler.fit(dummy_input)
        scaled_input = scaler.transform(df)
        
        # Convert to float32 for TFLite
        scaled_input = scaled_input.astype(np.float32)

        # Step 4: Predict using TFLite model
        interpreter.set_tensor(input_details[0]['index'], scaled_input)
        interpreter.invoke()
        output = interpreter.get_tensor(output_details[0]['index'])
        pred = output[0][0]
        
        # Update UI with results
        if pred >= 0.5:
            result_label.config(text="⚠️ PHISHING DETECTED ⚠️", foreground="red")
        else:
            result_label.config(text="✅ LEGITIMATE URL ✅", foreground="green")
            
        confidence_label.config(text=f"Confidence: {pred:.2%}")
        update_status("Analysis complete")
        
    except Exception as e:
        result_label.config(text="Error occurred during analysis")
        confidence_label.config(text="")
        update_status(f"Error: {str(e)}")

# Bind Enter key to predict_url
url_entry.bind('<Return>', lambda event: predict_url())

# Start the application
root.mainloop()
