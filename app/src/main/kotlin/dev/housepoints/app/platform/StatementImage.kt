package dev.housepoints.app.platform

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import dev.housepoints.app.R
import dev.housepoints.app.ui.payday.StatementModel
import dev.housepoints.app.ui.theme.LightColors
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Draws a statement to a PNG for the share sheet (SPEC FR-42): the same content and tokens as the screen,
 * always in the light palette because it is printed and stuck on fridges.
 */
class StatementImage(private val context: Context) {
    suspend fun share(model: StatementModel) {
        val file = withContext(Dispatchers.Default) { render(model) }
        val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Share ${model.childName}'s statement").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun render(model: StatementModel): File {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val c = LightColors
        canvas.drawColor(c.ground.toArgb())
        val card = RectF(MARGIN, MARGIN, WIDTH - MARGIN, HEIGHT - MARGIN)
        canvas.drawRoundRect(card, RADIUS, RADIUS, fill(c.surface.toArgb()))
        canvas.drawRoundRect(card, RADIUS, RADIUS, stroke(c.rule.toArgb(), 3f))

        val bricolage = font(R.font.bricolage, 700)
        val atkinson = font(R.font.atkinson_next, 400)
        val atkinsonBold = font(R.font.atkinson_next, 700)
        val mono = font(R.font.atkinson_mono, 400)
        val monoBold = font(R.font.atkinson_mono, 600)
        val left = MARGIN + PAD
        val right = WIDTH - MARGIN - PAD
        var y = MARGIN + PAD + 70f

        // Avatar disc and title
        val disc = c.child(model.colorIndex).fill.toArgb()
        canvas.drawCircle(left + 48f, y - 24f, 48f, fill(disc))
        canvas.drawText(model.childName.take(1).uppercase(), left + 48f, y - 4f, text(bricolage, 56f, Color.WHITE, Paint.Align.CENTER))
        canvas.drawText("${model.childName}'s week", left + 124f, y - 20f, text(bricolage, 64f, c.ink.toArgb()))
        canvas.drawText(model.weekLabel, left + 124f, y + 30f, text(atkinson, 36f, c.inkMuted.toArgb()))
        y += 130f

        fun row(label: String, detail: String?, amount: String) {
            canvas.drawText(label, left, y, text(atkinson, 42f, c.ink.toArgb()))
            canvas.drawText(amount, right, y, text(mono, 42f, c.ink.toArgb(), Paint.Align.RIGHT))
            if (detail != null) canvas.drawText(detail, left, y + 44f, text(atkinson, 32f, c.inkMuted.toArgb()))
            y += if (detail != null) 100f else 76f
            canvas.drawLine(left, y - 34f, right, y - 34f, stroke(c.rule.toArgb(), 2f))
        }
        row("Started with", null, model.startedWith)
        row("Earned", model.earnedDetail, model.earned)
        row("Spent or taken away", model.spentDetail, model.spent)
        y += 20f

        // Interest stamp
        val stamp = RectF(left, y, right, y + 150f)
        canvas.drawRoundRect(stamp, 8f, 8f, stroke(c.interest.toArgb(), 8f))
        canvas.drawText("Interest", left + 32f, y + 64f, text(atkinsonBold, 44f, c.interest.toArgb()))
        canvas.drawText(model.interestDetail, left + 32f, y + 112f, text(atkinson, 32f, c.interest.toArgb()))
        canvas.drawText(model.interest, right - 32f, y + 96f, text(monoBold, 64f, c.interest.toArgb(), Paint.Align.RIGHT))
        y += 220f

        canvas.drawLine(left, y, right, y, stroke(c.ink.toArgb(), 3f))
        canvas.drawLine(left, y + 10f, right, y + 10f, stroke(c.ink.toArgb(), 3f))
        y += 120f
        canvas.drawText("Now in the account", left, y, text(atkinsonBold, 42f, c.ink.toArgb()))
        canvas.drawText(model.closing, right, y + 8f, text(bricolage, 120f, c.ink.toArgb(), Paint.Align.RIGHT))
        y += 70f

        val body = TextPaint(text(atkinson, 40f, c.ink.toArgb()))
        val layout = StaticLayout.Builder.obtain(model.explanation, 0, model.explanation.length, body, (right - left).toInt())
            .setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(14f, 1f).build()
        canvas.save()
        canvas.translate(left, y)
        layout.draw(canvas)
        canvas.restore()

        canvas.drawText("House Points · paid ${model.paydayLabel}", left, HEIGHT - MARGIN - 50f, text(atkinson, 30f, c.inkMuted.toArgb()))

        val directory = File(context.cacheDir, "shared").apply { mkdirs() }
        val file = File(directory, "statement-${model.childName.filter { it.isLetterOrDigit() }}.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, it) }
        bitmap.recycle()
        return file
    }

    private fun font(resource: Int, weight: Int): Typeface {
        val base = ResourcesCompat.getFont(context, resource) ?: Typeface.DEFAULT
        return Typeface.create(base, weight, false)
    }

    private fun fill(colour: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colour; style = Paint.Style.FILL }
    private fun stroke(colour: Int, width: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colour; style = Paint.Style.STROKE; strokeWidth = width }
    private fun text(face: Typeface, size: Float, colour: Int, align: Paint.Align = Paint.Align.LEFT) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = face; textSize = size; color = colour; textAlign = align }

    private companion object {
        const val WIDTH = 1080
        const val HEIGHT = 1500
        const val MARGIN = 48f
        const val PAD = 64f
        const val RADIUS = 36f
        const val PNG_QUALITY = 100
    }
}
