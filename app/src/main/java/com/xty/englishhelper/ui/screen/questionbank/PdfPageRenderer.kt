package com.xty.englishhelper.ui.screen.questionbank

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

object PdfPageRenderer {

    suspend fun renderPages(
        context: Context,
        uri: Uri,
        pageRange: IntRange? = null,
        dpi: Int = 200
    ): List<ByteArray> = withContext(Dispatchers.IO) {
        val fd = context.contentResolver.openFileDescriptor(uri, "r")
            ?: throw IllegalArgumentException("Cannot open PDF")

        fd.use { descriptor ->
            val renderer = PdfRenderer(descriptor)
            renderer.use { pdf ->
                val range = pageRange ?: (0 until pdf.pageCount)
                val scaleFactor = dpi / 72f

                range.mapNotNull { pageIndex ->
                    if (pageIndex < 0 || pageIndex >= pdf.pageCount) return@mapNotNull null
                    val page = pdf.openPage(pageIndex)
                    page.use { p ->
                        val width = (p.width * scaleFactor).toInt()
                        val height = (p.height * scaleFactor).toInt()
                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        p.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        val stream = ByteArrayOutputStream()
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
                        bitmap.recycle()
                        stream.toByteArray()
                    }
                }
            }
        }
    }
}
