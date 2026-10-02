package com.anchor.adhd.desktop.platform

import com.sun.jna.Platform
import org.apache.pdfbox.Loader
import org.apache.pdfbox.rendering.PDFRenderer
import org.apache.pdfbox.text.PDFTextStripper
import java.io.File
import java.nio.file.Files
import java.util.UUID
import javax.imageio.ImageIO

/**
 * Windows 10/11 Native Offline OCR Engine Integration.
 *
 * For image-only and scanned PDFs (which contain zero digital text glyphs in PDFTextStripper),
 * this helper renders pages via Apache PDFBox to raster images and invokes the built-in
 * Windows.Media.Ocr.OcrEngine with spatial word-ordering to extract full course schedules,
 * tables, and dates without external dependencies or cloud uploads.
 */
object DesktopWindowsOcrHelper {
    private const val OCR_BATCH_SCRIPT = """
param (
    [string]${'$'}ManifestPath
)

try {
    Add-Type -AssemblyName 'System.Runtime.WindowsRuntime'
    [Windows.Media.Ocr.OcrEngine, Windows.Foundation.UniversalApiContract, ContentType = WindowsRuntime] | Out-Null
    [Windows.Graphics.Imaging.BitmapDecoder, Windows.Foundation.UniversalApiContract, ContentType = WindowsRuntime] | Out-Null
    [Windows.Storage.StorageFile, Windows.Foundation.UniversalApiContract, ContentType = WindowsRuntime] | Out-Null

    ${'$'}asTaskGeneric = [System.WindowsRuntimeSystemExtensions].GetMethods() | 
        Where-Object { ${'$'}_.Name -eq 'AsTask' -and ${'$'}_.GetParameters().Count -eq 1 -and ${'$'}_.IsGenericMethod } | 
        Select-Object -First 1

    function Await(${'$'}asyncOp, ${'$'}resultType) {
        ${'$'}method = ${'$'}asTaskGeneric.MakeGenericMethod(${'$'}resultType)
        ${'$'}task = ${'$'}method.Invoke(${'$'}null, @(${'$'}asyncOp))
        return ${'$'}task.GetAwaiter().GetResult()
    }

    ${'$'}engine = [Windows.Media.Ocr.OcrEngine]::TryCreateFromUserProfileLanguages()
    if (${'$'}null -eq ${'$'}engine) {
        ${'$'}lang = [Windows.Globalization.Language]::new("en-US")
        ${'$'}engine = [Windows.Media.Ocr.OcrEngine]::TryCreateFromLanguage(${'$'}lang)
    }

    if (${'$'}null -eq ${'$'}engine) {
        Write-Error "Windows OCR Engine is unavailable on this system."
        exit 1
    }

    ${'$'}imagePaths = Get-Content -Path ${'$'}ManifestPath
    ${'$'}allPagesText = @()

    foreach (${'$'}imgPath in ${'$'}imagePaths) {
        ${'$'}trimmedPath = ${'$'}imgPath.Trim()
        if ([string]::IsNullOrWhiteSpace(${'$'}trimmedPath) -or -not (Test-Path ${'$'}trimmedPath)) { continue }

        ${'$'}file = Await ([Windows.Storage.StorageFile]::GetFileFromPathAsync(${'$'}trimmedPath)) ([Windows.Storage.StorageFile])
        ${'$'}stream = Await (${'$'}file.OpenAsync([Windows.Storage.FileAccessMode]::Read)) ([Windows.Storage.Streams.IRandomAccessStream])
        ${'$'}decoder = Await ([Windows.Graphics.Imaging.BitmapDecoder]::CreateAsync(${'$'}stream)) ([Windows.Graphics.Imaging.BitmapDecoder])
        ${'$'}bitmap = Await (${'$'}decoder.GetSoftwareBitmapAsync()) ([Windows.Graphics.Imaging.SoftwareBitmap])
        ${'$'}result = Await (${'$'}engine.RecognizeAsync(${'$'}bitmap)) ([Windows.Media.Ocr.OcrResult])

        ${'$'}allWords = @()
        foreach (${'$'}line in ${'$'}result.Lines) {
            foreach (${'$'}word in ${'$'}line.Words) {
                ${'$'}allWords += [PSCustomObject]@{
                    Text = ${'$'}word.Text
                    X = ${'$'}word.BoundingRect.X
                    Y = ${'$'}word.BoundingRect.Y
                }
            }
        }

        ${'$'}allWords = ${'$'}allWords | Sort-Object Y, X
        ${'$'}rows = @()
        ${'$'}currentRow = @()
        ${'$'}currentY = -999

        foreach (${'$'}w in ${'$'}allWords) {
            if (${'$'}currentRow.Count -eq 0 -or [Math]::Abs(${'$'}w.Y - ${'$'}currentY) -le 14) {
                ${'$'}currentRow += ${'$'}w
                if (${'$'}currentRow.Count -eq 1) { ${'$'}currentY = ${'$'}w.Y }
            } else {
                ${'$'}sortedRow = ${'$'}currentRow | Sort-Object X
                ${'$'}rows += (${'$'}sortedRow | ForEach-Object { ${'$'}_.Text }) -join " "
                ${'$'}currentRow = @(${'$'}w)
                ${'$'}currentY = ${'$'}w.Y
            }
        }
        if (${'$'}currentRow.Count -gt 0) {
            ${'$'}sortedRow = ${'$'}currentRow | Sort-Object X
            ${'$'}rows += (${'$'}sortedRow | ForEach-Object { ${'$'}_.Text }) -join " "
        }

        ${'$'}allPagesText += (${'$'}rows -join "`n")
    }

    Write-Output (${'$'}allPagesText -join "`n--- PAGE BREAK ---`n")
} catch {
    Write-Error ${'$'}_.Exception.ToString()
    exit 2
}
"""

    /**
     * Extracts text from a PDF file. If the PDF lacks digital selectable text (scanned / image PDF),
     * automatically runs offline Windows.Media.Ocr.OcrEngine.
     */
    fun extractTextWithOcrFallback(file: File): String {
        if (!file.exists()) return "File not found: ${file.absolutePath}"

        // 1. Try standard digital text stripping first
        var rawText = ""
        try {
            val doc = Loader.loadPDF(file)
            try {
                val stripper = PDFTextStripper()
                rawText = stripper.getText(doc)
            } finally {
                doc.close()
            }
        } catch (e: Exception) {
            System.err.println("DesktopWindowsOcrHelper: Error in PDFTextStripper: ${e.message}")
        }

        // If digital text is substantial (> 120 characters), return it immediately
        if (rawText.trim().length >= 120) {
            return rawText
        }

        // 2. Fallback to Windows Native OCR for scanned/image PDFs
        if (!Platform.isWindows()) {
            return rawText.ifBlank { "Could not extract text: scanned/image PDFs require Windows 10/11 OCR or an OCR plugin." }
        }

        return runWindowsOcr(file).ifBlank { rawText }
    }

    private fun runWindowsOcr(file: File): String {
        val tempDir = Files.createTempDirectory("anchor_ocr_" + UUID.randomUUID().toString().take(8)).toFile()
        try {
            val doc = Loader.loadPDF(file)
            val imgPaths = mutableListOf<String>()
            try {
                val renderer = PDFRenderer(doc)
                val maxPages = doc.numberOfPages.coerceAtMost(25)
                for (pageIndex in 0 until maxPages) {
                    val img = renderer.renderImageWithDPI(pageIndex, 150f)
                    val imgFile = File(tempDir, "page_${pageIndex + 1}.png")
                    ImageIO.write(img, "PNG", imgFile)
                    imgPaths.add(imgFile.absolutePath)
                }
            } finally {
                doc.close()
            }

            if (imgPaths.isEmpty()) return ""

            val manifestFile = File(tempDir, "manifest.txt")
            manifestFile.writeText(imgPaths.joinToString("\n"))

            val scriptFile = File(tempDir, "ocr_batch.ps1")
            scriptFile.writeText(OCR_BATCH_SCRIPT)

            val cmd =
                listOf(
                    "powershell",
                    "-NoProfile",
                    "-ExecutionPolicy",
                    "Bypass",
                    "-File",
                    scriptFile.absolutePath,
                    "-ManifestPath",
                    manifestFile.absolutePath,
                )

            val proc =
                ProcessBuilder(cmd)
                    .redirectErrorStream(true)
                    .start()

            val ocrOutput = proc.inputStream.bufferedReader().readText()
            proc.waitFor()
            return ocrOutput
        } catch (e: Exception) {
            System.err.println("DesktopWindowsOcrHelper: Native OCR execution failed: ${e.message}")
            return ""
        } finally {
            tempDir.deleteRecursively()
        }
    }

    /**
     * Extracts text directly from an image file (.png, .jpg, .jpeg, .bmp, .webp) using Windows OCR.
     */
    fun extractTextFromImage(file: File): String {
        if (!file.exists()) return "File not found: ${file.absolutePath}"
        if (!Platform.isWindows()) return "Image OCR requires Windows 10/11."

        val tempDir = Files.createTempDirectory("anchor_ocr_img_" + UUID.randomUUID().toString().take(8)).toFile()
        try {
            val manifestFile = File(tempDir, "manifest.txt")
            manifestFile.writeText(file.absolutePath)

            val scriptFile = File(tempDir, "ocr_batch.ps1")
            scriptFile.writeText(OCR_BATCH_SCRIPT)

            val cmd =
                listOf(
                    "powershell",
                    "-NoProfile",
                    "-ExecutionPolicy",
                    "Bypass",
                    "-File",
                    scriptFile.absolutePath,
                    "-ManifestPath",
                    manifestFile.absolutePath,
                )

            val proc =
                ProcessBuilder(cmd)
                    .redirectErrorStream(true)
                    .start()

            val ocrOutput = proc.inputStream.bufferedReader().readText()
            proc.waitFor()
            return ocrOutput
        } catch (e: Exception) {
            System.err.println("DesktopWindowsOcrHelper: Image OCR failed: ${e.message}")
            return ""
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
