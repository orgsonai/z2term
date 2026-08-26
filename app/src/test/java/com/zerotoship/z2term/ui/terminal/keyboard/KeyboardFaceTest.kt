package com.zerotoship.z2term.ui.terminal.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 面 ([KeyboardFace]) の巡回の検証。
 *
 * 巡回そのものは Android に触れない純粋な計算なので、ここで固定できる。**実機でしか
 * 見られないのはキーの大きさと指の運びだけ**にしておきたい。
 */
class KeyboardFaceTest {

    @Test
    fun cyclesThroughAllThreeFaces() {
        val faces = KeyboardFace.ORDER_ASCII_FIRST
        assertEquals(KeyboardFace.ASCII, KeyboardFace.next(faces, KeyboardFace.KANA))
        assertEquals(KeyboardFace.NUMBER, KeyboardFace.next(faces, KeyboardFace.ASCII))
        // 3 つ目から先頭へ戻る (巡回)。
        assertEquals(KeyboardFace.KANA, KeyboardFace.next(faces, KeyboardFace.NUMBER))
    }

    @Test
    fun numberFirstOrderIsTheOtherDirection() {
        val faces = KeyboardFace.ORDER_NUMBER_FIRST
        assertEquals(KeyboardFace.NUMBER, KeyboardFace.next(faces, KeyboardFace.KANA))
        assertEquals(KeyboardFace.ASCII, KeyboardFace.next(faces, KeyboardFace.NUMBER))
        assertEquals(KeyboardFace.KANA, KeyboardFace.next(faces, KeyboardFace.ASCII))
    }

    @Test
    fun onlyTwoOrdersExistBecauseRotationsAreTheSameCycle() {
        // 3 面の巡回順は回転を除いて 2 通り。設定を 2 択のラジオにしている根拠なので、
        // 選択肢を足したくなったらここが落ちる。
        assertEquals(2, KeyboardFace.ORDERS.size)
        // `A → 12 → あ` は `あ → A → 12` を回しただけ = 同じ巡回。
        val rotated = KeyboardFace.ORDER_ASCII_FIRST.let { it.drop(1) + it.first() }
        assertEquals(
            KeyboardFace.ORDER_ASCII_FIRST.map { KeyboardFace.next(KeyboardFace.ORDER_ASCII_FIRST, it) },
            KeyboardFace.ORDER_ASCII_FIRST.map { KeyboardFace.next(rotated, it) }
        )
    }

    @Test
    fun kanaIsSkippedWhenTheAppIsNotJapanese() {
        val faces = KeyboardFace.available(KeyboardFace.ORDER_ASCII_FIRST, allowKana = false)
        assertEquals(listOf(KeyboardFace.ASCII, KeyboardFace.NUMBER), faces)
        // 英語では 2 面なので、どちらの順でも「もう片方へ」しか無い。
        assertEquals(KeyboardFace.NUMBER, KeyboardFace.next(faces, KeyboardFace.ASCII))
        assertEquals(KeyboardFace.ASCII, KeyboardFace.next(faces, KeyboardFace.NUMBER))
    }

    @Test
    fun numberFaceOffRestoresTheOldTwoFaceCycle() {
        val order = KeyboardFace.orderFrom(KeyboardFace.ORDER_ASCII_FIRST_ID, numberFace = false)
        val faces = KeyboardFace.available(order, allowKana = true)
        assertEquals(listOf(KeyboardFace.KANA, KeyboardFace.ASCII), faces)
        assertEquals(KeyboardFace.ASCII, KeyboardFace.next(faces, KeyboardFace.KANA))
        assertEquals(KeyboardFace.KANA, KeyboardFace.next(faces, KeyboardFace.ASCII))
    }

    @Test
    fun asciiAlwaysSurvivesSoTheKeyboardNeverRunsOutOfFaces() {
        // 英語 ∧ 数字面 OFF = 英字面だけ。ここが空になると切替キーの行き先が無くなる。
        val order = KeyboardFace.orderFrom(KeyboardFace.ORDER_ASCII_FIRST_ID, numberFace = false)
        val faces = KeyboardFace.available(order, allowKana = false)
        assertEquals(listOf(KeyboardFace.ASCII), faces)
        assertEquals(KeyboardFace.ASCII, KeyboardFace.next(faces, KeyboardFace.ASCII))
    }

    @Test
    fun currentFaceOutsideTheCycleFallsBackToTheFirst() {
        // 設定を変えた直後は「いま出ている面」が巡回から外れることがある。そこで詰まると
        // 切替キーが効かなくなるので、先頭へ戻す。
        val faces = KeyboardFace.available(KeyboardFace.ORDER_ASCII_FIRST, allowKana = false)
        assertTrue(KeyboardFace.KANA !in faces)
        assertEquals(KeyboardFace.ASCII, KeyboardFace.next(faces, KeyboardFace.KANA))
    }

    @Test
    fun storedOrderIdSurvivesARoundTrip() {
        for (order in KeyboardFace.ORDERS) {
            assertEquals(order, KeyboardFace.orderById(KeyboardFace.orderIdOf(order)))
        }
        // 知らない値・未設定は既定の「あ → A → 12」。
        assertEquals(KeyboardFace.ORDER_ASCII_FIRST, KeyboardFace.orderById(null))
        assertEquals(KeyboardFace.ORDER_ASCII_FIRST, KeyboardFace.orderById("nonsense"))
    }

    @Test
    fun storedFaceIdSurvivesARoundTripAndFallsBackToAscii() {
        for (face in KeyboardFace.entries) {
            assertEquals(face, KeyboardFace.byId(face.id))
        }
        assertEquals(KeyboardFace.ASCII, KeyboardFace.byId(null))
        assertEquals(KeyboardFace.ASCII, KeyboardFace.byId("nonsense"))
    }

    @Test
    fun switchLabelNamesTheDestinationFace() {
        // ラベルは「いま居る面」ではなく「押すと行く面」。2 面のときは区別が付かなかったが、
        // 3 面あるとここを取り違えると切替キーが嘘をつく。
        assertEquals("あ", KeyboardFace.KANA.switchLabel)
        assertEquals("ABC", KeyboardFace.ASCII.switchLabel)
        assertEquals("12", KeyboardFace.NUMBER.switchLabel)
    }

    @Test
    fun builtInAndCustomFacesCanBeFreelyOrderedAndEnabled() {
        val customKana = KeyLayout("my-kana", "かな改", emptyList(), KeyboardFace.KANA.id)
        val customAscii = KeyLayout("my-ascii", "Colemak", emptyList(), KeyboardFace.ASCII.id)
        val order = listOf(
            KeyboardFaceEntry.custom(customAscii),
            KeyboardFaceEntry.builtin(KeyboardFace.NUMBER),
            KeyboardFaceEntry.custom(customKana),
            KeyboardFaceEntry.builtin(KeyboardFace.KANA),
            KeyboardFaceEntry.builtin(KeyboardFace.ASCII),
        )
        val enabled = setOf(order[0].id, order[1].id, order[2].id)

        val resolved = KeyboardFaceConfig.enabledEntries(
            orderValue = KeyboardFaceConfig.encodeOrder(order),
            enabledValue = KeyboardFaceConfig.encodeEnabled(order, enabled),
            layouts = listOf(customKana, customAscii),
            legacyNumberEnabled = false,
            legacyActiveLayoutId = "",
            legacyKanaAvailable = false,
        )

        assertEquals(listOf(order[0].id, order[1].id, order[2].id), resolved.map { it.id })
        assertEquals(listOf("Cole", "12", "かな改"), resolved.map { it.switchLabel })
    }

    @Test
    fun layoutsMissingFromStoredOrderAreAppendedWithoutLosingSavedOrder() {
        val custom = KeyLayout("custom", "Custom", emptyList())
        val entries = KeyboardFaceConfig.allEntries(
            "${KeyboardFaceEntry.BUILTIN_NUMBER_ID},${KeyboardFaceEntry.BUILTIN_ASCII_ID}",
            listOf(custom),
        )

        assertEquals(KeyboardFaceEntry.BUILTIN_NUMBER_ID, entries[0].id)
        assertEquals(KeyboardFaceEntry.BUILTIN_ASCII_ID, entries[1].id)
        assertTrue(KeyboardFaceEntry.BUILTIN_KANA_ID in entries.map { it.id })
        assertTrue(KeyboardFaceEntry.customId(custom.id) in entries.map { it.id })
    }

    @Test
    fun blankNewSettingMigratesThePreviouslyActiveCustomLayout() {
        val custom = KeyLayout("custom", "Custom", emptyList(), KeyboardFace.ASCII.id)
        val entries = KeyboardFaceConfig.allEntries(null, listOf(custom))
        val enabled = KeyboardFaceConfig.enabledIds(
            enabledValue = "",
            entries = entries,
            legacyNumberEnabled = true,
            legacyActiveLayoutId = custom.id,
            legacyKanaAvailable = true,
        )

        assertTrue(KeyboardFaceEntry.customId(custom.id) in enabled)
        assertTrue(KeyboardFaceEntry.BUILTIN_ASCII_ID !in enabled)
        assertTrue(KeyboardFaceEntry.BUILTIN_KANA_ID in enabled)
        assertTrue(KeyboardFaceEntry.BUILTIN_NUMBER_ID in enabled)
    }

    @Test
    fun rememberedLegacyImeFaceResolvesToBuiltInEntryId() {
        val entries = KeyboardFaceConfig.allEntries(null, emptyList())
        assertEquals(
            KeyboardFaceEntry.BUILTIN_KANA_ID,
            KeyboardFaceConfig.initialEntryId(KeyboardFace.KANA.id, entries),
        )
    }

    @Test
    fun simpleFaceExistsButStartsDisabled() {
        val entries = KeyboardFaceConfig.allEntries(null, emptyList())
        val enabled = KeyboardFaceConfig.enabledIds(
            enabledValue = null,
            entries = entries,
            legacyNumberEnabled = false,
            legacyActiveLayoutId = "",
            legacyKanaAvailable = true,
        )

        val simple = entries.single { it.id == KeyboardFaceEntry.BUILTIN_ASCII_SIMPLE_ID }
        assertEquals(KeyboardStyle.COMPACT.id, simple.styleId)
        assertTrue(simple.id !in enabled)
        assertTrue(KeyboardFaceEntry.BUILTIN_ASCII_ID in enabled)
    }
}
