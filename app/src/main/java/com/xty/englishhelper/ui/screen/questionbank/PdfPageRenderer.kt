package com.xty.englishhelper.ui.screen.questionbank

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import com.xty.englishhelper.domain.background.AppResourceCoordinator
import com.xty.englishhelper.domain.background.ForegroundResourceDemand
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min

object PdfPageRenderer {

    suspend fun renderPages(
        context: Context,
        uri: Uri,
        pageRange: IntRange? = null,
        dpi: Int = 200,
        transform: suspend (ByteArray) -> ByteArray = { it }
    ): List<ByteArray> {
        val rendered = AppResourceCoordinator.withResourceUsage(
            owner = "pdf_render",
            demand = ForegroundResourceDemand(memoryHeavy = 1, cpuHeavy = 1)
        ) {
            withContext(Dispatchers.IO) {
                val fd = context.contentResolver.openFileDescriptor(uri, "r")
                    ?: throw IllegalArgumentException("Cannot open PDF")

                fd.use { descriptor ->
                    val renderer = PdfRenderer(descriptor)
                    renderer.use { pdf ->
                        val requestedPages = (pageRange ?: (0 until pdf.pageCount))
                            .asSequence()
                            .filter { it in 0 until pdf.pageCount }
                            .toList()
                        require(requestedPages.size <= MAX_PAGES) { "PDF 页数过多，最多支持 $MAX_PAGES 页" }

                        buildList {
                            requestedPages.forEach { pageIndex ->
                                val page = pdf.openPage(pageIndex)
                                page.use { p ->
                                    val requestedScale = dpi.coerceIn(MIN_DPI, MAX_DPI) / 72f
                                    val dimensionScale = MAX_RENDER_LONG_EDGE.toFloat() / max(p.width, p.height)
                                    val scaleFactor = min(requestedScale, dimensionScale)
                                    val width = (p.width * scaleFactor).toInt().coerceAtLeast(1)
                                    val height = (p.height * scaleFactor).toInt().coerceAtLeast(1)
                                    require(width.toLong() * height <= MAX_RENDER_PIXELS) { "PDF 页面尺寸过大" }
                                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
                                    try {
                                        p.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                        val stream = ByteArrayOutputStream()
                                        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
                                        add(stream.toByteArray())
                                    } finally {
                                        bitmap.recycle()
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return buildList(rendered.size) {
            for (pageBytes in rendered) add(transform(pageBytes))
        }
    }

    private const val MAX_PAGES = 40
    private const val MIN_DPI = 96
    private const val MAX_DPI = 240
    private const val MAX_RENDER_LONG_EDGE = 3200
    private const val MAX_RENDER_PIXELS = 12_000_000L
}
