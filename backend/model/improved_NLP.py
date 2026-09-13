import os
os.environ["OPENBLAS_NUM_THREADS"] = "1"
os.environ["OMP_NUM_THREADS"] = "1"
os.environ["MKL_NUM_THREADS"] = "1"
import sqlite3
import pandas as pd
import torch
import torch.nn as nn
from torch.utils.data import Dataset, DataLoader
from transformers import AutoTokenizer, AutoModel, get_linear_schedule_with_warmup
from torch.optim import AdamW
from tqdm import tqdm
import numpy as np
from sklearn.metrics import f1_score


def find_optimal_thresholds(y_true, y_prob):
    optimal_thresholds = []
    labels = ['toxic', 'severe_toxic', 'obscene', 'threat', 'insult', 'identity_hate']
    
    for i, label in enumerate(labels):
        best_thresh = 0.5
        best_f1 = 0
        for thresh in np.arange(0.1, 0.9, 0.05):
            preds = (y_prob[:, i] > thresh).astype(int)
            f1 = f1_score(y_true[:, i], preds, zero_division=0)
            if f1 > best_f1:
                best_f1 = f1
                best_thresh = thresh
        print(f"Optimal threshold for {label}: {best_thresh:.2f}")
        optimal_thresholds.append(best_thresh)
        
    return optimal_thresholds

class ActiveLearningDB:
    def __init__(self, db_path="human_corrections.db"):
        self.conn = sqlite3.connect(db_path)
        self.labels = ['toxic', 'severe_toxic', 'obscene', 'threat', 'insult', 'identity_hate']
        self._init_db()

    def _init_db(self):
        query = f"""
        CREATE TABLE IF NOT EXISTS corrections (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            comment_text TEXT,
            toxic INTEGER, severe_toxic INTEGER, obscene INTEGER, 
            threat INTEGER, insult INTEGER, identity_hate INTEGER,
            timestamp DATETIME DEFAULT CURRENT_TIMESTAMP
        )
        """
        self.conn.cursor().execute(query)
        self.conn.commit()

    def add_human_correction(self, text, human_scores):
        query = """
        INSERT INTO corrections (comment_text, toxic, severe_toxic, obscene, threat, insult, identity_hate) 
        VALUES (?, ?, ?, ?, ?, ?, ?)
        """
        values = [text] + [int(human_scores.get(col, 0)) for col in self.labels]
        self.conn.cursor().execute(query, values)
        self.conn.commit()

    def get_combined_training_data(self, base_csv_path="FINAL_TRAIN.csv", sample_size=5000):
        base_df = pd.read_csv(base_csv_path)[['comment_text'] + self.labels].dropna(subset=['comment_text'])

        if sample_size and len(base_df) > sample_size:
            base_df = base_df.sample(n=sample_size, random_state=42)

        dynamic_df = pd.read_sql_query(
            "SELECT comment_text, toxic, severe_toxic, obscene, threat, insult, identity_hate FROM corrections",
            self.conn
        )

        if not dynamic_df.empty:
            return pd.concat([base_df, dynamic_df], ignore_index=True)
        return base_df


class JigsawDataset(Dataset):
    def __init__(self, dataframe, tokenizer, max_len=128):
        self.data = dataframe.reset_index(drop=True)
        self.tokenizer = tokenizer
        self.max_len = max_len
        self.labels = ['toxic', 'severe_toxic', 'obscene', 'threat', 'insult', 'identity_hate']

    def __len__(self):
        return len(self.data)

    def __getitem__(self, idx):
        text = str(self.data.iloc[idx]['comment_text'])
        targets = self.data.iloc[idx][self.labels].values.astype(float)

        encoding = self.tokenizer(
            text,
            truncation=True,
            padding='max_length',
            max_length=self.max_len,
            return_tensors="pt"
        )

        return {
            'input_ids': encoding['input_ids'].squeeze(0),
            'attention_mask': encoding['attention_mask'].squeeze(0),
            'targets': torch.tensor(targets, dtype=torch.float)
        }


class MultilingualToxicityModel(nn.Module):
    def __init__(self, num_classes=6):
        super(MultilingualToxicityModel, self).__init__()
        self.encoder = AutoModel.from_pretrained("xlm-roberta-base")

    
        self.encoder.gradient_checkpointing_enable()

        
        for name, param in self.encoder.named_parameters():
            if any(f"encoder.layer.{i}." in name for i in range(6, 12)):
                param.requires_grad = True
            else:
                param.requires_grad = False

        self.classifier = nn.Sequential(
            nn.Dropout(0.2),
            nn.Linear(self.encoder.config.hidden_size, 128),
            nn.GELU(),  
            nn.Linear(128, num_classes)
        )

    def forward(self, input_ids, attention_mask):
        outputs = self.encoder(input_ids=input_ids, attention_mask=attention_mask)
        cls_embedding = outputs.last_hidden_state[:, 0, :]
        return self.classifier(cls_embedding)


def train_multilingual_model(base_csv_path="FINAL_TRAIN.csv", epochs=3, sample_size=15000):
    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    print(f"[*] Training on device: {device}")

    tokenizer = AutoTokenizer.from_pretrained("xlm-roberta-base")
    model = MultilingualToxicityModel().to(device)

    db = ActiveLearningDB()
    train_df = db.get_combined_training_data(base_csv_path=base_csv_path, sample_size=sample_size)
    print(f"[*] Total training samples loaded: {len(train_df)}")

    labels = ['toxic', 'severe_toxic', 'obscene', 'threat', 'insult', 'identity_hate']
    pos_counts = train_df[labels].sum().values
    neg_counts = len(train_df) - pos_counts
    
    pos_weights = np.clip(neg_counts / np.maximum(pos_counts, 1), a_min=1.0, a_max=10.0)
    pos_weight_tensor = torch.tensor(pos_weights, dtype=torch.float).to(device)
    
    batch_size = 2
    accumulation_steps = 16 
    
    dataset = JigsawDataset(train_df, tokenizer)
    dataloader = DataLoader(dataset, batch_size=batch_size, shuffle=True)
    criterion = nn.BCEWithLogitsLoss(pos_weight=pos_weight_tensor)
    
    
    optimizer = AdamW([
        {'params': [p for p in model.encoder.parameters() if p.requires_grad], 'lr': 2e-5},
        {'params': model.classifier.parameters(), 'lr': 1e-4} 
    ])
    
    total_steps = len(dataloader) * epochs
    scheduler = get_linear_schedule_with_warmup(
        optimizer, 
        num_warmup_steps=int(0.1 * total_steps), 
        num_training_steps=total_steps
    )
    scaler = torch.amp.GradScaler('cuda')
    
    torch.cuda.empty_cache()
    model.train()
    
    for epoch in range(epochs):
        total_loss = 0.0
        progress_bar = tqdm(enumerate(dataloader), total=len(dataloader), desc=f"Epoch {epoch+1}/{epochs}")
        
        optimizer.zero_grad() 

        for step, batch in progress_bar:
            input_ids = batch['input_ids'].to(device)
            mask = batch['attention_mask'].to(device)
            targets = batch['targets'].to(device)

            with torch.autocast(device_type='cuda', dtype=torch.float16):
                logits = model(input_ids, mask)
                loss = criterion(logits, targets)
                loss = loss / accumulation_steps  

            scaler.scale(loss).backward()

            if (step + 1) % accumulation_steps == 0 or (step + 1) == len(dataloader):
                scaler.unscale_(optimizer)
                torch.nn.utils.clip_grad_norm_(model.parameters(), max_norm=1.0)
                
                scaler.step(optimizer)
                scaler.update()
                scheduler.step() 
                optimizer.zero_grad()

            total_loss += (loss.item() * accumulation_steps)
            progress_bar.set_postfix({'batch_loss': f"{(loss.item() * accumulation_steps):.4f}"})

        avg_loss = total_loss / len(dataloader)
        print(f"[*] Epoch {epoch+1} Complete. Average Loss: {avg_loss:.4f}")

    torch.save(model.state_dict(), "multilingual_toxicity_head1.pt")
    print("[*] Model weights saved to multilingual_toxicity_head1.pt")
    return model, tokenizer

def predict_toxicity(text, model, tokenizer, device):
    labels = ['toxic', 'severe_toxic', 'obscene', 'threat', 'insult', 'identity_hate']
    model.eval()
    encoding = tokenizer(
        text, truncation=True, padding='max_length', max_length=128, return_tensors="pt"
    ).to(device)

    with torch.no_grad():
        logits = model(encoding['input_ids'], encoding['attention_mask'])
        probabilities = torch.sigmoid(logits)[0].cpu().numpy()

    return {label: round(float(prob), 4) for label, prob in zip(labels, probabilities)}


if __name__ == "__main__":
    db = ActiveLearningDB()

    db.add_human_correction(
        text="तुम तो बिल्कुल पागल हो! (Bro, you are crazy!)",
        human_scores={'toxic': 0, 'severe_toxic': 0, 'obscene': 0, 'threat': 0, 'insult': 0, 'identity_hate': 0}
    )

    trained_model, tokenizer = train_multilingual_model(
        base_csv_path="FINAL_TRAIN.csv", 
        epochs=5, 
        sample_size=15000
    )

    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    sample_text = "I will track you down and hurt you"
    scores = predict_toxicity(sample_text, trained_model, tokenizer, device)
    print("\nTest Prediction Scores:")
    for category, prob in scores.items():
        print(f"  - {category}: {prob}")