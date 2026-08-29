"""§12.1 - Résumé : "Produire un résumé court d'une demande longue et de ses derniers
échanges." §12.2 - "Point de vigilance : aucune donnée réelle confidentielle ne doit être
envoyée vers un service externe... un modèle local sera privilégié". Extractive, frequency-
based, pure Python (no model download, no network call, no GPU) : it selects the most
representative existing sentences rather than generating new text, which also keeps §12.3's
"absence d'information inventée" criterion true by construction - nothing here can
hallucinate a fact the source text never stated.
"""
import re

_SENTENCE_SPLIT_RE = re.compile(r"(?<=[.!?])\s+")
_WORD_RE = re.compile(r"[a-zàâäéèêëïîôöùûüç]+", re.IGNORECASE)

# Mots vides français les plus fréquents - exclus du score de fréquence pour qu'il reflète
# le contenu, pas la grammaire.
_STOPWORDS = {
    "le", "la", "les", "un", "une", "des", "de", "du", "et", "ou", "à", "au", "aux",
    "ce", "cet", "cette", "ces", "il", "elle", "ils", "elles", "je", "tu", "nous", "vous",
    "que", "qui", "quoi", "dont", "où", "est", "sont", "a", "ai", "as", "avons", "avez",
    "ont", "être", "avoir", "pour", "par", "sur", "dans", "en", "avec", "sans", "plus",
    "pas", "ne", "se", "sa", "son", "ses", "leur", "leurs", "mon", "ma", "mes", "ton",
    "ta", "tes", "notre", "nos", "votre", "vos", "y", "on", "si", "mais", "donc", "car",
}


def summarize(text: str, max_sentences: int = 3) -> str:
    """Returns the max_sentences most representative sentences from text, in their
    original order (a summary that reorders source sentences reads as a new claim, not an
    extract - RG-10/§12.3 both call for an aid a human can trust at a glance)."""
    if not text or not text.strip():
        return ""

    sentences = [s.strip() for s in _SENTENCE_SPLIT_RE.split(text.strip()) if s.strip()]
    if len(sentences) <= max_sentences:
        return " ".join(sentences)

    word_frequencies = _word_frequencies(text)
    scored = [(index, _score_sentence(sentence, word_frequencies)) for index, sentence in enumerate(sentences)]
    top_indices = sorted(index for index, _ in sorted(scored, key=lambda pair: pair[1], reverse=True)[:max_sentences])
    return " ".join(sentences[i] for i in top_indices)


def _word_frequencies(text: str) -> dict:
    words = [w.lower() for w in _WORD_RE.findall(text) if w.lower() not in _STOPWORDS and len(w) > 2]
    frequencies: dict = {}
    for word in words:
        frequencies[word] = frequencies.get(word, 0) + 1
    max_frequency = max(frequencies.values(), default=1)
    return {word: count / max_frequency for word, count in frequencies.items()}


def _score_sentence(sentence: str, word_frequencies: dict) -> float:
    words = [w.lower() for w in _WORD_RE.findall(sentence)]
    if not words:
        return 0.0
    return sum(word_frequencies.get(w, 0.0) for w in words) / len(words)
