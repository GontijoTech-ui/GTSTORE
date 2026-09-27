package com.gtstore

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

enum class PackageChangeType {
    ADDED,
    REMOVED,
    CHANGED,
    UNCHANGED
}

data class PackageChange(
    val type: PackageChangeType,
    val packageItem: CatalogPackage
)

data class CatalogSyncResult(
    val current: List<CatalogPackage>,
    val added: List<CatalogPackage>,
    val removed: List<CatalogPackage>,
    val changed: List<CatalogPackage>,
    val unchanged: List<CatalogPackage>
)

data class CatalogPackage(
    val id: String,
    val name: String,
    val file: String,
    val path: String,
    val size: Long,
    val modified: Long,
    val type: String = "pkg",
    val version: String = "",
    val description: String = ""
)

object PackageCatalog {

    private const val PREFS = "gtstore_catalog"
    private const val KEY_PACKAGES = "packages"

    fun save(context: Context, packages: List<CatalogPackage>) {
        val array = JSONArray()

        packages.forEach { pkg ->
            val item = JSONObject().apply {
                put("id", pkg.id)
                put("name", pkg.name)
                put("file", pkg.file)
                put("path", pkg.path)
                put("size", pkg.size)
                put("modified", pkg.modified)
                put("type", pkg.type)
                put("version", pkg.version)
                put("description", pkg.description)
            }
            array.put(item)
        }

        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PACKAGES, array.toString())
            .apply()
    }

    fun load(context: Context): List<CatalogPackage> {
        val json = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_PACKAGES, null) ?: return emptyList()

        return try {
            val array = JSONArray(json)
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    add(
                        CatalogPackage(
                            id = item.optString("id", ""),
                            name = item.optString("name", ""),
                            file = item.optString("file", ""),
                            path = item.optString("path", ""),
                            size = item.optLong("size", 0L),
                            modified = item.optLong("modified", 0L),
                            type = item.optString("type", "pkg"),
                            version = item.optString("version", ""),
                            description = item.optString("description", "")
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun synchronize(
        context: Context,
        currentPackages: List<CatalogPackage>
    ): CatalogSyncResult {
        val previousPackages = load(context)

        val previousByKey = previousPackages.associateBy { catalogKey(it) }
        val currentByKey = currentPackages.associateBy { catalogKey(it) }

        val added = mutableListOf<CatalogPackage>()
        val removed = mutableListOf<CatalogPackage>()
        val changed = mutableListOf<CatalogPackage>()
        val unchanged = mutableListOf<CatalogPackage>()

        for (current in currentPackages) {
            val key = catalogKey(current)
            val previous = previousByKey[key]

            when {
                previous == null -> added.add(current)
                hasChanged(previous, current) -> changed.add(current)
                else -> unchanged.add(current)
            }
        }

        for (previous in previousPackages) {
            val key = catalogKey(previous)
            if (!currentByKey.containsKey(key)) {
                removed.add(previous)
            }
        }

        save(context, currentPackages)

        return CatalogSyncResult(
            current = currentPackages,
            added = added,
            removed = removed,
            changed = changed,
            unchanged = unchanged
        )
    }

    private fun catalogKey(pkg: CatalogPackage): String {
        // Usa o id como chave primária se existir, senão utiliza o path do arquivo
        return pkg.id.ifBlank { pkg.path }
    }

    private fun hasChanged(previous: CatalogPackage, current: CatalogPackage): Boolean {
        return previous.size != current.size ||
                previous.modified != current.modified ||
                previous.name != current.name ||
                previous.file != current.file
    }
}
