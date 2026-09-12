package com.example.distll.ui.components

import android.content.res.Configuration
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.feedit.ui.theme.FeedITTheme

enum class NavigationContents(
    val icon: ImageVector,
    val title: String
) {
    HOME(Icons.Outlined.Home, "Home"),
    ANALYSIS(Icons.Outlined.Analytics, "Analysis"),
    SETTINGS(Icons.Outlined.Settings, "Settings"),
}

@Composable
fun BottomNavBar(
    modifier: Modifier = Modifier,
    selectedItem: NavigationContents,
    onItemSelected: (NavigationContents) -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(0.dp)
    ) {

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .align(Alignment.BottomCenter)
                .clip(
                    shape = RoundedCornerShape(
                        topStart = 5.dp,
                        topEnd = 5.dp,
                        bottomStart = 36.dp,
                        bottomEnd = 36.dp
                    )
                )
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .align(Alignment.BottomCenter),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            NavigationContents.entries.forEach { contents ->
                NavigationItem(
                    item = contents,
                    isSelected = selectedItem == contents,
                    onClick = { onItemSelected(contents) }
                )
            }
        }
    }
}

@Composable
fun NavigationItem(
    item: NavigationContents,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val yIndex by animateDpAsState(
        targetValue = if (isSelected) (-20).dp else 0.dp,
        label = "Y-axis animation"
    )

    val borderWidth by animateDpAsState(
        targetValue = if (isSelected) 10.dp else 0.dp,
        label = "Border ring Animation"
    )

    val borderOpacity by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.background else Color.Transparent,
        label = "Border ring Animation"
    )

    val bgColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
        label = "Button Background Color Animation"
    )

    Box(
        modifier = Modifier
            .offset(y = yIndex)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .border(
                width = borderWidth,
                color = borderOpacity,
                shape = CircleShape
            )
            .background(
                color = bgColor,
                shape = CircleShape
            )
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = item.title,
            modifier = Modifier
                .padding(5.dp)
                .size(30.dp),

            tint = if (isSelected) {
                MaterialTheme.colorScheme.background
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}

@Preview(name = "Light Mode", uiMode = Configuration.UI_MODE_NIGHT_NO, showSystemUi = true)
@Preview(name = "Dark Mode", uiMode = Configuration.UI_MODE_NIGHT_YES, showSystemUi = true)
@Composable
fun BottomNavBarPreview() {
    var selectedItem by remember { mutableStateOf(NavigationContents.HOME) }

    FeedITTheme(dynamicColor = false) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.BottomCenter
        ) {
            BottomNavBar(
                selectedItem = selectedItem,
                onItemSelected = { selectedItem = it }
            )
        }
    }
}
