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

    private FlymeHooks() {
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
        java.util.List<android.view.ViewGroup> containers = new java.util.ArrayList<>();
        synchronized (STATUS_ICON_CONTAINERS) {
            containers.addAll(STATUS_ICON_CONTAINERS.keySet());
        }
        for (int i = 0; i < containers.size(); i++) {
            final android.view.ViewGroup container = containers.get(i);
            if (container != null) {
                container.post(new java.lang.Runnable() { // from class: io.github.yixing233.hyperduo.FlymeHooks.1
                    @Override // java.lang.Runnable
                    public void run() {
                        io.github.yixing233.hyperduo.FlymeHooks.applySlots(container);
                    }
                });
            }
        }
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
                io.github.yixing233.hyperduo.FlymeHooks.applyBatteryPercent(host);
                if (!io.github.yixing233.hyperduo.TrioConfig.get().enabled) {
                    return chain.proceed();
                }
                io.github.yixing233.hyperduo.TrioState state = io.github.yixing233.hyperduo.FlymeHooks.stateFor(host);
                state.refresh();
                io.github.yixing233.hyperduo.TrioRenderer.drawFlyme((android.graphics.Canvas) arg, host, state, io.github.yixing233.hyperduo.TrioConfig.get());
                return null;
            }
        });
        int measure = hook(module, io.github.yixing233.hyperduo.Refl.method(viewClass, "onMeasure", java.lang.Integer.TYPE, java.lang.Integer.TYPE), "hyperduo-flyme-battery-measure", new io.github.libxposed.api.XposedInterface.Hooker() { // from class: io.github.yixing233.hyperduo.FlymeHooks.4
            public java.lang.Object intercept(io.github.libxposed.api.XposedInterface.Chain chain) throws java.lang.Throwable {
                java.lang.Object self = chain.getThisObject();
                if (!(self instanceof android.view.View) || !io.github.yixing233.hyperduo.TrioConfig.get().enabled) {
                    return chain.proceed();
                }
                java.lang.Object currentDrawable = io.github.yixing233.hyperduo.Refl.get(io.github.yixing233.hyperduo.FlymeHooks.sBatteryDrawableField, self);
                java.lang.Object stableDrawable = io.github.yixing233.hyperduo.Refl.get(io.github.yixing233.hyperduo.FlymeHooks.sBatteryNormalDrawableField, self);
                if (!(currentDrawable instanceof android.graphics.drawable.Drawable) || !(stableDrawable instanceof android.graphics.drawable.Drawable)) {
                    return chain.proceed();
                }
                int clipWidth = io.github.yixing233.hyperduo.Refl.getInt(io.github.yixing233.hyperduo.FlymeHooks.sBatteryClipWidthField, self, 0);
                io.github.yixing233.hyperduo.Refl.set(io.github.yixing233.hyperduo.FlymeHooks.sBatteryDrawableField, self, stableDrawable);
                io.github.yixing233.hyperduo.Refl.set(io.github.yixing233.hyperduo.FlymeHooks.sBatteryClipWidthField, self, 0);
                try {
                    return chain.proceed();
                } finally {
                    io.github.yixing233.hyperduo.Refl.set(io.github.yixing233.hyperduo.FlymeHooks.sBatteryDrawableField, self, currentDrawable);
                    io.github.yixing233.hyperduo.Refl.set(io.github.yixing233.hyperduo.FlymeHooks.sBatteryClipWidthField, self, java.lang.Integer.valueOf(clipWidth));
                }
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
                    ((android.view.View) self).postInvalidate();
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
                    ((android.view.View) self).invalidate();
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
                    io.github.yixing233.hyperduo.FlymeHooks.registerIconContainer((android.view.ViewGroup) icons);
                    io.github.yixing233.hyperduo.FlymeHooks.log(io.github.yixing233.hyperduo.FlymeHooks.sModule, "Flyme Control Center icon container captured");
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
    public static void registerIconContainer(android.view.ViewGroup container) {
        synchronized (STATUS_ICON_CONTAINERS) {
            STATUS_ICON_CONTAINERS.put(container, java.lang.Boolean.TRUE);
        }
        applySlots(container);
        updateWifiPresence(container);
    }

    private static int hookIconContainer(io.github.libxposed.api.XposedModule module, java.lang.ClassLoader cl) {
        java.lang.Class<?> container = io.github.yixing233.hyperduo.Refl.cls(ICON_CONTAINER, cl);
        if (container == null) {
            log(module, "StatusIconContainer missing");
            return 0;
        }
        sIgnoredSlotsField = io.github.yixing233.hyperduo.Refl.field(container, "mIgnoredSlots");
        return hook(module, io.github.yixing233.hyperduo.Refl.method(container, "onLayout", java.lang.Boolean.TYPE, java.lang.Integer.TYPE, java.lang.Integer.TYPE, java.lang.Integer.TYPE, java.lang.Integer.TYPE), "hyperduo-flyme-icon-layout", new io.github.libxposed.api.XposedInterface.Hooker() { // from class: io.github.yixing233.hyperduo.FlymeHooks.11
            public java.lang.Object intercept(io.github.libxposed.api.XposedInterface.Chain chain) throws java.lang.Throwable {
                java.lang.Object result = chain.proceed();
                java.lang.Object self = chain.getThisObject();
                if ((self instanceof android.view.ViewGroup) && io.github.yixing233.hyperduo.FlymeHooks.isRegistered((android.view.ViewGroup) self)) {
                    io.github.yixing233.hyperduo.FlymeHooks.applySlots((android.view.ViewGroup) self);
                    io.github.yixing233.hyperduo.FlymeHooks.updateWifiPresence((android.view.ViewGroup) self);
                }
                return result;
            }
        });
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
                    io.github.yixing233.hyperduo.TrioState.setWifiPresent(java.lang.Boolean.TRUE.equals(visible));
                    io.github.yixing233.hyperduo.FlymeHooks.invalidateHosts();
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

    /* JADX INFO: Access modifiers changed from: private */
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

    /* JADX INFO: Access modifiers changed from: private */
    public static void applyBatteryPercent(android.view.View host) {
        java.lang.Object value = io.github.yixing233.hyperduo.Refl.get(sBatteryPercentField, host);
        if (!(value instanceof android.view.View)) {
            return;
        }
        android.view.View percent = (android.view.View) value;
        if (io.github.yixing233.hyperduo.TrioConfig.get().enabled) {
            hideChild(percent);
        } else {
            restoreChild(percent);
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
        java.lang.Object slot = io.github.yixing233.hyperduo.Refl.callByName(child, "getSlot");
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
                java.lang.Object visible = io.github.yixing233.hyperduo.Refl.callByName(child, "isIconVisible");
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
