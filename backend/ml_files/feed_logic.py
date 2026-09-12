import statistics

HARM_WEIGHTS = {"toxic": 0.30, "obscene": 0.15, "threat": 0.40, "insult": 0.15}

def weighted_harm_score(scores: dict) -> float:
    return sum(scores[label] * HARM_WEIGHTS[label] for label in HARM_WEIGHTS)

def should_blur(scores: dict, threshold: float = 0.5) -> bool:
    return weighted_harm_score(scores) > threshold

TAG_THRESHOLD = 0.5

def get_tags(scores: dict, threshold: float = TAG_THRESHOLD) -> list:
    return [label for label, value in scores.items() if value > threshold]

def wellbeing_score(scores: dict) -> float:
    bad = weighted_harm_score(scores)
    good = (scores["joy"] + (1 - scores["sadness"])) / 2
    return round((good - bad) * 100, 2)

def update_ema(previous_ema: float, new_value: float, alpha: float = 0.3) -> float:
    return alpha * new_value + (1 - alpha) * previous_ema

def trend_slope(scores: list) -> float:
    n = len(scores)
    if n < 2:
        return 0.0
    x = list(range(n))
    x_mean, y_mean = sum(x) / n, sum(scores) / n
    num = sum((x[i]-x_mean)*(scores[i]-y_mean) for i in range(n))
    den = sum((x[i]-x_mean)**2 for i in range(n))
    return num / den if den != 0 else 0.0

def mood_label_from_slope(slope: float, flat_band: float = 1.0) -> str:
    if slope > flat_band: return "improving"
    if slope < -flat_band: return "declining"
    return "stable"

def mood_volatility(scores: list) -> float:
    return round(statistics.variance(scores), 2) if len(scores) > 1 else 0.0

class SessionTracker:
    def __init__(self, blur_threshold: float = 0.5, ema_alpha: float = 0.3):
        self.blur_threshold = blur_threshold
        self.ema_alpha = ema_alpha
        self.wellbeing_history = []
        self.ema = 50.0

    def process_post(self, scores: dict) -> dict:
        harm = weighted_harm_score(scores)
        blur = harm > self.blur_threshold
        tags = get_tags(scores)
        w_score = wellbeing_score(scores)
        self.wellbeing_history.append(w_score)
        self.ema = update_ema(self.ema, w_score, self.ema_alpha)
        slope = trend_slope(self.wellbeing_history)
        return {
            "should_blur": blur, "tags": tags, "wellbeing_score": w_score,
            "session_ema": round(self.ema, 2),
            "session_trend": mood_label_from_slope(slope),
            "session_trend_slope": round(slope, 3),
            "session_volatility": mood_volatility(self.wellbeing_history),
        }