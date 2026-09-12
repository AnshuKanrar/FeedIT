# empty for now, real file (is_blocked) added next step
"""
User-Curated Blocking Module
=============================
Handles: "I don't want to see Ravi Kishan" / "I don't want to see cricket"

Two layers, checked in order (cheapest first):
  1. Exact + fuzzy name/keyword matching (Levenshtein distance - DSA/DP)
  2. TF-IDF cosine similarity against the SAME vectorizer used by the
     toxicity model (no new model, reuses what's already trained)

This runs BEFORE the toxicity/mood pipeline in feed_logic.py. If a post
is blocked here, you never even need to call the ML model on it.
"""

from sklearn.metrics.pairwise import cosine_similarity


# ---------------------------------------------------------------------------
# LAYER 1: EXACT MATCH
# ---------------------------------------------------------------------------

def contains_blocked_term(post_text: str, blocked_terms: list) -> bool:
    """Simple case-insensitive substring check. Catches most real cases."""
    text_lower = post_text.lower()
    return any(term.lower() in text_lower for term in blocked_terms)


# ---------------------------------------------------------------------------
# LAYER 2: FUZZY MATCH (Levenshtein distance - dynamic programming)
#   Catches misspellings / minor variations, e.g. "Ravi Kishan" vs
#   "Ravi Kishen" vs "ravikishan".
# ---------------------------------------------------------------------------

def levenshtein_distance(s1: str, s2: str) -> int:
    m, n = len(s1), len(s2)
    dp = [[0] * (n + 1) for _ in range(m + 1)]
    for i in range(m + 1):
        dp[i][0] = i
    for j in range(n + 1):
        dp[0][j] = j
    for i in range(1, m + 1):
        for j in range(1, n + 1):
            cost = 0 if s1[i - 1] == s2[j - 1] else 1
            dp[i][j] = min(
                dp[i - 1][j] + 1,      # deletion
                dp[i][j - 1] + 1,      # insertion
                dp[i - 1][j - 1] + cost,  # substitution
            )
    return dp[m][n]


def fuzzy_contains_term(post_text: str, blocked_term: str, max_distance: int = 2) -> bool:
    """Slide a window the size of the blocked term across the post's words."""
    words = post_text.lower().split()
    term_words = blocked_term.lower().split()
    n = len(term_words)
    if n == 0 or len(words) < n:
        return False
    for i in range(len(words) - n + 1):
        phrase = " ".join(words[i:i + n])
        if levenshtein_distance(phrase, blocked_term.lower()) <= max_distance:
            return True
    return False


# ---------------------------------------------------------------------------
# LAYER 3: TF-IDF COSINE SIMILARITY
#   Catches content that shares vocabulary with the blocked term even
#   without an exact/fuzzy name match, e.g. blocking "cricket" also
#   catches "IPL match was intense tonight".
#   Reuses the SAME vectorizer already trained for the toxicity model -
#   no second model needed.
# ---------------------------------------------------------------------------

def semantic_similarity(text_a: str, text_b: str, vectorizer) -> float:
    vecs = vectorizer.transform([text_a, text_b])
    return cosine_similarity(vecs[0], vecs[1])[0][0]


def is_similar_to_blocked(post_text: str, blocked_terms: list, vectorizer, threshold: float = 0.3) -> bool:
    return any(
        semantic_similarity(post_text, term, vectorizer) > threshold
        for term in blocked_terms
    )


# ---------------------------------------------------------------------------
# COMBINED CHECK - use this one function everywhere
# ---------------------------------------------------------------------------

def is_blocked(post_text: str, blocked_terms: list, vectorizer, fuzzy_distance: int = 2, similarity_threshold: float = 0.3) -> dict:
    """
    Returns a dict explaining WHY a post was blocked (or not), so the app
    can show the user a reason and the pitch can point to which layer fired.
    """
    if contains_blocked_term(post_text, blocked_terms):
        return {"blocked": True, "reason": "exact_match"}

    for term in blocked_terms:
        if fuzzy_contains_term(post_text, term, fuzzy_distance):
            return {"blocked": True, "reason": f"fuzzy_match:{term}"}

    for term in blocked_terms:
        sim = semantic_similarity(post_text, term, vectorizer)
        if sim > similarity_threshold:
            return {"blocked": True, "reason": f"similarity_match:{term}", "similarity": round(float(sim), 3)}

    return {"blocked": False, "reason": None}


# ---------------------------------------------------------------------------
# INTEGRATION with feed_logic.py's SessionTracker
#   Call this FIRST, before running the toxicity model at all.
# ---------------------------------------------------------------------------

def process_post_with_blocking(post_text: str, ml_scores: dict, blocked_terms: list, vectorizer, tracker) -> dict:
    """
    tracker = an instance of SessionTracker from feed_logic.py
    ml_scores = the 6-label dict from the ML model (still computed even
                if blocked, if you want tags/mood tracked; or skip the ML
                call entirely if blocked, to save compute - your choice)
    """
    block_result = is_blocked(post_text, blocked_terms, vectorizer)
    if block_result["blocked"]:
        return {
            "should_blur": True,
            "tags": ["user-blocked"],
            "reason": block_result["reason"],
            "wellbeing_score": None,
        }
    # not blocked by user preference -> fall through to toxicity/mood pipeline
    return tracker.process_post(ml_scores)


# ---------------------------------------------------------------------------
# DEMO / TEST
# ---------------------------------------------------------------------------

if __name__ == "__main__":
    from sklearn.feature_extraction.text import TfidfVectorizer

    # Small fake vectorizer just for this demo - in the real app, load the
    # SAME vectorizer.pkl your toxicity model already trained and saved.
    corpus = [
        "Ravi Kishan gave a great speech at the event today",
        "IPL cricket match tonight was intense and thrilling",
        "just had the best coffee of my life",
        "exam stress is really getting to me this week",
        "the exam pressure this semester is unbearable honestly",
        "the new Bhojpuri movie starring the famous actor released this week",
    ]
    demo_vectorizer = TfidfVectorizer().fit(corpus)

    blocked_terms = ["Ravi Kishan", "cricket", "exam stress"]

    test_posts = [
        "Ravi Kishan's new movie trailer just dropped",             # exact match
        "ravi kishen gave a speech yesterday",                       # fuzzy match (typo)
        "IPL cricket match tonight was intense and thrilling",       # exact match (contains 'cricket')
        "the exam pressure this semester is unbearable honestly",    # similarity match (shares 'exam' vocab, no exact phrase)
        "the new Bhojpuri movie starring the famous actor released this week",  # NOT blocked - zero shared vocab (honest limitation)
        "just had the best coffee of my life",                       # not blocked
    ]

    print("--- with default threshold 0.3 (nothing shares enough vocab yet) ---")
    print(f"{'Post':<70}{'Result'}")
    for post in test_posts:
        result = is_blocked(post, blocked_terms, demo_vectorizer)
        print(f"{post:<70}{result}")

    print("\n--- with a lower, tuned threshold 0.15 (realistic for a small vocab) ---")
    for post in test_posts:
        result = is_blocked(post, blocked_terms, demo_vectorizer, similarity_threshold=0.15)
        print(f"{post:<70}{result}")