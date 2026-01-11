package com.example.expensemanagementapp.utils

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.example.expensemanagementapp.data.entity.TransactionEntity
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ExportUtils {

    fun exportAndShare(context: Context, transactions: List<TransactionEntity>, isCsv: Boolean) {
        try {
            val file = if (isCsv) createCsvFile(context, transactions) else createPdfFile(context, transactions)
            shareFile(context, file, if (isCsv) "text/csv" else "application/pdf")
        } catch (e: Exception) {
            e.printStackTrace()
            android.widget.Toast.makeText(context, "Export for share failed: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    fun exportAndSave(context: Context, transactions: List<TransactionEntity>, isCsv: Boolean) {
        try {
            val tempFile = if (isCsv) createCsvFile(context, transactions) else createPdfFile(context, transactions)
            val fileName = "finance_export_${getTimestamp()}.${if (isCsv) "csv" else "pdf"}"
            val mimeType = if (isCsv) "text/csv" else "application/pdf"
            
            saveToDownloads(context, tempFile, fileName, mimeType)
            android.widget.Toast.makeText(context, context.getString(com.example.expensemanagementapp.R.string.saved_to_downloads, fileName), android.widget.Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            e.printStackTrace()
             android.widget.Toast.makeText(context, context.getString(com.example.expensemanagementapp.R.string.save_failed, e.message), android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun createCsvFile(context: Context, transactions: List<TransactionEntity>): File {
        val fileName = "temp_export.csv"
        val file = File(context.cacheDir, fileName)
        
        FileOutputStream(file).use { out ->
            val header = "Date,Title,Type,Amount,Mode,Category\n"
            out.write(header.toByteArray())
            
            val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            
            for (tx in transactions) {
                val date = dateFormat.format(Date(tx.timestamp))
                val type = tx.transaction_type
                val amount = "%.2f".format(tx.amount)
                val mode = tx.mode
                val category = tx.category ?: "Uncategorized"
                val title = tx.title.replace(",", " ") // Escape commas
                
                val line = "$date,$title,$type,$amount,$mode,$category\n"
                out.write(line.toByteArray())
            }
        }
        return file
    }

    private fun createPdfFile(context: Context, transactions: List<TransactionEntity>): File {
        val fileName = "temp_export.pdf"
        val file = File(context.cacheDir, fileName)
        
        val pdfDocument = PdfDocument()
        val paint = Paint()
        val titlePaint = Paint()
        val headerPaint = Paint()
        
        // Paint Setups
        titlePaint.textSize = 18f
        titlePaint.isFakeBoldText = true
        titlePaint.color = Color.BLACK
        
        headerPaint.textSize = 10f
        headerPaint.isFakeBoldText = true
        headerPaint.color = Color.DKGRAY

        paint.textSize = 10f
        paint.isFakeBoldText = false
        paint.color = Color.BLACK
        
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 at 72 DPI (approx)
        var page = pdfDocument.startPage(pageInfo)
        var canvas = page.canvas
        
        var y = 0f
        
        fun drawHeader(canvas: Canvas) {
             // Title
            val titleY = 40f
            if (y == 0f) y = titleY // First page offset
            
            // Only draw Main Title on first page? Or all? Let's do header on all pages for now
            // But usually title is only on first page.
        }

        // Draw Title only on first page
        canvas.drawText(context.getString(com.example.expensemanagementapp.R.string.financial_records_export), 20f, 40f, titlePaint)
        paint.textSize = 12f
        canvas.drawText("Date: ${SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())}", 20f, 60f, paint)
        paint.textSize = 10f
        
        val startY = 90f
        y = startY
        
        // Column X positions
        val xDate = 20f
        val xTitle = 100f
        val xType = 250f
        val xAmount = 350f
        val xMode = 450f

        fun drawColumnHeaders(c: Canvas, currentY: Float) {
            c.drawText(context.getString(com.example.expensemanagementapp.R.string.date), xDate, currentY, headerPaint)
            c.drawText(context.getString(com.example.expensemanagementapp.R.string.title), xTitle, currentY, headerPaint)
            c.drawText(context.getString(com.example.expensemanagementapp.R.string.type), xType, currentY, headerPaint)
            c.drawText(context.getString(com.example.expensemanagementapp.R.string.amount), xAmount, currentY, headerPaint)
            c.drawText(context.getString(com.example.expensemanagementapp.R.string.mode), xMode, currentY, headerPaint)
            c.drawLine(20f, currentY + 5f, 575f, currentY + 5f, headerPaint)
        }

        drawColumnHeaders(canvas, y)
        y += 20f

        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        
        for (i in transactions.indices) {
            val tx = transactions[i]
            
            // Check for page break
            if (y > 800f) {
                pdfDocument.finishPage(page)
                
                // Create new page
                // Note: Page numbers in builder are arbitrary, just ID.
                val newPageInfo = PdfDocument.PageInfo.Builder(595, 842, i + 2).create()
                page = pdfDocument.startPage(newPageInfo)
                canvas = page.canvas
                y = 40f // Reset Y
                
                drawColumnHeaders(canvas, y)
                y += 20f
            }
            
            canvas.drawText(dateFormat.format(Date(tx.timestamp)), xDate, y, paint)
            
            var title = tx.title
            if (title.length > 25) title = title.substring(0, 22) + "..."
            canvas.drawText(title, xTitle, y, paint)
            
            canvas.drawText(tx.transaction_type, xType, y, paint)
            canvas.drawText("%.2f".format(tx.amount), xAmount, y, paint)
            canvas.drawText(tx.mode, xMode, y, paint)
            
            y += 15f
        }
        
        pdfDocument.finishPage(page)
        
        FileOutputStream(file).use { out ->
            pdfDocument.writeTo(out)
        }
        pdfDocument.close()
        return file
    }

    private fun saveToDownloads(context: Context, sourceFile: File, fileName: String, mimeType: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            
            if (uri != null) {
                resolver.openOutputStream(uri).use { output ->
                    FileInputStream(sourceFile).use { input ->
                        input.copyTo(output!!)
                    }
                }
            } else {
                throw IOException("Failed to create MediaStore entry")
            }
        } else {
            // Legacy approach
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val destFile = File(downloadsDir, fileName)
            FileInputStream(sourceFile).use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
        }
    }

    private fun getTimestamp(): String {
        return SimpleDateFormat("yyyy_MM_dd_HHmmss", Locale.getDefault()).format(Date())
    }

    private fun shareFile(context: Context, file: File, mimeType: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        
        val chooser = Intent.createChooser(intent, context.getString(com.example.expensemanagementapp.R.string.share_records))
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) 
        context.startActivity(chooser)
    }
}
