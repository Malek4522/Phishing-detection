from flask import Flask, render_template, request, jsonify
import numpy as np
import pandas as pd
import re
from urllib.parse import urlparse
import tensorflow as tf
from sklearn.preprocessing import StandardScaler
import tldextract

app = Flask(__name__)

# Load all TFLite models
models = {
    'model1': {
        'interpreter': tf.lite.Interpreter(model_path='model1.tflite'),
        'scaler': None,
        'data_features': None
    },
    'model2': {
        'interpreter': tf.lite.Interpreter(model_path='model2.tflite'),
        'scaler': StandardScaler()
    },
    'model3': {
        'interpreter': tf.lite.Interpreter(model_path='model3.tflite')
    }
}

# Initialize all models
for model_name, model_data in models.items():
    model_data['interpreter'].allocate_tensors()
    if model_name == 'model1':
        # Load dataset and prepare scaler for model1
        data = pd.read_csv('dataset1.csv')
        data['status'] = data['status'].map({'legitimate': 0, 'phishing': 1})
        model_data['data_features'] = data.drop(['url', 'status'], axis=1)
        model_data['scaler'] = StandardScaler()
        model_data['scaler'].fit(model_data['data_features'])

# Get input/output details for all models
model_details = {
    name: {
        'input': model['interpreter'].get_input_details(),
        'output': model['interpreter'].get_output_details()
    }
    for name, model in models.items()
}

def extract_features_model1(url):
    parsed = urlparse(url)
    ext = tldextract.extract(url)
    domain = ext.domain.lower()
    
    features = {
        'length_url': len(url),
        'length_hostname': len(parsed.hostname) if parsed.hostname else 0,
        'ip': 1 if re.fullmatch(r'(\d{1,3}\.){3}\d{1,3}', parsed.hostname or '') else 0,
        'nb_dots': url.count('.'),
        'nb_hyphens': url.count('-'),
        'nb_at': url.count('@'),
        'nb_qm': url.count('?'),
        'nb_and': url.count('&'),
        'nb_or': url.lower().count('or'),
        'nb_eq': url.count('='),
        'nb_underscore': url.count('_'),
        'nb_tilde': url.count('~'),
        'nb_percent': url.count('%'),
        'nb_slash': url.count('/'),
        'nb_star': url.count('*'),
        'nb_colon': url.count(':'),
        'nb_comma': url.count(','),
        'nb_semicolumn': url.count(';'),
        'nb_dollar': url.count('$'),
        'nb_space': url.count(' '),
        'nb_www': url.lower().count('www'),
        'nb_com': url.lower().count('.com'),
        'nb_dslash': url.count('//'),
        'http_in_path': 1 if 'http' in parsed.path.lower() else 0,
        'https_token': 1 if 'https' in ext.subdomain.lower() else 0,
        'ratio_digits_url': sum(c.isdigit() for c in url) / len(url),
        'ratio_digits_host': sum(c.isdigit() for c in (parsed.hostname or '')) / len(parsed.hostname or '1'),
        'punycode': 1 if 'xn--' in url else 0,
        'port': 1 if parsed.port else 0,
        'tld_in_path': 1 if ext.suffix in parsed.path else 0,
        'tld_in_subdomain': 1 if ext.suffix in ext.subdomain else 0,
        'abnormal_subdomain': 1 if len(ext.subdomain.split('.')) > 3 else 0,
        'nb_subdomains': len(ext.subdomain.split('.')),
        'prefix_suffix': 1 if '-' in ext.domain else 0,
        'random_domain': 0,
        'shortening_service': 1 if any(x in url for x in ['bit.ly', 'tinyurl', 'goo.gl']) else 0,
        'path_extension': 1 if re.search(r'\.\w{1,5}$', parsed.path) else 0,
        'nb_redirection': url.count('//') - 1,
        'nb_external_redirection': 0,
        'length_words_raw': len(re.findall(r'\w+', url)),
        'char_repeat': max([url.count(c) for c in set(url)]),
        'shortest_words_raw': min([len(w) for w in re.findall(r'\w+', url)]) if re.findall(r'\w+', url) else 0,
        'shortest_word_host': min([len(w) for w in (parsed.hostname or '').split('.')]) if parsed.hostname else 0,
        'shortest_word_path': min([len(w) for w in parsed.path.split('/')]) if parsed.path else 0,
        'longest_words_raw': max([len(w) for w in re.findall(r'\w+', url)]) if re.findall(r'\w+', url) else 0,
        'longest_word_host': max([len(w) for w in (parsed.hostname or '').split('.')]) if parsed.hostname else 0,
        'longest_word_path': max([len(w) for w in parsed.path.split('/')]) if parsed.path else 0,
        'avg_words_raw': np.mean([len(w) for w in re.findall(r'\w+', url)]) if re.findall(r'\w+', url) else 0,
        'avg_word_host': np.mean([len(w) for w in (parsed.hostname or '').split('.')]) if parsed.hostname else 0,
        'avg_word_path': np.mean([len(w) for w in parsed.path.split('/')]) if parsed.path else 0
    }

    aligned_features = [features[col] if col in features else 0 for col in models['model1']['data_features'].columns]
    scaled = models['model1']['scaler'].transform([aligned_features])
    return scaled

def extract_features_model2(url):
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
        "URLSimilarityIndex": 100,
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

    df = pd.DataFrame([features])
    for col in df.columns:
        if df[col].dtype == object:
            df.drop(columns=col, inplace=True)
    
    dummy_input = pd.DataFrame(np.zeros((1, df.shape[1])), columns=df.columns)
    models['model2']['scaler'].fit(dummy_input)
    scaled_input = models['model2']['scaler'].transform(df)
    return scaled_input.astype(np.float32)

# List of final feature columns for model3
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

def extract_features_model3(url):
    try:
        parsed_url = urlparse(url)
        domain = parsed_url.netloc
        parts = domain.split('.')
        domain_length = len(domain)
        subdomains_count = len(parts) - 2 if len(parts) > 2 else 0
        syms_digs = sum(c.isdigit() for c in domain)
        syms_dash = domain.count('-')
        syms_vowels = count_vowels(domain)
        syms_consonants = count_consonants(domain)
        # Only 10 numeric features for model3
        feature_array = [
            domain_length,         # 1
            subdomains_count,      # 2
            syms_digs,             # 3
            syms_dash,             # 4
            syms_vowels,           # 5
            syms_consonants,       # 6
            0,                    # 7 - stubbed (e.g. DOMAIN_AGE)
            0,                    # 8 - stubbed (e.g. DNS_MX_COUNT)
            0,                    # 9 - stubbed (e.g. DNS_A_COUNT)
            0                     # 10 - stubbed (e.g. FAVICON)
        ]
        return np.array([feature_array], dtype=np.float32)
    except Exception as e:
        print(f"Error extracting features: {e}")
        return np.zeros((1, 10), dtype=np.float32)

@app.route('/')
def home():
    return render_template('index.html')

@app.route('/predict', methods=['POST'])
def predict():
    try:
        url = request.json['url']
        
        if not url:
            return jsonify({'error': 'Please enter a URL'}), 400

        ext = tldextract.extract(url)
        domain = ext.domain.lower()
        whitelist = ['google', 'apple', 'facebook', 'microsoft', 'amazon', 'microsoftonline']
        if domain in whitelist:
            result = {
                'is_phishing': False,
                'confidence': 1.0,
                'message': 'Majority: Legitimate ✅'
            }
            return jsonify(result)

        if '@' in url:
            result = {
                'is_phishing': True,
                'confidence': 1.0,
                'message': 'Majority: Phishing ⚠️ (présence de \'@\' dans l\'URL)'
            }
            return jsonify(result)

        if '0' in domain or '__' in domain:
            result = {
                'is_phishing': True,
                'confidence': 1.0,
                'message': 'Majority: Phishing ⚠️'
            }
            return jsonify(result)

        url = re.sub(r'^https://', '', url, flags=re.IGNORECASE)

        # Get predictions from all models
        phishing_votes = 0
        total_confidence = 0

        for model_name in ['model1', 'model2', 'model3']:
            # Extract features based on model
            if model_name == 'model1':
                features = extract_features_model1(url)
            elif model_name == 'model2':
                features = extract_features_model2(url)
            else:  # model3
                features = extract_features_model3(url)

            # Get model details
            model = models[model_name]
            input_details = model_details[model_name]['input']
            output_details = model_details[model_name]['output']

            # Make prediction
            model['interpreter'].set_tensor(input_details[0]['index'], features.astype(np.float32))
            model['interpreter'].invoke()
            prediction = model['interpreter'].get_tensor(output_details[0]['index'])[0][0]

            if prediction > 0.5:
                phishing_votes += 1
            total_confidence += prediction

        # Calculate majority result
        majority_is_phishing = phishing_votes >= 2
        avg_confidence = total_confidence / 3

        result = {
            'is_phishing': majority_is_phishing,
            'confidence': float(avg_confidence),
            'message': 'Majority: Phishing ⚠️' if majority_is_phishing else 'Majority: Legitimate ✅'
        }
        return jsonify(result)

    except Exception as e:
        return jsonify({'error': str(e)}), 500

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=8080, debug=True) 