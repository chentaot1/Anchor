package com.anchor.adhd.desktop.blocker

import java.util.concurrent.ConcurrentHashMap

enum class ScopeVerdict {
    IN_SCOPE,
    OUT_OF_SCOPE,
    UNKNOWN,
}

data class ScopeEvaluationResult(
    val verdict: ScopeVerdict,
    val category: String,
    val reason: String,
    val source: String, // "HEURISTIC", "LOCAL_AI", "CACHE", "USER"
)

/**
 * AI & Heuristic Focus Scope Sentinel.
 *
 * Monitors active browser tabs and application windows during focus sessions to detect
 * out-of-scope intellectual rabbit holes and distractions.
 *
 * Configured Focus Boundaries:
 * - IN-SCOPE: Psychology, Neurology, Neuroscience, Biology, Cognitive Science, Physiology,
 *   Academic Portals (Canvas, Blackboard, JSTOR, PubMed, Docs, Drive, Overleaf).
 * - OUT-OF-SCOPE:
 *   1. Philosophy & Ethics (Epistemology, Metaphysics, Kant, Nietzsche, Descartes, etc.).
 *   2. Computer Science, AI, Machine Learning, Deep Learning, Coding, Programming,
 *      Software Development, Tech blogs, GitHub, LeetCode, ArXiv CS.
 *   3. Hard sciences beyond psychology/neuro/bio (Physics, Pure Math, Engineering, Aerospace, Quantum).
 *   4. General entertainment, gaming, social media, shopping.
 *
 * Distraction Tax:
 * - Lingering on out-of-scope windows deducts 1 minute per minute from the user's gained leisure pool.
 */
object DesktopScopeSentinel {

    // 1. In-scope allowed disciplines (Psychology, Neurology, Biology, core academic tools)
    val IN_SCOPE_KEYWORDS = listOf(
        "psychology", "psychiatry", "psych", "neurology", "neuroscience", "cortex", "hippocampus",
        "dopamine", "serotonin", "synapse", "neuro", "biology", "cellular", "molecular", "genetics",
        "evolution", "dna", "rna", "organism", "physiology", "anatomy", "immunology", "endocrinology",
        "neuroanatomy", "cognitive science", "cognition", "behavioral science", "clinical psychology",
        "abnormal psychology", "developmental psychology", "social psychology", "psychopathology",
        "canvas", "blackboard", "moodle", "instructure", "jstor", "pubmed", "ncbi", "google docs",
        "google drive", "google classroom", "overleaf", "zotero", "mendeley", "quizlet",
        "library", "university", "assignment", "syllabus", "lecture notes", "reading list",
    )

    // 2. Out-of-scope: Philosophy & Ethics rabbit holes
    val PHILOSOPHY_KEYWORDS = listOf(
        "philosophy", "epistemology", "ethics", "metaphysics", "kant", "nietzsche", "plato", "aristotle",
        "descartes", "locke", "hume", "spinoza", "existentialism", "moral philosophy", "philosophy of mind",
        "stoicism", "utilitarianism", "nihilism", "phenomenology", "philosophical", "philpapers",
        "stanford encyclopedia of philosophy", "stanford encyclopedia", "plato.stanford.edu", "stanford plato",
        "iep.utm.edu", "internet encyclopedia of philosophy",
    )

    // 3. Out-of-scope: Computer Science & AI / ML / Tech / AI Benchmarks
    val CS_AND_AI_KEYWORDS = listOf(
        // Core disciplines
        "computer science", "machine learning", "deep learning", "artificial intelligence",
        "neural network", "transformer model", "diffusion model", "large language model", "llm", "slm",

        // AI Benchmarks, Leaderboards & Evals
        "chatbot arena", "lmsys", "arena.lmsys", "chat.lmsys", "lm arena", "arena leaderboard",
        "artificial analysis", "artificialanalysis", "artificialanalysis.ai",
        "openrouter", "openrouter.ai",
        "papers with code", "paperswithcode",
        "open llm leaderboard", "llm leaderboard", "llm-leaderboard", "swe-bench", "swebench",
        "livebench", "human-eval", "humaneval", "gsm8k", "mmlu", "arc challenge", "arc-c",
        "leaderboard", "leaderboards", "benchmark", "benchmarks", "benchmarking", "evals", "evaluations",
        "weights & biases", "wandb", "wandb.ai",
        "kaggle", "kaggle.com",
        "epoch ai", "epochai",
        "scale seal", "seal leaderboard",
        "vellum.ai", "vellum", "aider leaderboard",

        // AI Inference, Hubs, Frameworks & Cloud
        "hugging face", "huggingface", "huggingface.co", "hf.co",
        "pytorch", "tensorflow", "keras", "scikit-learn", "lora", "qlora",
        "ollama", "ollama.com", "vllm", "vllm.ai", "unsloth", "unsloth.ai",
        "runpod", "runpod.io", "lambdalabs", "lambda cloud", "replicate.com", "replicate",
        "groq", "groq.com", "together ai", "together.ai", "deepinfra",
        "mistral.ai", "mistral", "cohere", "eleutherai", "eleuther.ai",
        "google ai studio", "aistudio.google", "vertex ai",
        "chatgpt", "openai", "openai platform", "platform.openai.com",
        "claude.ai", "anthropic", "console.anthropic.com",
        "deepseek", "deepseek.com", "perplexity.ai", "perplexity",
        "gguf", "safetensors", "quantization", "fine-tuning", "finetuning",
        "prompt engineering", "agentic", "ai agent", "autogen", "crewai",
        "langchain", "langgraph", "llamaindex", "chromadb", "pinecone", "qdrant",

        // Code Hosting, Version Control & Dev Ecosystem
        "github", "github.com", "gist.github.com", "raw.githubusercontent",
        "gitlab", "gitlab.com", "bitbucket", "codeberg",
        "leetcode", "hackerrank", "codewars", "codeforces",
        "stackoverflow", "stack overflow", "stack exchange",
        "arxiv", "arxiv.org",
        "programming", "software engineering", "coding tutorial", "algorithm", "data structures",
        "python", "kotlin", "java", "c++", "rust-lang", "golang", "javascript", "typescript",
        "html/css", "sql database", "linux kernel", "cuda", "nvidia gpu", "geforce rtx",
        "docker", "dockerfile", "kubernetes", "k8s", "pypi", "npmjs", "crates.io",
        "visual studio code", "vscode", "intellij", "pycharm", "cursor ide", "copilot", "windsurf",
        "hackernews", "y combinator", "news.ycombinator.com", "techcrunch", "tom's hardware", "anandtech", "w3schools",
    )

    // 4. Out-of-scope: Hard sciences beyond psychology & biology (Physics, Math, Engineering)
    val HARD_SCIENCES_KEYWORDS = listOf(
        "quantum physics", "quantum mechanics", "general relativity", "special relativity",
        "particle physics", "string theory", "astrophysics", "cosmology", "thermodynamics",
        "electromagnetism", "aerospace engineering", "mechanical engineering", "electrical engineering",
        "civil engineering", "robotics engineering", "semiconductor", "microprocessor",
        "abstract algebra", "topology", "differential geometry", "pure mathematics", "cryptography",
    )

    // 5. Out-of-scope: General entertainment, gaming, social media, shopping
    val GENERAL_DISTRACTION_KEYWORDS = listOf(
        "twitch", "steam", "steampowered", "ign.com", "epicgames", "netflix",
        "animepahe", "crunchyroll", "manga", "amazon", "ebay", "bestbuy",
        "tiktok", "instagram", "discord",
    )

    // Thread-safe in-memory cache to guarantee 0ms overhead on repeat tabs
    private val classificationCache = ConcurrentHashMap<String, ScopeEvaluationResult>()

    fun clearCache() {
        classificationCache.clear()
    }

    /**
     * Fast Heuristic Classifier (runs in < 0.1ms, 0% CPU).
     */
    fun evaluateHeuristics(windowTitle: String, activeTaskTitle: String = ""): ScopeEvaluationResult {
        val titleLower = windowTitle.lowercase().trim()
        if (titleLower.isBlank()) {
            return ScopeEvaluationResult(ScopeVerdict.IN_SCOPE, "Workspace", "Blank window title", "HEURISTIC")
        }

        // Check cache first
        val cached = classificationCache[titleLower]
        if (cached != null) return cached

        // 1. Check if window title contains Philosophy triggers (explicitly out-of-scope)
        val philMatch = PHILOSOPHY_KEYWORDS.firstOrNull { kw -> titleLower.contains(kw) }
        if (philMatch != null) {
            val res = ScopeEvaluationResult(
                verdict = ScopeVerdict.OUT_OF_SCOPE,
                category = "Philosophy",
                reason = "Out-of-Scope Topic (Philosophy: '$philMatch')",
                source = "HEURISTIC",
            )
            classificationCache[titleLower] = res
            return res
        }

        // 2. Check if window title explicitly contains Computer Science / AI triggers
        val csMatch = CS_AND_AI_KEYWORDS.firstOrNull { kw -> titleLower.contains(kw) }
        if (csMatch != null) {
            // Check if it's bioinformatics or neurobiology overlap
            val isBioOverlap = IN_SCOPE_KEYWORDS.any { bio -> titleLower.contains(bio) }
            if (!isBioOverlap) {
                val res = ScopeEvaluationResult(
                    verdict = ScopeVerdict.OUT_OF_SCOPE,
                    category = "Computer Science / AI",
                    reason = "Computer Science / AI / Tech: '$csMatch'",
                    source = "HEURISTIC",
                )
                classificationCache[titleLower] = res
                return res
            }
        }

        // 3. Check if window title contains Hard Sciences beyond psychology/bio
        val hardSciMatch = HARD_SCIENCES_KEYWORDS.firstOrNull { kw -> titleLower.contains(kw) }
        if (hardSciMatch != null) {
            val res = ScopeEvaluationResult(
                verdict = ScopeVerdict.OUT_OF_SCOPE,
                category = "Hard Science (Physics / Eng / Math)",
                reason = "Hard Science beyond Psych/Bio: '$hardSciMatch'",
                source = "HEURISTIC",
            )
            classificationCache[titleLower] = res
            return res
        }

        // 4. Check general distractions (Gaming, Streams, Shopping, etc.)
        val distMatch = GENERAL_DISTRACTION_KEYWORDS.firstOrNull { kw -> titleLower.contains(kw) }
        if (distMatch != null) {
            val res = ScopeEvaluationResult(
                verdict = ScopeVerdict.OUT_OF_SCOPE,
                category = "Entertainment / Shopping",
                reason = "Entertainment / Shopping: '$distMatch'",
                source = "HEURISTIC",
            )
            classificationCache[titleLower] = res
            return res
        }

        // 5. Check if title matches psychology, neurology, biology, or academic tools
        val psychMatch = IN_SCOPE_KEYWORDS.firstOrNull { kw -> titleLower.contains(kw) }
        if (psychMatch != null) {
            val res = ScopeEvaluationResult(
                verdict = ScopeVerdict.IN_SCOPE,
                category = "Psychology / Biology",
                reason = "Matches core subject: '$psychMatch'",
                source = "HEURISTIC",
            )
            classificationCache[titleLower] = res
            return res
        }

        // 5. Check if title matches words from current task title
        if (activeTaskTitle.isNotBlank()) {
            val taskTokens = activeTaskTitle.lowercase().split("\\s+".toRegex()).filter { it.length > 3 }
            if (taskTokens.any { token -> titleLower.contains(token) }) {
                val res = ScopeEvaluationResult(
                    verdict = ScopeVerdict.IN_SCOPE,
                    category = "Current Task",
                    reason = "Matches current task title",
                    source = "HEURISTIC",
                )
                classificationCache[titleLower] = res
                return res
            }
        }

        return ScopeEvaluationResult(
            verdict = ScopeVerdict.UNKNOWN,
            category = "Unknown",
            reason = "Needs local AI evaluation",
            source = "HEURISTIC",
        )
    }

    fun recordManualClassification(windowTitle: String, verdict: ScopeVerdict, reason: String = "User marked") {
        classificationCache[windowTitle.lowercase().trim()] = ScopeEvaluationResult(
            verdict = verdict,
            category = if (verdict == ScopeVerdict.IN_SCOPE) "User Allowed" else "User Blocked",
            reason = reason,
            source = "USER",
        )
    }

    fun getCachedVerdict(windowTitle: String): ScopeEvaluationResult? {
        return classificationCache[windowTitle.lowercase().trim()]
    }
}
