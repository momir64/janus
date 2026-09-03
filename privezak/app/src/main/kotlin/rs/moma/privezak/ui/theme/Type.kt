package rs.moma.privezak.ui.theme

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.unit.sp
import rs.moma.privezak.R

val ChakraPetch = FontFamily(Font(R.font.chakra_petch_medium, FontWeight.Medium))
val SplineSansMono = FontFamily(Font(R.font.spline_sans_mono))

private val Default = Typography()

val Typography = Typography(
    displayMedium = Default.bodyLarge.copy(
        fontFamily = ChakraPetch,
        fontSize = 42.sp
    ),
    displaySmall = Default.bodyLarge.copy(
        fontFamily = ChakraPetch,
        fontSize = 32.sp
    ),
    headlineMedium = Default.bodyLarge.copy(
        fontFamily = ChakraPetch,
        fontSize = 28.sp
    ),
    bodyMedium = Default.bodyMedium.copy(
        fontFamily = SplineSansMono,
        fontSize = 12.sp,
        lineHeight = 20.sp
    ),
    labelLarge = Default.labelLarge.copy(
        fontSize = 16.sp,
        fontWeight = FontWeight.Normal
    )
)
