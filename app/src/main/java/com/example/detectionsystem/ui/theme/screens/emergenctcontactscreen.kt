package com.example.detectionsystem.ui.theme.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

// ==========================================
// 1. ANA BİLEŞEN (Main Dialog)
// ==========================================
@Composable
fun EmergencyContactDialog(
    initialName: String,
    initialPhone: String,
    onDismiss: () -> Unit,
    onSave: (name: String, phone: String) -> Unit
) {
    // İş mantığı sızıntısını temizledik
    val defaultName = if (initialName == DialogConstants.EMPTY_CONTACT_NAME) "" else initialName

    var nameText by remember { mutableStateOf(defaultName) }
    var phoneText by remember { mutableStateOf(initialPhone) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(DialogConstants.PaddingSmall),
            shape = RoundedCornerShape(DialogConstants.RadiusLarge),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = DialogConstants.ElevationDefault)
        ) {
            Column(
                modifier = Modifier
                    .padding(DialogConstants.PaddingLarge)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Parçalanmış (Modüler) UI bileşenleri
                DialogHeaderSection()
                Spacer(modifier = Modifier.height(DialogConstants.SpacingMedium))

                DialogInputSection(
                    nameText = nameText,
                    phoneText = phoneText,
                    onNameChange = { nameText = it },
                    onPhoneChange = { phoneText = it }
                )
                Spacer(modifier = Modifier.height(DialogConstants.SpacingLarge))

                DialogActionSection(
                    onDismiss = onDismiss,
                    onSaveClick = { onSave(nameText, phoneText) }
                )
            }
        }
    }
}

// ==========================================
// 2. ALT BİLEŞENLER (SRP - Tek Sorumluluk)
// ==========================================

@Composable
private fun DialogHeaderSection() {
    Box(
        modifier = Modifier
            .size(DialogConstants.IconBoxSize)
            .clip(CircleShape)
            .background(DialogConstants.PrimaryColor.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Call,
            contentDescription = DialogConstants.IconDescSos,
            tint = DialogConstants.PrimaryColor,
            modifier = Modifier.size(DialogConstants.IconSize)
        )
    }

    Spacer(modifier = Modifier.height(DialogConstants.SpacingMedium))

    Text(
        text = DialogConstants.Title,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.ExtraBold
    )
    Text(
        text = DialogConstants.Description,
        style = MaterialTheme.typography.bodySmall,
        color = Color.Gray,
        modifier = Modifier.padding(top = 4.dp, bottom = DialogConstants.SpacingMedium)
    )
}

@Composable
private fun DialogInputSection(
    nameText: String,
    phoneText: String,
    onNameChange: (String) -> Unit,
    onPhoneChange: (String) -> Unit
) {
    OutlinedTextField(
        value = nameText,
        onValueChange = onNameChange,
        label = { Text(DialogConstants.LabelName) },
        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(DialogConstants.RadiusMedium)
    )

    Spacer(modifier = Modifier.height(DialogConstants.SpacingSmall))

    OutlinedTextField(
        value = phoneText,
        onValueChange = onPhoneChange,
        label = { Text(DialogConstants.LabelPhone) },
        leadingIcon = { Icon(Icons.Default.Call, contentDescription = null) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(DialogConstants.RadiusMedium)
    )
}

@Composable
private fun DialogActionSection(onDismiss: () -> Unit, onSaveClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        TextButton(onClick = onDismiss) {
            Text(DialogConstants.BtnCancel, color = Color.Gray)
        }
        Button(
            onClick = onSaveClick,
            shape = RoundedCornerShape(DialogConstants.RadiusMedium),
            colors = ButtonDefaults.buttonColors(containerColor = DialogConstants.PrimaryColor)
        ) {
            Text(DialogConstants.BtnSave, fontWeight = FontWeight.Bold)
        }
    }
}

// ==========================================
// 3. SABİTLER (Magic Strings / Numbers)
// ==========================================
private object DialogConstants {
    // Metinler
    const val EMPTY_CONTACT_NAME = "Kayıtlı Kişi Yok"
    const val Title = "SOS Acil Durum Kişisi"
    const val Description = "Uzun süreli göz kapanması algılanırsa bu kişiye otomatik SMS gönderimi denenir. Teslimat garanti edilmez."
    const val LabelName = "Kişinin Adı"
    const val LabelPhone = "Telefon Numarası (Örn: +905...)"
    const val BtnCancel = "İptal"
    const val BtnSave = "Numarayı Kaydet"
    const val IconDescSos = "SOS İkonu"

    // Renkler
    val PrimaryColor = Color(0xFFE91E63)

    // Boyutlar (dp)
    val PaddingSmall = 8.dp
    val PaddingLarge = 24.dp
    val SpacingSmall = 12.dp
    val SpacingMedium = 16.dp
    val SpacingLarge = 24.dp
    val RadiusMedium = 12.dp
    val RadiusLarge = 24.dp
    val IconBoxSize = 56.dp
    val IconSize = 28.dp
    val ElevationDefault = 8.dp
}
