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
    private static final java.util.Map<android.view.ViewGroup, java.lang.Boolean> STATUS_ICON_CONTAINERS = java.util.Collections.synchronizedMap(new java.util.WeakHashMap());

    // Reflection on the layout path has to be cached: onLayout runs per frame,
    // and a getMethod() per child per pass was what turned a re-layout into an
    // ANR. The re-entry flag keeps a setVisibility() that schedules another
    // layout pass from nesting inside this one.
    private static final java.util.Map<java.lang.Class<?>, java.lang.reflect.Method> SLOT_METHODS = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Map<java.lang.Class<?>, java.lang.reflect.Method> VISIBLE_METHODS = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Set<java.lang.Class<?>> NO_METHOD = java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<java.lang.Class<?>, java.lang.Boolean>());
    private static final java.util.Set<android.view.ViewGroup> PENDING_SLOTS = java.util.Collections.newSetFromMap(new java.util.WeakHashMap<android.view.ViewGroup, java.lang.Boolean>());
    private static volatile boolean sInApply;
    private static volatile long sLastSlotPass;

    private FlymeHooks() {
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

    static int install(io.github.libxposed.api.XposedModule module, java.lang.ClassLoader cl) {
        sModule = module;
        sActive = true;
        int count = 0 + hookBatteryView(module, cl);
        return count + hookStatusBarCapture(module, cl) + hookControlCenterCapture(module, cl) + hookKeyguardStatusBarCapture(module, cl) + hookIconContainer(module, cl) + hookWifiState(module, cl) + hookMobileSignal(module, cl) + hookMobileTypeIcon(module, cl);
    }

    static void onConfigChanged() {
        // The master switch decides what the stock icons should look like: hide
        // them while the ring takes over, put them back and re-run layout when it
        // does not, so a restore never leaves the row squeezed together.
        syncStockIcons();
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
                        io.github.yixing233.hyperduo.FlymeHooks.applyBatteryPercent(host);
                        if (io.github.yixing233.hyperduo.TrioConfig.get().enabled) {
                            io.github.yixing233.hyperduo.FlymeHooks.requestRing(host);
                        } else {
                            io.github.yixing233.hyperduo.FlymeHooks.requestRingRemoval(host);
                        }
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
                    io.github.yixing233.hyperduo.FlymeHooks.applyBatteryPercent(host);
                    io.github.yixing233.hyperduo.FlymeHooks.requestRingRemoval(host);
                    return chain.proceed();
                }
                // A region the user excluded keeps Flyme's own battery, stock look and all.
                if (io.github.yixing233.hyperduo.FlymeHooks.excludedRegion(host)) {
                    io.github.yixing233.hyperduo.FlymeHooks.applyBatteryPercent(host);
                    return chain.proceed();
                }
                // Everything else is drawn by the ring's own view: Flyme's battery
                // view changes width, position and visibility with the four stock
                // styles, and a glyph drawn inside it inherits all of that.
                io.github.yixing233.hyperduo.FlymeHooks.requestRing(host);
                return null;
            }
        });
        // The measure hook is gone on purpose. Forcing the normal drawable and a
        // zero clip width made the view narrower than Flyme's own layout expects
        // whenever the percentage is drawn outside the battery (or is the only
        // thing shown), and the whole right-hand cluster shifted to compensate.
        int measure = 0;
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
                    io.github.yixing233.hyperduo.FlymeHooks.restoreBatteryPercent((android.view.View) self);
                    io.github.yixing233.hyperduo.FlymeHooks.STATES.remove((android.view.View) self);
                }
                return result;
            }
        });
        return draw + measure + levelChanged + dark + detached;
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
     * Hides the managed slots - or puts them back.
     *
     * <p>{@code setVisibility(GONE)} alone leaves the space behind: Flyme's
     * container lays its slots out from {@code mIgnoredSlots}, and a slot that is
     * merely invisible still gets its width. Adding the slot to that list is what
     * actually frees the gap, and removing it is what restores the row without
     * squeezing.
     */
    @SuppressWarnings("unchecked")
    private static void applySlotVisibility(android.view.ViewGroup container, boolean hide) {
        try {
            java.lang.Object raw = io.github.yixing233.hyperduo.Refl.get(sIgnoredSlotsField, container);
            java.util.List<java.lang.String> ignored = (raw instanceof java.util.List)
                    ? (java.util.List<java.lang.String>) raw : null;
            boolean changed = false;
            for (int i = 0; i < container.getChildCount(); i++) {
                android.view.View child = container.getChildAt(i);
                java.lang.Object slot = callCached(SLOT_METHODS, child, "getSlot");
                java.lang.String slotName = (slot instanceof java.lang.String) ? (java.lang.String) slot : null;
                if (slotName == null || !MANAGED_SLOTS.contains(slotName)) {
                    continue;
                }
                int wanted = hide ? android.view.View.GONE : android.view.View.VISIBLE;
                if (child.getVisibility() != wanted) {
                    child.setVisibility(wanted);
                    changed = true;
                }
                if (ignored != null) {
                    if (hide) {
                        if (!ignored.contains(slotName)) {
                            ignored.add(slotName);
                            changed = true;
                        }
                    } else if (ignored.remove(slotName)) {
                        changed = true;
                    }
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
                    // This is Flyme's own "5G" label. The ring draws the type
                    // itself, so the stock one is hidden whenever the module is on
                    // - otherwise the setting for it has nothing to act on.
                    android.view.View typeView = (android.view.View) target;
                    if (io.github.yixing233.hyperduo.TrioConfig.get().enabled) {
                        if (typeView.getVisibility() != android.view.View.GONE) {
                            typeView.setVisibility(android.view.View.GONE);
                        }
                    } else if (typeView.getVisibility() == android.view.View.GONE) {
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


    public static void applySlots(android.view.ViewGroup container) {
        java.util.List<java.lang.String> current;
        if (!isRegistered(container)) {
            return;
        }
        io.github.yixing233.hyperduo.TrioAppearance appearance = io.github.yixing233.hyperduo.TrioConfig.appearance();
        boolean enabled = appearance.glyph;
        java.util.List<java.lang.String> wanted = new java.util.ArrayList<>(2);
        if (enabled && appearance.wifi) {
            wanted.add("wifi");
        }
        if (enabled && appearance.signalDots() && !appearance.typeOutOfRing) {
            wanted.add("mobile");
            wanted.add("stacked_mobile");
        }
        java.util.List<java.lang.String> ignored = ignoredSlots(container);
        synchronized (ADDED_SLOTS) {
            current = ADDED_SLOTS.get(container);
            if (current == null) {
                current = new java.util.ArrayList();
                ADDED_SLOTS.put(container, current);
            }
        }
        boolean slotsChanged = false;
        for (int i = 0; i < wanted.size(); i++) {
            java.lang.String slot = wanted.get(i);
            if (ignored != null && !ignored.contains(slot)) {
                ignored.add(slot);
                if (!current.contains(slot)) {
                    current.add(slot);
                }
                slotsChanged = true;
            }
        }
        int i2 = current.size();
        for (int i3 = i2 - 1; i3 >= 0; i3--) {
            java.lang.String slot2 = current.get(i3);
            if (!wanted.contains(slot2)) {
                if (ignored != null) {
                    ignored.remove(slot2);
                }
                current.remove(i3);
                slotsChanged = true;
            }
        }
        int count = container.getChildCount();
        for (int i4 = 0; i4 < count; i4++) {
            android.view.View child = container.getChildAt(i4);
            java.lang.String slot3 = slotOf(child);
            if (slot3 != null && MANAGED_SLOTS.contains(slot3)) {
                if (wanted.contains(slot3)) {
                    hideChild(child);
                } else {
                    restoreChild(child);
                }
            }
        }
        if (slotsChanged) {
            container.requestLayout();
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static boolean isRegistered(android.view.ViewGroup container) {
        boolean containsKey;
        synchronized (STATUS_ICON_CONTAINERS) {
            containsKey = STATUS_ICON_CONTAINERS.containsKey(container);
        }
        return containsKey;
    }

    private static java.util.List<java.lang.String> ignoredSlots(java.lang.Object container) {
        java.lang.Object value = io.github.yixing233.hyperduo.Refl.get(sIgnoredSlotsField, container);
        if (value instanceof java.util.List) {
            return (java.util.List) value;
        }
        return null;
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

    /** Cached because it walks the parent chain, and onDraw runs every frame. */
    private static final java.util.Map<android.view.View, java.lang.Boolean> QS_HOSTS =
            java.util.Collections.synchronizedMap(new java.util.WeakHashMap<android.view.View, java.lang.Boolean>());

    /** Battery host -> the ring view standing in for it. */
    private static final java.util.Map<android.view.View, io.github.yixing233.hyperduo.FlymeRingView> RINGS =
            java.util.Collections.synchronizedMap(new java.util.WeakHashMap<android.view.View, io.github.yixing233.hyperduo.FlymeRingView>());

    private static final java.util.Set<android.view.View> RING_PENDING =
            java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<android.view.View, java.lang.Boolean>());

    /** Queues the swap to a ring view; safe to call from onDraw. */
    static void requestRing(final android.view.View host) {
        if (RINGS.containsKey(host) || !RING_PENDING.add(host)) {
            return;
        }
        host.post(new java.lang.Runnable() {
            @Override
            public void run() {
                RING_PENDING.remove(host);
                try {
                    installRing(host);
                } catch (java.lang.Throwable t) {
                    log(sModule, "ring install failed: " + t);
                }
            }
        });
    }

    /** Queues the reverse swap; safe to call from onDraw. */
    static void requestRingRemoval(final android.view.View host) {
        if (!RINGS.containsKey(host) || !RING_PENDING.add(host)) {
            return;
        }
        host.post(new java.lang.Runnable() {
            @Override
            public void run() {
                RING_PENDING.remove(host);
                try {
                    removeRing(host);
                } catch (java.lang.Throwable t) {
                    log(sModule, "ring removal failed: " + t);
                }
            }
        });
    }

    private static void installRing(android.view.View host) {
        if (RINGS.containsKey(host)) {
            return;
        }
        android.view.ViewParent parent = host.getParent();
        if (!(parent instanceof android.view.ViewGroup)) {
            return;
        }
        android.view.ViewGroup group = (android.view.ViewGroup) parent;
        int index = group.indexOfChild(host);
        int[] size = ringSizePx(host);
        android.view.ViewGroup.LayoutParams lp = host.getLayoutParams();
        android.view.ViewGroup.LayoutParams ringLp;
        if (lp instanceof android.widget.LinearLayout.LayoutParams) {
            ringLp = new android.widget.LinearLayout.LayoutParams(size[0], size[1]);
            android.widget.LinearLayout.LayoutParams src = (android.widget.LinearLayout.LayoutParams) lp;
            ((android.widget.LinearLayout.LayoutParams) ringLp).gravity = src.gravity;
            ringLp.width = size[0];
            ringLp.height = size[1];
        } else if (lp instanceof android.view.ViewGroup.MarginLayoutParams) {
            android.view.ViewGroup.MarginLayoutParams src = (android.view.ViewGroup.MarginLayoutParams) lp;
            android.view.ViewGroup.MarginLayoutParams mp =
                    new android.view.ViewGroup.MarginLayoutParams(src);
            mp.width = size[0];
            mp.height = size[1];
            ringLp = mp;
        } else {
            ringLp = new android.view.ViewGroup.LayoutParams(size[0], size[1]);
        }
        io.github.yixing233.hyperduo.FlymeRingView ring =
                new io.github.yixing233.hyperduo.FlymeRingView(host.getContext(), host);
        group.addView(ring, index < 0 ? 0 : index, ringLp);
        host.setVisibility(android.view.View.GONE);
        RINGS.put(host, ring);
        hideBatteryExtras(host);
        ring.postDelayed(new java.lang.Runnable() {
            @Override
            public void run() {
                android.view.ViewParent p = ring.getParent();
                if (!(p instanceof android.view.ViewGroup)) {
                    return;
                }
                android.view.ViewGroup g = (android.view.ViewGroup) p;
                java.lang.StringBuilder kids = new java.lang.StringBuilder();
                for (int i = 0; i < g.getChildCount(); i++) {
                    android.view.View c = g.getChildAt(i);
                    kids.append(c.getClass().getSimpleName()).append('(').append(c.getWidth())
                            .append(c.getVisibility() == android.view.View.GONE ? "G" : "")
                            .append(") ");
                }
                log(sModule, "row parent=" + g.getClass().getSimpleName() + " pW=" + g.getWidth()
                        + " ringL=" + ring.getLeft() + " ringR=" + ring.getRight()
                        + " ringW=" + ring.getWidth() + " kids=" + kids);
                int[] loc = new int[2];
                ring.getLocationOnScreen(loc);
                int[] ploc = new int[2];
                g.getLocationOnScreen(ploc);
                if (g.getChildCount() > 1 && g.getChildAt(1) instanceof android.view.ViewGroup) {
                    android.view.ViewGroup icons = (android.view.ViewGroup) g.getChildAt(1);
                    java.lang.StringBuilder ik = new java.lang.StringBuilder();
                    for (int i = 0; i < icons.getChildCount(); i++) {
                        android.view.View c = icons.getChildAt(i);
                        java.lang.Object slot = callCached(SLOT_METHODS, c, "getSlot");
                        ik.append(slot).append('/').append(c.getWidth())
                                .append(c.getVisibility() == android.view.View.GONE ? "G" : "").append(' ');
                    }
                    log(sModule, "icons w=" + icons.getWidth() + " vis=" + icons.getVisibility()
                            + " kids=" + ik);
                }
                log(sModule, "row screen pX=" + ploc[0] + " ringX=" + loc[0]
                        + " ringEnd=" + (loc[0] + ring.getWidth()) + " ringY=" + loc[1]
                        + " screenW=" + g.getResources().getDisplayMetrics().widthPixels);
            }
        }, 1500);
    }

    private static void removeRing(android.view.View host) {
        io.github.yixing233.hyperduo.FlymeRingView ring = RINGS.remove(host);
        if (ring != null) {
            android.view.ViewParent parent = ring.getParent();
            if (parent instanceof android.view.ViewGroup) {
                ((android.view.ViewGroup) parent).removeView(ring);
            }
        }
        if (host.getVisibility() != android.view.View.VISIBLE) {
            host.setVisibility(android.view.View.VISIBLE);
        }
        restoreBatteryExtras(host);
    }

    /**
     * Hides the rest of Flyme's battery cluster.
     *
     * <p>The percentage is not always inside the battery view: for two of the
     * four stock styles it is a sibling of its own ({@code FlymeBatteryTextView}),
     * and that is why the number showed next to the ring and why the row kept
     * changing width. Everything in the cluster that is not the ring and not the
     * battery host itself goes.
     */
    static void hideBatteryExtras(android.view.View host) {
        android.view.ViewParent parent = host.getParent();
        if (!(parent instanceof android.view.ViewGroup)) {
            return;
        }
        android.view.ViewGroup group = (android.view.ViewGroup) parent;
        for (int i = 0; i < group.getChildCount(); i++) {
            android.view.View child = group.getChildAt(i);
            if (child == host || child instanceof io.github.yixing233.hyperduo.FlymeRingView) {
                continue;
            }
            if (child.getClass().getName().contains("FlymeBatteryTextView")) {
                if (child.getVisibility() != android.view.View.GONE) {
                    child.setVisibility(android.view.View.GONE);
                }
            }
        }
    }

    static void restoreBatteryExtras(android.view.View host) {
        android.view.ViewParent parent = host.getParent();
        if (!(parent instanceof android.view.ViewGroup)) {
            return;
        }
        android.view.ViewGroup group = (android.view.ViewGroup) parent;
        for (int i = 0; i < group.getChildCount(); i++) {
            android.view.View child = group.getChildAt(i);
            if (child.getClass().getName().contains("FlymeBatteryTextView")
                    && child.getVisibility() == android.view.View.GONE) {
                child.setVisibility(android.view.View.VISIBLE);
            }
        }
    }

    /**
     * The box the ring gets: the stock battery icon's own footprint.
     *
     * <p>Sizing it square from the height made the view wider than the icon it
     * replaced, and a right-aligned row pushed everything left by the difference.
     * That only showed after the next layout pass, which is why it looked right
     * until the screen was left and rebuilt.
     */
    private static int[] ringSizePx(android.view.View host) {
        float density = host.getResources().getDisplayMetrics().density;
        for (java.lang.reflect.Field field : new java.lang.reflect.Field[]{
                sBatteryNormalDrawableField, sBatteryDrawableField}) {
            try {
                Object value = io.github.yixing233.hyperduo.Refl.get(field, host);
                if (value instanceof android.graphics.drawable.Drawable) {
                    android.graphics.drawable.Drawable d = (android.graphics.drawable.Drawable) value;
                    int w = d.getIntrinsicWidth();
                    int h = d.getIntrinsicHeight();
                    if (w > 0 && h > 0) {
                        return new int[]{w, h};
                    }
                }
            } catch (java.lang.Throwable ignored) {
            }
        }
        return new int[]{java.lang.Math.round(14 * density), java.lang.Math.round(20 * density)};
    }

    /** True when this view lives inside the control centre rather than the status bar. */
    /** Which surface a battery host belongs to. */
    static final int REGION_STATUS_BAR = 0;
    static final int REGION_CONTROL_CENTRE = 1;
    static final int REGION_NOTIFICATION = 2;

    private static final java.util.Map<android.view.View, java.lang.Integer> REGION_CACHE =
            java.util.Collections.synchronizedMap(new java.util.WeakHashMap<android.view.View, java.lang.Integer>());

    static int regionOf(android.view.View host) {
        java.lang.Integer cached = REGION_CACHE.get(host);
        if (cached != null) {
            return cached.intValue();
        }
        int result = REGION_STATUS_BAR;
        android.view.ViewParent parent = host.getParent();
        for (int depth = 0; parent instanceof android.view.View && depth < 24; depth++) {
            java.lang.String name = parent.getClass().getName().toLowerCase(java.util.Locale.ROOT);
            if (name.contains("controlcenter") || name.contains("qsstatusbar") || name.contains("quickpanels")) {
                result = REGION_CONTROL_CENTRE;
                break;
            }
            if (name.contains("notification") || name.contains("shade")) {
                result = REGION_NOTIFICATION;
            }
            parent = parent.getParent();
        }
        REGION_CACHE.put(host, java.lang.Integer.valueOf(result));
        return result;
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
            return setting == io.github.yixing233.hyperduo.Prefs.REGION_NO_NOTIFICATION
                    || setting == io.github.yixing233.hyperduo.Prefs.REGION_NO_BOTH;
        }
        return false;
    }

    static boolean inControlCenter(android.view.View host) {
        return regionOf(host) != REGION_STATUS_BAR;
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
    public static void updateWifiPresence(android.view.ViewGroup container) {
        boolean z;
        boolean present = false;
        int i = 0;
        while (true) {
            if (i >= container.getChildCount()) {
                break;
            }
            android.view.View child = container.getChildAt(i);
            if (!"wifi".equals(slotOf(child))) {
                i++;
            } else {
                java.lang.Object visible = callCached(VISIBLE_METHODS, child, "isIconVisible");
                if (visible instanceof java.lang.Boolean) {
                    z = ((java.lang.Boolean) visible).booleanValue();
                } else {
                    z = child.getVisibility() == 0;
                }
                present = z;
            }
        }
        if (io.github.yixing233.hyperduo.TrioState.setWifiPresent(present)) {
            invalidateHosts();
        }
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
                io.github.yixing233.hyperduo.FlymeHooks.hideBatteryExtras(host);
                host.postInvalidate();
                // The host is hidden while the ring stands in for it, so the ring
                // is what actually has to repaint.
                io.github.yixing233.hyperduo.FlymeRingView ring = RINGS.get(host);
                if (ring != null) {
                    ring.postInvalidate();
                }
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
