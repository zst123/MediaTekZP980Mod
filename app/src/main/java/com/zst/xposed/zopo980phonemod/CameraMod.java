/*
 * Copyright (C) 2016 zst123
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.zst.xposed.zopo980phonemod;


import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.hardware.Camera;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_InitPackageResources.InitPackageResourcesParam;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class CameraMod {

    public static final String PKG = "com.android.gallery3d";

    /** Fix laggy menu by disabling animation */
    public static void loadRes(InitPackageResourcesParam p) {
        if (!p.packageName.equals(PKG)) return;
        p.res.setReplacement(PKG, "anim", "setting_popup_grow_fade_in", Xposed.modRes.fwd(R.anim.setting_popup_grow_fade_in));
        p.res.setReplacement(PKG, "anim", "setting_popup_shrink_fade_out", Xposed.modRes.fwd(R.anim.setting_popup_shrink_fade_out));
    }

    public static void fixPref(LoadPackageParam lpp) {
        if (!lpp.packageName.equals(PKG)) return;
        fixSettingReset(lpp);
        fixLaggyCamera(lpp);
        addCameraExposureSeekBar(lpp);
        addCameraOverlayGrid(lpp);
    }

    private static void fixSettingReset(LoadPackageParam lpp) {
        // Fix Setting Reset
        final Class<?> classSettingChecker = XposedHelpers.findClass("com.android.camera.SettingChecker", lpp.classLoader);
        XposedBridge.hookAllMethods(classSettingChecker, "resetSettings", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                super.afterHookedMethod(param);
                if (param.args.length != 0) {
                    final SharedPreferences pref = (SharedPreferences) param.args[0];
                    Editor editor = pref.edit();
                    editor.putString("pref_camera_edge_key", "high");
                    editor.putString("pref_camera_hue_key", "middle");
                    editor.putString("pref_camera_saturation_key", "high");
                    editor.putString("pref_camera_brightness_key", "low");
                    editor.putString("pref_camera_contrast_key", "high");
                    editor.apply();
                    // XposedBridge.log("Camera MTK: clearUserSettings");
                }
            }
        });
    }


    /**
     * Source of Mediatek Camera app:
     * https://github.com/ariafan/p201-packages/blob/master/apps/Camera/src/com/android/camera/SettingChecker.java
     * https://github.com/ariafan/p201-packages/blob/master/apps/Camera/src/com/android/camera/Camera.java
     * https://github.com/LuckJC/pro-pk/blob/61d8160bfaf28eea07a59651aace1f941dc82a4c/apps/Camera/src/com/mediatek/camera/AndroidCamera.java
     *
     *
     * Replace Capture Animation with Toast.
     */
    private static Toast sPreviewToast;
    private static void fixLaggyCamera(LoadPackageParam lpp) {
        final Class<?> classAppCamera = XposedHelpers.findClass("com.android.camera.Camera", lpp.classLoader);
        XposedBridge.hookAllMethods(classAppCamera, "onCreate", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                Activity thiz = (Activity) param.thisObject;
                sPreviewToast = createLoaderToast(thiz);
            }
        });
        XposedBridge.hookAllMethods(classAppCamera, "animateCapture", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                super.beforeHookedMethod(param);
                if (sPreviewToast != null) {
                    sPreviewToast.show();
                }
                param.setResult(null);
            }
        });
        final Class<?> classMtkCamera = XposedHelpers.findClass("com.mediatek.camera.AndroidCamera", lpp.classLoader);
        XposedBridge.hookAllMethods(classMtkCamera, "startPreview", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                if (sPreviewToast != null) {
                    sPreviewToast.cancel();
                }
            }
        });
    }

    private static Toast createLoaderToast(Context thiz) {
        final Toast toast = new Toast(thiz);
        toast.setDuration(Toast.LENGTH_LONG);
        toast.setGravity(Gravity.CENTER, 0, 0);

        final TextView tv = new TextView(thiz);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        tv.setText("Saving");

        final ProgressBar pb = new ProgressBar(thiz, null, android.R.attr.progressBarStyleHorizontal);
        pb.setIndeterminate(true);

        final LinearLayout lout = new LinearLayout(thiz);
        lout.setOrientation(LinearLayout.VERTICAL);
        float scale = thiz.getResources().getDisplayMetrics().density;
        int pad = (int) (16 * scale + 0.5f);
        lout.setPadding(pad, pad, pad, pad);
        lout.addView(tv);
        lout.addView(pb);
        lout.setGravity(Gravity.CENTER);
        lout.setBackgroundColor(Color.argb(160, 0, 0, 0));

        toast.setView(lout);

        // Disable toast animation
        Object tn = XposedHelpers.getObjectField(toast, "mTN");
        WindowManager.LayoutParams paramz = (WindowManager.LayoutParams)
                XposedHelpers.getObjectField(tn, "mParams");
        paramz.windowAnimations = 0;
        XposedHelpers.setObjectField(tn, "mParams", paramz);

        return toast;
    }

    private static void addCameraExposureSeekBar(LoadPackageParam lpp) {
        final Class<?> classAppCamera = XposedHelpers.findClass("com.android.camera.Camera", lpp.classLoader);
        XposedBridge.hookAllMethods(classAppCamera, "onCreate", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                final Activity thiz = (Activity) param.thisObject;

                final RelativeLayout rOut = (RelativeLayout)
                        XposedHelpers.getObjectField(param.thisObject, "mPreviewFrameLayout");

                float scale = thiz.getResources().getDisplayMetrics().density;
                RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams((int)(280 * scale),
                        RelativeLayout.LayoutParams.WRAP_CONTENT);
                params.addRule(RelativeLayout.ALIGN_TOP);
                params.addRule(RelativeLayout.CENTER_HORIZONTAL);

                SeekBar sb = new ExposureSeekBar(thiz, param.thisObject);

                rOut.addView(sb, params);
            }
        });
    }

    private static class ExposureSeekBar extends SeekBar {
        final Object thisObject;
        final Object cam_device;
        Camera.Parameters paramz;

        public ExposureSeekBar(Context context, Object thiz) {
            super(context);
            thisObject = thiz;
            cam_device = XposedHelpers.getObjectField(thisObject, "mCameraDevice");

            retrieveParams();

            final int minVal = paramz.getMinExposureCompensation();
            final int maxVal = paramz.getMaxExposureCompensation();
            final int fauxMaxVal = maxVal - minVal;
            final int fauxInitialVal = paramz.getExposureCompensation() - minVal;
            setMax(fauxMaxVal);
            setProgress(fauxInitialVal);

            setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, final int fauxCurrentVal, boolean fromUser) {
                    if (fromUser && paramz != null && cam_device != null) {
                        paramz.setExposureCompensation(fauxCurrentVal + minVal);
                        updateParams();
                        //Toast.makeText((Activity)thisObject, "fauxCurrentVal:" + fauxCurrentVal + "minVal:" + minVal + "maxVal:" + maxVal,Toast.LENGTH_SHORT).show();
                    }   //mCameraDevice.setParameters(mParameters);
                }
                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                    retrieveParams();
                }
                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {}
            });
        }

        private void retrieveParams() {
            paramz = (Camera.Parameters) XposedHelpers.getObjectField(thisObject, "mParameters");
        }
        private void updateParams() {
            XposedHelpers.callMethod(cam_device, "setParameters", paramz);
            paramz = null;
        }
    }

    private static void addCameraOverlayGrid(LoadPackageParam lpp) {
        final Class<?> classAppCamera = XposedHelpers.findClass("com.android.camera.Camera", lpp.classLoader);
        XposedBridge.hookAllMethods(classAppCamera, "onCreate", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                RelativeLayout rOut = (RelativeLayout)
                        XposedHelpers.getObjectField(param.thisObject, "mPreviewFrameLayout");

                GridOverlay gr = new GridOverlay(rOut.getContext());
                //float scale = rOut.getResources().getDisplayMetrics().density;
                RelativeLayout.LayoutParams params_grid = new RelativeLayout.LayoutParams(
                        RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT);
                params_grid.addRule(RelativeLayout.CENTER_IN_PARENT, RelativeLayout.TRUE);
                rOut.addView(gr, params_grid);

                RelativeLayout.LayoutParams params_toggle = new RelativeLayout.LayoutParams(
                        RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
                params_toggle.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, RelativeLayout.TRUE);
                params_toggle.addRule(RelativeLayout.ALIGN_LEFT, RelativeLayout.TRUE);
                rOut.addView(new GridOverlay.Toggle(rOut.getContext(), gr), params_toggle);
            }
        });
    }

    private static class GridOverlay extends FrameLayout {
        public GridOverlay(final Context c) {
            super(c);
            setGrid();
        }
        public SharedPreferences getPref() {
            return getContext().getSharedPreferences("zst123", Context.MODE_PRIVATE);
        }
        public int getGrid() {
            return getPref().getInt("grid", 0);
        }
        public int setGrid() {
            switch (getGrid()) {
                case 0:
                    setBackgroundColor(Color.TRANSPARENT);
                    break;
                case 1:
                    setBackgroundDrawable(Xposed.modRes.getDrawable(R.drawable.plugin_vf_grid_thirds16x9));
                    break;
                case 2:
                    setBackgroundDrawable(Xposed.modRes.getDrawable(R.drawable.plugin_vf_grid_trisec16x9));
                    break;
                case 3:
                    setBackgroundDrawable(Xposed.modRes.getDrawable(R.drawable.plugin_vf_grid_golden_a_16x9));
                    break;
                case 4:
                    setBackgroundDrawable(Xposed.modRes.getDrawable(R.drawable.plugin_vf_grid_golden_b_16x9));
                    break;
                case 5:
                    setBackgroundDrawable(Xposed.modRes.getDrawable(R.drawable.plugin_vf_grid_golden_c_16x9));
                    break;
                case 6:
                    setBackgroundDrawable(Xposed.modRes.getDrawable(R.drawable.plugin_vf_grid_golden_d_16x9));
                    break;
            }
            return getGrid();
        }

        public int switchGrid() {
            final SharedPreferences sharedpreferences = getPref();
            final Editor editor = sharedpreferences.edit();
            switch (sharedpreferences.getInt("grid", 0)) {
                case 0: editor.putInt("grid", 1); break;
                case 1: editor.putInt("grid", 0); break;
                /*case 2: editor.putInt("grid", 3); break;
                case 3: editor.putInt("grid", 4); break;
                case 4: editor.putInt("grid", 5); break;
                case 5: editor.putInt("grid", 6); break;
                case 6: editor.putInt("grid", 0); break;*/
            }
            editor.commit();
            return setGrid();
        }
        public boolean onTouchEvent(MotionEvent event) {
            return false;
        }

        public static class Toggle extends Button implements OnClickListener{
            final GridOverlay mGo;
            public Toggle(Context c, GridOverlay go) {
                super(c);
                mGo = go;
                setMinWidth(0);
                setPadding(0,0,0,0);
                changeText(mGo.getGrid());
                //setBackgroundResource(android.R.attr.selectableItemBackground);
                setOnClickListener(this);
            }
            private void changeText(int n) {
                setText("[" + n + "]");
            }
            @Override
            public void onClick(View v) {
                changeText(mGo.switchGrid());
            }
        }
    }

}

