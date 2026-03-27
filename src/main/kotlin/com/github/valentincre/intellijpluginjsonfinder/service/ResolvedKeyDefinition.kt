package com.github.valentincre.intellijpluginjsonfinder.service

import com.intellij.openapi.vfs.VirtualFile

data class ResolvedKeyDefinition(
    val virtualFile: VirtualFile,
    val offset: Int,
    val resolvedValue: String,
)
