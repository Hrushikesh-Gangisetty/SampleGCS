//package com.example.aerogcsclone.uimain
//
//import androidx.compose.foundation.background
//import androidx.compose.foundation.layout.Arrangement
//import androidx.compose.foundation.layout.Box
//import androidx.compose.foundation.layout.Column
//import androidx.compose.foundation.layout.ExperimentalLayoutApi
//import androidx.compose.foundation.layout.FlowRow
//import androidx.compose.foundation.layout.Spacer
//import androidx.compose.foundation.layout.height
//import androidx.compose.foundation.layout.padding
//import androidx.compose.foundation.layout.width
//import androidx.compose.material3.Text
//import androidx.compose.runtime.Composable
//import androidx.compose.ui.Alignment
//import androidx.compose.ui.Modifier
//import androidx.compose.ui.graphics.Color
//import androidx.compose.ui.text.font.FontWeight
//import androidx.compose.ui.unit.Dp
//import androidx.compose.ui.unit.dp
//import androidx.compose.ui.unit.sp
//
//
//data class InfoData(val label: String, val value: String)
//
//
//val sampleInfoItems = listOf(
//    InfoData("Alt", "2.5 (m)"),
//    InfoData("Speed", "15.9 (m/s)"),
//    InfoData("Area", "10 (Acre)"),
//    InfoData("Distance", "20m"),
//    InfoData("Consumed", "2.0 (ml)"),
//    InfoData("Flow Rate", "0 (L/min)"),
//    InfoData("Obs Alt", "10"),
//    InfoData("Time", "00:00:00"),
//)
//
//
//@OptIn(ExperimentalLayoutApi::class)
//@Composable
//fun StatusPanel(
//    modifier: Modifier = Modifier,
//    infoItems: List<InfoData> = sampleInfoItems,
//    panelWidth: Dp = 200.dp,           // same 300 width as Flutter
//    colSpacing: Dp = 15.dp,
//    rowSpacing: Dp = 4.dp,
//    itemsPerRow: Int = 5             // exactly 4 per row
//) {
//    // compute each item width so 4 fit inside 300dp with 3 gaps between them
//    val itemWidth = (panelWidth - colSpacing * (itemsPerRow - 1)) / itemsPerRow
//
//    Box(
//        modifier = modifier,
//        contentAlignment = Alignment.BottomStart
//    ) {
//        Box(
//            modifier = Modifier
//                .padding(10.dp)
//                .background(Color.Black.copy(alpha = 0.6f), shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
//                .padding(10.dp)
//                .width(panelWidth)
//        ) {
//            FlowRow(
//                horizontalArrangement = Arrangement.spacedBy(colSpacing),
//                verticalArrangement = Arrangement.spacedBy(rowSpacing),
//                maxItemsInEachRow = itemsPerRow
//            ) {
//                infoItems.forEach { item ->
//                    InfoItem(
//                        label = item.label,
//                        value = item.value,
//                        modifier = Modifier.width(itemWidth)
//                    )
//                }
//            }
//        }
//    }
//}
//
//// -------- Item --------
//@Composable
//fun InfoItem(
//    label: String,
//    value: String,
//    modifier: Modifier = Modifier
//) {
//    Column(modifier = modifier, horizontalAlignment = Alignment.Start) {
//        Text(
//            text = label,
//            color = Color.White,
//            fontWeight = FontWeight.Bold,
//            fontSize = 12.sp
//        )
//        Spacer(modifier = Modifier.height(2.dp))
//        Text(
//            text = value,
//            color = Color.White,
//            fontSize = 12.sp
//        )
//    }
//}
