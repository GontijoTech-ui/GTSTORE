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

            val objectJson = JSONObject()

            objectJson.put("id", pkg.id)
            objectJson.put("name", pkg.name)
            objectJson.put("file", pkg.file)
            objectJson.put("path", pkg.path)
            objectJson.put("size", pkg.size)
            objectJson.put("modified", pkg.modified)
            objectJson.put("type", pkg.type)
            objectJson.put("version", pkg.version)
            objectJson.put("description", pkg.description)

            array.put(objectJson)
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
                                "version"
                            ),
                            description = item.optString(
                                "description"
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
