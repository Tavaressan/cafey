package br.com.tavaressan.cafey.shared.ui

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

class NavShellSizeClassTest {

    @Test
    fun belowMediumBreakpointIsCompact() {
        assertEquals(NavShellSizeClass.Compact, navShellSizeClassFor(390.dp))
        assertEquals(NavShellSizeClass.Compact, navShellSizeClassFor(767.dp))
    }

    @Test
    fun betweenBreakpointsIsMedium() {
        assertEquals(NavShellSizeClass.Medium, navShellSizeClassFor(768.dp))
        assertEquals(NavShellSizeClass.Medium, navShellSizeClassFor(1023.dp))
    }

    @Test
    fun aboveExpandedBreakpointIsExpanded() {
        assertEquals(NavShellSizeClass.Expanded, navShellSizeClassFor(1024.dp))
        assertEquals(NavShellSizeClass.Expanded, navShellSizeClassFor(1440.dp))
    }

    @Test
    fun compactContentWidthUsesPlatformCompactMax() {
        assertEquals(390.dp, contentMaxWidthFor(NavShellSizeClass.Compact, compactMax = 390.dp))
    }

    @Test
    fun mediumContentWidthMatchesTabletShell() {
        assertEquals(754.dp, contentMaxWidthFor(NavShellSizeClass.Medium, compactMax = 390.dp))
    }

    @Test
    fun expandedContentWidthMatchesDesktopShell() {
        assertEquals(1018.dp, contentMaxWidthFor(NavShellSizeClass.Expanded, compactMax = 390.dp))
    }
}
