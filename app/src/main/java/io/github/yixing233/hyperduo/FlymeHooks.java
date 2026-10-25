package io.github.yixing233.hyperduo;

/* loaded from: classes3.dex */
final class FlymeHooks {
    private static final java.lang.String BATTERY_VIEW = "com.flyme.statusbar.battery.FlymeBatteryMeterView";
    private static final java.lang.String ICON_CONTAINER = "com.android.systemui.statusbar.phone.StatusIconContainer";
    private static final java.lang.String ICON_VIEW_BINDER = "com.android.systemui.common.ui.binder.IconViewBinder";
    private static final java.lang.String KEYGUARD_STATUS_BAR_VIEW = "com.android.systemui.statusbar.phone.KeyguardStatusBarView";
    private static final java.lang.String MOBILE_SIGNAL_DRAWABLE = "com.android.settingslib.graph.SignalDrawable";
    private static final java.lang.String PHONE_STATUS_BAR_VIEW = "com.android.systemui.statusbar.phone.PhoneStatusBarView";
    private static final java.lang.String QS_STATUS_BAR = "com.flyme.systemui.controlcenter.qs.QSStatusBar";
    private static final java.lang.String RESOURCE_ICON = "com.android.systemui.common.shared.model.Icon$Resource";
    private static final java.lang.String SYSTEM_ICON = "com.android.systemui.common.shared.model.Icon";
    private static final java.lang.String TAG = "HyperDuo";
    private static final java.lang.String WIFI_STATE = "com.flyme.systemui.statusbar.net.wifi.WifiIconState";
    private static final java.lang.String WIFI_VIEW = "com.flyme.systemui.statusbar.net.wifi.FlymeStatusBarWifiView";
    private static volatile boolean sActive;
    private static volatile java.lang.reflect.Field sBatteryClipWidthField;
    private static volatile java.lang.reflect.Field sBatteryDrawableField;
    private static volatile java.lang.reflect.Field sBatteryNormalDrawableField;
    private static volatile java.lang.reflect.Field sBatteryPercentField;
    private static volatile java.lang.reflect.Field sIgnoredSlotsField;
    private static volatile java.lang.reflect.Field sKeyguardIconContainerField;
    private static volatile io.github.libxposed.api.XposedModule sModule;
    private static volatile java.lang.reflect.Field sQsIconContainerField;
    private static volatile java.lang.reflect.Field sResourceIconResField;
    private static volatile java.lang.reflect.Field sSystemIconAreaField;
    private static volatile java.lang.reflect.Field sWifiStateResIdField;
    private static volatile java.lang.reflect.Field sWifiViewStateField;
    private static final java.util.List<java.lang.String> MANAGED_SLOTS = java.util.Collections.unmodifiableList(java.util.Arrays.asList("wifi", "mobile", "stacked_mobile"));
    private static final java.util.Map<android.view.View, io.github.yixing233.hyperduo.TrioState> STATES = java.util.Collections.synchronizedMap(new java.util.WeakHashMap());
    private static final java.util.Map<android.view.View, java.lang.Integer> SAVED_VISIBILITY = java.util.Collections.synchronizedMap(new java.util.WeakHashMap());
    private static final java.util.Map<java.lang.Object, java.util.List<java.lang.String>> ADDED_SLOTS = java.util.Collections.synchronizedMap(new java.util.WeakHashMap());
    /** Battery host -> the out-of-ring type label mounted beside it. */
    private static final java.util.Map<android.view.View, android.widget.TextView> TYPE_LABELS = java.util.Collections.synchronizedMap(new java.util.WeakHashMap());
    private static final java.util.Set<android.view.View> TYPE_LABEL_PENDING = java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<android.view.View, java.lang.Boolean>());
    private static final java.util.Map<android.view.ViewGroup, java.lang.Boolean> STATUS_ICON_CONTAINERS = java.util.Collections.synchronizedMap(new java.util.WeakHashMap());

    // Reflection on the layout path has to be cached: onLayout runs per frame,
    // and a getMethod() per child per pass was what turned a re-layout into an
    // ANR. The re-entry flag keeps a setVisibility() that schedules another
    // layout pass from nesting inside this one.
    private static final java.util.Map<java.lang.Class<?>, java.lang.reflect.Method> SLOT_METHODS = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Set<java.lang.Class<?>> NO_METHOD = java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<java.lang.Class<?>, java.lang.Boolean>());
    private static volatile long sLastSlotPass;

    private FlymeHooks() {
    }

    private static volatile java.lang.reflect.Method sSetMeasuredDimension;
    /** One box line per battery host, so a wrong size is quotable from the log. */
    private static final java.util.Set<android.view.View> MEASURE_LOG = java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<android.view.View, java.lang.Boolean>());

    /**
     * {@code View.setMeasuredDimension} is protected, and this is not the view's
     * own class - the one reflection call on the measure path, cached like the
     * layout-path lookups above.
     */
    private static void setMeasuredSize(android.view.View host, int w, int h) {
        try {
            java.lang.reflect.Method m = sSetMeasuredDimension;
            if (m == null) {
                m = android.view.View.class.getDeclaredMethod("setMeasuredDimension",
                        java.lang.Integer.TYPE, java.lang.Integer.TYPE);
                m.setAccessible(true);
                sSetMeasuredDimension = m;
            }
            m.invoke(host, w, h);
        } catch (java.lang.Throwable t) {
            log(sModule, "measured size override failed: " + t);
        }
    }

    private static java.lang.Object callCached(java.util.Map<java.lang.Class<?>, java.lang.reflect.Method> cache, java.lang.Object target, java.lang.String name) {
        if (target == null) {
            return null;
        }
        java.lang.Class<?> type = target.getClass();
        if (NO_METHOD.contains(type)) {
            return null;
        }
        java.lang.reflect.Method method = cache.get(type);
        if (method == null) {
            try {
                method = type.getMethod(name);
                method.setAccessible(true);
            } catch (java.lang.Throwable t) {
                // Remember the miss: building the exception per call per child per
                // layout pass is what made this path expensive enough to ANR.
                NO_METHOD.add(type);
                return null;
            }
            cache.put(type, method);
        }
        try {
            return method.invoke(target);
        } catch (java.lang.Throwable t) {
            return null;
        }
    }

    static boolean isFlyme(java.lang.ClassLoader cl) {
        return (io.github.yixing233.hyperduo.Refl.cls(BATTERY_VIEW, cl) == null || io.github.yixing233.hyperduo.Refl.cls(WIFI_VIEW, cl) == null) ? false : true;
    }

    static boolean isActive() {
        return sActive;
    }

    /**
     * Build marker, printed once at install time. An updated module APK does not
     * reload into a running SystemUI - the old dex keeps executing until the
     * process is killed - so "installed the new APK but nothing changed" is
     * indistinguishable from "the fix does not work" unless the log can say which
     * build answered.
     */
    static final String ADAPTER_BUILD = "1.7.5-flyme.33";

    static int install(io.github.libxposed.api.XposedModule module, java.lang.ClassLoader cl) {
        sModule = module;
        sActive = true;
        log(module, "flyme adapter build " + ADAPTER_BUILD);
        int count = hookBatteryView(module, cl);
        return count + hookStatusBarCapture(module, cl) + hookControlCenterCapture(module, cl) + hookKeyguardStatusBarCapture(module, cl) + hookIconContainer(module, cl) + hookWifiState(module, cl) + hookMobileSignal(module, cl) + hookMobileTypeIcon(module, cl);
    }

    static void onConfigChanged() {
        // The master switch decides what the stock cluster should look like: take
        // the percentage text out while the ring draws into the battery view, put
        // it back when it does not. No view is inserted, so nothing here changes
        // the row's geometry - that was the whole left-shift defect.
        syncStockIcons();
        final boolean enabled = io.github.yixing233.hyperduo.TrioConfig.get().enabled;
        java.util.List<android.view.View> hosts = new java.util.ArrayList<>();
        synchronized (STATES) {
            hosts.addAll(STATES.keySet());
        }
        for (int i2 = 0; i2 < hosts.size(); i2++) {
            final android.view.View host = hosts.get(i2);
            if (host != null) {
                host.post(new java.lang.Runnable() { // from class: io.github.yixing233.hyperduo.FlymeHooks.2
                    @Override // java.lang.Runnable
                    public void run() {
                        if (enabled && !io.github.yixing233.hyperduo.FlymeHooks.excludedRegion(host)) {
                            io.github.yixing233.hyperduo.FlymeHooks.hideBatteryExtras(host);
                            io.github.yixing233.hyperduo.FlymeHooks.applyBatteryPercent(host);
                            io.github.yixing233.hyperduo.FlymeHooks.ensureHostVisible(host);
                        } else {
                            io.github.yixing233.hyperduo.FlymeHooks.restoreBatteryExtras(host);
                            io.github.yixing233.hyperduo.FlymeHooks.applyBatteryPercent(host);
                        }
                        io.github.yixing233.hyperduo.FlymeHooks.syncTypeLabel(host);
                        host.requestLayout();
                        host.invalidate();
                    }
                });
            }
        }
    }

    private static int hookBatteryView(io.github.libxposed.api.XposedModule module, java.lang.ClassLoader cl) {
        java.lang.Class<?> viewClass = io.github.yixing233.hyperduo.Refl.cls(BATTERY_VIEW, cl);
        if (viewClass == null) {
            log(module, "FlymeBatteryMeterView missing");
            return 0;
        }
        sBatteryPercentField = io.github.yixing233.hyperduo.Refl.field(viewClass, "mBatteryPercentView");
        sBatteryDrawableField = io.github.yixing233.hyperduo.Refl.field(viewClass, "mDrawable");
        sBatteryNormalDrawableField = io.github.yixing233.hyperduo.Refl.field(viewClass, "mBatteryNormal");
        sBatteryClipWidthField = io.github.yixing233.hyperduo.Refl.field(viewClass, "mClipWidth");
        int draw = hook(module, io.github.yixing233.hyperduo.Refl.method(viewClass, "onDraw", android.graphics.Canvas.class), "hyperduo-flyme-draw", new io.github.libxposed.api.XposedInterface.Hooker() { // from class: io.github.yixing233.hyperduo.FlymeHooks.3
            public java.lang.Object intercept(io.github.libxposed.api.XposedInterface.Chain chain) throws java.lang.Throwable {
                java.lang.Object self = chain.getThisObject();
                java.lang.Object arg = chain.getArg(0);
                if (!(self instanceof android.view.View) || !(arg instanceof android.graphics.Canvas)) {
                    return chain.proceed();
                }
                android.view.View host = (android.view.View) self;
                io.github.yixing233.hyperduo.TrioConfig.installReceiver(host.getContext());
                io.github.yixing233.hyperduo.TrioState.attachContext(host.getContext());
                if (!io.github.yixing233.hyperduo.TrioConfig.get().enabled) {
                    io.github.yixing233.hyperduo.FlymeHooks.restoreBatteryExtras(host);
                    io.github.yixing233.hyperduo.FlymeHooks.applyBatteryPercent(host);
                    return chain.proceed();
                }
                // A region the user excluded keeps Flyme's own battery, stock look and all.
                if (io.github.yixing233.hyperduo.FlymeHooks.excludedRegion(host)) {
                    io.github.yixing233.hyperduo.FlymeHooks.applyBatteryPercent(host);
                    return chain.proceed();
                }
                // Draw into Flyme's own view, but keep that view's box fixed (see
                // the measure hook) and take its percentage text out of the row:
                // together those two are what make the drawing independent of the
                // four stock styles without adding a view of our own.
                io.github.yixing233.hyperduo.FlymeHooks.hideBatteryExtras(host);
                io.github.yixing233.hyperduo.FlymeHooks.applyBatteryPercent(host);
                io.github.yixing233.hyperduo.TrioState state =
                        io.github.yixing233.hyperduo.FlymeHooks.stateFor(host);
                state.refresh();
                io.github.yixing233.hyperduo.TrioRenderer.drawFlyme((android.graphics.Canvas) arg,
                        host, state, io.github.yixing233.hyperduo.TrioConfig.get());
                return null;
            }
        });
        // The stock styles resize this view (the percentage can be part of its
        // width) and "percentage only" even hides it when the charger is pulled.
        // Measuring always as the non-charging battery gives the ring one box for
        // every style; the percentage text is taken out of the row separately, so
        // nothing is left varying that the row would have to follow.
        int measure = hook(module, io.github.yixing233.hyperduo.Refl.method(viewClass, "onMeasure", java.lang.Integer.TYPE, java.lang.Integer.TYPE), "hyperduo-flyme-battery-measure", new io.github.libxposed.api.XposedInterface.Hooker() {
            public java.lang.Object intercept(io.github.libxposed.api.XposedInterface.Chain chain) throws java.lang.Throwable {
                java.lang.Object self = chain.getThisObject();
                if (!(self instanceof android.view.View)
                        || !io.github.yixing233.hyperduo.TrioConfig.get().enabled
                        || io.github.yixing233.hyperduo.FlymeHooks.excludedRegion((android.view.View) self)) {
                    return chain.proceed();
                }
                java.lang.Object currentDrawable = io.github.yixing233.hyperduo.Refl.get(io.github.yixing233.hyperduo.FlymeHooks.sBatteryDrawableField, self);
                java.lang.Object stableDrawable = io.github.yixing233.hyperduo.Refl.get(io.github.yixing233.hyperduo.FlymeHooks.sBatteryNormalDrawableField, self);
                if (!(currentDrawable instanceof android.graphics.drawable.Drawable)
                        || !(stableDrawable instanceof android.graphics.drawable.Drawable)) {
                    return chain.proceed();
                }
                int clipWidth = io.github.yixing233.hyperduo.Refl.getInt(io.github.yixing233.hyperduo.FlymeHooks.sBatteryClipWidthField, self, 0);
                io.github.yixing233.hyperduo.Refl.set(io.github.yixing233.hyperduo.FlymeHooks.sBatteryDrawableField, self, stableDrawable);
                io.github.yixing233.hyperduo.Refl.set(io.github.yixing233.hyperduo.FlymeHooks.sBatteryClipWidthField, self, 0);
                java.lang.Object drawn;
                try {
                    drawn = chain.proceed();
                } finally {
                    io.github.yixing233.hyperduo.Refl.set(io.github.yixing233.hyperduo.FlymeHooks.sBatteryDrawableField, self, currentDrawable);
                    io.github.yixing233.hyperduo.Refl.set(io.github.yixing233.hyperduo.FlymeHooks.sBatteryClipWidthField, self, java.lang.Integer.valueOf(clipWidth));
                }
                // The scale the ring actually renders at. The stock measure above
                // locks this view to the battery drawable's intrinsic box - a box
                // built for a ~10dp icon - so the 120-unit design space collapsed
                // into it and the whole glyph starved. The row is far taller than
                // that: the height spec the parent handed in IS the row height.
                // Measure a SQUARE of it (times the glyph-scale slider, which on
                // Flyme therefore means "share of the status bar row", 100% = full
                // height) and the renderer's uniform min(w,h) fit fills it. The
                // icons container beside it carries weight=1 and absorbs the width.
                android.view.View host = (android.view.View) self;
                // The row this view actually lives in. The height spec alone does
                // not answer on every surface - the plain status bar hands its row
                // down in a shape the spec read misses, which is why 1.7.5-flyme.31
                // scaled the control centre and the notification header but not the
                // bar. By the time a child is measured the parent has just set its
                // own size, and system_icons is match_parent on all three surfaces:
                // its measured height IS the height the row reserved for the
                // battery.
                int rowHeight = 0;
                android.view.ViewParent row = host.getParent();
                if (row instanceof android.view.View) {
                    rowHeight = ((android.view.View) row).getMeasuredHeight();
                }
                if (rowHeight <= 0) {
                    int heightSpec = ((java.lang.Number) chain.getArg(1)).intValue();
                    rowHeight = android.view.View.MeasureSpec.getSize(heightSpec);
                }
                int side = java.lang.Math.round(rowHeight
                        * io.github.yixing233.hyperduo.TrioConfig.appearance().glyphFit());
                if (side > 0 && side < 10000) {
                    setMeasuredSize(host, side, side);
                    if (MEASURE_LOG.add(host)) {
                        log(sModule, "battery box " + host.getClass().getSimpleName()
                                + " row=" + rowHeight + " side=" + side
                                + " region=" + regionName(regionOf(host))
                                + " excluded=" + excludedRegion(host));
                    }
                }
                return drawn;
            }
        });
        // "Percentage only" is the one stock style that hides the whole view when
        // the cable is out (FlymeBatteryMeterView.apply -> setVisibility(8)). A GONE
        // view is not drawn, so the ring could not paint at all - and in that style
        // the number lives in the sibling text view, which hideBatteryExtras takes
        // out, so the cluster went blank. Coercing the host back to VISIBLE after
        // Flyme's own decision keeps it drawn; nothing is coerced while the module
        // is off or the region is excluded.
        int apply = hook(module, io.github.yixing233.hyperduo.Refl.method(viewClass, "apply", java.lang.Boolean.TYPE), "hyperduo-flyme-apply", new io.github.libxposed.api.XposedInterface.Hooker() {
            public java.lang.Object intercept(io.github.libxposed.api.XposedInterface.Chain chain) throws java.lang.Throwable {
                java.lang.Object result = chain.proceed();
                java.lang.Object self = chain.getThisObject();
                if (self instanceof android.view.View) {
                    io.github.yixing233.hyperduo.FlymeHooks.ensureHostVisible((android.view.View) self);
                }
                return result;
            }
        });
        int levelChanged = hook(module, io.github.yixing233.hyperduo.Refl.method(viewClass, "onBatteryLevelChanged", java.lang.Integer.TYPE, java.lang.Boolean.TYPE, java.lang.Boolean.TYPE), "hyperduo-flyme-battery-level", new io.github.libxposed.api.XposedInterface.Hooker() { // from class: io.github.yixing233.hyperduo.FlymeHooks.5
            public java.lang.Object intercept(io.github.libxposed.api.XposedInterface.Chain chain) throws java.lang.Throwable {
                java.lang.Object result = chain.proceed();
                java.lang.Object self = chain.getThisObject();
                java.lang.Object value = chain.getArg(0);
                java.lang.Object plugged = chain.getArg(1);
                java.lang.Object charging = chain.getArg(2);
                if ((self instanceof android.view.View) && (value instanceof java.lang.Number) && (plugged instanceof java.lang.Boolean) && (charging instanceof java.lang.Boolean)) {
                    io.github.yixing233.hyperduo.TrioState.noteFlymeBatteryState(((java.lang.Number) value).intValue(), ((java.lang.Boolean) plugged).booleanValue(), ((java.lang.Boolean) charging).booleanValue());
                    // The host is hidden while the ring stands in for it, so this
                    // goes through the shared path, which also repaints the ring.
                    io.github.yixing233.hyperduo.FlymeHooks.invalidateHosts();
                }
                return result;
            }
        });
        int dark = hook(module, io.github.yixing233.hyperduo.Refl.method(viewClass, "onDarkChanged", java.util.ArrayList.class, java.lang.Float.TYPE, java.lang.Integer.TYPE), "hyperduo-flyme-dark", new io.github.libxposed.api.XposedInterface.Hooker() { // from class: io.github.yixing233.hyperduo.FlymeHooks.6
            public java.lang.Object intercept(io.github.libxposed.api.XposedInterface.Chain chain) throws java.lang.Throwable {
                java.lang.Object result = chain.proceed();
                java.lang.Object self = chain.getThisObject();
                if (self instanceof android.view.View) {
                    io.github.yixing233.hyperduo.TrioState state = io.github.yixing233.hyperduo.FlymeHooks.stateFor((android.view.View) self);
                    java.lang.Object intensity = chain.getArg(1);
                    java.lang.Object tint = chain.getArg(2);
                    if (intensity instanceof java.lang.Number) {
                        state.darkIntensity = ((java.lang.Number) intensity).floatValue();
                    }
                    if (tint instanceof java.lang.Number) {
                        int color = ((java.lang.Number) tint).intValue();
                        state.useTint = color != 0;
                        state.tintColor = color;
                        state.lightColor = color;
                        state.darkColor = color;
                    }
                    io.github.yixing233.hyperduo.FlymeHooks.invalidateHosts();
                }
                return result;
            }
        });
        int detached = hook(module, io.github.yixing233.hyperduo.Refl.method(viewClass, "onDetachedFromWindow", new java.lang.Class[0]), "hyperduo-flyme-detach", new io.github.libxposed.api.XposedInterface.Hooker() { // from class: io.github.yixing233.hyperduo.FlymeHooks.7
            public java.lang.Object intercept(io.github.libxposed.api.XposedInterface.Chain chain) throws java.lang.Throwable {
                java.lang.Object result = chain.proceed();
                java.lang.Object self = chain.getThisObject();
                if (self instanceof android.view.View) {
                    io.github.yixing233.hyperduo.FlymeHooks.removeTypeLabel((android.view.View) self);
                    io.github.yixing233.hyperduo.FlymeHooks.restoreBatteryPercent((android.view.View) self);
                    io.github.yixing233.hyperduo.FlymeHooks.STATES.remove((android.view.View) self);
                    io.github.yixing233.hyperduo.FlymeHooks.REGION_CACHE.remove((android.view.View) self);
                }
                return result;
            }
        });
        // Re-classify on every attachment: a host inflated and measured before it
        // met its real parent must not keep a region filed from that orphan pass,
        // and a view re-parented onto another surface moves with this.
        int attached = hook(module, io.github.yixing233.hyperduo.Refl.method(viewClass, "onAttachedToWindow", new java.lang.Class[0]), "hyperduo-flyme-attach", new io.github.libxposed.api.XposedInterface.Hooker() { // from class: io.github.yixing233.hyperduo.FlymeHooks.7b
            public java.lang.Object intercept(io.github.libxposed.api.XposedInterface.Chain chain) throws java.lang.Throwable {
                java.lang.Object result = chain.proceed();
                java.lang.Object self = chain.getThisObject();
                if (self instanceof android.view.View) {
                    io.github.yixing233.hyperduo.FlymeHooks.REGION_CACHE.remove((android.view.View) self);
                    io.github.yixing233.hyperduo.FlymeHooks.MEASURE_LOG.remove((android.view.View) self);
                }
                return result;
            }
        });
        return draw + measure + apply + levelChanged + dark + detached + attached;
    }

    private static int hookStatusBarCapture(io.github.libxposed.api.XposedModule module, java.lang.ClassLoader cl) {
        java.lang.Class<?> barClass = io.github.yixing233.hyperduo.Refl.cls(PHONE_STATUS_BAR_VIEW, cl);
        if (barClass == null) {
            log(module, "PhoneStatusBarView missing");
            return 0;
        }
        sSystemIconAreaField = io.github.yixing233.hyperduo.Refl.field(barClass, "mSystemIconArea");
        return hook(module, io.github.yixing233.hyperduo.Refl.method(barClass, "onFinishInflate", new java.lang.Class[0]), "hyperduo-flyme-statusbar", new io.github.libxposed.api.XposedInterface.Hooker() { // from class: io.github.yixing233.hyperduo.FlymeHooks.8
            public java.lang.Object intercept(io.github.libxposed.api.XposedInterface.Chain chain) throws java.lang.Throwable {
                java.lang.Object result = chain.proceed();
                java.lang.Object self = chain.getThisObject();
                java.lang.Object area = io.github.yixing233.hyperduo.Refl.get(io.github.yixing233.hyperduo.FlymeHooks.sSystemIconAreaField, self);
                if (area instanceof android.view.View) {
                    android.view.ViewGroup found = io.github.yixing233.hyperduo.FlymeHooks.findIconContainer((android.view.View) area);
                    if (found != null) {
                        io.github.yixing233.hyperduo.FlymeHooks.registerIconContainer(found);
                        io.github.yixing233.hyperduo.FlymeHooks.log(io.github.yixing233.hyperduo.FlymeHooks.sModule, "Flyme status icon container captured: " + found.getClass().getName());
                    } else {
                        io.github.yixing233.hyperduo.FlymeHooks.log(io.github.yixing233.hyperduo.FlymeHooks.sModule, "StatusIconContainer not found under mSystemIconArea");
                    }
                }
                if (self instanceof android.view.View) {
                    io.github.yixing233.hyperduo.TrioConfig.installReceiver(((android.view.View) self).getContext());
                    io.github.yixing233.hyperduo.TrioState.attachContext(((android.view.View) self).getContext());
                }
                return result;
            }
        });
    }

    private static int hookControlCenterCapture(io.github.libxposed.api.XposedModule module, java.lang.ClassLoader cl) {
        java.lang.Class<?> qsStatusBar = io.github.yixing233.hyperduo.Refl.cls(QS_STATUS_BAR, cl);
        if (qsStatusBar == null) {
            log(module, "Flyme QSStatusBar missing");
            return 0;
        }
        sQsIconContainerField = io.github.yixing233.hyperduo.Refl.field(qsStatusBar, "mIconContainer");
        return hook(module, io.github.yixing233.hyperduo.Refl.method(qsStatusBar, "onFinishInflate", new java.lang.Class[0]), "hyperduo-flyme-control-center", new io.github.libxposed.api.XposedInterface.Hooker() { // from class: io.github.yixing233.hyperduo.FlymeHooks.9
            public java.lang.Object intercept(io.github.libxposed.api.XposedInterface.Chain chain) throws java.lang.Throwable {
                java.lang.Object result = chain.proceed();
                java.lang.Object self = chain.getThisObject();
                java.lang.Object icons = io.github.yixing233.hyperduo.Refl.get(io.github.yixing233.hyperduo.FlymeHooks.sQsIconContainerField, self);
                if (icons instanceof android.view.ViewGroup) {
                    // Left untouched on purpose: the control centre keeps its
                    // stock status bar, so nothing is hidden or re-laid out here.
                    io.github.yixing233.hyperduo.FlymeHooks.log(io.github.yixing233.hyperduo.FlymeHooks.sModule, "Flyme Control Center captured (left stock)");
                }
                if (self instanceof android.view.View) {
                    android.view.View view = (android.view.View) self;
                    io.github.yixing233.hyperduo.TrioConfig.installReceiver(view.getContext());
                    io.github.yixing233.hyperduo.TrioState.attachContext(view.getContext());
                }
                return result;
            }
        });
    }

    private static int hookKeyguardStatusBarCapture(io.github.libxposed.api.XposedModule module, java.lang.ClassLoader cl) {
        java.lang.Class<?> keyguardStatusBar = io.github.yixing233.hyperduo.Refl.cls(KEYGUARD_STATUS_BAR_VIEW, cl);
        if (keyguardStatusBar == null) {
            log(module, "Flyme KeyguardStatusBarView missing");
            return 0;
        }
        sKeyguardIconContainerField = io.github.yixing233.hyperduo.Refl.field(keyguardStatusBar, "mStatusIconContainer");
        return hook(module, io.github.yixing233.hyperduo.Refl.method(keyguardStatusBar, "onFinishInflate", new java.lang.Class[0]), "hyperduo-flyme-keyguard", new io.github.libxposed.api.XposedInterface.Hooker() { // from class: io.github.yixing233.hyperduo.FlymeHooks.10
            public java.lang.Object intercept(io.github.libxposed.api.XposedInterface.Chain chain) throws java.lang.Throwable {
                java.lang.Object result = chain.proceed();
                java.lang.Object self = chain.getThisObject();
                java.lang.Object icons = io.github.yixing233.hyperduo.Refl.get(io.github.yixing233.hyperduo.FlymeHooks.sKeyguardIconContainerField, self);
                if (icons instanceof android.view.ViewGroup) {
                    io.github.yixing233.hyperduo.FlymeHooks.registerIconContainer((android.view.ViewGroup) icons);
                    io.github.yixing233.hyperduo.FlymeHooks.log(io.github.yixing233.hyperduo.FlymeHooks.sModule, "Flyme Keyguard icon container captured");
                }
                if (self instanceof android.view.View) {
                    android.view.View view = (android.view.View) self;
                    io.github.yixing233.hyperduo.TrioConfig.installReceiver(view.getContext());
                    io.github.yixing233.hyperduo.TrioState.attachContext(view.getContext());
                }
                return result;
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void registerIconContainer(final android.view.ViewGroup container) {
        synchronized (STATUS_ICON_CONTAINERS) {
            if (!STATUS_ICON_CONTAINERS.containsKey(container)) {
                STATUS_ICON_CONTAINERS.put(container, java.lang.Boolean.TRUE);
                hideStockIconsOnce(container);
            }
        }
    }

    /**
     * Hides exactly the three slots the ring replaces, and nothing else.
     *
     * <p>Children are {@code StatusBarIconView}s whose slot only shows through
     * {@code getSlot()}. The pass runs once per capture and once more after the
     * first traversal; it never feeds back into another pass, which is what
     * livelocked the main thread before.
     */
    private static void hideStockIconsOnce(final android.view.ViewGroup container) {
        hideStockChildren(container, 0);
        hideStockChildren(container, 4000);
    }

    /** One pass over every captured container, rate limited. */
    static void hideAllStockNow() {
        long now = android.os.SystemClock.elapsedRealtime();
        if (now - sLastSlotPass < 500) {
            return;
        }
        sLastSlotPass = now;
        java.util.List<android.view.ViewGroup> containers = new java.util.ArrayList<>();
        synchronized (STATUS_ICON_CONTAINERS) {
            containers.addAll(STATUS_ICON_CONTAINERS.keySet());
        }
        for (int i = 0; i < containers.size(); i++) {
            hideStockChildren(containers.get(i), 0);
        }
    }

    /** Applies the current on/off state to the stock icons and asks for a re-layout. */
    static void syncStockIcons() {
        final boolean hide = io.github.yixing233.hyperduo.TrioConfig.get().enabled;
        java.util.List<android.view.ViewGroup> containers = new java.util.ArrayList<>();
        synchronized (STATUS_ICON_CONTAINERS) {
            containers.addAll(STATUS_ICON_CONTAINERS.keySet());
        }
        for (int i = 0; i < containers.size(); i++) {
            final android.view.ViewGroup container = containers.get(i);
            container.post(new java.lang.Runnable() {
                @Override
                public void run() {
                    applySlotVisibility(container, hide);
                    container.requestLayout();
                }
            });
        }
    }

    private static void hideStockChildren(final android.view.ViewGroup container, long delay) {
        container.postDelayed(new java.lang.Runnable() {
            @Override
            public void run() {
                if (!io.github.yixing233.hyperduo.TrioConfig.get().enabled) {
                    return;
                }
                applySlotVisibility(container, true);
            }
        }, delay);
    }

    /**
     * Hides the managed slots the ring actually stands in for - or puts them back.
     *
     * <p>{@code setVisibility(GONE)} alone leaves the space behind: Flyme's
     * container lays its slots out from {@code mIgnoredSlots}, and a slot that is
     * merely invisible still gets its width. Adding the slot to that list is what
     * actually frees the gap.
     *
     * <p>Both the list entry and the view belong to somebody. This pass therefore
     * only touches a slot when the glyph is drawing it ({@code appearance.wifi} /
     * {@code signalDots()}), records every entry it added, and remembers the
     * visibility it overrode, so releasing the switch never removes an entry the
     * ROM put there and never un-hides an icon the ROM had hidden.
     */
    @SuppressWarnings("unchecked")
    private static void applySlotVisibility(android.view.ViewGroup container, boolean hide) {
        try {
            // The keyguard header doubles as the notification centre's top row on
            // Flyme; when that surface is excluded, its icon row stays stock along
            // with its battery instead of losing its wifi and signal to a ring
            // that is no longer drawn there.
            if (hide && excludedRegion(container)) {
                hide = false;
            }
            java.lang.Object raw = io.github.yixing233.hyperduo.Refl.get(sIgnoredSlotsField, container);
            final java.util.List<java.lang.String> ignored = (raw instanceof java.util.List)
                    ? (java.util.List<java.lang.String>) raw : null;
            final io.github.yixing233.hyperduo.TrioAppearance a =
                    io.github.yixing233.hyperduo.TrioConfig.appearance();
            final java.util.List<java.lang.String> wanted = new java.util.ArrayList<>(3);
            if (hide && a.glyph) {
                // Every managed slot, every time the module owns the battery.
                // The placement settings say what the MODULE draws and where; they
                // are not the stock row's business. Making the native mobile icon
                // conditional on the ring drawing its own dots - which is what
                // 1.7.5-flyme.27/.28 did - is exactly how the stock signal icon
                // ended up on screen beside the ring: any placement the condition
                // did not cover (type outside the ring, signal outside the ring,
                // the card not dealt a slot) kept the native icon, and with it
                // Flyme's own type text. The 1.7.5-flyme.11 baseline this port
                // started from hid all three unconditionally.
                wanted.add("wifi");
                wanted.add("mobile");
                wanted.add("stacked_mobile");
            }
            final java.util.List<java.lang.String> added;
            synchronized (ADDED_SLOTS) {
                java.util.List<java.lang.String> current = ADDED_SLOTS.get(container);
                if (current == null) {
                    current = new java.util.ArrayList<>();
                    ADDED_SLOTS.put(container, current);
                }
                added = current;
            }
            boolean changed = false;
            for (int i = 0; i < wanted.size(); i++) {
                java.lang.String slot = wanted.get(i);
                if (ignored != null && !ignored.contains(slot)) {
                    ignored.add(slot);
                    synchronized (added) {
                        added.add(slot);
                    }
                    changed = true;
                }
            }
            for (int i = added.size() - 1; i >= 0; i--) {
                java.lang.String slot = added.get(i);
                if (!wanted.contains(slot)) {
                    if (ignored != null) {
                        ignored.remove(slot);
                    }
                    added.remove(i);
                    changed = true;
                }
            }
            for (int i = 0; i < container.getChildCount(); i++) {
                android.view.View child = container.getChildAt(i);
                java.lang.String slotName = slotOf(child);
                if (slotName == null || !MANAGED_SLOTS.contains(slotName)) {
                    continue;
                }
                if (wanted.contains(slotName)) {
                    hideChild(child);
                } else {
                    restoreChild(child);
                }
            }
            if (changed) {
                container.requestLayout();
            }
        } catch (java.lang.Throwable t) {
            log(sModule, "slot visibility failed: " + t);
        }
    }
    private static int hookIconContainer(io.github.libxposed.api.XposedModule module, java.lang.ClassLoader cl) {
        java.lang.Class<?> container = io.github.yixing233.hyperduo.Refl.cls(ICON_CONTAINER, cl);
        if (container == null) {
            log(module, "StatusIconContainer missing");
            return 0;
        }
        sIgnoredSlotsField = io.github.yixing233.hyperduo.Refl.field(container, "mIgnoredSlots");
        // The onLayout hook is deliberately not installed: see registerIconContainer.
        return 0;
    }

    private static int hookWifiState(io.github.libxposed.api.XposedModule module, java.lang.ClassLoader cl) {
        java.lang.Class<?> wifiClass = io.github.yixing233.hyperduo.Refl.cls(WIFI_VIEW, cl);
        java.lang.Class<?> stateClass = io.github.yixing233.hyperduo.Refl.cls(WIFI_STATE, cl);
        if (wifiClass == null || stateClass == null) {
            log(module, "Flyme Wi-Fi view/state missing");
            return 0;
        }
        sWifiViewStateField = io.github.yixing233.hyperduo.Refl.field(wifiClass, "mState");
        sWifiStateResIdField = io.github.yixing233.hyperduo.Refl.field(stateClass, "resId");
        return hook(module, io.github.yixing233.hyperduo.Refl.method(wifiClass, "updateState", stateClass), "hyperduo-flyme-wifi-state", new io.github.libxposed.api.XposedInterface.Hooker() { // from class: io.github.yixing233.hyperduo.FlymeHooks.12
            public java.lang.Object intercept(io.github.libxposed.api.XposedInterface.Chain chain) throws java.lang.Throwable {
                java.lang.Object result = chain.proceed();
                java.lang.Object self = chain.getThisObject();
                if (self instanceof android.view.View) {
                    java.lang.Object state = io.github.yixing233.hyperduo.Refl.get(io.github.yixing233.hyperduo.FlymeHooks.sWifiViewStateField, self);
                    int resId = io.github.yixing233.hyperduo.Refl.getInt(io.github.yixing233.hyperduo.FlymeHooks.sWifiStateResIdField, state, 0);
                    if (resId != 0) {
                        io.github.yixing233.hyperduo.TrioState.noteSignalIcon(((android.view.View) self).getResources(), resId);
                    }
                    java.lang.Object visible = io.github.yixing233.hyperduo.Refl.callByName(self, "isIconVisible");
                    // The view itself is hidden below, so its visibility stops
                    // answering this after the first pass. The state's resource id
                    // does: zero means there is no Wi-Fi icon to show.
                    io.github.yixing233.hyperduo.TrioState.setWifiPresent(resId != 0 || java.lang.Boolean.TRUE.equals(visible));
                    io.github.yixing233.hyperduo.FlymeHooks.invalidateHosts();
                    // The Wi-Fi view is hidden through the container slot pass,
                    // not here: this hook also runs for the control centre copy,
                    // which is meant to keep its stock icons.
                }
                return result;
            }
        });
    }

    private static int hookMobileSignal(io.github.libxposed.api.XposedModule module, java.lang.ClassLoader cl) {
        java.lang.Class<?> signal = io.github.yixing233.hyperduo.Refl.cls(MOBILE_SIGNAL_DRAWABLE, cl);
        if (signal != null) {
            return hook(module, io.github.yixing233.hyperduo.Refl.method(signal, "onLevelChange", java.lang.Integer.TYPE), "hyperduo-flyme-mobile-signal", new io.github.libxposed.api.XposedInterface.Hooker() { // from class: io.github.yixing233.hyperduo.FlymeHooks.13
                public java.lang.Object intercept(io.github.libxposed.api.XposedInterface.Chain chain) throws java.lang.Throwable {
                    java.lang.Object result = chain.proceed();
                    if (io.github.yixing233.hyperduo.TrioState.pollSimsNow()) {
                        io.github.yixing233.hyperduo.FlymeHooks.invalidateHosts();
                    }
                    // Flyme re-shows the mobile view on its own updates, so the
                    // hide is re-applied here - one pass, driven by an icon
                    // change rather than by layout.
                    io.github.yixing233.hyperduo.FlymeHooks.hideAllStockNow();
                    return result;
                }
            });
        }
        log(module, "SignalDrawable missing");
        return 0;
    }

    private static int hookMobileTypeIcon(io.github.libxposed.api.XposedModule module, java.lang.ClassLoader cl) {
        java.lang.Class<?> binder = io.github.yixing233.hyperduo.Refl.cls(ICON_VIEW_BINDER, cl);
        java.lang.Class<?> icon = io.github.yixing233.hyperduo.Refl.cls(SYSTEM_ICON, cl);
        java.lang.Class<?> resourceIcon = io.github.yixing233.hyperduo.Refl.cls(RESOURCE_ICON, cl);
        java.lang.Class<?> imageView = io.github.yixing233.hyperduo.Refl.cls("android.widget.ImageView", cl);
        if (binder == null || icon == null || resourceIcon == null || imageView == null) {
            log(module, "Flyme IconViewBinder/Icon.Resource missing");
            return 0;
        }
        sResourceIconResField = io.github.yixing233.hyperduo.Refl.field(resourceIcon, "res");
        int bind = hook(module, io.github.yixing233.hyperduo.Refl.method(binder, "bind", icon, imageView), "hyperduo-flyme-mobile-type", new io.github.libxposed.api.XposedInterface.Hooker() { // from class: io.github.yixing233.hyperduo.FlymeHooks.14
            public java.lang.Object intercept(io.github.libxposed.api.XposedInterface.Chain chain) throws java.lang.Throwable {
                java.lang.Object result = chain.proceed();
                java.lang.Object target = chain.getArg(1);
                if ((target instanceof android.widget.ImageView) && io.github.yixing233.hyperduo.FlymeHooks.isMobileTypeView((android.view.View) target)) {
                    java.lang.Object drawable = chain.getArg(0);
                    int resId = io.github.yixing233.hyperduo.Refl.getInt(io.github.yixing233.hyperduo.FlymeHooks.sResourceIconResField, drawable, 0);
                    java.lang.String type = io.github.yixing233.hyperduo.FlymeHooks.mobileTypeForResource(((android.view.View) target).getResources(), resId);
                    if (io.github.yixing233.hyperduo.TrioState.setMobileType(type)) {
                        io.github.yixing233.hyperduo.FlymeHooks.invalidateHosts();
                    }
                    // Flyme's own label is left alone: the ring's out-of-ring type
                    // is not ported to this adapter yet, and hiding this one took
                    // the type away entirely.
                    android.view.View typeView = (android.view.View) target;
                    if (!io.github.yixing233.hyperduo.TrioConfig.get().enabled
                            && typeView.getVisibility() == android.view.View.GONE) {
                        typeView.setVisibility(android.view.View.VISIBLE);
                    }
                }
                return result;
            }
        });
        int clear = hook(module, io.github.yixing233.hyperduo.Refl.method(imageView, "setImageResource", java.lang.Integer.TYPE), "hyperduo-flyme-mobile-type-clear", new io.github.libxposed.api.XposedInterface.Hooker() { // from class: io.github.yixing233.hyperduo.FlymeHooks.15
            public java.lang.Object intercept(io.github.libxposed.api.XposedInterface.Chain chain) throws java.lang.Throwable {
                java.lang.Object result = chain.proceed();
                java.lang.Object self = chain.getThisObject();
                java.lang.Object resId = chain.getArg(0);
                if ((self instanceof android.view.View) && io.github.yixing233.hyperduo.FlymeHooks.isMobileTypeView((android.view.View) self) && (resId instanceof java.lang.Number) && ((java.lang.Number) resId).intValue() == 0 && io.github.yixing233.hyperduo.TrioState.setMobileType("")) {
                    io.github.yixing233.hyperduo.FlymeHooks.invalidateHosts();
                }
                return result;
            }
        });
        return bind + clear;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static boolean isMobileTypeView(android.view.View view) {
        try {
            return "mobile_type".equals(view.getResources().getResourceEntryName(view.getId()));
        } catch (java.lang.Throwable th) {
            return false;
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static java.lang.String mobileTypeForResource(android.content.res.Resources resources, int resId) {
        java.lang.String name = "";
        if (resId == 0 || resources == null) {
            return "";
        }
        try {
            name = resources.getResourceEntryName(resId).toLowerCase(java.util.Locale.ROOT);
        } catch (java.lang.Throwable th) {
            return "";
        }
        if (!name.startsWith("ic_")) {
            return "";
        }
        if (name.startsWith("ic_5g_plus_plus")) {
            return "5G++";
        }
        if (name.startsWith("ic_5g_plus")) {
            return "5G+";
        }
        if (name.startsWith("ic_5g_a")) {
            return "5GA";
        }
        if (name.startsWith("ic_5g_e")) {
            return "5G E";
        }
        if (name.startsWith("ic_5g")) {
            return "5G";
        }
        if (!name.startsWith("ic_4g_plus") && !name.startsWith("ic_4g_lte_plus")) {
            if (!name.startsWith("ic_4g")) {
                return "";
            }
            return "4G";
        }
        return "4G+";
    }



    private static void hideChild(android.view.View child) {
        if (!SAVED_VISIBILITY.containsKey(child)) {
            SAVED_VISIBILITY.put(child, java.lang.Integer.valueOf(child.getVisibility()));
        }
        if (child.getVisibility() != 8) {
            child.setVisibility(8);
        }
    }

    private static void restoreChild(android.view.View child) {
        java.lang.Integer saved = SAVED_VISIBILITY.remove(child);
        if (saved != null && child.getVisibility() != saved.intValue()) {
            child.setVisibility(saved.intValue());
        }
    }


    /**
     * Takes the rest of Flyme's battery cluster out of the row.
     *
     * <p>The percentage is not always inside the battery view: for two of the
     * four stock styles it is a sibling of its own ({@code FlymeBatteryTextView}),
     * and that is why the number showed next to the ring and why the row kept
     * changing width.
     *
     * <p>The visibility it overrides is recorded, so turning the module off puts
     * the text back exactly as the stock style had it - a style that kept it
     * hidden stays hidden instead of gaining a number it never showed.
     */
    static void hideBatteryExtras(android.view.View host) {
        android.view.ViewParent parent = host.getParent();
        if (!(parent instanceof android.view.ViewGroup)) {
            return;
        }
        android.view.ViewGroup group = (android.view.ViewGroup) parent;
        for (int i = 0; i < group.getChildCount(); i++) {
            android.view.View child = group.getChildAt(i);
            if (child == host) {
                continue;
            }
            if (child.getClass().getName().contains("FlymeBatteryTextView")) {
                hideChild(child);
            }
        }
    }

    /** Puts back only the views {@link #hideBatteryExtras} took away. */
    static void restoreBatteryExtras(android.view.View host) {
        android.view.ViewParent parent = host.getParent();
        if (!(parent instanceof android.view.ViewGroup)) {
            return;
        }
        android.view.ViewGroup group = (android.view.ViewGroup) parent;
        for (int i = 0; i < group.getChildCount(); i++) {
            android.view.View child = group.getChildAt(i);
            if (child.getClass().getName().contains("FlymeBatteryTextView")) {
                restoreChild(child);
            }
        }
    }

    /**
     * Mounts, updates or removes the out-of-ring network-type label.
     *
     * <p>Flyme has no separate label view of its own: the type is an
     * {@code ImageView} inside the mobile slot, and hiding that slot to make room
     * for the ring takes the label with it. The "out of ring" placement therefore
     * needs a view of ours in the same row, which is also the only way the
     * out-of-ring size can mean anything - the box the ring is drawn into is the
     * battery icon's own footprint, and a label larger than that box would simply
     * be clipped by it.
     *
     * <p>Only a horizontal {@code LinearLayout} row is supported. Flyme's status
     * bar cluster is one; the control centre is a {@code RelativeLayout} that
     * keeps its stock battery anyway.
     *
     * <p>Never call this from a draw pass: it may add a view.
     */
    static void syncTypeLabel(android.view.View host) {
        if (host == null) {
            return;
        }
        final android.view.ViewParent parent = host.getParent();
        final io.github.yixing233.hyperduo.TrioAppearance a =
                io.github.yixing233.hyperduo.TrioConfig.appearance();
        final io.github.yixing233.hyperduo.TrioState state;
        synchronized (STATES) {
            state = STATES.get(host);
        }
        final java.lang.String type = (state == null) ? "" : state.mobileType;
        // The native mobile slot is hidden for the whole time the module owns the
        // battery (see applySlotVisibility), so the platform's own type text is
        // gone with it - this label is the only place the reading appears, and it
        // must not depend on where the signal dots happen to sit.
        final boolean wanted = a.glyph
                && a.typeOutOfRing
                && !excludedRegion(host)
                && !type.isEmpty()
                && (parent instanceof android.widget.LinearLayout);
        if (!wanted) {
            removeTypeLabel(host);
            return;
        }
        final android.widget.LinearLayout row = (android.widget.LinearLayout) parent;
        final float density = host.getResources().getDisplayMetrics().density;
        android.widget.TextView label;
        synchronized (STATES) {
            label = TYPE_LABELS.get(host);
        }
        if (label == null || label.getParent() != row) {
            removeTypeLabel(host);
            label = new android.widget.TextView(host.getContext());
            label.setSingleLine(true);
            label.setIncludeFontPadding(false);
            label.setGravity(android.view.Gravity.CENTER_VERTICAL);
            final android.widget.LinearLayout.LayoutParams lp =
                    new android.widget.LinearLayout.LayoutParams(
                            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                            android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.gravity = android.view.Gravity.CENTER_VERTICAL;
            int index = row.indexOfChild(host);
            row.addView(label, index < 0 ? row.getChildCount() : index, lp);
            TYPE_LABELS.put(host, label);
        }
        final int color = (state == null) ? 0xFFFFFFFF : state.foreground();
        label.setText(type);
        label.setTextColor(color);
        label.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,
                java.lang.Math.max(1f, a.outTypeSize * density));
        label.setTypeface(io.github.yixing233.hyperduo.TrioRenderer.typefaceFor(a.typeWeight));
        label.getPaint().setFakeBoldText(
                io.github.yixing233.hyperduo.TrioRenderer.fakeBoldFor(a.typeWeight));
        final android.widget.LinearLayout.LayoutParams lp =
                (android.widget.LinearLayout.LayoutParams) label.getLayoutParams();
        final int left = java.lang.Math.round(a.outTypeMarginLeft * density);
        final int right = java.lang.Math.round(a.outTypeMarginRight * density);
        if (lp.leftMargin != left || lp.rightMargin != right) {
            lp.leftMargin = left;
            lp.rightMargin = right;
            label.setLayoutParams(lp);
        }
    }

    /** Queues a label sync; safe to call from any hook, including a layout pass. */
    static void requestTypeLabelSync(final android.view.View host) {
        if (host == null || !TYPE_LABEL_PENDING.add(host)) {
            return;
        }
        host.post(new java.lang.Runnable() {
            @Override // java.lang.Runnable
            public void run() {
                TYPE_LABEL_PENDING.remove(host);
                try {
                    syncTypeLabel(host);
                } catch (java.lang.Throwable t) {
                    log(sModule, "type label failed: " + t);
                }
            }
        });
    }

    static void removeTypeLabel(android.view.View host) {
        final android.widget.TextView label;
        synchronized (STATES) {
            label = TYPE_LABELS.remove(host);
        }
        if (label != null) {
            android.view.ViewParent p = label.getParent();
            if (p instanceof android.view.ViewGroup) {
                ((android.view.ViewGroup) p).removeView(label);
            }
        }
    }


    /** The surfaces a battery host can live on. */
    static final int REGION_STATUS_BAR = 0;
    static final int REGION_CONTROL_CENTRE = 1;
    static final int REGION_NOTIFICATION = 2;

    private static final java.util.Map<android.view.View, java.lang.Integer> REGION_CACHE =
            java.util.Collections.synchronizedMap(new java.util.WeakHashMap<android.view.View, java.lang.Integer>());

    /**
     * Which surface a battery host belongs to.
     *
     * <p>The closest decisive ancestor wins, and the order matters: the status bar
     * itself sits inside the notification shade window on AOSP-derived ROMs, so a
     * name containing "shade" is a fallback, not a verdict. Classifying by "has a
     * shade ancestor" alone marked the status bar as the notification centre, and
     * "exclude the notification centre" then took the ring out of the status bar
     * as well.
     */
    static int regionOf(android.view.View host) {
        if (host.getParent() == null) {
            // Not attached: there is nothing to walk, and the default below is
            // only a guess. Flyme measures its status rows BEFORE attaching them,
            // so an unattached call used to cache every battery host as a status
            // bar within a second of process start - five hosts, five wrong
            // verdicts, and exclusion then had no surface left to act on. Not
            // cached; the attached call classifies and caches instead.
            return REGION_STATUS_BAR;
        }
        java.lang.Integer cached = REGION_CACHE.get(host);
        if (cached != null) {
            return cached.intValue();
        }
        int result = REGION_STATUS_BAR;
        boolean sawShade = false;
        boolean decided = false;
        android.view.ViewParent parent = host.getParent();
        for (int depth = 0; parent instanceof android.view.View && depth < 24; depth++) {
            java.lang.String name = parent.getClass().getName().toLowerCase(java.util.Locale.ROOT);
            if (name.contains("controlcenter") || name.contains("qsstatusbar") || name.contains("quickpanel")) {
                result = REGION_CONTROL_CENTRE;
                decided = true;
                break;
            }
            // Not a verdict. On Flyme the keyguard header is INCLUDED in the
            // notification panel - status_bar_expanded (NotificationPanelView)
            // pulls in keyguard_status_bar, and that header is the notification
            // centre's own top row (and the lock screen's). Deciding on its class
            // name, which says "statusbar", one step before the panel above it
            // can answer is exactly why "exclude the notification centre" never
            // reached this battery: it was filed as a status bar, and no
            // exclusion option touches the status bar. Keep walking; the panel
            // above decides.
            if (name.contains("keyguardstatusbar")) {
                parent = parent.getParent();
                continue;
            }
            if (name.contains("statusbar") || name.contains("status_bar")) {
                result = REGION_STATUS_BAR;
                decided = true;
                break;
            }
            if (name.contains("notification") || name.contains("shade")) {
                sawShade = true;
            }
            parent = parent.getParent();
        }
        if (!decided && sawShade) {
            result = REGION_NOTIFICATION;
        }
        // Once per host, not per frame: the one line that answers "which surface
        // was this battery filed under" straight from the LSPosed log.
        log(sModule, "battery region=" + regionName(result) + " host="
                + host.getClass().getSimpleName() + "@"
                + java.lang.Integer.toHexString(java.lang.System.identityHashCode(host)));
        REGION_CACHE.put(host, java.lang.Integer.valueOf(result));
        return result;
    }

    private static java.lang.String regionName(int region) {
        if (region == REGION_CONTROL_CENTRE) {
            return "control-centre";
        }
        return region == REGION_NOTIFICATION ? "notification" : "status-bar";
    }

    /** True when the user asked to leave the stock battery alone in this host's region. */
    static boolean excludedRegion(android.view.View host) {
        int setting = io.github.yixing233.hyperduo.TrioConfig.get().excludeRegions;
        if (setting == io.github.yixing233.hyperduo.Prefs.REGION_ALL) {
            return false;
        }
        int region = regionOf(host);
        if (region == REGION_CONTROL_CENTRE) {
            return setting == io.github.yixing233.hyperduo.Prefs.REGION_NO_CONTROL_CENTRE
                    || setting == io.github.yixing233.hyperduo.Prefs.REGION_NO_BOTH;
        }
        if (region == REGION_NOTIFICATION) {
            // A real surface on Flyme: the notification panel's own top row is
            // the keyguard header (see regionOf). The 1.7.5-flyme.28 shortcut
            // that folded this option into the control centre is withdrawn - it
            // was only needed while the panel's battery had no region of its own.
            return setting == io.github.yixing233.hyperduo.Prefs.REGION_NO_NOTIFICATION
                    || setting == io.github.yixing233.hyperduo.Prefs.REGION_NO_BOTH;
        }
        return false;
    }

    /**
     * Keeps the battery view drawable while the ring lives inside it.
     *
     * <p>See the {@code apply} hook: the "percentage only" style hides this very
     * view when the cable is out, and a hidden view does not draw the ring.
     */
    static void ensureHostVisible(android.view.View host) {
        if (!io.github.yixing233.hyperduo.TrioConfig.get().enabled
                || io.github.yixing233.hyperduo.FlymeHooks.excludedRegion(host)) {
            return;
        }
        if (host.getVisibility() != android.view.View.VISIBLE) {
            host.setVisibility(android.view.View.VISIBLE);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void applyBatteryPercent(android.view.View host) {
        java.lang.Object value = io.github.yixing233.hyperduo.Refl.get(sBatteryPercentField, host);
        if (!(value instanceof android.view.View)) {
            return;
        }
        android.view.View percent = (android.view.View) value;
        // The control centre keeps Flyme's own battery, percentage included.
        if (excludedRegion(host)) {
            if (percent.getAlpha() != 1f) {
                percent.setAlpha(1f);
            }
            return;
        }
        // Alpha, not visibility. Flyme measures the percentage view and pads it
        // from its own animation state; taking it out of the layout leaves the
        // stock battery with a stale width, which is what clipped "100" down to
        // "1" and pushed the bolt over the battery outline.
        float wanted = io.github.yixing233.hyperduo.TrioConfig.get().enabled ? 0f : 1f;
        if (percent.getAlpha() != wanted) {
            percent.setAlpha(wanted);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void restoreBatteryPercent(android.view.View host) {
        java.lang.Object value = io.github.yixing233.hyperduo.Refl.get(sBatteryPercentField, host);
        if (value instanceof android.view.View) {
            restoreChild((android.view.View) value);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static io.github.yixing233.hyperduo.TrioState stateFor(android.view.View host) {
        io.github.yixing233.hyperduo.TrioState state;
        synchronized (STATES) {
            state = STATES.get(host);
            if (state == null) {
                state = new io.github.yixing233.hyperduo.TrioState(host);
                STATES.put(host, state);
            }
        }
        return state;
    }

    private static java.lang.String slotOf(android.view.View child) {
        if (child == null) {
            return null;
        }
        java.lang.Object slot = callCached(SLOT_METHODS, child, "getSlot");
        if (!(slot instanceof java.lang.String)) {
            return null;
        }
        return (java.lang.String) slot;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static android.view.ViewGroup findIconContainer(android.view.View root) {
        if (ICON_CONTAINER.equals(root.getClass().getName()) && (root instanceof android.view.ViewGroup)) {
            return (android.view.ViewGroup) root;
        }
        if (!(root instanceof android.view.ViewGroup)) {
            return null;
        }
        android.view.ViewGroup group = (android.view.ViewGroup) root;
        for (int i = 0; i < group.getChildCount(); i++) {
            android.view.View found = findIconContainer(group.getChildAt(i));
            if (found instanceof android.view.ViewGroup) {
                return (android.view.ViewGroup) found;
            }
        }
        return null;
    }


    /* JADX INFO: Access modifiers changed from: private */
    public static void invalidateHosts() {
        java.util.List<android.view.View> hosts = new java.util.ArrayList<>();
        synchronized (STATES) {
            hosts.addAll(STATES.keySet());
        }
        for (int i = 0; i < hosts.size(); i++) {
            android.view.View host = hosts.get(i);
            if (host != null) {
                // The excluded check is the whole point: this path runs on every
                // battery event, and hiding the percent sibling for an excluded
                // host too (which is what 1.7.5-flyme.27/.28 did) took the control
                // centre's own percentage away while the user had explicitly asked
                // to leave that surface stock - the exclusion looked dead.
                if (io.github.yixing233.hyperduo.TrioConfig.get().enabled
                        && !io.github.yixing233.hyperduo.FlymeHooks.excludedRegion(host)) {
                    io.github.yixing233.hyperduo.FlymeHooks.hideBatteryExtras(host);
                } else {
                    io.github.yixing233.hyperduo.FlymeHooks.restoreBatteryExtras(host);
                }
                io.github.yixing233.hyperduo.FlymeHooks.requestTypeLabelSync(host);
                host.postInvalidate();
            }
        }
    }

    private static int hook(io.github.libxposed.api.XposedModule module, java.lang.reflect.Method method, java.lang.String id, io.github.libxposed.api.XposedInterface.Hooker hooker) {
        if (method == null) {
            log(module, "skip " + id + ": method not found");
            return 0;
        }
        try {
            module.hook(method).setId(id).intercept(hooker);
            return 1;
        } catch (java.lang.Throwable t) {
            log(module, "skip " + id + ": " + t);
            return 0;
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void log(io.github.libxposed.api.XposedModule module, java.lang.String message) {
        if (module == null) {
            return;
        }
        try {
            module.log(4, "HyperDuo", message);
        } catch (java.lang.Throwable th) {
        }
    }
}
