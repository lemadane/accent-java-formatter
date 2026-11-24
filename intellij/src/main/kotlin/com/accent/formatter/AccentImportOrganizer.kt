package com.accent.formatter

data class ImportResult(
    val hasImports: Boolean,
    val startLineIndex: Int,
    val endLineIndex: Int,
    val importsText: String
)

object AccentImportOrganizer {

    fun organizeJavaImports(lines: List<String>): ImportResult {
        var startIdx = -1
        var endIdx = -1
        val importLines = mutableListOf<String>()

        for (i in lines.indices) {
            val line = lines[i].trim()
            if (line.startsWith("import ")) {
                if (startIdx == -1) startIdx = i
                endIdx = i
                importLines.add(line)
            }
        }

        if (importLines.isEmpty()) {
            return ImportResult(false, -1, -1, "")
        }

        val uniqueImports = importLines.distinct().sorted()

        val staticImports = uniqueImports.filter { it.startsWith("import static ") }
        val javaImports = uniqueImports.filter { !it.startsWith("import static ") && (it.startsWith("import java.") || it.startsWith("import javax.")) }
        val orgImports = uniqueImports.filter { !it.startsWith("import static ") && it.startsWith("import org.") }
        val accentImports = uniqueImports.filter { !it.startsWith("import static ") && (it.startsWith("import com.accent.") || it.startsWith("import accent.")) }
        val otherImports = uniqueImports.filter { !it.startsWith("import static ") && !javaImports.contains(it) && !orgImports.contains(it) && !accentImports.contains(it) }

        val groups = listOf(staticImports, javaImports, orgImports, accentImports, otherImports)
            .filter { it.isNotEmpty() }

        val importsText = groups.joinToString("\n\n") { group -> group.joinToString("\n") }

        return ImportResult(true, startIdx, endIdx, importsText)
    }
}
