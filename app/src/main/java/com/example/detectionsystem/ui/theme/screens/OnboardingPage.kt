package com.example.detectionsystem.ui.theme.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

// ==========================================
// 1. VERİ MODELİ
// ==========================================
data class OnboardingPage(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val color: Color
)

// ==========================================
// 2. ANA BİLEŞEN (Main Dialog)
// ==========================================
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DriverOnboardingDialog(onDismiss: () -> Unit) {
    val pages = OnboardingConstants.getPages()
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val coroutineScope = rememberCoroutineScope()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(OnboardingConstants.DialogWidthFraction)
                .wrapContentHeight()
                .padding(OnboardingConstants.PaddingSmall),
            shape = RoundedCornerShape(OnboardingConstants.RadiusLarge),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = OnboardingConstants.ElevationDefault)
        ) {
            Column(
                modifier = Modifier
                    .padding(OnboardingConstants.PaddingLarge)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 1. Kaydırılabilir İçerik Alanı
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxWidth().height(OnboardingConstants.PagerHeight)
                ) { pageIndex ->
                    OnboardingPagerContent(page = pages[pageIndex])
                }

                Spacer(modifier = Modifier.height(OnboardingConstants.SpacingMedium))

                // 2. Sayfa İşaretçileri (Noktalar)
                PaginationDots(pageCount = pages.size, currentPage = pagerState.currentPage)

                Spacer(modifier = Modifier.height(OnboardingConstants.SpacingLarge))

                // 3. Alt Kontrol Butonları
                OnboardingControls(
                    pagerState = pagerState,
                    pageCount = pages.size,
                    coroutineScope = coroutineScope,
                    onDismiss = onDismiss
                )
            }
        }
    }
}

// ==========================================
// 3. ALT BİLEŞENLER (SRP - Modüler UI)
// ==========================================

@Composable
private fun OnboardingPagerContent(page: OnboardingPage) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(OnboardingConstants.IconBoxSize)
                .clip(CircleShape)
                .background(page.color.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = page.icon,
                contentDescription = null,
                tint = page.color,
                modifier = Modifier.size(OnboardingConstants.IconSize)
            )
        }

        Spacer(modifier = Modifier.height(OnboardingConstants.SpacingLarge))

        Text(
            text = page.title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(OnboardingConstants.SpacingSmall))

        Text(
            text = page.description,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp,
            modifier = Modifier.padding(horizontal = OnboardingConstants.PaddingSmall)
        )
    }
}

@Composable
private fun PaginationDots(pageCount: Int, currentPage: Int) {
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(pageCount) { index ->
            val isSelected = currentPage == index
            Box(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .height(8.dp)
                    .width(if (isSelected) 24.dp else 8.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary
                        else Color.Gray.copy(alpha = 0.3f)
                    )
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun OnboardingControls(
    pagerState: PagerState,
    pageCount: Int,
    coroutineScope: CoroutineScope,
    onDismiss: () -> Unit
) {
    val isLastPage = pagerState.currentPage == pageCount - 1

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (!isLastPage) {
            TextButton(onClick = onDismiss) {
                Text("Atla", color = Color.Gray)
            }
        } else {
            Spacer(modifier = Modifier.width(1.dp))
        }

        Button(
            onClick = {
                if (!isLastPage) {
                    coroutineScope.launch {
                        pagerState.animateScrollToPage(pagerState.currentPage + 1)
                    }
                } else {
                    onDismiss()
                }
            },
            shape = RoundedCornerShape(14.dp)
        ) {
            Text(
                text = if (!isLastPage) "İleri" else "Sürüşe Başla",
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }
    }
}

// ==========================================
// 4. SABİTLER VE VERİ DEPOSU (Clean Code)
// ==========================================
private object OnboardingConstants {
    // Boyutlar ve Boşluklar
    val DialogWidthFraction = 0.9f
    val PagerHeight = 260.dp
    val IconBoxSize = 72.dp
    val IconSize = 38.dp
    val PaddingSmall = 8.dp
    val PaddingLarge = 24.dp
    val SpacingSmall = 12.dp
    val SpacingMedium = 16.dp
    val SpacingLarge = 20.dp
    val RadiusLarge = 28.dp
    val ElevationDefault = 12.dp

    // Sayfa Verileri (Hardcode metinler ana fonksiyondan buraya taşındı)
    fun getPages() = listOf(
        OnboardingPage(
            title = "Sürücü Takip Sistemleri (DMS) Nedir?",
            description = "DMS; sürücünün yüzünü, göz kapaklarını ve baş hareketlerini gerçek zamanlı izleyen aktif bir güvenlik teknolojisidir. Modern otonom ve yarı-otonom araçların en kritik parçalarından biridir.",
            icon = Icons.Default.Info,
            color = Color(0xFF2196F3) // Mavi
        ),
        OnboardingPage(
            title = "Temel Kullanım Amacı",
            description = "Temel amaç; uzun yolda veya gece sürüşlerinde ortaya çıkan 'mikro uyku' (saniyelik dalma) ve dikkat dağınıklığını sürücü henüz bilincini kaybetmeden önce tespit edip uyarmaktır.",
            icon = Icons.Default.Favorite,
            color = Color(0xFF4CAF50) // Yeşil
        ),
        OnboardingPage(
            title = "Neden Hayati Önem Taşıyor?",
            description = "Otoyol hızında (120 km/s) araç kullanan bir sürücü sadece 1.5 saniye gözlerini kapattığında araç yaklaşık 50 metre kontrolsüz ilerler! Smart DMS, bu kayıp saniyeleri sıfıra indirmeyi hedefler.",
            icon = Icons.Default.Warning,
            color = Color(0xFFFF9800) // Turuncu
        ),
        OnboardingPage(
            title = "Smart DMS'e Hoş Geldiniz",
            description = "Bu uygulamanın amacı; direksiyon başında yorgunluk, uyuklama ve dikkat dağınıklığını yapay görme teknolojisiyle algılayarak olası kazaları önlemektir.",
            icon = Icons.Default.Favorite,
            color = Color(0xFF4CAF50)
        ),
        OnboardingPage(
            title = "Yapay Zeka ve Göz Takibi",
            description = "Ön kamera göz kapağı (EAR) ve ağız açıklığınızı (MAR) sürekli ölçer. Gözleriniz 1.2 saniyeden uzun kapanırsa veya esnerseniz sistem anında uyarır.",
            icon = Icons.Default.Warning,
            color = Color(0xFFFF9800)
        ),
        OnboardingPage(
            title = "Acil Durum SOS Sistemi",
            description = "Eğer alarm çaldıktan sonra 10 saniye boyunca hiç tepki vermezseniz, sistem bilincinizin kapandığını varsayar ve acil durum kişinize SMS ile SOS bildirimi gönderir.",
            icon = Icons.Default.Send,
            color = Color(0xFFE91E63) // Pembe
        )
    )
}