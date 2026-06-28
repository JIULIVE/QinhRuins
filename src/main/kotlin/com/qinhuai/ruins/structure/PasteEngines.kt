package com.qinhuai.ruins.structure

import java.io.File

object PasteEngines {

    private lateinit var native: NativePasteEngine
    private var active: PasteEngine? = null

    fun init(templatesDir: File, spreadThreshold: Int, handleMarkers: Boolean = true, pasteMillisPerTick: Long = 8L) {
        native = NativePasteEngine(templatesDir, spreadThreshold, handleMarkers, pasteMillisPerTick)
        active = native
    }

    fun active(): PasteEngine = active ?: throw IllegalStateException("PasteEngines 未初始化")
}
