/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.desktop.user

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.askimo.core.user.domain.UserProfile
import io.askimo.core.util.AskimoHome
import io.askimo.desktop.common.components.primaryButton
import io.askimo.desktop.common.components.secondaryButton
import io.askimo.desktop.common.i18n.stringResource
import io.askimo.desktop.common.theme.ComponentColors
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.io.path.Path
import kotlin.io.path.exists
import org.jetbrains.skia.Image as SkiaImage

/**
 * User interest categories that users can select
 */
enum class UserInterestCategory(val key: String, val displayKey: String) {
    TECHNOLOGY("technology", "user.interest.technology"),
    SCIENCE("science", "user.interest.science"),
    TRAVEL("travel", "user.interest.travel"),
    POLITICS("politics", "user.interest.politics"),
    BUSINESS("business", "user.interest.business"),
    HEALTH("health", "user.interest.health"),
    ARTS("arts", "user.interest.arts"),
    SPORTS("sports", "user.interest.sports"),
    COOKING("cooking", "user.interest.cooking"),
    LITERATURE("literature", "user.interest.literature"),
    MUSIC("music", "user.interest.music"),
    MOVIES("movies", "user.interest.movies"),
    GAMING("gaming", "user.interest.gaming"),
    FINANCE("finance", "user.interest.finance"),
    EDUCATION("education", "user.interest.education"),
    ENVIRONMENT("environment", "user.interest.environment"),
}

/**
 * User profile dialog for viewing and editing user information
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun userProfileDialog(
    profile: UserProfile,
    onDismiss: () -> Unit,
    onSave: (UserProfile) -> Unit,
) {
    var name by remember { mutableStateOf(profile.name ?: "") }
    var email by remember { mutableStateOf(profile.email ?: "") }
    var occupation by remember { mutableStateOf(profile.occupation ?: "") }
    var location by remember { mutableStateOf(profile.location ?: "") }
    var bio by remember { mutableStateOf(profile.bio ?: "") }

    // Parse existing interests into predefined and custom
    val existingInterests = profile.interests
    val predefinedInterests = UserInterestCategory.entries.map { it.key }.toSet()
    var selectedInterests by remember {
        mutableStateOf(
            existingInterests.filter { it in predefinedInterests }.toSet(),
        )
    }
    var customInterests by remember {
        mutableStateOf(
            existingInterests.filter { it !in predefinedInterests }.joinToString(", "),
        )
    }

    // Avatar handling
    var avatarPath by remember { mutableStateOf(profile.preferences["avatarPath"]) }
    var avatarImage by remember { mutableStateOf<ImageBitmap?>(null) }

    // Load existing avatar if available
    remember(avatarPath) {
        avatarPath?.let { path ->
            try {
                val file = File(path)
                if (file.exists()) {
                    val bytes = file.readBytes()
                    avatarImage = SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap()
                }
            } catch (_: Exception) {
                // Failed to load avatar, use default
                avatarImage = null
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.width(600.dp).height(700.dp),
            shape = MaterialTheme.shapes.large,
            tonalElevation = 8.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource("user.profile.title"),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                    ) {
                        Icon(Icons.Default.Close, contentDescription = stringResource("action.close"))
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Scrollable content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    // Avatar section
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(120.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .border(3.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                .clickable {
                                    val fileDialog = FileDialog(null as Frame?, "Select Avatar Image", FileDialog.LOAD)
                                    fileDialog.setFilenameFilter { _, name ->
                                        name.lowercase().endsWith(".png") ||
                                            name.lowercase().endsWith(".jpg") ||
                                            name.lowercase().endsWith(".jpeg") ||
                                            name.lowercase().endsWith(".gif")
                                    }
                                    fileDialog.isVisible = true
                                    val selectedFile = fileDialog.file
                                    val selectedDir = fileDialog.directory

                                    if (selectedFile != null && selectedDir != null) {
                                        val sourcePath = Path("$selectedDir$selectedFile")
                                        if (sourcePath.exists()) {
                                            // Copy to app data directory
                                            val userDataDir = AskimoHome.base().resolve("avatars")
                                            Files.createDirectories(userDataDir)
                                            val destPath = userDataDir.resolve("user_avatar_${System.currentTimeMillis()}.${sourcePath.fileName.toString().substringAfterLast(".")}")
                                            Files.copy(sourcePath, destPath, StandardCopyOption.REPLACE_EXISTING)

                                            avatarPath = destPath.toString()

                                            // Load image
                                            try {
                                                val bytes = destPath.toFile().readBytes()
                                                avatarImage = SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap()
                                            } catch (_: Exception) {
                                                // Failed to load image
                                            }
                                        }
                                    }
                                }
                                .pointerHoverIcon(PointerIcon.Hand),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (avatarImage != null) {
                                Image(
                                    bitmap = avatarImage!!,
                                    contentDescription = stringResource("user.profile.avatar"),
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop,
                                )
                            } else {
                                val initials = name.split(" ")
                                    .mapNotNull { it.firstOrNull()?.uppercase() }
                                    .take(2)
                                    .joinToString("")
                                    .ifBlank { "?" }

                                Text(
                                    text = initials,
                                    style = MaterialTheme.typography.displayLarge,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        Text(
                            text = stringResource("user.profile.avatar.click_to_change"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    HorizontalDivider()

                    // Basic Information
                    Text(
                        text = stringResource("user.profile.basic_info"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )

                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(stringResource("user.profile.name")) },
                        colors = ComponentColors.outlinedTextFieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )

                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text(stringResource("user.profile.email")) },
                        colors = ComponentColors.outlinedTextFieldColors(),
                        placeholder = { Text(stringResource("user.profile.email.placeholder")) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )

                    OutlinedTextField(
                        value = occupation,
                        onValueChange = { occupation = it },
                        label = { Text(stringResource("user.profile.occupation")) },
                        colors = ComponentColors.outlinedTextFieldColors(),
                        placeholder = { Text(stringResource("user.profile.occupation.placeholder")) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )

                    OutlinedTextField(
                        value = location,
                        onValueChange = { location = it },
                        label = { Text(stringResource("user.profile.location")) },
                        colors = ComponentColors.outlinedTextFieldColors(),
                        placeholder = { Text(stringResource("user.profile.location.placeholder")) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )

                    OutlinedTextField(
                        value = bio,
                        onValueChange = { bio = it },
                        label = { Text(stringResource("user.profile.bio")) },
                        colors = ComponentColors.outlinedTextFieldColors(),
                        placeholder = { Text(stringResource("user.profile.bio.placeholder")) },
                        modifier = Modifier.fillMaxWidth().height(100.dp),
                        maxLines = 4,
                    )

                    HorizontalDivider()

                    // Interests
                    Text(
                        text = stringResource("user.profile.interests"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )

                    Text(
                        text = stringResource("user.profile.interests.description"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    // Predefined interest chips
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        UserInterestCategory.entries.forEach { category ->
                            FilterChip(
                                selected = category.key in selectedInterests,
                                onClick = {
                                    selectedInterests = if (category.key in selectedInterests) {
                                        selectedInterests - category.key
                                    } else {
                                        selectedInterests + category.key
                                    }
                                },
                                label = { Text(stringResource(category.displayKey)) },
                                modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                            )
                        }
                    }

                    // Custom interests
                    OutlinedTextField(
                        value = customInterests,
                        onValueChange = { customInterests = it },
                        label = { Text(stringResource("user.profile.interests.custom")) },
                        colors = ComponentColors.outlinedTextFieldColors(),
                        placeholder = { Text(stringResource("user.profile.interests.custom.placeholder")) },
                        modifier = Modifier.fillMaxWidth(),
                        supportingText = {
                            Text(
                                text = stringResource("user.profile.interests.custom.hint"),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        },
                    )
                }

                Spacer(Modifier.height(16.dp))

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    secondaryButton(
                        onClick = onDismiss,
                    ) {
                        Text(stringResource("action.cancel"))
                    }

                    Spacer(Modifier.width(8.dp))

                    primaryButton(
                        onClick = {
                            // Combine selected predefined interests with custom interests
                            val allInterests = selectedInterests.toMutableSet()
                            customInterests.split(",")
                                .map { it.trim() }
                                .filter { it.isNotBlank() }
                                .forEach { allInterests.add(it) }

                            val updatedPreferences = profile.preferences.toMutableMap()
                            avatarPath?.let { updatedPreferences["avatarPath"] = it }

                            val updatedProfile = profile.copy(
                                name = name.ifBlank { null },
                                email = email.ifBlank { null },
                                occupation = occupation.ifBlank { null },
                                location = location.ifBlank { null },
                                bio = bio.ifBlank { null },
                                interests = allInterests.toList(),
                                preferences = updatedPreferences,
                            )
                            onSave(updatedProfile)
                        },
                    ) {
                        Text(stringResource("action.save"))
                    }
                }
            }
        }
    }
}
