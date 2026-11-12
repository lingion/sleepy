package com.lingion.sleepy.widget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetGalleryContractTest {
    @Test
    fun `gallery groups partition ALL_WIDGET_VARIANTS into 5 primary families`() {
        val groups = WidgetGalleryData.buildGroups()
        assertEquals("画廊必须分为 5 个主组件家族", 5, groups.size)
        assertEquals(
            "5 个主家族按注册表首现顺序排列",
            listOf("WeekGrid", "Today", "WeekList", "WeekView", "TwoDay"),
            groups.map { it.key }
        )
        val allVariants = groups.flatMap { it.allVariants }
        assertEquals(
            "全 15 个变体完整保留在 5 组中, 无遗漏",
            ALL_WIDGET_VARIANTS.size,
            allVariants.size
        )
        assertEquals(
            "变体集合与 ALL_WIDGET_VARIANTS 完全一致",
            ALL_WIDGET_VARIANTS.toSet(),
            allVariants.toSet()
        )
    }

    @Test
    fun `each primary variant is the largest size variant in its group`() {
        val groups = WidgetGalleryData.buildGroups()
        groups.forEach { group ->
            val primary = group.primaryVariant
            val maxArea = group.allVariants.maxOf { it.widthDp.toLong() * it.heightDp }
            assertEquals(
                "家族 ${group.key} 的主变体必须是组内面积最大档位",
                maxArea,
                primary.widthDp.toLong() * primary.heightDp
            )
            assertEquals(
                "当前 5 族最大档位均为 4×4 (300×250)",
                300L * 250L,
                primary.widthDp.toLong() * primary.heightDp
            )
            assertEquals(
                "remainingVariants 必须恰好排除主变体",
                group.allVariants.filter { it != primary },
                group.remainingVariants
            )
            assertTrue("主变体必须在 allVariants 中", group.allVariants.contains(primary))
        }
    }

    @Test
    fun `multi-size flag and label suffix logic`() {
        val groups = WidgetGalleryData.buildGroups()
        // 当前全部 5 族均有多种尺寸 (WeekView 有 2 种, 其余 4 族各 3 种)
        groups.forEach { group ->
            assertTrue("${group.key} 拥有多尺寸", group.isMultiSize)
            assertEquals(
                "多尺寸变体必须追加 (多尺寸) 后缀",
                "组件名(多尺寸)",
                group.formattedName("组件名")
            )
        }

        // 验证单尺寸分支: 单尺寸变体不追加 (多尺寸) 后缀
        val sampleVariant = groups.first().primaryVariant
        val singleGroup = WidgetGalleryGroup(
            key = "SingleFamily",
            primaryVariant = sampleVariant,
            allVariants = listOf(sampleVariant)
        )
        assertFalse("单个尺寸时 isMultiSize 必须为 false", singleGroup.isMultiSize)
        assertEquals(
            "单尺寸变体显示原名, 不追加 (多尺寸)",
            "单尺寸组件",
            singleGroup.formattedName("单尺寸组件")
        )
    }

    @Test
    fun `gallery cards are a 1-to-1 projection of ALL_WIDGET_VARIANTS`() {
        val cards = WidgetGalleryData.buildCards()
        assertEquals(
            "画廊卡片数量必须恒等于注册表数量(当前 ${ALL_WIDGET_VARIANTS.size})",
            ALL_WIDGET_VARIANTS.size, cards.size
        )
        assertEquals(
            "卡片与注册表逐项同序同类",
            ALL_WIDGET_VARIANTS.map { it.receiverClass },
            cards.map { it.variant.receiverClass }
        )
        cards.forEach {
            assertTrue("每张卡必须有可解析名称资源", it.variant.displayNameRes != 0)
            assertTrue("每张卡必须有可解析预览图资源", it.variant.previewImageRes != 0)
        }
    }
}
