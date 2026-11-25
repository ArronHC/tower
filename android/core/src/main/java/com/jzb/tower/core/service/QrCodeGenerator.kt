package com.jzb.tower.core.service

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.WriterException
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.jzb.tower.core.model.QrMatrix

object QrCodeGenerator {
    fun generate(content: String): QrMatrix? {
        if (content.isBlank()) return null
        return try {
            val bits = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 0, 0, mapOf(
                EncodeHintType.CHARACTER_SET to "UTF-8",
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
                EncodeHintType.MARGIN to 0,
            ))
            QrMatrix(bits.width, BooleanArray(bits.width * bits.height) { bits[it % bits.width, it / bits.width] })
        } catch (_: WriterException) {
            null
        }
    }
}
