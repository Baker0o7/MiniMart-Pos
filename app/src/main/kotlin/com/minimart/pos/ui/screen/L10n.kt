package com.minimart.pos.ui.screen

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.minimart.pos.R

/** Short helpers so screen code reads `tr(R.string.checkout)`; text lives in res/values*/strings.xml (English and Arabic). */
@Composable
internal fun tr(id: Int, vararg args: Any): String = stringResource(id, *args)

/** "3 items" in the right plural form for the current language (Arabic has six forms). */
@Composable
internal fun itemsText(n: Int): String = LocalContext.current.resources.getQuantityString(R.plurals.item_count, n, n)
