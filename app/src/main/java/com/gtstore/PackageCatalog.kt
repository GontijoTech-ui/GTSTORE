package com.gtstore

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

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

    fun save(
        context: Context,
        packages: List<CatalogPackage>
    ) {
        val array = JSONArray()

        packages.forEach { pkg ->

            val item = JSONObject()

            item.put("id", pkg.id)
            item.put("name", pkg.name)
            item.put("file", pkg.file)
            item.put("path", pkg.path)
            item.put("size", pkg.size)
            item.put("modified", pkg.modified)
            item.put("type", pkg.type)
            item.put("version", pkg.version)
            item.put("description", pkg.description)

            array.put(item)
        }

        context
            .getSharedPreferences(
                PREFS,
                Context.MODE_PRIVATE
            )
            .edit()
            .putString(
                KEY_PACKAGES,
                array.toString()
            )
            .apply()
    }

    fun load(
        context: Context
    ): List<CatalogPackage> {

        val json = context
            .getSharedPreferences(
                PREFS,
                Context.MODE_PRIVATE
            )
            .getString(
                KEY_PACKAGES,
                null
            )
            ?: return emptyList()

        return try {

            val array = JSONArray(json)

            buildList {

                for (index in 0 until array.length()) {

                    val item = array.getJSONObject(index)

                    add(
                        CatalogPackage(
                            id = item.optString("id"),
                            name = item.optString("name"),
                            file = item.optString("file"),
                            path = item.optString("path"),
                            size = item.optLong("size"),
                            modified = item.optLong("modified"),
                            type = item.optString(
                                "type",
                                "pkg"
                            ),
                            version = item.optString(
                                "version",
                                ""
                            ),
                            description = item.optString(
                                "description",
                                ""
                            )
                        )
                    )
                }
            }

        } catch (_: Exception) {

            emptyList()
        }
    }
}



