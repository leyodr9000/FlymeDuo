/*
 * Copyright (C) 2026 yixing233 and HyperDuo contributors
 *
 * This file is part of HyperDuo, released under the GNU General Public
 * License v3.0. See the LICENSE file at the repository root, or
 * <https://www.gnu.org/licenses/gpl-3.0.html>.
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package io.github.yixing233.hyperduo;

import android.content.Context;
import android.graphics.Canvas;
import android.view.View;

/**
 * The ring in a box of its own.
 *
 * <p>Flyme's battery view is the wrong place to draw: its width and visibility
 * follow the four stock styles and the charging state - "percentage only" even
 * hides the whole view when the charger is pulled - so a glyph drawn into it
 * moved, shrank, or disappeared with them.
 *
 * <p>This view sits where the battery view was and is sized once, so the ring
 * looks the same whatever the stock style is doing. The stock view stays alive
 * as the state source; it is only hidden.
 */
final class FlymeRingView extends View {

    private final View host;

    FlymeRingView(Context context, View host) {
        super(context);
        this.host = host;
        setClickable(false);
        setFocusable(false);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!TrioConfig.get().enabled) {
            return;
        }
        TrioState state = FlymeHooks.stateFor(host);
        if (state == null) {
            return;
        }
        // The values still come from Flyme's own view: it keeps tracking the
        // level, the charger and the tint even while it is hidden.
        state.refresh();
        TrioRenderer.drawInto(canvas, getWidth(), getHeight(),
                state.level, state.charging, state.quickCharging, state.powerSave, state.low,
                state.wifiPresent ? state.wifiLevel : -1, state.mobileLevel,
                state.slotLevels, state.mobileType, state.foreground(),
                TrioConfig.get(), true, state.wifiPresent);
    }
}
