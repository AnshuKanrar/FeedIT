import os
import sqlite3
import pandas as pd
import torch
import torch.nn as nn
import torch.optim as optim
from torch.utils.data import Dataset, DataLoader
from torchvision import transforms, models
from PIL import Image
from torch.cuda.amp import GradScaler, autocast

DB_PATH = "cv_active_learning.db"

def init_db():
    """Creates a database to store dynamically flagged images from the UI."""
    conn = sqlite3.connect(DB_PATH)
    cursor = conn.cursor()
    cursor.execute('''
        CREATE TABLE IF NOT EXISTS dynamic_images (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            image_path TEXT UNIQUE,
            toxic INTEGER, severe_toxic INTEGER, obscene INTEGER, 
            threat INTEGER, insult INTEGER, identity_hate INTEGER
        )
    ''')
    conn.commit()
    conn.close()

def get_combined_dataset(base_csv="FINAL_IMAGE_TRAIN.csv", base_img_dir="dataset_images"):
    """Merges your static CSV with any newly flagged UI images in the SQLite DB."""
    
    df = pd.read_csv(base_csv)
    
   
    df['image_path'] = df['image_path'].apply(lambda x: os.path.join(base_img_dir, x))
    conn = sqlite3.connect(DB_PATH)
    dynamic_df = pd.read_sql_query("SELECT * FROM dynamic_images", conn)
    conn.close()
    
    if not dynamic_df.empty:
        dynamic_df = dynamic_df.drop(columns=['id'])
        df = pd.concat([df, dynamic_df], ignore_index=True)
        print(f"[*] Injected {len(dynamic_df)} dynamic UI images into training data.")
        
    return df
class CVMultiLabelDataset(Dataset):
    def __init__(self, dataframe):
        self.df = dataframe
        self.labels = ['toxic', 'severe_toxic', 'obscene', 'threat', 'insult', 'identity_hate']
        
        
        self.transform = transforms.Compose([
            transforms.Resize((224, 224)),
            transforms.ToTensor(),
            transforms.Normalize(mean=[0.485, 0.456, 0.406], std=[0.229, 0.224, 0.225])
        ])

    def __len__(self):
        return len(self.df)

    def __getitem__(self, idx):
        row = self.df.iloc[idx]
        img_path = row['image_path']
        
        try:
            image = Image.open(img_path).convert("RGB")
        except Exception as e:
            
            image = Image.new('RGB', (224, 224), color='black')
            
        image_tensor = self.transform(image)
        targets = torch.tensor(row[self.labels].values.astype(float), dtype=torch.float)
        
        return image_tensor, targets


def build_model(device):
    """Loads MobileNetV3_Large, freezes features, maps output to 6 scores."""
    print("[*] Downloading/Loading MobileNetV3_Large...")
    model = models.mobilenet_v3_large(weights=models.MobileNet_V3_Large_Weights.DEFAULT)
    
    
    for param in model.features.parameters():
        param.requires_grad = False
        
    
    in_features = model.classifier[3].in_features
    model.classifier[3] = nn.Linear(in_features, 6)
    
    return model.to(device)


def train_model(epochs=3, batch_size=32):
    init_db()
    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    print(f"[*] Training on device: {device}")
    
    
    df = get_combined_dataset()
    dataset = CVMultiLabelDataset(df)
    dataloader = DataLoader(dataset, batch_size=batch_size, shuffle=True, num_workers=0)
    
    model = build_model(device)
    
    
    criterion = nn.BCEWithLogitsLoss()
    
    optimizer = optim.AdamW(model.classifier.parameters(), lr=1e-3)
    
    
    scaler = GradScaler()
    
    print(f"[*] Starting Training for {epochs} epochs on {len(dataset)} images...")
    
    model.train()
    for epoch in range(epochs):
        epoch_loss = 0
        
        for batch_idx, (images, targets) in enumerate(dataloader):
            images, targets = images.to(device), targets.to(device)
            
            optimizer.zero_grad()
            
            
            with autocast():
                outputs = model(images)
                loss = criterion(outputs, targets)
                
            scaler.scale(loss).backward()
            scaler.step(optimizer)
            scaler.update()
            
            epoch_loss += loss.item()
            
            if batch_idx % 10 == 0:
                print(f"  -> Epoch [{epoch+1}/{epochs}] | Batch {batch_idx}/{len(dataloader)} | Loss: {loss.item():.4f}")
                
        print(f"[*] Epoch {epoch+1} Complete | Average Loss: {epoch_loss/len(dataloader):.4f}\n")
        
    
    torch.save(model.state_dict(), "cv_toxicity_model.pth")
    print("[*] SUCCESS! Model saved to 'cv_toxicity_model.pth'")

if __name__ == "__main__":
    train_model(epochs=3)