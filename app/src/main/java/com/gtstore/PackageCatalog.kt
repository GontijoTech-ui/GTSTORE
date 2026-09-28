package com.gtstore

import android.content.Context
import android.net.Uri

/**
 * Fachada do catálogo.
 *
 * Esta classe existe para que a futura UI do catálogo não precise
 * conhecer os detalhes do scanner.
 */
object PkgCatalog {

    fun scan(
        context: Context,
        treeUri: Uri
    ): PkgCatalogScanner.Result {
        return PkgCatalogScanner.scan(
            context = context,
            treeUri = treeUri
        )
    }

    fun games(
        result: PkgCatalogScanner.Result
    ): List<PkgCatalogItem> {
        return result.items.filter {
            it.isGame
        }
    }

    fun updates(
        result: PkgCatalogScanner.Result
    ): List<PkgCatalogItem> {
        return result.items.filter {
            it.isUpdate
        }
    }

    fun dlc(
        result: PkgCatalogScanner.Result
    ): List<PkgCatalogItem> {
        return result.items.filter {
            it.isDlc
        }
    }

    fun other(
        result: PkgCatalogScanner.Result
    ): List<PkgCatalogItem> {
        return result.items.filter {
            it.isOther
        }
    }
}
