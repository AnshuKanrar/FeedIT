"""Request/response schemas for POST /classify."""

from typing import List, Optional

from pydantic import BaseModel


class PostIn(BaseModel):
    id: str
    text: str


class ClassifyRequest(BaseModel):
    user_id: str
    blocked_terms: List[str]
    posts: List[PostIn]


class ClassifyResult(BaseModel):
    postId: str
    should_blur: bool
    tags: List[str]
    wellbeing_score: Optional[float] = None
    reason: Optional[str] = None


class MoodHistoryPoint(BaseModel):
    t: float
    valence: float
    arousal: float


class MoodResponse(BaseModel):
    mood: str
    valence: float
    arousal: float
    confidence: float
    trend: str
    session_minutes: float
    history: List[MoodHistoryPoint]
