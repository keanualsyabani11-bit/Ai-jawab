// language: Kotlin | path: app/src/main/java/com/jaccksallas/aiexam/FloatingViewManager.kt
// runtime: Android API 26+ | target: Android device

package com.jaccksallas.aiexam

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.view.*
import android.widget.*
import kotlinx.coroutines.*

class FloatingViewManager(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private lateinit var root: View
    private val ai = AIEngine()
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var isExpanded = true
    private var currentScore = 0
    private val kkm = 75
    private val maxScore = 100

    // State auto-scan
    private var autoScanJob: Job? = null
    private var screenHelper: ScreenCaptureHelper? = null

    private val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        StealthManager.getOverlayType(),
        StealthManager.getOverlayFlags(),
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.END
        x = 16
        y = 140
        alpha = 0.96f
    }

    // ─── Drag tracking ───────────────────────────────────────────────────────

    private var ix = 0; private var iy = 0
    private var tx = 0f; private var ty = 0f
    private var dragged = false

    fun show() {
        root = LayoutInflater.from(context).inflate(R.layout.layout_floating, null)
        attachDrag()
        bindUI()
        windowManager.addView(root, params)
    }

    fun setScreenHelper(helper: ScreenCaptureHelper) {
        screenHelper = helper
    }

    fun remove() {
        autoScanJob?.cancel()
        scope.cancel()
        screenHelper?.release()
        if (::root.isInitialized && root.isAttachedToWindow)
            windowManager.removeView(root)
    }

    // ─── Drag ────────────────────────────────────────────────────────────────

    private fun attachDrag() {
        root.findViewById<View>(R.id.dragHandle).setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    ix = params.x; iy = params.y
                    tx = e.rawX; ty = e.rawY; dragged = false; true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (e.rawX - tx).toInt(); val dy = (e.rawY - ty).toInt()
                    if (Math.abs(dx) > 4 || Math.abs(dy) > 4) dragged = true
                    params.x = ix + dx; params.y = iy + dy
                    windowManager.updateViewLayout(root, params); true
                }
                MotionEvent.ACTION_UP -> { if (!dragged) toggleExpand(); true }
                else -> false
            }
        }
    }

    // ─── UI ──────────────────────────────────────────────────────────────────

    private fun bindUI() {
        val etQuestion  = root.findViewById<EditText>(R.id.etQuestion)
        val btnSolve    = root.findViewById<TextView>(R.id.btnSolve)
        val btnFill     = root.findViewById<TextView>(R.id.btnFill)
        val btnScan     = root.findViewById<TextView>(R.id.btnScan)
        val btnScore    = root.findViewById<TextView>(R.id.btnScore)
        val tvAnswer    = root.findViewById<TextView>(R.id.tvAnswer)
        val tvStatus    = root.findViewById<TextView>(R.id.tvStatus)
        val tvScoreInfo = root.findViewById<TextView>(R.id.tvScoreInfo)
        val btnMin      = root.findViewById<TextView>(R.id.btnMinimize)
        val btnClose    = root.findViewById<TextView>(R.id.btnClose)

        updateScore(tvScoreInfo)

        // Jawab soal otomatis (dari input manual)
        btnSolve.setOnClickListener {
            val q = etQuestion.text.toString().trim()
            if (q.isEmpty()) { setStatus(tvStatus, "Ketik soal dulu"); return@setOnClickListener }
            setStatus(tvStatus, "AI memproses...")
            tvAnswer.text = ""
            scope.launch {
                val r = ai.solveRaw(q)
                tvAnswer.text = r.answer
                if (r.explanation.isNotEmpty()) setStatus(tvStatus, r.explanation)
                else setStatus(tvStatus, "Selesai")
            }
        }

        // Jawaban diisi AI — auto-type ke kolom aktif
        btnFill.setOnClickListener {
            val answer = tvAnswer.text.toString().trim()
            if (answer.isEmpty()) { setStatus(tvStatus, "Solve dulu"); return@setOnClickListener }

            val accessibility = ExamAccessibilityService.instance
            if (accessibility != null) {
                accessibility.typeAnswer(answer)
                setStatus(tvStatus, "Jawaban diisi ke kolom")
            } else {
                // Fallback: clipboard
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE)
                    as android.content.ClipboardManager
                cm.setPrimaryClip(android.content.ClipData.newPlainText("ans", answer))
                setStatus(tvStatus, "Di-copy ke clipboard (aktifkan Accessibility)")
            }
        }

        // Scan layar otomatis via OCR
        btnScan.setOnClickListener {
            if (screenHelper == null) {
                setStatus(tvStatus, "Aktifkan izin screen capture dulu")
                return@setOnClickListener
            }
            setStatus(tvStatus, "Scan layar...")
            screenHelper!!.captureAndOcr { ocrText ->
                scope.launch {
                    if (ocrText.isEmpty()) {
                        setStatus(tvStatus, "OCR kosong — coba lagi")
                        return@launch
                    }
                    val parsed = ai.parseOcrText(ocrText)
                    etQuestion.setText(parsed.questionText)
                    setStatus(tvStatus, "AI memproses soal...")
                    val r = ai.solve(parsed)
                    tvAnswer.text = r.answer
                    setStatus(tvStatus, if (r.explanation.isNotEmpty()) r.explanation else "Selesai")
                }
            }
        }

        // Auto nilai di atas KKM
        btnScore.setOnClickListener {
            currentScore = (currentScore + 10).coerceAtMost(maxScore)
            updateScore(tvScoreInfo)
            setStatus(tvStatus, ai.scoreAdvice(currentScore, maxScore, kkm))
        }

        btnMin.setOnClickListener { toggleExpand() }
        btnClose.setOnClickListener {
            context.stopService(Intent(context, FloatingService::class.java))
        }
    }

    private fun updateScore(tv: TextView) {
        val color = if (currentScore >= kkm) "#00D68F" else "#FF6B6B"
        tv.text = "Nilai: $currentScore  |  KKM $kkm"
        tv.setTextColor(android.graphics.Color.parseColor(color))
    }

    private fun setStatus(tv: TextView, msg: String) {
        tv.text = msg
    }

    private fun toggleExpand() {
        val panel = root.findViewById<View>(R.id.contentPanel)
        isExpanded = !isExpanded
        panel.visibility = if (isExpanded) View.VISIBLE else View.GONE
        root.findViewById<TextView>(R.id.btnMinimize).text = if (isExpanded) "—" else "+"
    }
}
