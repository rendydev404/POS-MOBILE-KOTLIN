package com.sukashawarma.pos.presentation.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import com.sukashawarma.pos.R

/**
 * Official stacked GrabFood logo from Grab's Merchant Brand Centre.
 * The stacked variant is intended for tight spaces and is rendered with Fit
 * so its aspect ratio and required clear space are never distorted.
 */
@Composable
fun GrabFoodMark(
    size: Dp,
    modifier: Modifier = Modifier
) {
    Image(
        painter = painterResource(R.drawable.ic_grabfood_official),
        contentDescription = "GrabFood",
        contentScale = ContentScale.Fit,
        modifier = modifier.size(size)
    )
}
