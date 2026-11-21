package com.example.ui.shared

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.HomeWork
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.ApprovalStatus
import com.example.data.model.Booking
import com.example.data.model.BookingStatus
import com.example.data.model.NotificationItem
import com.example.data.model.OwnerVerificationStatus
import com.example.data.model.PayoutStatus
import com.example.data.model.PgCatalog
import com.example.data.model.PgFormatters
import com.example.ui.theme.AmberContainer
import com.example.ui.theme.AmberGold
import com.example.ui.theme.RoseAlert
import com.example.ui.theme.RoseContainer
import com.example.ui.theme.SageGreen
import com.example.ui.theme.SageGreenContainer

@Composable
fun PropertyCoverImage(
    imageKey: String,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    val resId = PgCatalog.drawableForKey(imageKey)
    if (resId != null) {
        Image(
            painter = painterResource(id = resId),
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = ContentScale.Crop
        )
    } else if (imageKey.startsWith("content://") || imageKey.startsWith("http")) {
        AsyncImage(
            model = imageKey,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = ContentScale.Crop,
            error = painterResource(id = R.drawable.img_room_single),
            placeholder = painterResource(id = R.drawable.img_room_single)
        )
    } else {
        Image(
            painter = painterResource(id = R.drawable.img_room_single),
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = ContentScale.Crop
        )
    }
}

@Composable
fun StatusPill(
    text: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = containerColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(50),
        modifier = modifier
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}

@Composable
fun BookingStatusPill(status: BookingStatus) {
    val (bg, fg) = when (status) {
        BookingStatus.CONFIRMED -> SageGreenContainer to SageGreen
        BookingStatus.APPROVED_AWAITING_PAYMENT,
        BookingStatus.AWAITING_PAYMENT,
        BookingStatus.PENDING_APPROVAL -> AmberContainer to AmberGold
        BookingStatus.REJECTED,
        BookingStatus.CANCELLED,
        BookingStatus.EXPIRED -> RoseContainer to RoseAlert
    }
    StatusPill(text = status.label, containerColor = bg, contentColor = fg)
}

@Composable
fun ApprovalStatusPill(status: ApprovalStatus) {
    val (bg, fg) = when (status) {
        ApprovalStatus.APPROVED -> SageGreenContainer to SageGreen
        ApprovalStatus.PENDING_REVIEW -> AmberContainer to AmberGold
        ApprovalStatus.REJECTED, ApprovalStatus.SUSPENDED -> RoseContainer to RoseAlert
    }
    StatusPill(text = status.label, containerColor = bg, contentColor = fg)
}

@Composable
fun OwnerVerificationPill(status: OwnerVerificationStatus) {
    val (bg, fg) = when (status) {
        OwnerVerificationStatus.VERIFIED -> SageGreenContainer to SageGreen
        OwnerVerificationStatus.PENDING -> AmberContainer to AmberGold
        OwnerVerificationStatus.REJECTED -> RoseContainer to RoseAlert
    }
    StatusPill(text = status.label, containerColor = bg, contentColor = fg)
}

@Composable
fun PayoutStatusPill(status: PayoutStatus) {
    val (bg, fg) = when (status) {
        PayoutStatus.SETTLED -> SageGreenContainer to SageGreen
        PayoutStatus.REQUESTED, PayoutStatus.PROCESSING -> AmberContainer to AmberGold
        PayoutStatus.REJECTED -> RoseContainer to RoseAlert
    }
    StatusPill(text = status.label, containerColor = bg, contentColor = fg)
}

@Composable
fun PgCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val cardModifier = if (onClick != null) modifier.clickable(onClick = onClick) else modifier
    Card(
        modifier = cardModifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.large),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        content = content
    )
}

@Composable
fun PgEmptyState(
    icon: ImageVector = Icons.Outlined.HomeWork,
    title: String,
    subtitle: String,
    primaryActionLabel: String? = null,
    onPrimaryAction: (() -> Unit)? = null,
    secondaryActionLabel: String? = null,
    onSecondaryAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(68.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (primaryActionLabel != null && onPrimaryAction != null) {
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = onPrimaryAction,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.testTag("empty_primary_action")
            ) {
                Text(primaryActionLabel)
            }
        }
        if (secondaryActionLabel != null && onSecondaryAction != null) {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = onSecondaryAction,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.testTag("empty_secondary_action")
            ) {
                Text(secondaryActionLabel)
            }
        }
    }
}

@Composable
fun PgErrorBanner(message: String, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.ErrorOutline, contentDescription = null)
            Spacer(modifier = Modifier.width(10.dp))
            Text(text = message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun BookingReceiptDialog(
    booking: Booking,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = SageGreen,
                modifier = Modifier.size(36.dp)
            )
        },
        title = {
            Text(
                text = "Booking Receipt",
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ReceiptLine("Property", booking.propertyName)
                ReceiptLine("Location", "${booking.locality}, ${booking.city}")
                ReceiptLine("Allocation", "Room ${booking.roomNumber} • Bed ${booking.bedCode}")
                ReceiptLine("Move-in Date", booking.moveInDate)
                ReceiptLine("Booking Mode", booking.typeEnum.label)
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                ReceiptLine("Monthly Rent", PgFormatters.formatInr(booking.monthlyRent))
                ReceiptLine("Security Deposit", PgFormatters.formatInr(booking.securityDeposit))
                ReceiptLine("Total Paid", PgFormatters.formatInr(booking.totalAmount), bold = true)
                booking.paymentId?.let {
                    ReceiptLine("Payment Ref", it)
                }
                ReceiptLine("Issued", PgFormatters.formatTimestamp(booking.updatedAt ?: booking.createdAt))
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, modifier = Modifier.testTag("receipt_close_button")) {
                Text("Done")
            }
        }
    )
}

@Composable
private fun ReceiptLine(label: String, value: String, bold: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = value,
            style = if (bold) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium,
            textAlign = TextAlign.End
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsSheet(
    notifications: List<NotificationItem>,
    onMarkRead: (String) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Notifications", style = MaterialTheme.typography.headlineSmall)
                TextButton(onClick = onDismiss) { Text("Close") }
            }
            Spacer(modifier = Modifier.height(8.dp))
            if (notifications.isEmpty()) {
                PgEmptyState(
                    icon = Icons.Filled.Notifications,
                    title = "No notifications yet",
                    subtitle = "Booking approvals, payment confirmations, and settlement alerts will appear here."
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.height(380.dp)
                ) {
                    items(notifications, key = { it.id }) { item ->
                        PgCard(
                            onClick = { if (!item.read) onMarkRead(item.id) }
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                if (!item.read) {
                                    Box(
                                        modifier = Modifier
                                            .padding(top = 6.dp, end = 10.dp)
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary)
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = item.title,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = PgFormatters.formatShortDate(item.createdAt),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = item.message,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
